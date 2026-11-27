package com.example.engine

import com.example.util.NucleoLogger

/**
 * Ponte JNI para a biblioteca nativa `libnucleo_llama.so` compilada com llama.cpp.
 * Em builds sem NDK/llama.cpp (ex.: preview rápido), `isNativeLoaded` permanece false
 * e o app utiliza automaticamente o [StructuralPreviewEngine].
 */
object LlamaCppNativeBridge {

    var loadErrorMessage: String? = null
        private set

    val isNativeLoaded: Boolean = try {
        System.loadLibrary("nucleo_llama")
        true
    } catch (t: UnsatisfiedLinkError) {
        loadErrorMessage = t.message ?: "UnsatisfiedLinkError"
        false
    }

    interface TokenCallback {
        fun onTokenBytes(bytes: ByteArray)
    }

    external fun nativeSystemInfo(): String

    external fun nativeDrainLogs(): Array<String>

    external fun nativeLoadModel(
        modelPath: String,
        nCtxRequested: Int
    ): Boolean

    external fun nativeUnloadModel()

    external fun nativeCancel()

    external fun nativeGenerateStream(
        roles: Array<String>,
        contents: Array<String>,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        maxTokens: Int,
        callback: TokenCallback
    ): Int

    fun drainLogsToLogger() {
        if (!isNativeLoaded) return
        val lines = runCatching { nativeDrainLogs() }.getOrNull() ?: return
        for (line in lines) {
            NucleoLogger.native(line)
        }
    }
}
