package app.bdayboom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.bdayboom.R
import app.bdayboom.ui.theme.AppTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// Confetti colours taken from the logo.
private val confetti = listOf(Color(0xFFFF4FA3), Color(0xFFFFC21A), Color(0xFF29B6F6), Color(0xFFB04DFF), Color(0xFFFF8A1F))

/** The logo pops in with a little confetti burst, holds, then the whole overlay fades into the app. ~1.5 s. */
@Composable
fun SplashOverlay(onDone: () -> Unit) {
    val scale = remember { Animatable(0.3f) }
    val tilt = remember { Animatable(-10f) }
    val burst = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val pop = async { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        val turn = async { tilt.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow)) }
        delay(120)
        burst.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        pop.await(); turn.await()
        delay(150)
        fade.animateTo(0f, tween(280))
        onDone()
    }

    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(AppTheme.colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(320.dp)) {
            val p = burst.value
            if (p <= 0f || p >= 1f) return@Canvas
            val reach = size.minDimension / 2 * (0.35f + 0.65f * p)
            for (i in 0 until 14) {
                val angle = Math.toRadians(i * 360.0 / 14 + 8).toFloat()
                drawCircle(
                    confetti[i % confetti.size].copy(alpha = 1f - p),
                    radius = (if (i % 2 == 0) 7.dp else 5.dp).toPx(),
                    center = center + Offset(cos(angle) * reach, sin(angle) * reach),
                )
            }
        }
        Image(
            painterResource(R.drawable.splash_logo),
            contentDescription = "BdayBoom",
            modifier = Modifier.size(220.dp).graphicsLayer {
                scaleX = scale.value; scaleY = scale.value; rotationZ = tilt.value
                alpha = (scale.value * 2.5f - 0.75f).coerceIn(0f, 1f)
            },
        )
    }
}
