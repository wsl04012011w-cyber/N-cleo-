package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.data.local.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.StandardCharsets

class LlamaCppInferenceEngine(
    private val context: Context
) : InferenceEngine {

    private val _status = MutableStateFlow(EngineState.READY)
    override val status: StateFlow<EngineState> = _status.asStateFlow()

    override val capabilities = EngineCapabilities(
        engineName = "llama.cpp (arm64 + KleidiAI)",
        engineVersion = "JNI Native",
        supportsStreaming = true,
        supportsLocalGguf = true,
        supportsSystemPrompts = true,
        isStructuralMode = false
    )

    override fun generateStream(request: InferenceRequest): Flow<GenerationChunk> = callbackFlow {
        val modelPath = request.modelSlot.localFilePath
        val modelFile = if (!modelPath.isNullOrBlank()) File(modelPath) else null

        if (modelFile == null || !modelFile.exists() || modelFile.length() <= 0L) {
            _status.value = EngineState.UNCONFIGURED
            trySend(
                GenerationChunk(
                    deltaText = "Modelo **${request.modelSlot.displayName}** ainda não foi baixado para este aparelho.\n\n" +
                        "Abra o menu lateral → **Modelos** e faça o download do arquivo `.gguf` para executar a engine `llama.cpp` localmente.",
                    isFinished = true,
                    tokensGenerated = 0,
                    elapsedMs = 0L
                )
            )
            close()
            return@callbackFlow
        }

        val job = launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            var tokenCount = 0

            try {
                _status.value = EngineState.INITIALIZING
                val ctxSize = request.modelSlot.contextLength.coerceIn(1024, 4096)
                val loaded = LlamaCppNativeBridge.nativeLoadModel(modelFile.absolutePath, ctxSize)
                if (!loaded) {
                    _status.value = EngineState.ERROR
                    trySend(
                        GenerationChunk(
                            deltaText = "Não foi possível carregar o arquivo GGUF em `${modelFile.name}`. Verifique se o download foi concluído ou se há RAM disponível.",
                            isFinished = true,
                            tokensGenerated = 0,
                            elapsedMs = System.currentTimeMillis() - startedAt
                        )
                    )
                    close()
                    return@launch
                }

                _status.value = EngineState.GENERATING
                val (roles, contents) = buildChatArrays(request)

                val callback = object : LlamaCppNativeBridge.TokenCallback {
                    override fun onTokenBytes(bytes: ByteArray) {
                        val piece = String(bytes, StandardCharsets.UTF_8)
                        tokenCount += 1
                        trySend(
                            GenerationChunk(
                                deltaText = piece,
                                isFinished = false,
                                tokensGenerated = tokenCount,
                                elapsedMs = System.currentTimeMillis() - startedAt
                            )
                        )
                    }
                }

                val result = LlamaCppNativeBridge.nativeGenerateStream(
                    roles = roles,
                    contents = contents,
                    temperature = request.parameters.temperature,
                    topP = request.parameters.topP,
                    topK = request.parameters.topK,
                    repeatPenalty = request.parameters.repeatPenalty,
                    maxTokens = request.parameters.maxTokens,
                    callback = callback
                )

                val elapsed = System.currentTimeMillis() - startedAt
                if (result < 0) {
                    _status.value = EngineState.ERROR
                    trySend(
                        GenerationChunk(
                            deltaText = "\n\n[Erro nativo llama.cpp: código $result]",
                            isFinished = true,
                            tokensGenerated = tokenCount,
                            elapsedMs = elapsed
                        )
                    )
                } else {
                    _status.value = EngineState.READY
                    trySend(
                        GenerationChunk(
                            deltaText = "",
                            isFinished = true,
                            tokensGenerated = result.coerceAtLeast(tokenCount),
                            elapsedMs = elapsed
                        )
                    )
                }
            } catch (t: Throwable) {
                _status.value = EngineState.ERROR
                trySend(
                    GenerationChunk(
                        deltaText = "\n\n[Falha na inferência local: ${t.message ?: "erro desconhecido"}]",
                        isFinished = true,
                        tokensGenerated = tokenCount,
                        elapsedMs = System.currentTimeMillis() - startedAt
                    )
                )
            } finally {
                if (_status.value == EngineState.GENERATING || _status.value == EngineState.INITIALIZING) {
                    _status.value = EngineState.READY
                }
                close()
            }
        }

        awaitClose {
            LlamaCppNativeBridge.nativeCancel()
            job.cancel()
        }
    }

    private fun buildChatArrays(request: InferenceRequest): Pair<Array<String>, Array<String>> {
        val roles = mutableListOf<String>()
        val contents = mutableListOf<String>()

        if (request.systemPrompt.isNotBlank()) {
            roles += "system"
            contents += request.systemPrompt.trim()
        }

        // Mantém histórico recente evitando duplicar a última mensagem do usuário se já inserida
        val recentHistory = request.history
            .filter { it.content.isNotBlank() }
            .takeLast(16)

        val historyWithoutDuplicateTail = if (
            recentHistory.lastOrNull()?.role == MessageRole.USER &&
            recentHistory.lastOrNull()?.content?.trim() == request.userPrompt.trim()
        ) {
            recentHistory.dropLast(1)
        } else {
            recentHistory
        }

        for (msg in historyWithoutDuplicateTail) {
            val mappedRole = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM_NOTE -> "system"
                else -> "user"
            }
            roles += mappedRole
            contents += msg.content
        }

        val attachmentContext = buildAttachmentContext(request.attachments)
        val finalUserPrompt = buildString {
            if (attachmentContext.isNotBlank()) {
                append(attachmentContext)
                append("\n\n")
            }
            append(request.userPrompt)
        }.trim()

        roles += "user"
        contents += finalUserPrompt

        return roles.toTypedArray() to contents.toTypedArray()
    }

    private fun buildAttachmentContext(attachments: List<ChatAttachment>): String {
        if (attachments.isEmpty()) return ""
        val sb = StringBuilder()
        for (att in attachments) {
            if (att.isImage) {
                sb.append("[Imagem anexada: ${att.displayName} (${att.mimeType}) — projetor multimodal mtmd não carregado]\n")
            } else {
                val snippet = readTextAttachmentSnippet(att.uriString)
                if (snippet != null) {
                    sb.append("--- Arquivo: ${att.displayName} ---\n")
                    sb.append(snippet)
                    sb.append("\n--- Fim do arquivo ---\n")
                } else {
                    sb.append("[Arquivo anexado: ${att.displayName} (${att.mimeType})]\n")
                }
            }
        }
        return sb.toString().trim()
    }

    private fun readTextAttachmentSnippet(uriString: String, maxBytes: Int = 12 * 1024): String? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(maxBytes)
                val read = stream.read(buffer)
                if (read <= 0) return null
                // Verifica rapidamente se parece binário (presença de NUL byte)
                for (i in 0 until read) {
                    if (buffer[i].toInt() == 0) return null
                }
                String(buffer, 0, read, StandardCharsets.UTF_8)
            }
        } catch (_: Throwable) {
            null
        }
    }
}
