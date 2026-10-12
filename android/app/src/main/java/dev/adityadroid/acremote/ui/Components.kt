package dev.adityadroid.acremote.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Round dark button (used for -, +, settings). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Palette.Text,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Palette.Tile)
            .border(1.dp, Palette.Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Circular power button with a warm amber glow when on. */
@Composable
fun PowerButton(on: Boolean, onClick: () -> Unit, icon: ImageVector, modifier: Modifier = Modifier) {
    val glow by animateFloatAsState(if (on) 1f else 0f, tween(400), label = "glow")
    val tint by animateColorAsState(if (on) Palette.Amber else Palette.Secondary, label = "powerTint")
    Box(
        modifier
            .size(52.dp)
            .drawBehind {
                if (glow > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Palette.Amber.copy(alpha = 0.35f * glow), Color.Transparent),
                            center = center,
                            radius = size.minDimension * 0.75f,
                        ),
                        radius = size.minDimension * 0.75f,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF161411))
                .border(1.dp, if (on) Palette.Amber.copy(alpha = 0.45f) else Palette.Border, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, if (on) "Turn off" else "Turn on", tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Wide temperature slider: warm-beige gradient fill left of a light pill thumb.
 * [onChange] fires on every whole-degree change; the repository debounces the network send.
 */
@Composable
fun TempSlider(
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val onChangeState by rememberUpdatedState(onChange)
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val range = (max - min).toFloat()
    val target = dragFrac ?: ((value - min) / range).coerceIn(0f, 1f)
    val frac by animateFloatAsState(
        target,
        if (dragFrac != null) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "sliderFrac",
    )
    val fillAlpha = if (dimmed) 0.55f else 1f
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pad = with(density) { 4.dp.toPx() }
    val thumbW = with(density) { 22.dp.toPx() }

    fun fracAt(x: Float): Float = ((x - pad - thumbW / 2f) / (widthPx - 2 * pad - thumbW)).coerceIn(0f, 1f)
    fun emit(f: Float) = onChangeState((min + f * range).roundToInt().coerceIn(min, max))

    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color(0xFF141414))
            .border(1.dp, Palette.Border, shape)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics {
                contentDescription = "Set temperature"
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), min.toFloat()..max.toFloat(), steps = (max - min - 1))
                setProgress { v -> onChangeState(v.roundToInt().coerceIn(min, max)); true }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos -> emit(fracAt(pos.x)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { pos -> dragFrac = fracAt(pos.x); emit(fracAt(pos.x)) },
                    onDragEnd = { dragFrac = null },
                    onDragCancel = { dragFrac = null },
                ) { change, _ ->
                    change.consume()
                    val f = fracAt(change.position.x)
                    dragFrac = f
                    emit(f)
                }
            }
            .drawBehind {
                val h = size.height
                val thumbH = h - 2 * pad - 4f
                val thumbX = pad + frac * (size.width - 2 * pad - thumbW)
                val fillEnd = thumbX + thumbW
                val r = 12.dp.toPx()
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF2A2620).copy(alpha = fillAlpha),
                            Color(0xFF6E6656).copy(alpha = fillAlpha),
                            Palette.Accent.copy(alpha = 0.85f * fillAlpha),
                        ),
                        startX = 0f, endX = fillEnd.coerceAtLeast(1f),
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(fillEnd + pad, h),
                    cornerRadius = CornerRadius(r, r),
                )
                // thumb
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.35f),
                    topLeft = Offset(thumbX + 1f, (h - thumbH) / 2f + 2f),
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(thumbW / 2.6f, thumbW / 2.6f),
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFFF7F4EE), Color(0xFFDCD6CB)), startY = (h - thumbH) / 2f, endY = (h + thumbH) / 2f),
                    topLeft = Offset(thumbX, (h - thumbH) / 2f),
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(thumbW / 2.6f, thumbW / 2.6f),
                )
            },
    )
}
