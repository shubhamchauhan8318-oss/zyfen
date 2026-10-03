package com.zyfen.music.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.cos
import kotlin.math.sin

/**
 * Liquid Glassmorphism Design System Tokens
 */
object LiquidGlassTokens {
    val GlassFillLight = Color.White.copy(alpha = 0.12f)
    val GlassFillMedium = Color.White.copy(alpha = 0.08f)
    val GlassFillDeep = Color(0xFF130E26).copy(alpha = 0.82f)
    val GlassFillDialog = Color(0xFF16102E).copy(alpha = 0.92f)

    val GlassBorderSubtle = Color.White.copy(alpha = 0.15f)
    val GlassBorderHighlight = Color.White.copy(alpha = 0.35f)

    val ShadowColor = Color.Black.copy(alpha = 0.45f)

    // Soft drop shadow for crisp text legibility over fluid mesh gradients
    val TextShadow = Shadow(
        color = Color.Black.copy(alpha = 0.50f),
        offset = Offset(0f, 2f),
        blurRadius = 6f
    )

    val SubtleTextShadow = Shadow(
        color = Color.Black.copy(alpha = 0.35f),
        offset = Offset(0f, 1.5f),
        blurRadius = 4f
    )
}

/**
 * HSL Color calculation helpers for dynamic ambient mesh gradient synthesis
 */
internal fun colorToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    var h: Float
    val s: Float
    val l = (max + min) / 2f

    if (max == min) {
        h = 0f
        s = 0f
    } else {
        val d = max - min
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        h /= 6f
    }
    return floatArrayOf(h * 360f, s, l)
}

internal fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val hNorm = (h % 360f + 360f) % 360f
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((hNorm / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        hNorm < 60f -> Triple(c, x, 0f)
        hNorm < 120f -> Triple(x, c, 0f)
        hNorm < 180f -> Triple(0f, c, x)
        hNorm < 240f -> Triple(0f, x, c)
        hNorm < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Dynamic Fluid Animated Mesh Gradient
 * Harmonically adapts all mesh orbs, radiant glows, and dark obsidian base tones
 * directly from the user's selected accent color (Green, Purple, Blue, Coral, etc.).
 */
@Composable
fun LiquidMeshBackground(
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val transition = rememberInfiniteTransition(label = "LiquidMeshAnim")

    val t1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t1"
    )

    val t2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t2"
    )

    val t3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t3"
    )

    // Compute harmonic accent-derived palette
    val hsl = remember(accentColor) { colorToHsl(accentColor) }
    val hue = hsl[0]
    val sat = hsl[1].coerceAtLeast(0.40f)

    val baseCanvasTop = remember(accentColor) { hslToColor(hue, sat * 0.35f, 0.045f) }
    val baseCanvasMid = remember(accentColor) { hslToColor(hue, sat * 0.25f, 0.025f) }
    val baseCanvasBottom = remember(accentColor) { hslToColor(hue, sat * 0.18f, 0.012f) }

    // Harmonic mesh gradient orbs matching user's accent color
    val orb1Primary = remember(accentColor) { hslToColor(hue, sat.coerceAtLeast(0.80f), 0.48f) }
    val orb1Secondary = remember(accentColor) { hslToColor(hue - 15f, sat.coerceAtLeast(0.70f), 0.38f) }

    val orb2Analogous = remember(accentColor) { hslToColor(hue + 35f, sat.coerceAtLeast(0.75f), 0.42f) }
    val orb2Deep = remember(accentColor) { hslToColor(hue + 25f, sat.coerceAtLeast(0.60f), 0.22f) }

    val orb3Complement = remember(accentColor) { hslToColor(hue - 30f, sat.coerceAtLeast(0.80f), 0.46f) }
    val orb3Deep = remember(accentColor) { hslToColor(hue - 45f, sat.coerceAtLeast(0.65f), 0.20f) }

    val orb4Highlight = remember(accentColor) { hslToColor(hue + 15f, sat.coerceAtLeast(0.85f), 0.62f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@drawBehind

                // Base Deep Accent-Tinted Obsidian Canvas
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            baseCanvasTop,
                            baseCanvasMid,
                            baseCanvasBottom
                        )
                    )
                )

                // 1. Top-Left / Center Pulsing Primary Accent Orb
                val cx1 = w * (0.28f + 0.16f * sin(t1))
                val cy1 = h * (0.22f + 0.14f * cos(t1 * 0.9f))
                val r1 = (w * 0.68f) * (0.85f + 0.15f * sin(t1 * 1.2f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.50f),
                            orb1Primary.copy(alpha = 0.38f),
                            orb1Secondary.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(cx1, cy1),
                        radius = r1
                    ),
                    radius = r1,
                    center = Offset(cx1, cy1)
                )

                // 2. Center-Right Radiant Analogous Accent Orb
                val cx2 = w * (0.75f + 0.18f * cos(t2))
                val cy2 = h * (0.45f + 0.18f * sin(t2 * 0.8f))
                val r2 = (w * 0.72f) * (0.88f + 0.12f * cos(t2 * 1.1f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            orb2Analogous.copy(alpha = 0.42f),
                            orb2Deep.copy(alpha = 0.26f),
                            Color.Transparent
                        ),
                        center = Offset(cx2, cy2),
                        radius = r2
                    ),
                    radius = r2,
                    center = Offset(cx2, cy2)
                )

                // 3. Bottom Flowing Secondary Accent Glow Orb
                val cx3 = w * (0.35f + 0.22f * cos(t3))
                val cy3 = h * (0.78f + 0.14f * sin(t3 * 0.95f))
                val r3 = (w * 0.76f) * (0.90f + 0.10f * sin(t3))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            orb3Complement.copy(alpha = 0.38f),
                            orb3Deep.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(cx3, cy3),
                        radius = r3
                    ),
                    radius = r3,
                    center = Offset(cx3, cy3)
                )

                // 4. Subtle Top-Right Ambient Luminous Shimmer
                val cx4 = w * (0.82f + 0.12f * sin(t1 * 0.7f))
                val cy4 = h * (0.12f + 0.10f * cos(t2 * 0.8f))
                val r4 = (w * 0.52f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            orb4Highlight.copy(alpha = 0.32f),
                            Color.Transparent
                        ),
                        center = Offset(cx4, cy4),
                        radius = r4
                    ),
                    radius = r4,
                    center = Offset(cx4, cy4)
                )

                // 5. Overall Frosted Ambient Tint Overlay
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.08f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.35f)
                        )
                    )
                )
            }
    ) {
        content()
    }
}

/**
 * Liquid Glassmorphism Container Card with light refraction highlight and micro-press animation
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    fillAlpha: Float = 0.08f,
    borderHighlightAlpha: Float = 0.32f,
    borderShadowAlpha: Float = 0.08f,
    accentGlow: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.975f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glass_press_scale"
    )

    val borderBrush = remember(borderHighlightAlpha, borderShadowAlpha, accentGlow) {
        if (accentGlow != null) {
            Brush.verticalGradient(
                listOf(
                    accentGlow.copy(alpha = 0.60f),
                    Color.White.copy(alpha = borderHighlightAlpha),
                    accentGlow.copy(alpha = 0.20f),
                    Color.White.copy(alpha = borderShadowAlpha)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = borderHighlightAlpha),
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = borderShadowAlpha)
                )
            )
        }
    }

    val backgroundBrush = remember(fillAlpha, accentGlow) {
        if (accentGlow != null) {
            Brush.verticalGradient(
                listOf(
                    accentGlow.copy(alpha = fillAlpha * 1.5f),
                    Color.White.copy(alpha = fillAlpha),
                    Color.Black.copy(alpha = fillAlpha * 2f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = fillAlpha * 1.4f),
                    Color.White.copy(alpha = fillAlpha),
                    Color.White.copy(alpha = fillAlpha * 0.6f)
                )
            )
        }
    }

    val baseModifier = modifier
        .scale(scale)
        .shadow(
            elevation = 10.dp,
            shape = shape,
            clip = false,
            spotColor = Color.Black.copy(alpha = 0.45f),
            ambientColor = Color.White.copy(alpha = 0.10f)
        )
        .clip(shape)
        .background(backgroundBrush)
        .border(1.dp, borderBrush, shape)

    val finalModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        baseModifier
    }

    Column(
        modifier = finalModifier.padding(contentPadding),
        content = content
    )
}

/**
 * Floating Frosted Glass Settings / List Tile with Glowing Icon Badge
 */
@Composable
fun GlassTile(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = LocalAccentColor.current,
    badgeText: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tile_scale"
    )

    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .scale(scale)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                clip = false,
                spotColor = Color.Black.copy(alpha = 0.40f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.05f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glowing Glass Icon Badge
            if (icon != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = iconTint.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, iconTint.copy(alpha = 0.40f)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        shadow = LiquidGlassTokens.SubtleTextShadow
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            shadow = LiquidGlassTokens.SubtleTextShadow
                        ),
                        color = Color(0xFFD1C4E9),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = iconTint.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, iconTint.copy(alpha = 0.50f)),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = iconTint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.50f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Deep Frosted Glass Modal Dialog with Light Refraction Border and Blur Feel
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val accent = LocalAccentColor.current
    val shape = RoundedCornerShape(28.dp)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = modifier
                    .fillMaxWidth(0.94f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // prevent dismissing when tapping inside
                    )
                    .shadow(
                        elevation = 24.dp,
                        shape = shape,
                        clip = false,
                        spotColor = Color.Black.copy(alpha = 0.70f)
                    )
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1F1738).copy(alpha = 0.94f),
                                Color(0xFF130E26).copy(alpha = 0.97f)
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.75f),
                                Color.White.copy(alpha = 0.40f),
                                Color(0xFF4C1D95).copy(alpha = 0.30f)
                            )
                        ),
                        shape
                    )
                    .padding(22.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            shadow = LiquidGlassTokens.TextShadow
                        ),
                        color = Color.White
                    )

                    Spacer(Modifier.height(16.dp))

                    content()

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.invoke()
                        if (confirmButton != null && dismissButton != null) {
                            Spacer(Modifier.width(8.dp))
                        }
                        confirmButton?.invoke()
                    }
                }
            }
        }
    }
}

/**
 * Frosted Glass Action Pill Button
 */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = LocalAccentColor.current,
    selected: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pill_scale"
    )

    val shape = RoundedCornerShape(24.dp)

    val bgBrush = if (selected) {
        Brush.horizontalGradient(
            listOf(
                accentColor.copy(alpha = 0.85f),
                accentColor.copy(alpha = 0.65f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.05f)
            )
        )
    }

    val borderBrush = if (selected) {
        Brush.horizontalGradient(
            listOf(
                Color.White.copy(alpha = 0.80f),
                accentColor
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.35f),
                Color.White.copy(alpha = 0.10f)
            )
        )
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .shadow(6.dp, shape = shape, spotColor = if (selected) accentColor.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.3f))
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            ),
        shape = shape,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    shadow = LiquidGlassTokens.SubtleTextShadow
                ),
                color = Color.White,
                maxLines = 1
            )
        }
    }
}
