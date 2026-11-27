package com.example.data.repository

import android.net.Uri
import com.example.data.download.ModelDownloadManager
import com.example.data.download.ModelDownloadState
import com.example.data.local.AssistantPresetEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MessageRole
import com.example.data.local.ModelEngineSlotState
import com.example.data.local.ModelSlotEntity
import com.example.data.local.NucleoDatabase
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesDataStore
import com.example.engine.ChatAttachment
import com.example.engine.EngineCapabilities
import com.example.engine.EngineState
import com.example.engine.GenerationChunk
import com.example.engine.GenerationParameters
import com.example.engine.InferenceEngine
import com.example.engine.InferenceRequest
import com.example.ui.theme.AppThemeMode
import com.example.util.NucleoLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID

class NucleoRepository(
    private val database: NucleoDatabase,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val inferenceEngine: InferenceEngine,
    private val modelDownloadManager: ModelDownloadManager
) {
    private val assistantDao = database.assistantPresetDao()
    private val modelSlotDao = database.modelSlotDao()
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val seedMutex = Mutex()

    val assistantsFlow: Flow<List<AssistantPresetEntity>> = assistantDao.observeAllAssistants()
    val modelSlotsFlow: Flow<List<ModelSlotEntity>> = modelSlotDao.observeAllModelSlots()
    val activeModelSlotFlow: Flow<ModelSlotEntity?> = modelSlotDao.observeActiveModelSlot()
    val conversationsFlow: Flow<List<ConversationEntity>> = conversationDao.observeAllConversations()
    val preferencesFlow: Flow<UserPreferences> = preferencesDataStore.preferencesFlow
    val totalConversationsCountFlow: Flow<Int> = conversationDao.observeCount()
    val totalMessagesCountFlow: Flow<Int> = messageDao.observeTotalMessageCount()

    val engineStatus: StateFlow<EngineState> = inferenceEngine.status
    val engineCapabilities: EngineCapabilities = inferenceEngine.capabilities
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = modelDownloadManager.downloadStates

    suspend fun ensureSeeded() = seedMutex.withLock {
        if (assistantDao.count() == 0) {
            assistantDao.insertAll(defaultAssistants())
        }
        if (modelSlotDao.count() == 0) {
            modelSlotDao.insertAll(defaultModelSlots())
        } else {
            modelSlotDao.clearPhantomInstalls()
            val legacyLfm = modelSlotDao.getById("slot-lfm-2-700m")
            if (legacyLfm != null && legacyLfm.localFilePath.isNullOrBlank()) {
                modelSlotDao.deleteById("slot-lfm-2-700m")
            }
        }
    }

    fun observeConversation(conversationId: String): Flow<ConversationEntity?> =
        conversationDao.observeConversationById(conversationId)

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.observeMessagesForConversation(conversationId)

    suspend fun getDefaultAssistant(): AssistantPresetEntity {
        ensureSeeded()
        return assistantDao.getDefaultAssistant() ?: defaultAssistants().first()
    }

    suspend fun getActiveModelSlot(): ModelSlotEntity? {
        ensureSeeded()
        return modelSlotDao.getActiveModelSlot()?.takeIf {
            it.isInstalled && !it.localFilePath.isNullOrBlank()
        }
    }

    suspend fun getAssistantById(id: String): AssistantPresetEntity? =
        assistantDao.getAssistantById(id)

    suspend fun saveAssistant(assistant: AssistantPresetEntity) {
        assistantDao.insert(assistant.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleAssistantFavorite(id: String, currentFavorite: Boolean) {
        assistantDao.setFavorite(id, !currentFavorite)
    }

    suspend fun deleteAssistant(id: String) {
        assistantDao.deleteById(id)
    }

    suspend fun selectModelSlot(slotId: String) {
        NucleoLogger.i("Models", "Selecionando modelo ativo: $slotId")
        modelSlotDao.clearActive()
        modelSlotDao.markActive(slotId)
    }

    suspend fun preloadModelSlot(slotId: String): Boolean {
        val slot = modelSlotDao.getById(slotId)
            ?.takeIf { it.isInstalled && !it.localFilePath.isNullOrBlank() }
            ?: return false
        return inferenceEngine.preloadModel(slot)
    }

    fun cancelModelPreload() {
        inferenceEngine.cancelPreload()
    }

    suspend fun startModelDownload(slotId: String) {
        val slot = modelSlotDao.getById(slotId) ?: return
        modelDownloadManager.startDownload(
            modelId = slot.id,
            url = slot.downloadUrl
        ) { localPath, totalBytes ->
            modelSlotDao.updateInstallState(
                id = slot.id,
                installed = true,
                localFilePath = localPath,
                downloadedBytes = totalBytes
            )
            modelSlotDao.clearActive()
            modelSlotDao.markActive(slot.id)
        }
    }

    suspend fun importLocalGgufModel(
        sourceUri: Uri,
        fileName: String,
        sizeBytes: Long,
        onError: (String) -> Unit
    ): ModelSlotEntity {
        val id = "imported-${UUID.randomUUID().toString().take(8)}"
        val cleanFile = fileName.trim().ifBlank { "modelo_local.gguf" }
        val baseName = cleanFile.removeSuffix(".gguf").removeSuffix(".GGUF")
        val family = detectModelFamily(baseName)
        val quant = detectQuantization(baseName)
        val displayName = baseName
            .replace(Regex("[-_.]?(Q\\d+_[A-Z0-9_]+|IQ\\d+_[A-Z0-9_]+|F16|BF16)$", RegexOption.IGNORE_CASE), "")
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
            .ifBlank { baseName }

        val slot = ModelSlotEntity(
            id = id,
            displayName = displayName,
            family = family,
            quantizationLabel = quant,
            parameterSizeLabel = "Local",
            sizeLabel = if (sizeBytes > 0L) formatSizeLabel(sizeBytes) else "GGUF",
            tagsCsv = "local,importado",
            downloadUrl = "",
            repoSlug = "Importado do aparelho • $cleanFile",
            isInstalled = false,
            supportsVision = baseName.contains("vision", true) ||
                baseName.contains("vl", true) ||
                baseName.contains("llava", true) ||
                baseName.contains("qwen3.5", true),
            contextLength = 4096,
            estimatedRamGb = if (sizeBytes > 0L) ((sizeBytes / (1024f * 1024f * 1024f)) * 1.25f).coerceAtLeast(0.5f) else 2.0f,
            description = "Modelo GGUF importado diretamente do armazenamento do dispositivo.",
            isActive = false,
            engineState = ModelEngineSlotState.STRUCTURAL_READY
        )
        modelSlotDao.insert(slot)
        NucleoLogger.i("ModelImport", "Slot criado para importação: id=$id, nome=$displayName, quant=$quant, file=$cleanFile")

        modelDownloadManager.importLocalGguf(
            modelId = id,
            sourceUri = sourceUri,
            onCompleted = { localPath, totalBytes ->
                modelSlotDao.updateInstallState(
                    id = id,
                    installed = true,
                    localFilePath = localPath,
                    downloadedBytes = totalBytes
                )
                modelSlotDao.clearActive()
                modelSlotDao.markActive(id)
            },
            onError = { errorMsg ->
                modelSlotDao.deleteById(id)
                onError(errorMsg)
            }
        )
        return slot
    }

    suspend fun cancelModelDownload(slotId: String) {
        modelDownloadManager.cancelDownload(slotId)
        if (slotId.startsWith("imported-")) {
            val slot = modelSlotDao.getById(slotId)
            if (slot != null && !slot.isInstalled) {
                modelSlotDao.deleteById(slotId)
            }
        }
    }

    fun clearDownloadError(slotId: String) {
        modelDownloadManager.clearDownloadState(slotId)
    }

    suspend fun addCustomGgufModel(
        displayName: String,
        downloadUrl: String,
        quantizationLabel: String
    ): ModelSlotEntity {
        val cleanUrl = downloadUrl.trim()
        val id = "custom-${UUID.randomUUID().toString().take(8)}"
        val family = detectModelFamily("$displayName $cleanUrl")
        val slug = cleanUrl
            .removePrefix("https://huggingface.co/")
            .substringBefore("/resolve/")
            .ifBlank { "URL personalizada" }

        val slot = ModelSlotEntity(
            id = id,
            displayName = displayName.trim(),
            family = family,
            quantizationLabel = quantizationLabel.trim().ifBlank { "Q4_K_M" },
            parameterSizeLabel = "Custom",
            sizeLabel = "GGUF",
            tagsCsv = "personalizado",
            downloadUrl = cleanUrl,
            repoSlug = slug,
            isInstalled = false,
            supportsVision = displayName.contains("vision", true) ||
                cleanUrl.contains("vl", true) ||
                cleanUrl.contains("llava", true) ||
                cleanUrl.contains("qwen3.5", true),
            contextLength = 8192,
            estimatedRamGb = 2.0f,
            description = "Modelo GGUF adicionado manualmente para download direto no aparelho.",
            isActive = false,
            engineState = ModelEngineSlotState.STRUCTURAL_READY
        )
        modelSlotDao.insert(slot)
        return slot
    }

    suspend fun uninstallModelSlot(slotId: String) {
        val current = modelSlotDao.getById(slotId) ?: return
        NucleoLogger.i("Models", "Removendo modelo: $slotId (${current.localFilePath})")
        modelDownloadManager.deleteLocalFile(current.localFilePath)
        modelDownloadManager.clearDownloadState(slotId)
        if (slotId.startsWith("imported-") || slotId.startsWith("custom-")) {
            modelSlotDao.deleteById(slotId)
        } else {
            modelSlotDao.updateInstallState(
                id = slotId,
                installed = false,
                localFilePath = null,
                downloadedBytes = 0L
            )
        }
        if (current.isActive) {
            inferenceEngine.cancelPreload()
            modelSlotDao.clearActive()
            val nextInstalled = modelSlotDao.getFirstRealInstalledModel(slotId)
            if (nextInstalled != null) {
                modelSlotDao.markActive(nextInstalled.id)
            }
        }
    }

    private fun detectModelFamily(hint: String): String = when {
        hint.contains("qwen", true) -> "Qwen"
        hint.contains("gemma", true) -> "Gemma"
        hint.contains("llama", true) -> "Llama"
        hint.contains("phi", true) -> "Phi"
        hint.contains("lfm", true) -> "LFM"
        else -> "GGUF"
    }

    private fun detectQuantization(fileName: String): String {
        val match = Regex("(I?Q\\d+_[A-Z0-9_]+|B?F16)", RegexOption.IGNORE_CASE).find(fileName)
        return match?.value?.uppercase(Locale.US) ?: "GGUF"
    }

    private fun formatSizeLabel(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        val gb = mb / 1024.0
        return if (gb >= 1.0) String.format(Locale.US, "%.2f GB", gb)
        else String.format(Locale.US, "%.0f MB", mb)
    }

    suspend fun createConversation(
        assistant: AssistantPresetEntity,
        modelSlot: ModelSlotEntity,
        firstUserPrompt: String,
        maxTokens: Int
    ): ConversationEntity {
        val cleanTitle = firstUserPrompt
            .trim()
            .replace("\n", " ")
            .let { if (it.length > 44) it.take(44) + "..." else it }
            .ifBlank { "Nova conversa" }

        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = cleanTitle,
            assistantId = assistant.id,
            assistantNameSnapshot = assistant.name,
            activeSystemPrompt = assistant.systemPrompt,
            modelSlotId = modelSlot.id,
            temperature = assistant.defaultTemperature,
            topP = assistant.defaultTopP,
            topK = assistant.defaultTopK,
            repeatPenalty = assistant.defaultRepeatPenalty,
            maxTokens = maxTokens,
            isPinned = false,
            createdAt = now,
            updatedAt = now
        )
        conversationDao.insert(conversation)
        return conversation
    }

    suspend fun updateConversationConfig(
        conversationId: String,
        assistant: AssistantPresetEntity?,
        systemPrompt: String,
        modelSlotId: String,
        params: GenerationParameters
    ) {
        val existing = conversationDao.getConversationById(conversationId) ?: return
        conversationDao.update(
            existing.copy(
                assistantId = assistant?.id ?: existing.assistantId,
                assistantNameSnapshot = assistant?.name ?: existing.assistantNameSnapshot,
                activeSystemPrompt = systemPrompt,
                modelSlotId = modelSlotId,
                temperature = params.temperature,
                topP = params.topP,
                topK = params.topK,
                repeatPenalty = params.repeatPenalty,
                maxTokens = params.maxTokens,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun togglePinConversation(conversationId: String, currentPinned: Boolean) {
        conversationDao.setPinned(conversationId, !currentPinned)
    }

    suspend fun renameConversation(conversationId: String, newTitle: String) {
        if (newTitle.isNotBlank()) {
            conversationDao.rename(conversationId, newTitle.trim())
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        conversationDao.deleteById(conversationId)
    }

    suspend fun clearAllConversations() {
        conversationDao.deleteAll()
    }

    suspend fun insertUserMessage(
        conversationId: String,
        content: String,
        attachments: List<ChatAttachment> = emptyList()
    ): MessageEntity {
        val attachmentsSummary = attachments.joinToString(" • ") {
            if (it.isImage) "Imagem: ${it.displayName}" else "Arquivo: ${it.displayName}"
        }
        val message = MessageEntity(
            conversationId = conversationId,
            role = MessageRole.USER,
            content = content.trim(),
            attachmentsSummary = attachmentsSummary,
            tokenCountEstimate = estimateTokens(content),
            createdAt = System.currentTimeMillis()
        )
        messageDao.insert(message)
        conversationDao.getConversationById(conversationId)?.let { conv ->
            conversationDao.update(conv.copy(updatedAt = System.currentTimeMillis()))
        }
        return message
    }

    suspend fun createPendingAssistantMessage(conversationId: String, modelLabel: String): MessageEntity {
        val message = MessageEntity(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            modelLabel = modelLabel,
            createdAt = System.currentTimeMillis()
        )
        messageDao.insert(message)
        return message
    }

    suspend fun updateStreamingAssistantMessage(
        messageId: String,
        content: String,
        tokenCount: Int,
        durationMs: Long
    ) {
        messageDao.updateStreamingMessage(messageId, content, tokenCount, durationMs)
    }

    suspend fun streamInference(
        conversation: ConversationEntity,
        userPrompt: String,
        attachments: List<ChatAttachment>,
        modelSlot: ModelSlotEntity
    ): Flow<GenerationChunk> {
        val history = messageDao.getMessagesForConversationOnce(conversation.id)
        val request = InferenceRequest(
            conversationId = conversation.id,
            assistantName = conversation.assistantNameSnapshot,
            systemPrompt = conversation.activeSystemPrompt,
            history = history,
            userPrompt = userPrompt,
            attachments = attachments,
            modelSlot = modelSlot,
            parameters = GenerationParameters(
                temperature = conversation.temperature,
                topP = conversation.topP,
                topK = conversation.topK,
                repeatPenalty = conversation.repeatPenalty,
                maxTokens = conversation.maxTokens
            )
        )
        return inferenceEngine.generateStream(request)
    }

    suspend fun setThemeMode(mode: AppThemeMode) = preferencesDataStore.setThemeMode(mode)

    suspend fun setShowTokenTelemetry(enabled: Boolean) =
        preferencesDataStore.setShowTokenTelemetry(enabled)

    suspend fun setDefaultMaxTokens(maxTokens: Int) =
        preferencesDataStore.setDefaultMaxTokens(maxTokens)

    private fun estimateTokens(text: String): Int =
        (text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size * 1.3f).toInt().coerceAtLeast(1)

    private fun defaultAssistants(): List<AssistantPresetEntity> = listOf(
        AssistantPresetEntity(
            id = "preset-geral",
            name = "Assistente Geral",
            description = "Equilibrado para perguntas rápidas, organização de ideias e conversas do dia a dia.",
            systemPrompt = "Você é um assistente pessoal privado e direto. Responda em português do Brasil com clareza, objetividade e precisão.",
            iconKey = "ATOM",
            accentTag = "CYAN",
            defaultTemperature = 0.7f,
            defaultTopP = 0.9f,
            defaultTopK = 40,
            defaultRepeatPenalty = 1.1f,
            isBuiltIn = true,
            isFavorite = true
        ),
        AssistantPresetEntity(
            id = "preset-codigo",
            name = "Arquiteto de Código",
            description = "Focado em Kotlin, Python, arquitetura limpa, depuração e exemplos práticos.",
            systemPrompt = "Você é um engenheiro de software sênior. Forneça soluções idiomáticas, seguras e bem estruturadas com blocos de código claros, explicando os trade-offs técnicos em português.",
            iconKey = "CODE",
            accentTag = "EMERALD",
            defaultTemperature = 0.25f,
            defaultTopP = 0.85f,
            defaultTopK = 25,
            defaultRepeatPenalty = 1.15f,
            isBuiltIn = true,
            isFavorite = true
        ),
        AssistantPresetEntity(
            id = "preset-redator",
            name = "Revisor & Redator",
            description = "Aprimora textos, e-mails e documentos mantendo tom natural e elegante.",
            systemPrompt = "Você é um editor especialista em língua portuguesa. Reescreva e melhore textos preservando a intenção do autor, corrigindo gramática, fluidez e concisão.",
            iconKey = "EDIT",
            accentTag = "AMBER",
            defaultTemperature = 0.6f,
            defaultTopP = 0.92f,
            defaultTopK = 40,
            defaultRepeatPenalty = 1.08f,
            isBuiltIn = true,
            isFavorite = false
        ),
        AssistantPresetEntity(
            id = "preset-tutor",
            name = "Tutor Socrático",
            description = "Explica conceitos complexos passo a passo e faz perguntas para fixar o aprendizado.",
            systemPrompt = "Você é um tutor paciente e didático. Explique conceitos difíceis usando analogias simples, divida em passos e proponha uma pergunta rápida ao final para validar o entendimento.",
            iconKey = "SCHOOL",
            accentTag = "INDIGO",
            defaultTemperature = 0.75f,
            defaultTopP = 0.9f,
            defaultTopK = 45,
            defaultRepeatPenalty = 1.1f,
            isBuiltIn = true,
            isFavorite = false
        ),
        AssistantPresetEntity(
            id = "preset-resumo",
            name = "Resumidor Executivo",
            description = "Extrai decisões, pontos-chave e próximos passos de textos longos.",
            systemPrompt = "Você é um analista objetivo. Resuma qualquer texto recebido em tópicos curtos: Visão Geral, Pontos Principais e Próximas Ações.",
            iconKey = "SUMMARIZE",
            accentTag = "ROSE",
            defaultTemperature = 0.35f,
            defaultTopP = 0.85f,
            defaultTopK = 30,
            defaultRepeatPenalty = 1.12f,
            isBuiltIn = true,
            isFavorite = false
        )
    )

    private fun defaultModelSlots(): List<ModelSlotEntity> = listOf(
        ModelSlotEntity(
            id = "slot-qwen-3-5-08b",
            displayName = "Qwen 3.5 (0.8B)",
            family = "Qwen",
            quantizationLabel = "Q4_K_M",
            parameterSizeLabel = "0.8B",
            sizeLabel = "703 MB",
            tagsCsv = "raciocínio,visão,recomendado",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf",
            repoSlug = "unsloth/Qwen3.5-0.8B-GGUF",
            isInstalled = false,
            supportsVision = true,
            contextLength = 8192,
            estimatedRamGb = 1.2f,
            description = "Modelo compacto com bom raciocínio híbrido. Ótimo para diálogos simples e respostas rápidas.",
            isActive = false,
            engineState = ModelEngineSlotState.EXTERNAL_ENGINE_REQUIRED
        ),
        ModelSlotEntity(
            id = "slot-gemma-3-1b",
            displayName = "Gemma 3 (1B)",
            family = "Gemma",
            quantizationLabel = "Q4_K_M",
            parameterSizeLabel = "1B",
            sizeLabel = "769 MB",
            tagsCsv = "rápido,uso diário",
            downloadUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
            repoSlug = "unsloth/gemma-3-1b-it-GGUF",
            isInstalled = false,
            contextLength = 8192,
            estimatedRamGb = 1.4f,
            description = "Modelo veloz com consumo otimizado de memória para conversas diretas no aparelho.",
            isActive = false,
            engineState = ModelEngineSlotState.STRUCTURAL_READY
        ),
        ModelSlotEntity(
            id = "slot-lfm-2-5-thinking",
            displayName = "LFM 2.5 Thinking (1.2B)",
            family = "LFM",
            quantizationLabel = "Q4_K_M",
            parameterSizeLabel = "1.2B",
            sizeLabel = "890 MB",
            tagsCsv = "raciocínio,recomendado",
            downloadUrl = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Thinking-GGUF/resolve/main/LFM2.5-1.2B-Thinking-Q4_K_M.gguf",
            repoSlug = "LiquidAI/LFM2.5-1.2B-Thinking-GGUF",
            isInstalled = false,
            contextLength = 16384,
            estimatedRamGb = 1.7f,
            description = "Focado em cadeia de raciocínio passo a passo antes de formular a resposta final.",
            isActive = false,
            engineState = ModelEngineSlotState.STRUCTURAL_READY
        ),
        ModelSlotEntity(
            id = "slot-llama-3-2-3b",
            displayName = "Llama 3.2 (3B)",
            family = "Llama",
            quantizationLabel = "Q4_K_M",
            parameterSizeLabel = "3B",
            sizeLabel = "2.02 GB",
            tagsCsv = "uso diário,equilibrado",
            downloadUrl = "https://huggingface.co/hugging-quants/Llama-3.2-3B-Instruct-Q4_K_M-GGUF/resolve/main/llama-3.2-3b-instruct-q4_k_m.gguf",
            repoSlug = "hugging-quants/Llama-3.2-3B-Instruct-Q4_K_M-GGUF",
            isInstalled = false,
            contextLength = 8192,
            estimatedRamGb = 2.4f,
            description = "Equilíbrio sólido entre fluidez em português, resumos e instruções detalhadas.",
            isActive = false,
            engineState = ModelEngineSlotState.EXTERNAL_ENGINE_REQUIRED
        ),
        ModelSlotEntity(
            id = "slot-phi-4-mini",
            displayName = "Phi-4 Mini (3.8B)",
            family = "Phi",
            quantizationLabel = "Q4_K_M",
            parameterSizeLabel = "3.8B",
            sizeLabel = "2.49 GB",
            tagsCsv = "raciocínio,código",
            downloadUrl = "https://huggingface.co/bartowski/microsoft_Phi-4-mini-instruct-GGUF/resolve/main/microsoft_Phi-4-mini-instruct-Q4_K_M.gguf",
            repoSlug = "bartowski/microsoft_Phi-4-mini-instruct-GGUF",
            isInstalled = false,
            contextLength = 8192,
            estimatedRamGb = 2.9f,
            description = "Especializado em lógica, programação e explicações estruturadas.",
            isActive = false,
            engineState = ModelEngineSlotState.STRUCTURAL_READY
        )
    )
}
