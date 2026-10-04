package ai.sudo.ondevice


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/*class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        initializeModel()
    }

    fun initializeModel() {
        if (_uiState.value.modelState is ModelState.Loading) {
            return
        }

        _uiState.update {
            it.copy(
                modelState = ModelState.Loading,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                repository.initialize()
            }

            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            modelState = ModelState.Ready,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    val message =
                        error.message ?: "Unable to load model"

                    _uiState.update {
                        it.copy(
                            modelState = ModelState.Error(message),
                            errorMessage = message
                        )
                    }
                }
        }
    }

    fun sendMessage(text: String) {
        val prompt = text.trim()

        if (prompt.isEmpty()) return

        if (_uiState.value.modelState !is ModelState.Ready) {
            return
        }

        if (_uiState.value.isGenerating) {
            return
        }

        val userMessage = ChatMessage(
            id = System.nanoTime(),
            text = prompt,
            role = MessageRole.USER
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isGenerating = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {

            val result = withContext(Dispatchers.Default) {
                repository.generate(
                    prompt = prompt,
                    maxTokens = 128
                )
            }

            result
                .onSuccess { response ->

                    val assistantMessage = ChatMessage(
                        id = System.nanoTime(),
                        text = response,
                        role = MessageRole.ASSISTANT
                    )

                    _uiState.update {
                        it.copy(
                            messages = it.messages + assistantMessage,
                            isGenerating = false
                        )
                    }
                }
                .onFailure { error ->

                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            errorMessage =
                                error.message ?: "Generation failed"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }

    override fun onCleared() {
        viewModelScope.launch {
            repository.close()
        }
        super.onCleared()
    }
}*/

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)

    private
    val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var isInitializing = false

    init {
        initializeModel()
    }

    fun initializeModel(force: Boolean = false) {
        if (isInitializing && !force) {
            return
        }
        isInitializing = true
        _uiState.update {
            it.copy(
                modelState = ModelState.Loading,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.Default) { repository.initialize() }
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        modelState = ModelState.Ready,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        modelState = ModelState.Error(
                            error.message ?: "Unable to load model"
                        ), errorMessage = error.message ?: "Unable to load model"
                    )
                }
            }
            isInitializing = false
        }
    }

    fun retryLoadModel() {
        initializeModel(force = true)
    }

    fun sendMessage(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty()) {
            return
        }
        if (_uiState.value.modelState != ModelState.Ready) {
            return
        }
        if (_uiState.value.isGenerating) {
            return
        }
        val userMessage = ChatMessage(
            id = System.nanoTime(),
            text = prompt,
            role = MessageRole.USER
        )
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isGenerating = true,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                repository.generate(
                    prompt = prompt,
                    maxTokens = 128
                )
            }
            result.onSuccess { response ->
                val assistantMessage = ChatMessage(
                    id = System.nanoTime(),
                    text = response,
                    role = MessageRole.ASSISTANT
                )
                _uiState.update {
                    it.copy(
                        messages = it.messages + assistantMessage,
                        isGenerating = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = error.message ?: "Generation failed"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
