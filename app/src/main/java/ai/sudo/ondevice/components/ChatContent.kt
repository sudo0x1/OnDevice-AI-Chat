package ai.sudo.ondevice.components


import ai.sudo.ondevice.ChatUiState
import ai.sudo.ondevice.ModelState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


@Composable
fun ChatContent(
    state: ChatUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (val modelState = state.modelState) {

        ModelState.Loading -> {
            LoadingModel(
                modifier = modifier
            )
        }

        ModelState.Ready -> {
            ChatMessages(
                messages = state.messages,
                isGenerating = state.isGenerating,
                modifier = modifier
            )
        }

        is ModelState.Error -> {
            ModelError(
                message = modelState.message,
                onRetry = onRetry,
                modifier = modifier
            )
        }
    }
}