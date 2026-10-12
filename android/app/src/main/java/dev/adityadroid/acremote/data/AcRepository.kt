package dev.adityadroid.acremote.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import javax.net.ssl.SSLSocketFactory

enum class Phase { NEEDS_LOGIN, LOADING, READY }
enum class Link { IDLE, CONNECTING, CONNECTED }

data class UiState(
    val phase: Phase = Phase.NEEDS_LOGIN,
    val loggingIn: Boolean = false,
    val loginError: String? = null,
    val device: Device? = null,
    val ac: AcState = AcState(),
    val hasStatus: Boolean = false,
    val link: Link = Link.IDLE,
    val syncing: Boolean = false,
    val error: String? = null,
)

/**
 * Process-wide singleton shared by the activity and the Quick Settings tile.
 * REST for login / homes / status, MQTT (Paho, TLS) for live status and control.
 */
object AcRepository {
    private const val TAG = "AcRepository"
    private const val SYNC_TIMEOUT_MS = 4_000L
    private const val TEMP_DEBOUNCE_MS = 450L
    private const val RECONNECT_DELAY_MS = 5_000L
    private const val IDLE_DISCONNECT_MS = 20_000L

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private lateinit var store: CredentialStore
    private val authMutex = Mutex()
    private val mqttMutex = Mutex()
    private val deviceMutex = Mutex()

    @Volatile private var session: Session? = null
    @Volatile private var homeId: String? = null
    @Volatile private var device: Device? = null
    @Volatile private var mqtt: MqttClient? = null
    @Volatile private var liveUsers = 0

    private var idleJob: Job? = null
    private var reconnectJob: Job? = null
    private var syncTimeoutJob: Job? = null
    private var tempJob: Job? = null

    @Synchronized
    fun init(context: Context) {
        if (::store.isInitialized) return
        store = CredentialStore(context.applicationContext)
        session = store.loadSession()
        _state.update { it.copy(phase = if (store.hasCredentials) Phase.LOADING else Phase.NEEDS_LOGIN) }
    }

    val isSignedIn: Boolean get() = ::store.isInitialized && store.hasCredentials

    // ------------------------------------------------------------------ auth

    // Callers include the UI's main-thread scope; the REST and MQTT calls below block, and Android
    // throws NetworkOnMainThreadException (with no message) if they run there.
    suspend fun login(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        _state.update { it.copy(loggingIn = true, loginError = null) }
        try {
            val s = MiraieApi.login(username, password)
            store.saveCredentials(username.trim(), password)
            store.saveSession(s)
            session = s
            device = null
            homeId = null
            _state.update { it.copy(phase = Phase.LOADING, loggingIn = false, error = null) }
            refreshStatus()
            if (liveUsers > 0) connectMqtt()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Login failed", e)
            _state.update { it.copy(loggingIn = false, loginError = friendly(e)) }
            false
        }
    }

    fun signOut() {
        scope.launch {
            disconnectMqtt()
            store.clearAll()
            session = null
            device = null
            homeId = null
            _state.value = UiState(phase = Phase.NEEDS_LOGIN)
        }
    }

    /** Logs in again with the stored credentials. */
    private suspend fun relogin(stale: Session?): Session = authMutex.withLock {
        val current = session
        if (current != null && current !== stale) return current // someone else refreshed already
        val user = store.username
        val pass = store.password
        if (user.isNullOrEmpty() || pass.isNullOrEmpty()) {
            _state.update { UiState(phase = Phase.NEEDS_LOGIN) }
            throw IOException("Please sign in")
        }
        Log.i(TAG, "Re-authenticating")
        val s = try {
            MiraieApi.login(user, pass)
        } catch (e: HttpException) {
            if (e.code == 400 || e.code == 401 || e.code == 403) {
                _state.update { it.copy(phase = Phase.NEEDS_LOGIN, loginError = "Session expired. Sign in again.") }
            }
            throw e
        }
        store.saveSession(s)
        session = s
        s
    }

    private suspend fun validSession(): Session {
        val s = session
        if (s != null && s.expiresAtMs - 60_000L > System.currentTimeMillis()) return s
        return relogin(s)
    }

    private suspend fun <T> authed(block: (String) -> T): T {
        val s = validSession()
        return try {
            block(s.accessToken)
        } catch (e: HttpException) {
            if (e.code != 401) throw e
            block(relogin(s).accessToken)
        }
    }

    // ------------------------------------------------------------------ REST

    private suspend fun ensureDevice(): Device = deviceMutex.withLock {
        device?.let { return it }
        val homes = authed { MiraieApi.homes(it) }
        val home = homes.firstOrNull() ?: throw IOException("No home found on this account")
        val d = home.devices.firstOrNull() ?: throw IOException("No AC found on this account")
        homeId = home.homeId
        device = d
        _state.update { it.copy(device = d) }
        d
    }

    /** Fetches status over REST and replaces local state. Returns true on success. */
    suspend fun refreshStatus(): Boolean {
        if (!isSignedIn) return false
        return try {
            val d = ensureDevice()
            val json = authed { MiraieApi.status(it, d.deviceId) }
            _state.update {
                it.copy(
                    phase = Phase.READY,
                    ac = Protocol.merge(it.ac, json),
                    hasStatus = true,
                    syncing = false,
                    error = null,
                )
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Status refresh failed: ${e.javaClass.simpleName}")
            _state.update {
                if (it.phase == Phase.NEEDS_LOGIN) it
                else it.copy(phase = Phase.READY, syncing = false, error = friendly(e))
            }
            false
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /** The UI is visible: keep MQTT connected. */
    fun startLive() {
        liveUsers++
        idleJob?.cancel()
        scope.launch {
            refreshStatus()
            connectMqtt()
        }
    }

    fun stopLive() {
        liveUsers = (liveUsers - 1).coerceAtLeast(0)
        scheduleIdleDisconnect()
    }

    private fun scheduleIdleDisconnect() {
        if (liveUsers > 0) return
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_DISCONNECT_MS)
            if (liveUsers == 0) disconnectMqtt()
        }
    }

    // ------------------------------------------------------------------ MQTT

    private suspend fun connectMqtt(): MqttClient? = mqttMutex.withLock {
        mqtt?.takeIf { it.isConnected }?.let { return it }
        if (!isSignedIn) return null
        _state.update { it.copy(link = Link.CONNECTING) }
        try {
            val d = ensureDevice()
            var s = validSession()
            val client = try {
                openMqtt(d, s)
            } catch (e: MqttException) {
                if (e.reasonCode != MqttException.REASON_CODE_NOT_AUTHORIZED.toInt() &&
                    e.reasonCode != MqttException.REASON_CODE_FAILED_AUTHENTICATION.toInt()
                ) throw e
                s = relogin(s)
                openMqtt(d, s)
            }
            mqtt = client
            _state.update { it.copy(link = Link.CONNECTED, error = null) }
            client
        } catch (e: Exception) {
            Log.w(TAG, "MQTT connect failed: ${e.javaClass.simpleName} ${(e as? MqttException)?.reasonCode}")
            _state.update { it.copy(link = Link.IDLE, error = friendly(e)) }
            scheduleReconnect()
            null
        }
    }

    private fun openMqtt(d: Device, s: Session): MqttClient {
        val client = MqttClient(Protocol.MQTT_URI, "acremote-" + UUID.randomUUID(), MemoryPersistence())
        val opts = MqttConnectOptions().apply {
            userName = homeId
            password = s.accessToken.toCharArray()
            keepAliveInterval = 60
            connectionTimeout = 15
            isCleanSession = true
            isAutomaticReconnect = false
            mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
            socketFactory = SSLSocketFactory.getDefault()
        }
        client.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.i(TAG, "MQTT connection lost")
                if (mqtt === client) {
                    _state.update { it.copy(link = Link.IDLE) }
                    scheduleReconnect()
                }
            }

            override fun messageArrived(topic: String, message: MqttMessage) = onMessage(topic, message.payload)
            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
        try {
            client.connect(opts)
            client.subscribe(arrayOf("${d.topicBase}/status", "${d.topicBase}/connectionStatus"), intArrayOf(0, 0))
        } catch (e: Exception) {
            runCatching { client.close(true) }
            throw e
        }
        return client
    }

    private fun scheduleReconnect() {
        if (liveUsers == 0 || !isSignedIn) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (liveUsers > 0) connectMqtt()
        }
    }

    private suspend fun disconnectMqtt() = mqttMutex.withLock {
        reconnectJob?.cancel()
        val c = mqtt
        mqtt = null
        if (c != null) {
            runCatching { if (c.isConnected) c.disconnect(2_000) }
            runCatching { c.close(true) }
        }
        _state.update { it.copy(link = Link.IDLE) }
    }

    private fun onMessage(topic: String, payload: ByteArray) {
        val json = runCatching { JSONObject(String(payload)) }.getOrNull() ?: return
        if (topic.endsWith("/status")) {
            syncTimeoutJob?.cancel()
            _state.update {
                it.copy(ac = Protocol.merge(it.ac, json), hasStatus = true, syncing = false, phase = Phase.READY)
            }
        } else if (topic.endsWith("/connectionStatus")) {
            _state.update { it.copy(ac = Protocol.merge(it.ac, json)) }
        }
    }

    private suspend fun publish(settings: Map<String, Any>) {
        try {
            val d = ensureDevice()
            val client = connectMqtt() ?: throw IOException("Not connected")
            val msg = MqttMessage(Protocol.control(settings).toString().toByteArray()).apply { qos = 0 }
            client.publish("${d.topicBase}/control", msg)
            armSyncTimeout()
        } catch (e: Exception) {
            Log.w(TAG, "Publish failed: ${e.javaClass.simpleName}")
            _state.update { it.copy(error = friendly(e)) }
            armSyncTimeout()
        } finally {
            scheduleIdleDisconnect()
        }
    }

    /** No MQTT echo within 4 s: fall back to a REST status fetch. */
    private fun armSyncTimeout() {
        syncTimeoutJob?.cancel()
        syncTimeoutJob = scope.launch {
            delay(SYNC_TIMEOUT_MS)
            if (_state.value.syncing) refreshStatus()
        }
    }

    // ------------------------------------------------------------------ commands

    /** Optimistic: apply locally, mark syncing, publish. */
    fun send(settings: Map<String, Any>) {
        _state.update { it.copy(ac = Protocol.merge(it.ac, settings), syncing = true, error = null) }
        scope.launch { publish(settings) }
    }

    fun setPower(on: Boolean) = send(Protocol.power(on))
    fun setMode(m: AcMode) = send(Protocol.mode(m))
    fun setFan(f: FanSpeed) = send(Protocol.fan(f))
    fun setVerticalSwing(v: Int) = send(Protocol.verticalSwing(v))
    fun setEco(on: Boolean) = send(Protocol.eco(on))
    fun setPowerful(on: Boolean) = send(Protocol.powerful(on))

    /** Slider and +/-: update locally now, publish 450 ms after the last change. */
    fun setTemperature(t: Int) {
        val clamped = t.coerceIn(MIN_TEMP, MAX_TEMP)
        val settings = Protocol.temperature(clamped)
        _state.update { it.copy(ac = Protocol.merge(it.ac, settings), syncing = true, error = null) }
        syncTimeoutJob?.cancel()
        tempJob?.cancel()
        tempJob = scope.launch {
            delay(TEMP_DEBOUNCE_MS)
            publish(settings)
        }
    }

    /** Used by the tile: make sure we know the current power state, then flip it. */
    suspend fun togglePower() {
        if (!_state.value.hasStatus) refreshStatus()
        setPower(!_state.value.ac.power)
    }

    private fun friendly(e: Throwable): String = when (e) {
        is HttpException -> if (e.message.isNullOrBlank() || e.message == "HTTP ${e.code}") "Server error (${e.code})" else "${e.message} (${e.code})"
        is java.net.UnknownHostException -> "No internet connection"
        is java.net.SocketTimeoutException -> "Connection timed out"
        is MqttException -> when (e.reasonCode) {
            MqttException.REASON_CODE_NOT_AUTHORIZED.toInt(),
            MqttException.REASON_CODE_FAILED_AUTHENTICATION.toInt() -> "MQTT not authorized"
            else -> "Can't reach the AC service"
        }
        else -> e.message ?: "Something went wrong (${e.javaClass.simpleName})"
    }
}
