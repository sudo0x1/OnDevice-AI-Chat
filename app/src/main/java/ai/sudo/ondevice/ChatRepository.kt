package ai.sudo.ondevice

import ai.sudo.llama.LlamaBridge
import android.content.Context
import android.util.Log
import java.io.File

class ChatRepository(
    context: Context
) : AutoCloseable {

    private val llama = LlamaBridge()
    private val modelFileManager = ModelFileManager(context.applicationContext)
    private val modelPath: String =
        File(
            context.filesDir,
            "models/tinyllama.gguf"
        ).absolutePath

    fun initialize(): Result<Unit> {
        return runCatching {
            val modelFile =
                modelFileManager.getModelFile()
            check (modelFile.exists()) { "Model file does not exist: ${modelFile.absolutePath}" }
            check (modelFile.length() > 0) { "Model file is empty" }
            val loaded = llama.load(
                modelPath = modelFile.absolutePath,
                nCtx = 2048,
                nThreads = 6
            )
            check (loaded) { "Failed to load model" }
        }
    }

    fun generate(
        prompt: String,
        maxTokens: Int
    ): Result<String> {
        return runCatching {
            return runCatching {
                Log.d( "ChatRepository", "Starting generation: prompt=$prompt" )
                val response = llama.generateText( prompt = prompt, maxTokens = maxTokens )
                Log.d( "ChatRepository", "Generation finished: response=$response" )
                response
            }
        }
    }

    override fun close() {
        llama.close()
    }
}