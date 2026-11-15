package com.akjarvis.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akjarvis.app.domain.model.AiMode
import com.akjarvis.app.domain.model.ChatMessage
import com.akjarvis.app.ui.theme.Indigo
import com.akjarvis.app.ui.theme.IndigoDeep
import com.akjarvis.app.ui.theme.IndigoLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Liquid Glass" chat bubble (design borrowed from the AbuZar Jarvis app):
 * user messages carry an indigo gradient, assistant messages a subtle
 * translucent glass surface with a hairline border.
 */
@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.isFromUser
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(IndigoLight, Indigo),
                            ),
                        ),
                )
            }
            BubbleSurface(isUser = isUser, isDark = isDark) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 19.sp,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isUser && message.mode != null) {
                Text(
                    text = when (message.mode) {
                        AiMode.CLOUD -> "\u2601 cloud"
                        AiMode.FALLBACK -> "\u26a1 offline"
                        null -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun BubbleSurface(
    isUser: Boolean,
    isDark: Boolean,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    if (isUser) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(Indigo.copy(alpha = 0.95f), IndigoDeep.copy(alpha = 0.92f)),
                    ),
                )
                .border(1.dp, IndigoLight.copy(alpha = 0.5f), shape),
        ) { content() }
    } else {
        val fill = if (isDark) {
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f)),
            )
        } else {
            Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.85f)))
        }
        val border = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(shape)
                .background(fill)
                .border(1.dp, border, shape),
        ) { content() }
    }
}

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))
