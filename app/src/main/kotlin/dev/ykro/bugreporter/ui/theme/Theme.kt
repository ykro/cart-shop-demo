package dev.ykro.bugreporter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Cart Shop palette: indigo primary, amber accent, very light indigo canvas (matches generated art).
val Indigo = Color(0xFF3F51B5)
val IndigoDark = Color(0xFF2C3A8C)
val IndigoSoft = Color(0xFFE3E6FB)
val Amber = Color(0xFFFFB300)
val AmberSoft = Color(0xFFFFF1CC)
val Canvas = Color(0xFFEEF0FF)
val Ink = Color(0xFF1A1C2E)
val InkMuted = Color(0xFF5B5F7A)
val Danger = Color(0xFFD32F2F)
val DangerSoft = Color(0xFFFFE3E3)
val Success = Color(0xFF2E7D32)
val SuccessSoft = Color(0xFFDFF3E1)

private val scheme =
  lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = IndigoSoft,
    onPrimaryContainer = IndigoDark,
    secondary = Amber,
    onSecondary = Ink,
    secondaryContainer = AmberSoft,
    onSecondaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = IndigoSoft,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFC5C8E6),
    error = Danger,
    errorContainer = DangerSoft,
    onErrorContainer = Danger,
  )

private val shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))

@Composable
fun CartShopTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
}
