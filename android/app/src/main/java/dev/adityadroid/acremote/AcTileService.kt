package dev.adityadroid.acremote

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.adityadroid.acremote.data.AcRepository
import dev.adityadroid.acremote.data.Phase
import dev.adityadroid.acremote.data.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Quick Settings tile: tap toggles power, long-press opens the panel (QS_TILE_PREFERENCES). */
class AcTileService : TileService() {
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        AcRepository.init(applicationContext)
        render(AcRepository.state.value)
        listenJob?.cancel()
        listenJob = AcRepository.scope.launch {
            launch(Dispatchers.Main) { AcRepository.state.collectLatest { render(it) } }
            AcRepository.refreshStatus()
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        AcRepository.init(applicationContext)
        if (!AcRepository.isSignedIn) {
            openPanel()
            return
        }
        AcRepository.scope.launch { AcRepository.togglePower() }
    }

    // The Intent overload is the only option below API 34; newer versions use the PendingIntent one.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openPanel() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(ui: UiState) {
        val tile = qsTile ?: return
        val ac = ui.ac
        tile.label = "AC"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_ac)
        when {
            !AcRepository.isSignedIn || ui.phase == Phase.NEEDS_LOGIN -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "Sign in"
            }
            !ui.hasStatus -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = if (ui.error != null) "Unavailable" else "…"
            }
            !ac.online -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = "Offline"
            }
            ac.power -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = "${ac.setTemp.roundToInt()}° · ${ac.mode.label}"
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "Off"
            }
        }
        tile.updateTile()
    }
}
