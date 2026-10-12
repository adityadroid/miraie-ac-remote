package dev.adityadroid.acremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.adityadroid.acremote.data.AcRepository
import dev.adityadroid.acremote.data.Phase
import dev.adityadroid.acremote.ui.AcPanel
import dev.adityadroid.acremote.ui.AcTheme
import dev.adityadroid.acremote.ui.LoginScreen
import dev.adityadroid.acremote.ui.Palette
import dev.adityadroid.acremote.ui.PanelActions
import kotlinx.coroutines.launch

/**
 * Translucent, dialog-like activity: a dark card pinned near the top of the screen so that
 * opening it from the Quick Settings tile feels like a drop-down panel. Tapping outside closes it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AcRepository.init(this)
        setContent { AcTheme { Root(onDismiss = ::finish) } }
    }

    override fun onStart() {
        super.onStart()
        AcRepository.startLive()
    }

    override fun onStop() {
        AcRepository.stopLive()
        super.onStop()
    }
}

@Composable
private fun Root(onDismiss: () -> Unit) {
    val ui by AcRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    val actions = remember {
        PanelActions(
            onPower = AcRepository::setPower,
            onTemp = AcRepository::setTemperature,
            onEco = AcRepository::setEco,
            onMode = AcRepository::setMode,
            onFan = AcRepository::setFan,
            onPowerful = AcRepository::setPowerful,
            onSwing = AcRepository::setVerticalSwing,
            onSignOut = AcRepository::signOut,
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { -it / 6 },
        ) {
            val shape = RoundedCornerShape(30.dp)
            Box(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, shape)
                    .clip(shape)
                    .background(Palette.Card)
                    .border(1.dp, Palette.Border, shape)
                    // swallow taps so they don't dismiss
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState()),
            ) {
                when (ui.phase) {
                    Phase.NEEDS_LOGIN -> LoginScreen(
                        busy = ui.loggingIn,
                        error = ui.loginError,
                        onLogin = { u, p -> scope.launch { AcRepository.login(u, p) } },
                    )
                    Phase.LOADING -> Box(Modifier.fillMaxWidth().heightIn(min = 220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Palette.Accent, strokeWidth = 2.dp)
                    }
                    Phase.READY -> AcPanel(ui, actions)
                }
            }
        }
    }
}
