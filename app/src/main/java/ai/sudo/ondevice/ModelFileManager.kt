package ai.sudo.ondevice

import android.content.Context
import java.io.File


class ModelFileManager(private val context: Context) {
    companion object {
        private const val MODEL_NAME = "model_1.gguf"
    }

    fun getModelFile(): File {
        val modelDirectory = File(context.filesDir, "models")
        if (!modelDirectory.exists()) {
            modelDirectory.mkdirs()
        }
        val modelFile = File(modelDirectory, MODEL_NAME)
        if (!modelFile.exists()) {
            copyModelFromAssets(modelFile)
        }
        return modelFile
    }

    private fun copyModelFromAssets(destination: File) {
        context.assets.open(MODEL_NAME).use { input ->
            destination.outputStream()
                .use { output -> input.copyTo(output, bufferSize = 1024 * 1024) }
        }
    }
}