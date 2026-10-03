package com.zyfen.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun ZyfenSplashScreen(
    onFinished: () -> Unit
) {
    var startAnim by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "logo_alpha"
    )

    val pulseGlow by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    LaunchedEffect(Unit) {
        startAnim = true
        delay(1400)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0B12)),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient glow behind logo
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(scale * 1.2f)
                .alpha(pulseGlow)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
        ) {
            // Official ZYFEN Musical Logo (as provided in reference image 1)
            Image(
                painter = painterResource(R.drawable.ic_zyfen_logo_white),
                contentDescription = "ZYFEN Logo",
                modifier = Modifier.size(110.dp)
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "ZYFEN MUSIC",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                ),
                color = Color.White
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Pure Sound • Infinite Waves",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp
                ),
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
