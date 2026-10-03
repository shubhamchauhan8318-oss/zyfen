package com.zyfen.music.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zyfen.music.ui.theme.LocalAccentColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Signature Curved Album Artwork Component inspired by the premium minimalist reference.
 * Features a large upper arch extending downward with a curved organic bottom edge.
 */
@Composable
fun CurvedAlbumArtwork(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    bottomCurveRadius: Dp = 60.dp,
    elevation: Dp = 12.dp,
    contentScale: ContentScale = ContentScale.Crop
) {
    val shape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = bottomCurveRadius,
        bottomEnd = bottomCurveRadius
    )

    AnimatedContent(
        targetState = artworkUri,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "curvedArtworkAnimation"
    ) { uri ->
        Box(
            modifier = modifier
                .shadow(elevation, shape = shape, clip = false)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (uri.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = "No Artwork",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album Artwork",
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Curved Arc Seeker drawn smoothly around the curved bottom of the artwork dome.
 * Directly inspired by the reference design with minimal sweep track and thumb.
 */
@Composable
fun CurvedArcSeeker(
    progress: Float, // 0f to 1f
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current,
    trackColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
    thumbRadius: Dp = 6.dp,
    strokeWidth: Dp = 3.dp
) {
    var draggingProgress by remember { mutableStateOf<Float?>(null) }
    val currentProgress = (draggingProgress ?: progress).coerceIn(0f, 1f)
    val bgColor = MaterialTheme.colorScheme.background

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = 0f
                    val dx = offset.x - cx
                    val dy = offset.y - cy
                    val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    // Arc goes from 150 deg (left) to 30 deg (bottom-right) or 160 -> 20
                    // Let's map x position across width smoothly:
                    val p = (offset.x / w).coerceIn(0f, 1f)
                    onSeek(p)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        draggingProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val p = (change.position.x / size.width).coerceIn(0f, 1f)
                        draggingProgress = p
                    },
                    onDragEnd = {
                        draggingProgress?.let { onSeek(it) }
                        draggingProgress = null
                    },
                    onDragCancel = {
                        draggingProgress = null
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val strokePx = strokeWidth.toPx()
        val thumbPx = thumbRadius.toPx()

        // We draw a graceful downward curve (arc) from left to right
        val startX = strokePx * 2
        val endX = w - strokePx * 2
        val startY = 10f
        val bottomY = h - thumbPx * 2 - 4f

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(startX, startY)
            quadraticBezierTo(w / 2f, bottomY * 1.35f, endX, startY)
        }

        // Draw background inactive arc
        drawPath(
            path = path,
            color = trackColor,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // Draw active colored progress arc
        // Approximate quadratic bezier position at currentProgress:
        // B(t) = (1-t)^2 P0 + 2(1-t)t P1 + t^2 P2
        val t = currentProgress.toDouble()
        val p0x = startX.toDouble()
        val p0y = startY.toDouble()
        val p1x = (w / 2f).toDouble()
        val p1y = (bottomY * 1.35f).toDouble()
        val p2x = endX.toDouble()
        val p2y = startY.toDouble()

        val activePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(startX, startY)
            // Divide into small increments for accurate active curve
            val steps = 30
            val activeSteps = (steps * currentProgress).toInt().coerceAtLeast(1)
            for (step in 1..activeSteps) {
                val subT = (step.toDouble() / steps) * currentProgress
                val invT = 1.0 - subT
                val bx = invT * invT * p0x + 2.0 * invT * subT * p1x + subT * subT * p2x
                val by = invT * invT * p0y + 2.0 * invT * subT * p1y + subT * subT * p2y
                lineTo(bx.toFloat(), by.toFloat())
            }
        }

        drawPath(
            path = activePath,
            color = accentColor,
            style = Stroke(width = strokePx + 1f, cap = StrokeCap.Round)
        )

        // Draw Thumb dot
        val invT = 1.0 - t
        val thumbX = (invT * invT * p0x + 2.0 * invT * t * p1x + t * t * p2x).toFloat()
        val thumbY = (invT * invT * p0y + 2.0 * invT * t * p1y + t * t * p2y).toFloat()

        // Thumb outer glow / ring
        drawCircle(
            color = bgColor,
            radius = thumbPx + 2f,
            center = Offset(thumbX, thumbY)
        )
        drawCircle(
            color = accentColor,
            radius = thumbPx,
            center = Offset(thumbX, thumbY)
        )
    }
}
