package ai.sudo.ondevice.components


import ai.sudo.ondevice.ChatMessage
import ai.sudo.ondevice.MessageRole
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun MessageBubble(
    message: ChatMessage
) {
    val isUser = message.role == MessageRole.USER

    val alignment =
        if (isUser) {
            Alignment.CenterEnd
        } else {
            Alignment.CenterStart
        }

    val containerColor =
        if (isUser) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    val contentColor =
        if (isUser) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(18.dp),
            color = containerColor
        ) {
            Text(
                text = message.text,
                color = contentColor,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}