package ai.sudo.ondevice

import ai.sudo.ondevice.components.ChatContent
import ai.sudo.ondevice.components.MessageInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel


@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var inputText by rememberSaveable {
        mutableStateOf("")
    }

    Scaffold(
        topBar = {
            ChatTopBar(modelState = uiState.modelState)
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .navigationBarsPadding()
        ) {

            ChatContent(
                state = uiState,
                onRetry = viewModel::initializeModel,
                modifier = Modifier.weight(1f)
            )

            MessageInput(
                value = inputText,
                enabled =
                    uiState.modelState is ModelState.Ready &&
                            !uiState.isGenerating,
                onValueChange = {
                    inputText = it
                },
                onSend = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    modelState: ModelState
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Local AI",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = when (modelState) {
                        ModelState.Loading -> "Loading model…"
                        ModelState.Ready -> "Ready"
                        is ModelState.Error -> "Model unavailable"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (modelState) {
                        ModelState.Loading ->
                            MaterialTheme.colorScheme.onSurfaceVariant

                        ModelState.Ready ->
                            MaterialTheme.colorScheme.primary

                        is ModelState.Error ->
                            MaterialTheme.colorScheme.error
                    }
                )
            }
        },
        navigationIcon = {
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = "Local AI"
            )
        }
    )
}



