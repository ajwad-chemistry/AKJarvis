package com.akjarvis.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akjarvis.app.domain.model.AssistantState

private fun stateColor(state: AssistantState): Color = when (state) {
    AssistantState.IDLE -> Color(0xFF6366F1)
    AssistantState.LISTENING -> Color(0xFF22C55E)
    AssistantState.THINKING -> Color(0xFF818CF8)
    AssistantState.SPEAKING -> Color(0xFFA5B4FC)
}

/**
 * The animated assistant orb. Pulses slowly when idle and faster while
 * listening/thinking/speaking, with the color reflecting the current state.
 */
@Composable
fun AssistantOrb(state: AssistantState, modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == AssistantState.IDLE) 3000 else 1100,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )
    val color = stateColor(state)

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = this.size.minDimension / 2f

        repeat(3) { ring ->
            val phase = (pulse + ring / 3f) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - phase) * 0.30f),
                radius = baseRadius * (0.55f + phase * 0.72f),
                center = center,
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.12f)),
                center = center,
                radius = baseRadius * 0.6f,
            ),
            radius = baseRadius * 0.52f * (1f + 0.06f * pulse),
            center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = baseRadius * 0.10f,
            center = center,
        )
    }
}
