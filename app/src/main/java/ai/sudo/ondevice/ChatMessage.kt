package ai.sudo.ondevice

enum class MessageRole {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: Long,
    val text: String,
    val role: MessageRole
)