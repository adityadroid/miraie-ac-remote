package dev.adityadroid.acremote.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.adityadroid.acremote.data.Protocol
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DialogBg = Color(0xFF161616)

/** Clock-time formatter that follows the system 12/24h setting. */
@Composable
fun rememberClockFormatter(): (Long) -> String {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    return remember(is24) {
        val f = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.getDefault())
        val fn: (Long) -> String = { epoch -> f.format(Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault())) }
        fn
    }
}

/** Tile value text for the two timer slots. */
fun timerSummary(offAt: String?, onAt: String?): String = when {
    offAt != null && onAt != null -> "Off $offAt · On $onAt"
    offAt != null -> "Off at $offAt"
    onAt != null -> "On at $onAt"
    else -> "None"
}

private enum class Slot { OFF, ON }

@Composable
fun TimerDialog(
    offAt: Long?,
    onAt: Long?,
    onOffTimer: (Long?) -> Unit,
    onOnTimer: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val fmt = rememberClockFormatter()
    var picking by remember { mutableStateOf<Slot?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(DialogBg)
                .border(1.dp, Palette.Border, shape)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, null, tint = Palette.Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Timers", color = Palette.Text, fontSize = 18.sp, fontFamily = Rounded, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Run on the AC itself, even with this app closed.",
                color = Palette.Secondary, fontSize = 12.sp, fontFamily = Rounded, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            TimerRow("Turn off at", offAt?.let(fmt), onSet = { picking = Slot.OFF }, onClear = { onOffTimer(null) })
            Spacer(Modifier.height(10.dp))
            TimerRow("Turn on at", onAt?.let(fmt), onSet = { picking = Slot.ON }, onClear = { onOnTimer(null) })
            Spacer(Modifier.height(16.dp))
            Text("Quick", color = Palette.Secondary, fontSize = 12.sp, fontFamily = Rounded, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 4, 8).forEach { h ->
                    Pill(if (h == 1) "Off in 1h" else "${h}h") {
                        onOffTimer(System.currentTimeMillis() / 1000 + h * 3600L)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Pill("Done", onClick = onDismiss)
            }
        }
    }

    picking?.let { slot ->
        val current = if (slot == Slot.OFF) offAt else onAt
        TimePickDialog(
            title = if (slot == Slot.OFF) "Turn off at" else "Turn on at",
            initialEpoch = current,
            onPicked = { h, m ->
                val at = Protocol.nextOccurrence(h, m)
                if (slot == Slot.OFF) onOffTimer(at) else onOnTimer(at)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun TimerRow(label: String, value: String?, onSet: () -> Unit, onClear: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Tile)
            .border(1.dp, Palette.Border, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Palette.Secondary, fontSize = 12.sp, fontFamily = Rounded, fontWeight = FontWeight.SemiBold)
            Text(
                value ?: "Not set",
                color = if (value != null) Palette.Text else Palette.Secondary,
                fontSize = 16.sp, fontFamily = Rounded, fontWeight = FontWeight.ExtraBold,
            )
        }
        Pill("Set", onClick = onSet)
        if (value != null) {
            Spacer(Modifier.width(6.dp))
            Pill("Clear", onClick = onClear)
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        color = Palette.Text, fontSize = 13.sp, fontFamily = Rounded, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(shape)
            .background(Color(0xFF242424))
            .border(1.dp, Palette.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(
    title: String,
    initialEpoch: Long?,
    onPicked: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val init = (initialEpoch?.let { Instant.ofEpochSecond(it) } ?: Instant.now()).atZone(ZoneId.systemDefault())
    val state = rememberTimePickerState(initialHour = init.hour, initialMinute = init.minute, is24Hour = is24)
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .clip(shape)
                .background(DialogBg)
                .border(1.dp, Palette.Border, shape)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title, color = Palette.Text, fontSize = 18.sp, fontFamily = Rounded, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Palette.Tile,
                    clockDialSelectedContentColor = Color(0xFF1A1712),
                    clockDialUnselectedContentColor = Palette.Text,
                    selectorColor = Palette.Accent,
                    containerColor = DialogBg,
                    periodSelectorBorderColor = Palette.Border,
                    periodSelectorSelectedContainerColor = Palette.Accent,
                    periodSelectorUnselectedContainerColor = Palette.Tile,
                    periodSelectorSelectedContentColor = Color(0xFF1A1712),
                    periodSelectorUnselectedContentColor = Palette.Text,
                    timeSelectorSelectedContainerColor = Palette.Accent,
                    timeSelectorUnselectedContainerColor = Palette.Tile,
                    timeSelectorSelectedContentColor = Color(0xFF1A1712),
                    timeSelectorUnselectedContentColor = Palette.Text,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Pill("Cancel", onClick = onDismiss)
                Pill("Set") { onPicked(state.hour, state.minute) }
            }
        }
    }
}
