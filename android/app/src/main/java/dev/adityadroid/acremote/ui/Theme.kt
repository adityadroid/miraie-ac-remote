package dev.adityadroid.acremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.adityadroid.acremote.R

object Palette {
    val Background = Color(0xFF0E0E0E)
    val Card = Color(0xFF0E0E0E)
    val Tile = Color(0xFF1A1A1A)
    val Border = Color.White.copy(alpha = 0.06f)
    val Text = Color(0xFFEDE8E0)
    val Secondary = Color(0xFF8C877F)
    val Accent = Color(0xFFCFC6B4)
    val Amber = Color(0xFFFFB24D)
    val Error = Color(0xFFE5867A)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Rounded display font (Nunito variable, OFL) used for the big temperature and headings. */
val Rounded = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700), nunito(800))

@Composable
fun AcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color(0xFF1A1712),
            background = Palette.Background,
            surface = Palette.Tile,
            onSurface = Palette.Text,
            onBackground = Palette.Text,
            surfaceContainer = Palette.Tile,
            error = Palette.Error,
        ),
        content = content,
    )
}
