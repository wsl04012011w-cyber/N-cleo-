package com.example.engine

import com.example.data.local.MessageEntity
import com.example.data.local.ModelSlotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

enum class EngineState(val labelPtBr: String) {
    READY_STRUCTURAL("Modo Estrutural"),
    UNCONFIGURED("Engine não conectada"),
    INITIALIZING("Inicializando motor..."),
    READY("Pronto para inferência"),
    GENERATING("Gerando resposta..."),
    ERROR("Erro no motor")
}

data class ChatAttachment(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val mimeType: String,
    val uriString: String,
    val isImage: Boolean,
    val sizeBytes: Long = 0L
)

data class GenerationParameters(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 1024
)

data class InferenceRequest(
    val conversationId: String,
    val assistantName: String,
    val systemPrompt: String,
    val history: List<MessageEntity>,
    val userPrompt: String,
    val attachments: List<ChatAttachment> = emptyList(),
    val modelSlot: ModelSlotEntity,
    val parameters: GenerationParameters
)

data class GenerationChunk(
    val deltaText: String,
    val isFinished: Boolean = false,
    val tokensGenerated: Int = 0,
    val elapsedMs: Long = 0L
)

data class EngineCapabilities(
    val engineName: String,
    val engineVersion: String,
    val supportsStreaming: Boolean,
    val supportsLocalGguf: Boolean,
    val supportsSystemPrompts: Boolean,
    val isStructuralMode: Boolean
)

/**
 * Contrato desacoplado de inferência do Núcleo Chat.
 * Permite plugar futuramente motores como llama.cpp (GGUF local),
 * MediaPipe LLM Inference ou Firebase AI Logic sem alterar telas ou banco Room.
 */
interface InferenceEngine {
    val status: StateFlow<EngineState>
    val capabilities: EngineCapabilities
    suspend fun preloadModel(modelSlot: ModelSlotEntity): Boolean = true
    fun cancelPreload() {}
    fun generateStream(request: InferenceRequest): Flow<GenerationChunk>
}
