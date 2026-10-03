package com.zyfen.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

// Fallback & default colors
val ZyfenBg = Color(0xff12141a)
val ZyfenSurface = Color(0xff181a24)
val ZyfenCard = Color(0xff222533)
val ZyfenPurple = Color(0xff8b5cf6)
val ZyfenNeon = Color(0xffef4444)
val ZyfenText = Color(0xfff1f5f9)
val ZyfenTextSecondary = Color(0xff94a3b8)
val ZyfenTextDisabled = Color(0xff64748b)

val LocalAccentColor = staticCompositionLocalOf { Color(0xfff43f5e) }
val LocalUIStyle = staticCompositionLocalOf { "glass" } // "glass" or "classic"

val MaterialTheme.textPrimary: Color
    @Composable get() = colorScheme.onBackground

val MaterialTheme.textSecondary: Color
    @Composable get() = colorScheme.onSurfaceVariant

fun parseHexColor(hex: String, fallback: Color = Color(0xfff43f5e)): Color {
    val clean = hex.trim().removePrefix("#")
    return try {
        when (clean.length) {
            6 -> Color(android.graphics.Color.parseColor("#$clean"))
            8 -> Color(android.graphics.Color.parseColor("#$clean"))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

private val ZyfenTypography = Typography(
    displayLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 56.sp, shadow = LiquidGlassTokens.TextShadow),
    displayMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 44.sp, shadow = LiquidGlassTokens.TextShadow),
    displaySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 36.sp, shadow = LiquidGlassTokens.TextShadow),
    headlineLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 32.sp, shadow = LiquidGlassTokens.TextShadow),
    headlineMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 28.sp, shadow = LiquidGlassTokens.TextShadow),
    headlineSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, shadow = LiquidGlassTokens.TextShadow),
    titleLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, shadow = LiquidGlassTokens.SubtleTextShadow),
    titleMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, shadow = LiquidGlassTokens.SubtleTextShadow),
    titleSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, shadow = LiquidGlassTokens.SubtleTextShadow),
    bodyLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 16.sp, shadow = LiquidGlassTokens.SubtleTextShadow),
    bodyMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 14.sp, shadow = LiquidGlassTokens.SubtleTextShadow),
    bodySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.sp, shadow = LiquidGlassTokens.SubtleTextShadow),
    labelLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp, shadow = LiquidGlassTokens.SubtleTextShadow),
    labelMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 12.sp, shadow = LiquidGlassTokens.SubtleTextShadow),
    labelSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 11.sp, shadow = LiquidGlassTokens.SubtleTextShadow)
)

@Composable
fun ZyfenTheme(
    themeMode: String = "spotify", // "dark", "spotify", "blush", "light", "amoled", "system"
    accentColorHex: String = "#1DB954",
    uiStyle: String = "glass", // "glass" (Liquid Glassmorphism) or "solid"/"classic"
    content: @Composable () -> Unit
) {
    val accent = parseHexColor(accentColorHex, fallback = Color(0xFF1DB954))
    val isGlass = uiStyle == "glass"

    val (bg, surf, surfVar, onSurfVar) = when {
        isGlass -> {
            val hsl = colorToHsl(accent)
            val baseBg = hslToColor(hsl[0], hsl[1] * 0.35f, 0.035f)
            val baseSurf = Color.White.copy(alpha = 0.08f)
            val baseSurfVar = Color.White.copy(alpha = 0.12f)
            val textSec = hslToColor(hsl[0], 0.70f, 0.75f)
            Quad(baseBg, baseSurf, baseSurfVar, textSec)
        }
        themeMode.equals("amoled", ignoreCase = true) -> {
            Quad(Color(0xFF000000), Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFFB3B3B3))
        }
        themeMode.equals("light", ignoreCase = true) -> {
            Quad(Color(0xFFF8F9FA), Color(0xFFFFFFFF), Color(0xFFECEFF1), Color(0xFF616161))
        }
        else -> { // spotify / dark
            Quad(Color(0xFF121212), Color(0xFF181818), Color(0xFF282828), Color(0xFFB3B3B3))
        }
    }

    val scheme = if (themeMode.equals("light", ignoreCase = true) && !isGlass) {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = accent,
            onSecondary = Color.White,
            tertiary = accent,
            background = bg,
            onBackground = Color(0xFF121212),
            surface = surf,
            onSurface = Color(0xFF121212),
            surfaceVariant = surfVar,
            onSurfaceVariant = onSurfVar,
            outline = Color(0xFFE0E0E0),
            error = Color(0xFFE53935)
        )
    } else {
        darkColorScheme(
            primary = accent,
            onPrimary = if (isLightColor(accent)) Color.Black else Color.White,
            secondary = accent,
            onSecondary = if (isLightColor(accent)) Color.Black else Color.White,
            tertiary = accent.copy(alpha = 0.85f),
            background = bg,
            onBackground = Color(0xFFFFFFFF),
            surface = surf,
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = surfVar,
            onSurfaceVariant = onSurfVar,
            outline = if (isGlass) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f),
            error = Color(0xFFFF3366)
        )
    }

    CompositionLocalProvider(
        LocalAccentColor provides accent,
        LocalUIStyle provides uiStyle
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ZyfenTypography,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp)
            )
        ) {
            Surface(color = Color.Transparent, content = content)
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun isLightColor(c: Color): Boolean {
    val luminance = 0.299 * c.red + 0.587 * c.green + 0.114 * c.blue
    return luminance > 0.65
}
