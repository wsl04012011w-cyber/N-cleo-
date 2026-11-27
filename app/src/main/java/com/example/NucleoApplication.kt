package com.example

import android.app.Application
import com.example.data.download.ModelDownloadManager
import com.example.data.local.NucleoDatabase
import com.example.data.local.UserPreferencesDataStore
import com.example.data.repository.NucleoRepository
import com.example.engine.InferenceEngine
import com.example.engine.LlamaCppInferenceEngine
import com.example.engine.LlamaCppNativeBridge
import com.example.engine.StructuralPreviewEngine

class NucleoApplication : Application() {

    val database: NucleoDatabase by lazy { NucleoDatabase.getInstance(this) }
    val preferencesDataStore: UserPreferencesDataStore by lazy { UserPreferencesDataStore(this) }
    val modelDownloadManager: ModelDownloadManager by lazy { ModelDownloadManager(this) }

    // Usa llama.cpp nativo quando compilado com NDK (ex.: GitHub Actions); fallback estrutural no preview.
    val inferenceEngine: InferenceEngine by lazy {
        if (LlamaCppNativeBridge.isNativeLoaded) {
            LlamaCppInferenceEngine(this)
        } else {
            StructuralPreviewEngine()
        }
    }

    val repository: NucleoRepository by lazy {
        NucleoRepository(
            database = database,
            preferencesDataStore = preferencesDataStore,
            inferenceEngine = inferenceEngine,
            modelDownloadManager = modelDownloadManager
        )
    }
}
