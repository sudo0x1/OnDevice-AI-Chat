package ai.sudo.ondevice

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val modelState: ModelState = ModelState.Loading,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface ModelState {
    data object Loading : ModelState
    data object Ready : ModelState
    data class Error(val message: String) : ModelState
}