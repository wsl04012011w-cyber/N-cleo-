package com.example.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * Implementação estrutural usada antes do acoplamento da engine real.
 * Demonstra streaming, cancelamento, leitura do prompt de sistema ativo,
 * parâmetros de geração e histórico persistido no Room.
 */
class StructuralPreviewEngine : InferenceEngine {

    private val _status = MutableStateFlow(EngineState.READY_STRUCTURAL)
    override val status: StateFlow<EngineState> = _status.asStateFlow()

    override val capabilities = EngineCapabilities(
        engineName = "StructuralPreviewEngine",
        engineVersion = "1.0-estrutura",
        supportsStreaming = true,
        supportsLocalGguf = false,
        supportsSystemPrompts = true,
        isStructuralMode = true
    )

    override fun generateStream(request: InferenceRequest): Flow<GenerationChunk> = flow {
        val startMs = System.currentTimeMillis()
        _status.value = EngineState.GENERATING

        val promptSnippet = request.systemPrompt
            .trim()
            .replace("\n", " ")
            .let { if (it.length > 110) it.take(110) + "..." else it }

        val tempFmt = String.format(Locale.US, "%.2f", request.parameters.temperature)
        val topPFmt = String.format(Locale.US, "%.2f", request.parameters.topP)
        val repeatFmt = String.format(Locale.US, "%.2f", request.parameters.repeatPenalty)

        val fullResponse = buildString {
            appendLine("[Modo Estrutural • Engine desacoplada]")
            appendLine()
            appendLine("Sua mensagem foi recebida e persistida localmente no Room:")
            appendLine("> \"${request.userPrompt.trim()}\"")
            appendLine()
            appendLine("Contexto aplicado nesta execução:")
            appendLine("• Assistente: ${request.assistantName}")
            appendLine("• Prompt de sistema: \"$promptSnippet\"")
            appendLine("• Slot selecionado: ${request.modelSlot.displayName} (${request.modelSlot.quantizationLabel}) • Visão: ${if (request.modelSlot.supportsVision) "Sim" else "Não"}")
            if (request.attachments.isNotEmpty()) {
                val names = request.attachments.joinToString(", ") { it.displayName }
                appendLine("• Anexos (${request.attachments.size}): $names")
            }
            appendLine("• Parâmetros: Temp $tempFmt | Top-P $topPFmt | Top-K ${request.parameters.topK} | Repeat $repeatFmt | Max ${request.parameters.maxTokens} tokens")
            appendLine("• Mensagens no contexto: ${request.history.size}")
            appendLine()
            append("Quando conectarmos a engine real (GGUF local, MediaPipe ou Gemini) à interface InferenceEngine, esta resposta será substituída pela geração completa mantendo todo o histórico, assistentes e controles intactos.")
        }

        val tokens = fullResponse.split(Regex("(?<=\\s)"))
        var tokenCount = 0

        try {
            for (token in tokens) {
                delay(24L)
                tokenCount++
                emit(
                    GenerationChunk(
                        deltaText = token,
                        isFinished = false,
                        tokensGenerated = tokenCount,
                        elapsedMs = System.currentTimeMillis() - startMs
                    )
                )
            }
            emit(
                GenerationChunk(
                    deltaText = "",
                    isFinished = true,
                    tokensGenerated = tokenCount,
                    elapsedMs = System.currentTimeMillis() - startMs
                )
            )
        } finally {
            _status.value = EngineState.READY_STRUCTURAL
        }
    }
}
