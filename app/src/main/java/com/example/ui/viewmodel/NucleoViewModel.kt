package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.download.ModelDownloadState
import com.example.data.local.AssistantPresetEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.ModelSlotEntity
import com.example.data.local.UserPreferences
import com.example.data.repository.NucleoRepository
import com.example.engine.ChatAttachment
import com.example.engine.EngineCapabilities
import com.example.engine.EngineState
import com.example.engine.GenerationParameters
import com.example.ui.theme.AppThemeMode
import com.example.util.NucleoLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class AssistantFilter(val label: String) {
    ALL("Todos"),
    FAVORITES("Favoritos"),
    CUSTOM("Personalizados")
}

data class DraftNewChatConfig(
    val assistantId: String? = null,
    val assistantName: String = "Assistente Geral",
    val systemPrompt: String = "",
    val parameters: GenerationParameters = GenerationParameters()
)

class NucleoViewModel(
    private val repository: NucleoRepository
) : ViewModel() {

    private var activeGenerationJob: Job? = null

    private val _selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversationId: StateFlow<String?> = _selectedConversationId.asStateFlow()

    private val _draftInput = MutableStateFlow("")
    val draftInput: StateFlow<String> = _draftInput.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<ChatAttachment>>(emptyList())
    val pendingAttachments: StateFlow<List<ChatAttachment>> = _pendingAttachments.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _assistantSearchQuery = MutableStateFlow("")
    val assistantSearchQuery: StateFlow<String> = _assistantSearchQuery.asStateFlow()

    private val _assistantFilter = MutableStateFlow(AssistantFilter.ALL)
    val assistantFilter: StateFlow<AssistantFilter> = _assistantFilter.asStateFlow()

    private val _modelSearchQuery = MutableStateFlow("")
    val modelSearchQuery: StateFlow<String> = _modelSearchQuery.asStateFlow()

    private val _draftNewChatConfig = MutableStateFlow(DraftNewChatConfig())
    val draftNewChatConfig: StateFlow<DraftNewChatConfig> = _draftNewChatConfig.asStateFlow()

    val engineStatus: StateFlow<EngineState> = repository.engineStatus
    val engineCapabilities: EngineCapabilities = repository.engineCapabilities
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = repository.downloadStates

    val preferences: StateFlow<UserPreferences> = repository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val allAssistants: StateFlow<List<AssistantPresetEntity>> = repository.assistantsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAssistants: StateFlow<List<AssistantPresetEntity>> = combine(
        allAssistants,
        _assistantSearchQuery,
        _assistantFilter
    ) { list, query, filter ->
        list.filter { item ->
            val matchesFilter = when (filter) {
                AssistantFilter.ALL -> true
                AssistantFilter.FAVORITES -> item.isFavorite
                AssistantFilter.CUSTOM -> !item.isBuiltIn
            }
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                item.name.contains(q, ignoreCase = true) ||
                item.description.contains(q, ignoreCase = true) ||
                item.systemPrompt.contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allModelSlots: StateFlow<List<ModelSlotEntity>> = repository.modelSlotsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredModelSlots: StateFlow<List<ModelSlotEntity>> = combine(
        allModelSlots,
        _modelSearchQuery
    ) { list, query ->
        val q = query.trim()
        if (q.isEmpty()) list
        else list.filter {
            it.displayName.contains(q, ignoreCase = true) ||
                it.family.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeModelSlot: StateFlow<ModelSlotEntity?> = repository.activeModelSlotFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allConversations: StateFlow<List<ConversationEntity>> = repository.conversationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredConversations: StateFlow<List<ConversationEntity>> = combine(
        allConversations,
        _historySearchQuery
    ) { list, query ->
        val q = query.trim()
        if (q.isEmpty()) list
        else list.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.assistantNameSnapshot.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeConversation: StateFlow<ConversationEntity?> = _selectedConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.observeConversation(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<MessageEntity>> = _selectedConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalConversationsCount: StateFlow<Int> = repository.totalConversationsCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalMessagesCount: StateFlow<Int> = repository.totalMessagesCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            val defaultAssistant = repository.getDefaultAssistant()
            val prefsMax = preferences.value.defaultMaxTokens
            _draftNewChatConfig.value = DraftNewChatConfig(
                assistantId = defaultAssistant.id,
                assistantName = defaultAssistant.name,
                systemPrompt = defaultAssistant.systemPrompt,
                parameters = GenerationParameters(
                    temperature = defaultAssistant.defaultTemperature,
                    topP = defaultAssistant.defaultTopP,
                    topK = defaultAssistant.defaultTopK,
                    repeatPenalty = defaultAssistant.defaultRepeatPenalty,
                    maxTokens = prefsMax
                )
            )
        }
    }

    fun updateDraftInput(text: String) {
        _draftInput.value = text
    }

    fun addAttachment(attachment: ChatAttachment) {
        _pendingAttachments.update { current -> current + attachment }
    }

    fun removeAttachment(id: String) {
        _pendingAttachments.update { current -> current.filterNot { it.id == id } }
    }

    fun updateHistorySearch(query: String) {
        _historySearchQuery.value = query
    }

    fun updateAssistantSearch(query: String) {
        _assistantSearchQuery.value = query
    }

    fun setAssistantFilter(filter: AssistantFilter) {
        _assistantFilter.value = filter
    }

    fun updateModelSearch(query: String) {
        _modelSearchQuery.value = query
    }

    fun selectConversation(conversationId: String) {
        stopGeneration()
        _selectedConversationId.value = conversationId
    }

    fun startNewChat(withAssistant: AssistantPresetEntity? = null) {
        stopGeneration()
        _selectedConversationId.value = null
        viewModelScope.launch {
            val assistant = withAssistant ?: repository.getDefaultAssistant()
            _draftNewChatConfig.value = DraftNewChatConfig(
                assistantId = assistant.id,
                assistantName = assistant.name,
                systemPrompt = assistant.systemPrompt,
                parameters = GenerationParameters(
                    temperature = assistant.defaultTemperature,
                    topP = assistant.defaultTopP,
                    topK = assistant.defaultTopK,
                    repeatPenalty = assistant.defaultRepeatPenalty,
                    maxTokens = preferences.value.defaultMaxTokens
                )
            )
        }
    }

    fun applyAssistantToCurrentOrDraft(assistant: AssistantPresetEntity) {
        val currentConv = activeConversation.value
        if (currentConv == null) {
            _draftNewChatConfig.update {
                it.copy(
                    assistantId = assistant.id,
                    assistantName = assistant.name,
                    systemPrompt = assistant.systemPrompt,
                    parameters = GenerationParameters(
                        temperature = assistant.defaultTemperature,
                        topP = assistant.defaultTopP,
                        topK = assistant.defaultTopK,
                        repeatPenalty = assistant.defaultRepeatPenalty,
                        maxTokens = it.parameters.maxTokens
                    )
                )
            }
        } else {
            viewModelScope.launch {
                repository.updateConversationConfig(
                    conversationId = currentConv.id,
                    assistant = assistant,
                    systemPrompt = assistant.systemPrompt,
                    modelSlotId = currentConv.modelSlotId,
                    params = GenerationParameters(
                        temperature = assistant.defaultTemperature,
                        topP = assistant.defaultTopP,
                        topK = assistant.defaultTopK,
                        repeatPenalty = assistant.defaultRepeatPenalty,
                        maxTokens = currentConv.maxTokens
                    )
                )
            }
        }
    }

    fun applyPromptAndParameters(
        systemPrompt: String,
        params: GenerationParameters
    ) {
        val currentConv = activeConversation.value
        if (currentConv == null) {
            _draftNewChatConfig.update {
                it.copy(
                    systemPrompt = systemPrompt.trim(),
                    parameters = params
                )
            }
        } else {
            viewModelScope.launch {
                repository.updateConversationConfig(
                    conversationId = currentConv.id,
                    assistant = null,
                    systemPrompt = systemPrompt.trim(),
                    modelSlotId = currentConv.modelSlotId,
                    params = params
                )
            }
        }
    }

    fun restoreAssistantDefaults(onRestored: (String, GenerationParameters) -> Unit) {
        viewModelScope.launch {
            val targetId = activeConversation.value?.assistantId ?: _draftNewChatConfig.value.assistantId
            val assistant = targetId?.let { repository.getAssistantById(it) } ?: repository.getDefaultAssistant()
            val params = GenerationParameters(
                temperature = assistant.defaultTemperature,
                topP = assistant.defaultTopP,
                topK = assistant.defaultTopK,
                repeatPenalty = assistant.defaultRepeatPenalty,
                maxTokens = preferences.value.defaultMaxTokens
            )
            onRestored(assistant.systemPrompt, params)
        }
    }

    fun sendMessage(overrideText: String? = null) {
        val text = (overrideText ?: _draftInput.value).trim()
        val attachmentsToSend = if (overrideText == null) _pendingAttachments.value else emptyList()
        if ((text.isEmpty() && attachmentsToSend.isEmpty()) || _isGenerating.value) return
        if (overrideText == null) {
            _draftInput.value = ""
            _pendingAttachments.value = emptyList()
        }

        val effectivePrompt = text.ifEmpty {
            attachmentsToSend.joinToString(", ") { it.displayName }
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _isGenerating.value = true
            var assistantMessageId: String? = null
            val accumulated = StringBuilder()
            var lastTokens = 0
            var lastElapsed = 0L

            try {
                val modelSlot = activeModelSlot.value ?: repository.getActiveModelSlot()
                NucleoLogger.i(
                    "Chat",
                    "Enviando mensagem (${effectivePrompt.length} chars) usando modelo '${modelSlot.displayName}' (installed=${modelSlot.isInstalled}, path=${modelSlot.localFilePath})"
                )
                val existingConv = activeConversation.value
                val conversation = if (existingConv != null) {
                    existingConv
                } else {
                    val draft = _draftNewChatConfig.value
                    val baseAssistant = draft.assistantId?.let { repository.getAssistantById(it) }
                        ?: repository.getDefaultAssistant()
                    val created = repository.createConversation(
                        assistant = baseAssistant.copy(
                            systemPrompt = draft.systemPrompt.ifBlank { baseAssistant.systemPrompt },
                            defaultTemperature = draft.parameters.temperature,
                            defaultTopP = draft.parameters.topP,
                            defaultTopK = draft.parameters.topK,
                            defaultRepeatPenalty = draft.parameters.repeatPenalty
                        ),
                        modelSlot = modelSlot,
                        firstUserPrompt = effectivePrompt,
                        maxTokens = draft.parameters.maxTokens
                    )
                    _selectedConversationId.value = created.id
                    created
                }

                repository.insertUserMessage(conversation.id, text, attachmentsToSend)
                val pending = repository.createPendingAssistantMessage(
                    conversationId = conversation.id,
                    modelLabel = modelSlot.displayName
                )
                assistantMessageId = pending.id

                var lastDbFlushMs = 0L
                var lastFlushedLen = 0
                repository.streamInference(conversation, effectivePrompt, attachmentsToSend, modelSlot).collect { chunk ->
                    if (chunk.deltaText.isNotEmpty()) {
                        accumulated.append(chunk.deltaText)
                    }
                    lastTokens = chunk.tokensGenerated
                    lastElapsed = chunk.elapsedMs

                    val now = System.currentTimeMillis()
                    val shouldFlush = chunk.isFinished ||
                        (lastFlushedLen == 0 && accumulated.isNotEmpty()) ||
                        (now - lastDbFlushMs >= 90L && accumulated.length != lastFlushedLen)

                    if (shouldFlush) {
                        lastDbFlushMs = now
                        lastFlushedLen = accumulated.length
                        repository.updateStreamingAssistantMessage(
                            messageId = pending.id,
                            content = accumulated.toString(),
                            tokenCount = lastTokens,
                            durationMs = lastElapsed
                        )
                    }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                NucleoLogger.e("Chat", "Erro no fluxo de geração", t)
                if (accumulated.isEmpty()) {
                    accumulated.append("[Erro ao gerar resposta: ${t.message ?: "falha inesperada"}]")
                }
            } finally {
                val id = assistantMessageId
                if (id != null && accumulated.isNotEmpty()) {
                    repository.updateStreamingAssistantMessage(
                        messageId = id,
                        content = accumulated.toString(),
                        tokenCount = lastTokens,
                        durationMs = lastElapsed
                    )
                }
                _isGenerating.value = false
            }
        }
    }

    fun stopGeneration() {
        if (_isGenerating.value) {
            NucleoLogger.i("Chat", "Geração interrompida pelo usuário")
        }
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _isGenerating.value = false
    }

    fun togglePinConversation(conversation: ConversationEntity) {
        viewModelScope.launch {
            repository.togglePinConversation(conversation.id, conversation.isPinned)
        }
    }

    fun renameConversation(conversationId: String, title: String) {
        viewModelScope.launch {
            repository.renameConversation(conversationId, title)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_selectedConversationId.value == conversationId) {
                stopGeneration()
                _selectedConversationId.value = null
            }
            repository.deleteConversation(conversationId)
        }
    }

    fun saveAssistant(
        existingId: String?,
        name: String,
        description: String,
        systemPrompt: String,
        iconKey: String,
        accentTag: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        isBuiltIn: Boolean,
        isFavorite: Boolean
    ) {
        viewModelScope.launch {
            val entity = AssistantPresetEntity(
                id = existingId ?: UUID.randomUUID().toString(),
                name = name.trim(),
                description = description.trim(),
                systemPrompt = systemPrompt.trim(),
                iconKey = iconKey,
                accentTag = accentTag,
                defaultTemperature = temperature,
                defaultTopP = topP,
                defaultTopK = topK,
                defaultRepeatPenalty = repeatPenalty,
                isBuiltIn = isBuiltIn,
                isFavorite = isFavorite
            )
            repository.saveAssistant(entity)
        }
    }

    fun duplicateAssistant(source: AssistantPresetEntity) {
        viewModelScope.launch {
            repository.saveAssistant(
                source.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${source.name} (Cópia)",
                    isBuiltIn = false,
                    isFavorite = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun toggleAssistantFavorite(assistant: AssistantPresetEntity) {
        viewModelScope.launch {
            repository.toggleAssistantFavorite(assistant.id, assistant.isFavorite)
        }
    }

    fun deleteAssistant(id: String) {
        viewModelScope.launch {
            repository.deleteAssistant(id)
        }
    }

    fun selectModelSlot(slotId: String) {
        viewModelScope.launch {
            repository.selectModelSlot(slotId)
        }
    }

    fun startModelDownload(slotId: String) {
        viewModelScope.launch {
            repository.startModelDownload(slotId)
        }
    }

    fun cancelModelDownload(slotId: String) {
        viewModelScope.launch {
            repository.cancelModelDownload(slotId)
        }
    }

    fun clearDownloadError(slotId: String) {
        repository.clearDownloadError(slotId)
    }

    fun importLocalGguf(
        sourceUri: Uri,
        fileName: String,
        sizeBytes: Long,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            repository.importLocalGgufModel(
                sourceUri = sourceUri,
                fileName = fileName,
                sizeBytes = sizeBytes,
                onError = onError
            )
        }
    }

    fun addAndDownloadCustomGguf(
        displayName: String,
        downloadUrl: String,
        quantizationLabel: String
    ) {
        viewModelScope.launch {
            val created = repository.addCustomGgufModel(displayName, downloadUrl, quantizationLabel)
            repository.startModelDownload(created.id)
        }
    }

    fun uninstallModelSlot(slotId: String) {
        viewModelScope.launch {
            repository.uninstallModelSlot(slotId)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setShowTokenTelemetry(enabled: Boolean) {
        viewModelScope.launch { repository.setShowTokenTelemetry(enabled) }
    }

    fun setDefaultMaxTokens(maxTokens: Int) {
        viewModelScope.launch { repository.setDefaultMaxTokens(maxTokens) }
    }

    fun clearAllConversations() {
        stopGeneration()
        _selectedConversationId.value = null
        viewModelScope.launch {
            repository.clearAllConversations()
        }
    }

    class Factory(private val repository: NucleoRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NucleoViewModel::class.java)) {
                return NucleoViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
