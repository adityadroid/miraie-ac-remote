package dev.adityadroid.acremote.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.sin

/**
 * White split AC unit with a vent slit and a tiny set-temp readout. When [on], soft curved
 * airflow streaks drift down from the vent and fade (cool = blue-white, heat = warm).
 */
@Composable
fun AcIllustration(on: Boolean, warm: Boolean, setTemp: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "airflow")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val flowAlpha by animateFloatAsState(if (on) 1f else 0f, tween(600), label = "flowAlpha")
    val tint = if (warm) Color(0xFFFFD2A6) else Color(0xFFDDEBFF)

    Canvas(modifier) {
        val unitW = size.width * 0.56f
        val unitH = unitW * 0.30f
        val left = (size.width - unitW) / 2f
        val top = size.height * 0.08f
        drawUnit(left, top, unitW, unitH, setTemp, on)
        if (flowAlpha > 0.01f) {
            drawAirflow(left, top + unitH, unitW, size.height - (top + unitH), t, tint, flowAlpha)
        }
    }
}

private fun DrawScope.drawUnit(left: Float, top: Float, w: Float, h: Float, setTemp: Int, on: Boolean) {
    val r = h * 0.16f
    // Soft drop shadow
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.45f),
        topLeft = Offset(left + 4f, top + 8f),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r),
    )
    // Body: white vertical gradient
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFFFFFFF), Color(0xFFF1F1EF), Color(0xFFD9D9D6)),
            startY = top, endY = top + h,
        ),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r),
    )
    // Subtle top highlight
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), startY = top, endY = top + h * 0.3f),
        topLeft = Offset(left + w * 0.02f, top + 1.5f),
        size = Size(w * 0.96f, h * 0.3f),
        cornerRadius = CornerRadius(r, r),
    )
    // Right side face shading for a bit of depth
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.10f)), startX = left + w * 0.85f, endX = left + w),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r),
    )
    // Vent slit
    val ventTop = top + h * 0.70f
    val ventH = h * 0.17f
    val ventL = left + w * 0.04f
    val ventW = w * 0.92f
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF151515)), startY = ventTop, endY = ventTop + ventH),
        topLeft = Offset(ventL, ventTop),
        size = Size(ventW, ventH),
        cornerRadius = CornerRadius(ventH / 2, ventH / 2),
    )
    // Fine slats
    val slats = 34
    for (i in 1 until slats) {
        val x = ventL + ventW * i / slats
        drawLine(
            color = Color(0xFF5A5A5A).copy(alpha = 0.55f),
            start = Offset(x, ventTop + ventH * 0.2f),
            end = Offset(x - ventH * 0.25f, ventTop + ventH * 0.85f),
            strokeWidth = 1f,
        )
    }
    // Lower lip
    drawLine(
        color = Color.White.copy(alpha = 0.7f),
        start = Offset(left + r, top + h - 1.5f),
        end = Offset(left + w - r, top + h - 1.5f),
        strokeWidth = 1f,
    )
    // Tiny set-temp readout
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = if (on) android.graphics.Color.argb(200, 70, 70, 70) else android.graphics.Color.argb(70, 70, 70, 70)
        textSize = h * 0.15f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(setTemp.toString(), left + w * 0.80f, top + h * 0.28f, paint)
    // Status LED
    drawCircle(
        color = if (on) Color(0xFF7FD3FF) else Color(0xFFBDBDBD),
        radius = h * 0.025f,
        center = Offset(left + w * 0.88f, top + h * 0.24f),
    )
}

private fun DrawScope.drawAirflow(
    left: Float, top: Float, w: Float, h: Float, t: Float, tint: Color, alpha: Float,
) {
    val streaks = 22
    for (i in 0 until streaks) {
        val frac = (i + 0.5f) / streaks
        val x0 = left + w * (0.08f + 0.84f * frac)
        // each streak has its own phase offset so they don't move in lockstep
        val p = (t + (i * 0.37f) % 1f) % 1f
        val len = h * 0.42f
        val y0 = top + 4f + p * (h * 0.65f)
        val fade = sin(p * PI).toFloat() // fade in then out
        val a = (0.32f * fade * alpha).coerceIn(0f, 1f)
        if (a < 0.01f) continue
        val drift = (frac - 0.5f) * w * 0.10f
        val sway = sin((p * 2f + i * 0.21f) * PI).toFloat() * w * 0.012f
        val path = Path().apply {
            moveTo(x0, y0)
            cubicTo(
                x0 + drift * 0.3f + sway, y0 + len * 0.35f,
                x0 + drift * 0.7f - sway, y0 + len * 0.7f,
                x0 + drift, y0 + len,
            )
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                listOf(tint.copy(alpha = a), tint.copy(alpha = a * 0.5f), Color.Transparent),
                startY = y0, endY = y0 + len,
            ),
            style = Stroke(width = 1.6f + (i % 3) * 0.5f, cap = StrokeCap.Round),
        )
    }
}
