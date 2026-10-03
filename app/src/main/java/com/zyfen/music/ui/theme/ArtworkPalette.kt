package com.zyfen.music.ui.theme

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import com.zyfen.music.data.media.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class DynamicPalette(
    val primary: Color,
    val secondary: Color,
    val backgroundTop: Color,
    val backgroundMid: Color,
    val backgroundBottom: Color,
    val accent: Color,
    val cardTint: Color,
    val borderGlow: Color
)

// 16 rich vibrant musical gradient pairs for instant aesthetic zero-latency display
val CuratedPalettes = listOf(
    Pair(Color(0xFF6366F1), Color(0xFFA855F7)), // Indigo Twilight
    Pair(Color(0xFF06B6D4), Color(0xFF3B82F6)), // Electric Ocean
    Pair(Color(0xFFEC4899), Color(0xFFF43F5E)), // Hot Neon Pink
    Pair(Color(0xFF10B981), Color(0xFF059669)), // Emerald Wave
    Pair(Color(0xFFF59E0B), Color(0xFFEA580C)), // Sunset Amber
    Pair(Color(0xFF8B5CF6), Color(0xFFC084FC)), // Cyber Violet
    Pair(Color(0xFF14B8A6), Color(0xFF06B6D4)), // Turquoise Glow
    Pair(Color(0xFFEF4444), Color(0xFFF97316)), // Fiery Crimson
    Pair(Color(0xFF3B82F6), Color(0xFF8B5CF6)), // Deep Royal Blue
    Pair(Color(0xFF84CC16), Color(0xFF10B981)), // Lime Spring
    Pair(Color(0xFFD946EF), Color(0xFF8B5CF6)), // Vivid Fuchsia
    Pair(Color(0xFF0284C7), Color(0xFF0D9488)), // Cobalt Mint
    Pair(Color(0xFFE11D48), Color(0xFF9333EA)), // Ruby Magenta
    Pair(Color(0xFFF97316), Color(0xFFFACC15)), // Solar Sunrise
    Pair(Color(0xFF6D28D9), Color(0xFF4C1D95)), // Deep Velvet
    Pair(Color(0xFF059669), Color(0xFF047857))  // Forest Jade
)

@Composable
fun rememberDynamicPalette(
    song: Song?,
    dynamicArtworkEnabled: Boolean = false,
    themeAccent: Color = LocalAccentColor.current
): DynamicPalette {
    val context = LocalContext.current

    // Base fallback based on themeAccent or song id
    val seed = abs((song?.id ?: song?.title ?: "zyfen").hashCode())
    val basePair = CuratedPalettes[seed % CuratedPalettes.size]

    var extractedDominant by remember(song?.id) { mutableStateOf<Color?>(null) }
    var extractedSecondary by remember(song?.id) { mutableStateOf<Color?>(null) }

    LaunchedEffect(song?.id, song?.artworkUri, dynamicArtworkEnabled) {
        if (!dynamicArtworkEnabled) {
            extractedDominant = null
            extractedSecondary = null
            return@LaunchedEffect
        }
        val artUri = song?.artworkUri
        if (!artUri.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val req = ImageRequest.Builder(context)
                        .data(artUri)
                        .allowHardware(false)
                        .size(32, 32)
                        .build()
                    val result = ImageLoader(context).execute(req)
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val (dom, sec) = extractColorsFromBitmap(bitmap)
                        if (dom != null) {
                            extractedDominant = dom
                            extractedSecondary = sec ?: dom
                        }
                    }
                } catch (_: Exception) {
                    // Fall back cleanly
                }
            }
        } else {
            extractedDominant = null
            extractedSecondary = null
        }
    }

    val targetPrimary = if (!dynamicArtworkEnabled) {
        themeAccent
    } else {
        extractedDominant ?: themeAccent
    }

    val targetSecondary = if (!dynamicArtworkEnabled) {
        themeAccent
    } else {
        extractedSecondary ?: themeAccent
    }

    // Synthesize harmonious dark glassmorphic background colors from primary
    val targetBgTop = Color(
        red = (targetPrimary.red * 0.22f).coerceIn(0.04f, 0.35f),
        green = (targetPrimary.green * 0.16f).coerceIn(0.03f, 0.30f),
        blue = (targetPrimary.blue * 0.28f).coerceIn(0.06f, 0.40f),
        alpha = 1f
    )
    val targetBgMid = Color(
        red = (targetPrimary.red * 0.40f + targetSecondary.red * 0.15f).coerceIn(0.08f, 0.55f),
        green = (targetPrimary.green * 0.30f + targetSecondary.green * 0.15f).coerceIn(0.06f, 0.45f),
        blue = (targetPrimary.blue * 0.45f + targetSecondary.blue * 0.20f).coerceIn(0.10f, 0.60f),
        alpha = 1f
    )
    val targetBgBottom = Color(
        red = (targetSecondary.red * 0.14f).coerceIn(0.02f, 0.20f),
        green = (targetSecondary.green * 0.10f).coerceIn(0.02f, 0.18f),
        blue = (targetSecondary.blue * 0.18f).coerceIn(0.04f, 0.25f),
        alpha = 1f
    )

    val animPrimary by animateColorAsState(targetPrimary, tween(600), label = "palPrimary")
    val animSecondary by animateColorAsState(targetSecondary, tween(600), label = "palSecondary")
    val animBgTop by animateColorAsState(targetBgTop, tween(600), label = "palBgTop")
    val animBgMid by animateColorAsState(targetBgMid, tween(600), label = "palBgMid")
    val animBgBottom by animateColorAsState(targetBgBottom, tween(600), label = "palBgBottom")

    return remember(animPrimary, animSecondary, animBgTop, animBgMid, animBgBottom) {
        DynamicPalette(
            primary = animPrimary,
            secondary = animSecondary,
            backgroundTop = animBgTop,
            backgroundMid = animBgMid,
            backgroundBottom = animBgBottom,
            accent = animPrimary,
            cardTint = animPrimary.copy(alpha = 0.15f),
            borderGlow = animPrimary.copy(alpha = 0.45f)
        )
    }
}

/**
 * Fast 32x32 pixel analyzer: finds the most vibrant, saturated, non-neutral colors.
 */
private fun extractColorsFromBitmap(bitmap: Bitmap): Pair<Color?, Color?> {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return Pair(null, null)

    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    var bestColor1: Color? = null
    var bestScore1 = -1f

    var bestColor2: Color? = null
    var bestScore2 = -1f

    for (pixel in pixels) {
        val r = (pixel shr 16 and 0xFF) / 255f
        val g = (pixel shr 8 and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f

        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC

        // Saturation
        val sat = if (maxC > 0f) delta / maxC else 0f
        // Luminance
        val lum = 0.299f * r + 0.587f * g + 0.114f * b

        // Reject almost pure black, pure white, or completely gray pixels
        if (lum < 0.12f || lum > 0.88f || sat < 0.20f) continue

        // Score based on saturation and balanced luminance
        val score = sat * 1.5f + (1f - abs(lum - 0.5f))

        val col = Color(r, g, b, 1f)
        if (score > bestScore1) {
            bestScore2 = bestScore1
            bestColor2 = bestColor1
            bestScore1 = score
            bestColor1 = col
        } else if (score > bestScore2 && (bestColor1 == null || colorDistance(col, bestColor1) > 0.3f)) {
            bestScore2 = score
            bestColor2 = col
        }
    }

    return Pair(bestColor1, bestColor2)
}

private fun colorDistance(c1: Color, c2: Color): Float {
    val dr = c1.red - c2.red
    val dg = c1.green - c2.green
    val db = c1.blue - c2.blue
    return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
}
