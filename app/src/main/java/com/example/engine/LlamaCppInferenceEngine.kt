package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import com.example.data.local.MessageRole
import com.example.data.local.ModelSlotEntity
import com.example.util.NucleoLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class LlamaCppInferenceEngine(
    private val context: Context
) : InferenceEngine {

    @Volatile
    private var lastLoadedPath: String? = null

    @Volatile
    private var lastLoadedCtx: Int = 0

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

    override suspend fun preloadModel(modelSlot: ModelSlotEntity): Boolean {
        val modelPath = modelSlot.localFilePath
        val modelFile = if (!modelPath.isNullOrBlank()) File(modelPath) else null
        if (modelFile == null || !modelFile.exists() || modelFile.length() <= 0L) {
            NucleoLogger.w("Inference", "Pré-carregamento ignorado: arquivo GGUF ausente (${modelSlot.displayName})")
            return false
        }

        val ctxSize = computeSafeContextSize(modelSlot, modelFile)
        if (lastLoadedPath == modelFile.absolutePath && lastLoadedCtx == ctxSize && _status.value == EngineState.READY) {
            NucleoLogger.i("Inference", "Modelo já pré-carregado em memória: ${modelFile.name} (ctx=$ctxSize)")
            return true
        }

        _status.value = EngineState.INITIALIZING
        NucleoLogger.i("Inference", "Pré-carregando modelo ao iniciar chat: ${modelFile.name} (ctx=$ctxSize)...")

        return try {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val poller = launch {
                        while (isActive) {
                            LlamaCppNativeBridge.drainLogsToLogger()
                            delay(300L)
                        }
                    }
                    try {
                        val loaded = LlamaCppNativeBridge.nativeLoadModel(modelFile.absolutePath, ctxSize)
                        LlamaCppNativeBridge.drainLogsToLogger()
                        if (loaded) {
                            lastLoadedPath = modelFile.absolutePath
                            lastLoadedCtx = ctxSize
                            if (_status.value == EngineState.INITIALIZING) {
                                _status.value = EngineState.READY
                            }
                            NucleoLogger.i("Inference", "Pré-carregamento concluído com sucesso: ${modelFile.name}")
                        } else {
                            lastLoadedPath = null
                            lastLoadedCtx = 0
                            if (_status.value == EngineState.INITIALIZING) {
                                _status.value = EngineState.ERROR
                            }
                            NucleoLogger.e("Inference", "Pré-carregamento falhou para ${modelFile.name}")
                        }
                        loaded
                    } finally {
                        poller.cancel()
                        LlamaCppNativeBridge.drainLogsToLogger()
                    }
                }
            }
        } catch (ce: CancellationException) {
            NucleoLogger.i("Inference", "Pré-carregamento cancelado (${modelFile.name})")
            if (_status.value == EngineState.INITIALIZING) {
                _status.value = EngineState.READY
            }
            throw ce
        } catch (t: Throwable) {
            NucleoLogger.e("Inference", "Erro inesperado no pré-carregamento", t)
            if (_status.value == EngineState.INITIALIZING) {
                _status.value = EngineState.ERROR
            }
            false
        }
    }

    override fun cancelPreload() {
        if (_status.value == EngineState.INITIALIZING) {
            LlamaCppNativeBridge.nativeCancel()
        }
    }

    private fun computeSafeContextSize(modelSlot: ModelSlotEntity, modelFile: File): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        var availMb = -1L
        var totalMb = -1L
        if (am != null) {
            am.getMemoryInfo(memInfo)
            availMb = memInfo.availMem / (1024L * 1024L)
            totalMb = memInfo.totalMem / (1024L * 1024L)
        }
        val modelMb = modelFile.length() / (1024L * 1024L)
        val capCtx = if ((availMb in 1L..2600L) || (modelMb > 1200L && totalMb in 1L..6500L)) {
            2048
        } else {
            4096
        }
        val safeCtx = modelSlot.contextLength.coerceIn(1024, capCtx)
        NucleoLogger.i(
            "Inference",
            "Memória p/ inferência: livre=${availMb}MB/${totalMb}MB, modelo=${modelMb}MB, ctx=$safeCtx (solicitado=${modelSlot.contextLength})"
        )
        return safeCtx
    }

    override fun generateStream(request: InferenceRequest): Flow<GenerationChunk> = callbackFlow {
        val modelPath = request.modelSlot.localFilePath
        val modelFile = if (!modelPath.isNullOrBlank()) File(modelPath) else null

        NucleoLogger.i(
            "Inference",
            "Iniciando inferência: slot=${request.modelSlot.id} (${request.modelSlot.displayName}), path=${modelPath ?: "null"}, exists=${modelFile?.exists() == true}, size=${modelFile?.length() ?: 0L}"
        )

        if (modelFile == null || !modelFile.exists() || modelFile.length() <= 0L) {
            NucleoLogger.w("Inference", "Arquivo GGUF ausente ou vazio para slot=${request.modelSlot.id}")
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

        var finishedNormally = false
        val logPoller = launch(Dispatchers.IO) {
            while (isActive) {
                LlamaCppNativeBridge.drainLogsToLogger()
                delay(350L)
            }
        }

        val job = launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            var tokenCount = 0
            var emittedChars = 0

            try {
                val ctxSize = computeSafeContextSize(request.modelSlot, modelFile)
                val alreadyLoaded = (lastLoadedPath == modelFile.absolutePath && lastLoadedCtx == ctxSize)
                if (!alreadyLoaded) {
                    _status.value = EngineState.INITIALIZING
                    NucleoLogger.i("Inference", "Carregando modelo nativo (ctx=$ctxSize)...")
                }
                val loaded = LlamaCppNativeBridge.nativeLoadModel(modelFile.absolutePath, ctxSize)
                LlamaCppNativeBridge.drainLogsToLogger()

                if (!loaded) {
                    lastLoadedPath = null
                    lastLoadedCtx = 0
                    NucleoLogger.e("Inference", "nativeLoadModel retornou false para ${modelFile.absolutePath}")
                    _status.value = EngineState.ERROR
                    trySend(
                        GenerationChunk(
                            deltaText = "Não foi possível carregar o arquivo GGUF em `${modelFile.name}`. Verifique o log em `Downloads/NucleoChat` ou se há RAM disponível.",
                            isFinished = true,
                            tokensGenerated = 0,
                            elapsedMs = System.currentTimeMillis() - startedAt
                        )
                    )
                    finishedNormally = true
                    close()
                    return@launch
                }

                lastLoadedPath = modelFile.absolutePath
                lastLoadedCtx = ctxSize

                _status.value = EngineState.GENERATING
                val (roles, contents) = buildChatArrays(request)
                NucleoLogger.i(
                    "Inference",
                    "Disparando nativeGenerateStream: msgs=${roles.size}, temp=${request.parameters.temperature}, topP=${request.parameters.topP}, topK=${request.parameters.topK}, maxTokens=${request.parameters.maxTokens}"
                )

                val firstTokenReceived = AtomicBoolean(false)
                val watchdogTriggered = AtomicBoolean(false)
                val watchdogJob = launch(Dispatchers.IO) {
                    delay(60_000L)
                    if (!firstTokenReceived.get() && isActive) {
                        watchdogTriggered.set(true)
                        NucleoLogger.e(
                            "Inference",
                            "Watchdog: 60 s sem receber primeiro token; cancelando llama_decode."
                        )
                        LlamaCppNativeBridge.nativeCancel()
                        LlamaCppNativeBridge.drainLogsToLogger()
                    }
                }

                val callback = object : LlamaCppNativeBridge.TokenCallback {
                    override fun onTokenBytes(bytes: ByteArray) {
                        if (firstTokenReceived.compareAndSet(false, true)) {
                            watchdogJob.cancel()
                        }
                        val piece = String(bytes, StandardCharsets.UTF_8)
                        tokenCount += 1
                        emittedChars += piece.length
                        if (tokenCount == 1) {
                            NucleoLogger.i(
                                "Inference",
                                "Primeiro chunk recebido em ${System.currentTimeMillis() - startedAt} ms (${piece.length} chars)"
                            )
                        }
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

                val result = try {
                    LlamaCppNativeBridge.nativeGenerateStream(
                        roles = roles,
                        contents = contents,
                        temperature = request.parameters.temperature,
                        topP = request.parameters.topP,
                        topK = request.parameters.topK,
                        repeatPenalty = request.parameters.repeatPenalty,
                        maxTokens = request.parameters.maxTokens,
                        callback = callback
                    )
                } finally {
                    watchdogJob.cancel()
                }
                LlamaCppNativeBridge.drainLogsToLogger()

                val elapsed = System.currentTimeMillis() - startedAt
                NucleoLogger.i(
                    "Inference",
                    "nativeGenerateStream retornou result=$result, callbacks=$tokenCount, chars=$emittedChars, elapsed=${elapsed}ms, watchdog=${watchdogTriggered.get()}"
                )

                if (watchdogTriggered.get()) {
                    _status.value = EngineState.ERROR
                    trySend(
                        GenerationChunk(
                            deltaText = "[Tempo limite excedido: 60 s sem resposta do motor durante o prefill/decode. Consulte os logs em Downloads/NucleoChat.]",
                            isFinished = true,
                            tokensGenerated = tokenCount,
                            elapsedMs = elapsed
                        )
                    )
                } else if (result < 0) {
                    _status.value = EngineState.ERROR
                    trySend(
                        GenerationChunk(
                            deltaText = "\n\n[Erro nativo llama.cpp: código $result. Consulte Downloads/NucleoChat]",
                            isFinished = true,
                            tokensGenerated = tokenCount,
                            elapsedMs = elapsed
                        )
                    )
                } else if (emittedChars == 0 && isActive) {
                    _status.value = EngineState.READY
                    trySend(
                        GenerationChunk(
                            deltaText = "[O modelo não retornou texto visível (tokens=$result). Consulte os detalhes em Downloads/NucleoChat.]",
                            isFinished = true,
                            tokensGenerated = result.coerceAtLeast(tokenCount),
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
                finishedNormally = true
            } catch (ce: CancellationException) {
                NucleoLogger.i("Inference", "Inferência cancelada após ${System.currentTimeMillis() - startedAt} ms")
                throw ce
            } catch (t: Throwable) {
                NucleoLogger.e("Inference", "Exceção durante inferência local", t)
                _status.value = EngineState.ERROR
                trySend(
                    GenerationChunk(
                        deltaText = "\n\n[Falha na inferência local: ${t.message ?: "erro desconhecido"}]",
                        isFinished = true,
                        tokensGenerated = tokenCount,
                        elapsedMs = System.currentTimeMillis() - startedAt
                    )
                )
                finishedNormally = true
            } finally {
                logPoller.cancel()
                LlamaCppNativeBridge.drainLogsToLogger()
                if (_status.value == EngineState.GENERATING || _status.value == EngineState.INITIALIZING) {
                    _status.value = EngineState.READY
                }
                close()
            }
        }

        awaitClose {
            if (!finishedNormally) {
                LlamaCppNativeBridge.nativeCancel()
            }
            logPoller.cancel()
            job.cancel()
            LlamaCppNativeBridge.drainLogsToLogger()
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
            val cleanedContent = if (msg.role == MessageRole.ASSISTANT) {
                stripCompletedThinking(msg.content)
            } else {
                msg.content
            }
            roles += mappedRole
            contents += cleanedContent
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

    private fun stripCompletedThinking(text: String): String {
        val closeTag = "</think>"
        val closeIdx = text.indexOf(closeTag)
        if (closeIdx < 0) return text
        val after = text.substring(closeIdx + closeTag.length).trimStart()
        return after.ifEmpty { text }
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
