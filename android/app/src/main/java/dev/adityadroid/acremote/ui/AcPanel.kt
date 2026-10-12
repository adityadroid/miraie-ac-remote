package dev.adityadroid.acremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.outlined.Toys
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.adityadroid.acremote.data.AcMode
import dev.adityadroid.acremote.data.FanSpeed
import dev.adityadroid.acremote.data.Link
import dev.adityadroid.acremote.data.MAX_TEMP
import dev.adityadroid.acremote.data.MIN_TEMP
import dev.adityadroid.acremote.data.UiState
import dev.adityadroid.acremote.data.swingLabel
import java.util.Locale
import kotlin.math.roundToInt

class PanelActions(
    val onPower: (Boolean) -> Unit,
    val onTemp: (Int) -> Unit,
    val onEco: (Boolean) -> Unit,
    val onMode: (AcMode) -> Unit,
    val onFan: (FanSpeed) -> Unit,
    val onPowerful: (Boolean) -> Unit,
    val onSwing: (Int) -> Unit,
    val onSignOut: () -> Unit,
)

private fun modeIcon(m: AcMode): ImageVector = when (m) {
    AcMode.COOL -> Icons.Outlined.AcUnit
    AcMode.AUTO -> Icons.Outlined.Autorenew
    AcMode.DRY -> Icons.Outlined.WaterDrop
    AcMode.FAN -> Icons.Outlined.Toys
    AcMode.HEAT -> Icons.Outlined.WbSunny
}

private fun fmtTemp(t: Double): String =
    if (t == t.roundToInt().toDouble()) t.roundToInt().toString() else String.format(Locale.US, "%.1f", t)

@Composable
fun AcPanel(ui: UiState, actions: PanelActions, modifier: Modifier = Modifier) {
    val ac = ui.ac
    val setTemp = ac.setTemp.roundToInt().coerceIn(MIN_TEMP, MAX_TEMP)
    Column(modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        // ---- top row
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Thermostat, null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Room", color = Palette.Secondary, fontSize = 16.sp, fontFamily = Rounded, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                ac.roomTemp?.let { "${fmtTemp(it)}°C" } ?: "--",
                color = Palette.Text, fontSize = 16.sp, fontFamily = Rounded, fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.weight(1f))
            PowerButton(on = ac.power, onClick = { actions.onPower(!ac.power) }, icon = Icons.Rounded.PowerSettingsNew)
        }

        // ---- AC illustration + airflow
        AcIllustration(
            on = ac.power,
            warm = ac.mode == AcMode.HEAT,
            setTemp = setTemp,
            modifier = Modifier.fillMaxWidth().height(150.dp),
        )

        // ---- big temp + eco
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    setTemp.toString(),
                    color = Palette.Text,
                    fontSize = 52.sp,
                    lineHeight = 52.sp,
                    fontFamily = Rounded,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "°C",
                    color = Palette.Secondary,
                    fontSize = 18.sp,
                    fontFamily = Rounded,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            EcoPill(on = ac.eco, onChange = actions.onEco)
        }

        Spacer(Modifier.height(14.dp))

        // ---- slider row
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RoundIconButton(Icons.Rounded.Remove, "Decrease temperature", { actions.onTemp(setTemp - 1) })
            Spacer(Modifier.width(10.dp))
            TempSlider(
                value = setTemp, min = MIN_TEMP, max = MAX_TEMP,
                onChange = { if (it != setTemp) actions.onTemp(it) },
                dimmed = !ac.power,
                modifier = Modifier.weight(1f).height(44.dp),
            )
            Spacer(Modifier.width(10.dp))
            RoundIconButton(Icons.Rounded.Add, "Increase temperature", { actions.onTemp(setTemp + 1) })
        }
        Row(Modifier.fillMaxWidth().padding(start = 62.dp, end = 62.dp, top = 6.dp)) {
            Text("$MIN_TEMP°", color = Palette.Secondary, fontSize = 12.sp, fontFamily = Rounded, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("$MAX_TEMP°", color = Palette.Secondary, fontSize = 12.sp, fontFamily = Rounded, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(14.dp))

        // ---- tiles
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MenuTile(
                icon = modeIcon(ac.mode), label = "Mode", value = ac.mode.label,
                options = AcMode.entries.map { it.label to { actions.onMode(it) } },
            )
            MenuTile(
                icon = Icons.Outlined.Toys,
                label = "Fan speed", value = ac.fan.label,
                options = FanSpeed.entries.map { it.label to { actions.onFan(it) } },
            )
            ToggleTile(
                icon = Icons.Outlined.Bolt, label = "Powerful", on = ac.powerful,
                onClick = { actions.onPowerful(!ac.powerful) },
            )
            MenuTile(
                icon = Icons.Outlined.Air, label = "Airflow", value = swingLabel(ac.vSwing),
                options = (0..5).map { v -> swingLabel(v) to { actions.onSwing(v) } },
            )
        }

        Spacer(Modifier.height(14.dp))
        Footer(ui, actions.onSignOut)
    }
}

@Composable
private fun EcoPill(on: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(Palette.Tile)
            .border(1.dp, Palette.Border, shape)
            .clickable { onChange(!on) }
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Icon(
            Icons.Outlined.EnergySavingsLeaf, null,
            tint = if (on) Color(0xFF9CC79A) else Palette.Secondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Eco Mode", color = Palette.Text, fontSize = 15.sp, fontFamily = Rounded, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1A1712),
                checkedTrackColor = Palette.Accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Palette.Text,
                uncheckedTrackColor = Color(0xFF2A2A2A),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun RowScope.TileFrame(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .weight(1f)
            .clip(shape)
            .background(Palette.Tile)
            .border(1.dp, Palette.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) { content() }
}

@Composable
private fun TileBody(icon: ImageVector, label: String, value: String, corner: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, null, tint = Palette.Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.weight(1f))
            corner()
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = Palette.Secondary, fontSize = 12.sp, fontFamily = Rounded, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Palette.Text, fontSize = 15.sp, fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowScope.MenuTile(
    icon: ImageVector,
    label: String,
    value: String,
    options: List<Pair<String, () -> Unit>>,
) {
    var open by remember { mutableStateOf(false) }
    TileFrame(onClick = { open = true }) {
        TileBody(icon, label, value) {
            Icon(Icons.Rounded.KeyboardArrowDown, "Choose $label", tint = Palette.Secondary, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color(0xFF1C1C1C),
            shape = RoundedCornerShape(14.dp),
        ) {
            options.forEach { (name, action) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            color = if (name == value) Palette.Accent else Palette.Text,
                            fontFamily = Rounded,
                            fontWeight = if (name == value) FontWeight.ExtraBold else FontWeight.SemiBold,
                        )
                    },
                    onClick = { open = false; action() },
                )
            }
        }
    }
}

@Composable
private fun RowScope.ToggleTile(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    TileFrame(onClick = onClick) {
        TileBody(icon, label, if (on) "On" else "Off") {
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (on) Palette.Amber else Color(0xFF4A4741)),
            )
        }
    }
}

@Composable
private fun Footer(ui: UiState, onSignOut: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
        val syncing = ui.syncing || ui.link == Link.CONNECTING
        when {
            ui.error != null && !syncing -> {
                Icon(Icons.Outlined.ErrorOutline, null, tint = Palette.Error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(ui.error, color = Palette.Error, fontSize = 13.sp, fontFamily = Rounded, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            syncing -> {
                CircularProgressIndicator(color = Palette.Secondary, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                FooterText("Syncing…")
            }
            ui.hasStatus && !ui.ac.online -> {
                Icon(Icons.Outlined.CloudOff, null, tint = Palette.Secondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                FooterText("AC offline")
            }
            ui.link == Link.CONNECTED -> {
                Icon(Icons.Outlined.GppGood, null, tint = Palette.Secondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                FooterText("Connected")
            }
            else -> {
                CircularProgressIndicator(color = Palette.Secondary, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                FooterText("Connecting…")
            }
        }
        Spacer(Modifier.weight(1f))
        Box {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Tune, "Settings", tint = Palette.Secondary, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = Color(0xFF1C1C1C),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    ui.device?.name ?: "No device",
                    color = Palette.Text, fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider(color = Palette.Border)
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = Palette.Secondary) },
                    text = { Text("Sign out", color = Palette.Text, fontFamily = Rounded, fontWeight = FontWeight.SemiBold) },
                    onClick = { menu = false; onSignOut() },
                )
            }
        }
    }
}

@Composable
private fun FooterText(s: String) =
    Text(s, color = Palette.Secondary, fontSize = 13.sp, fontFamily = Rounded, fontWeight = FontWeight.SemiBold)
