package com.example.engine

/**
 * Ponte JNI para a biblioteca nativa `libnucleo_llama.so` compilada com llama.cpp.
 * Em builds sem NDK/llama.cpp (ex.: preview rápido), `isNativeLoaded` permanece false
 * e o app utiliza automaticamente o [StructuralPreviewEngine].
 */
object LlamaCppNativeBridge {

    val isNativeLoaded: Boolean = try {
        System.loadLibrary("nucleo_llama")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    interface TokenCallback {
        fun onTokenBytes(bytes: ByteArray)
    }

    external fun nativeSystemInfo(): String

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
}
