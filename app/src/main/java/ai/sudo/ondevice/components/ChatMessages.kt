package ai.sudo.ondevice.components

import ai.sudo.ondevice.ChatMessage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatMessages(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(
                messages.lastIndex
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        items(
            items = messages,
            key = { message -> message.id }
        ) { message ->

            MessageBubble(
                message = message
            )
        }

        if (isGenerating) {
            item(key = "typing_indicator") {
                TypingIndicator()
            }
        }
    }
}