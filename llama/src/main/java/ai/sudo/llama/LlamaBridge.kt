package ai.sudo.llama

//class LlamaBridge {
//
//    companion object {
//        init {
//            System.loadLibrary("llama-jni") // loads libllama-android.so
//        }
//    }
//
//    private var sessionPtr: Long = 0L
//
//    // --- Native (JNI) methods — names/signatures must match llama-android.cpp exactly ---
//    private external fun loadModel(modelPath: String, nCtx: Int, nThreads: Int): Long
//    private external fun generate(sessionPtr: Long, prompt: String, maxTokens: Int): String
//    private external fun freeModel(sessionPtr: Long)
//
//    // --- Kotlin-friendly wrappers ---
//    fun load(modelPath: String, nCtx: Int = 2048, nThreads: Int = 4): Boolean {
//        if (sessionPtr != 0L) unload()
//        sessionPtr = loadModel(modelPath, nCtx, nThreads)
//        return sessionPtr != 0L
//    }
//
//    fun generateText(prompt: String, maxTokens: Int = 256): String {
//        check(sessionPtr != 0L) { "Model not loaded. Call load() first." }
//        return generate(sessionPtr, prompt, maxTokens)
//    }
//
//    fun unload() {
//        if (sessionPtr != 0L) {
//            freeModel(sessionPtr)
//            sessionPtr = 0L
//        }
//    }
//
//    val isLoaded: Boolean
//        get() = sessionPtr != 0L
//}

/*

class LlamaBridge : AutoCloseable {

    companion object {
        init {
            System.loadLibrary("llama-jni")
        }
    }

    private var sessionPtr: Long = 0L


    *//*
     * Native functions
     *//*
    private external fun loadModel(
        modelPath: String,
        nCtx: Int,
        nThreads: Int
    ): Long

    private external fun generate(
        sessionPtr: Long,
        prompt: String,
        maxTokens: Int
    ): String

    private external fun freeModel(
        sessionPtr: Long
    )


    *//*
     * Load GGUF model.
     *//*
    @Synchronized
    fun load(
        modelPath: String,
        nCtx: Int = 2048,
        nThreads: Int = 6
    ): Boolean {

        *//*
         * If another model is already loaded,
         * release it first.
         *//*
        unload()

        require(modelPath.isNotBlank()) {
            "Model path cannot be empty"
        }

        require(nCtx > 0) {
            "nCtx must be greater than 0"
        }

        require(nThreads > 0) {
            "nThreads must be greater than 0"
        }


        val pointer =
            loadModel(
                modelPath,
                nCtx,
                nThreads
            )


        if (pointer == 0L) {
            return false
        }


        sessionPtr = pointer

        return true
    }


    *//*
     * Generate text synchronously.
     *//*
    @Synchronized
    fun generateText(
        prompt: String,
        maxTokens: Int = 256
    ): String {

        check(isLoaded) {
            "Model not loaded. Call load() first."
        }

        require(prompt.isNotBlank()) {
            "Prompt cannot be empty"
        }

        require(maxTokens > 0) {
            "maxTokens must be greater than 0"
        }


        return generate(
            sessionPtr,
            prompt,
            maxTokens
        )
    }


    *//*
     * Check whether model is loaded.
     *//*
    val isLoaded: Boolean
        @Synchronized
        get() = sessionPtr != 0L


    *//*
     * Unload model.
     *//*
    @Synchronized
    fun unload() {

        val pointer = sessionPtr

        if (pointer == 0L) {
            return
        }

        sessionPtr = 0L

        freeModel(pointer)
    }


    *//*
     * AutoCloseable support.
     *
     * This allows:
     *
     * llamaBridge.use {
     *     ...
     * }
     *//*
    override fun close() {
        unload()
    }
}*/




/**
 * Receives generated text as it is produced.
 * Each [piece] is complete UTF-8 (never a half emoji / half character).
 * Return false to stop generation.
 *
 * NOTE: called on the thread that called generateText() — run that on a
 * background thread and post to the UI thread yourself.
 */
fun interface TokenListener {
    fun onToken(piece: ByteArray): Boolean
}

class LlamaBridge : AutoCloseable {

    companion object {
        init {
            System.loadLibrary("llama-jni")
        }
    }

    private var sessionPtr: Long = 0L

    @Volatile
    private var cancelRequested = false

    // ---- Native functions ----------------------------------

    private external fun loadModel(
        modelPath: String,
        nCtx: Int,
        nThreads: Int
    ): Long

    private external fun generate(
        sessionPtr: Long,
        systemPrompt: ByteArray?,
        prompt: ByteArray,
        maxTokens: Int,
        temperature: Float,
        listener: TokenListener?
    ): ByteArray

    private external fun freeModel(sessionPtr: Long)

    // ---- Public API ----------------------------------------

    /**
     * @param nThreads 0 = auto-detect performance cores (recommended).
     */
    @Synchronized
    fun load(
        modelPath: String,
        nCtx: Int = 2048,
        nThreads: Int = 0
    ): Boolean {
        require(modelPath.isNotBlank()) { "Model path cannot be empty" }
        require(nCtx > 0) { "nCtx must be greater than 0" }
        require(nThreads >= 0) { "nThreads must be >= 0" }

        unload()

        val pointer = loadModel(modelPath, nCtx, nThreads)
        if (pointer == 0L) return false

        sessionPtr = pointer
        return true
    }

    /**
     * @param temperature 0 = greedy (deterministic). ~0.7 gives natural text.
     * @param onToken     optional streaming callback, see [TokenListener].
     */
    @Synchronized
    fun generateText(
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        onToken: ((String) -> Unit)? = null
    ): String {
        check(isLoaded) { "Model not loaded. Call load() first." }
        require(prompt.isNotBlank()) { "Prompt cannot be empty" }
        require(maxTokens > 0) { "maxTokens must be greater than 0" }

        cancelRequested = false

        val listener = TokenListener { bytes ->
            onToken?.invoke(String(bytes, Charsets.UTF_8))
            !cancelRequested
        }

        val result = generate(
            sessionPtr,
            systemPrompt?.takeIf { it.isNotBlank() }?.toByteArray(Charsets.UTF_8),
            prompt.toByteArray(Charsets.UTF_8),
            maxTokens,
            temperature,
            listener
        )
        return String(result, Charsets.UTF_8)
    }

    /** Safe to call from any thread; stops the running generation. */
    fun cancel() {
        cancelRequested = true
    }

    val isLoaded: Boolean
        @Synchronized get() = sessionPtr != 0L

    @Synchronized
    fun unload() {
        val pointer = sessionPtr
        if (pointer == 0L) return
        sessionPtr = 0L
        freeModel(pointer)
    }

    override fun close() {
        cancel()      // let a running generation finish quickly
        unload()      // waits for the lock, then frees
    }
}