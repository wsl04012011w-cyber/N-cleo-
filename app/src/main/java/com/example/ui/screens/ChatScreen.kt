package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.ChatAttachment
import com.example.engine.EngineState
import com.example.ui.components.AtomicChatInputBar
import com.example.ui.components.AtomicDrawerContent
import com.example.ui.components.AtomicEmptyState
import com.example.ui.components.AtomicMessageItem
import com.example.ui.components.AtomicTopBar
import com.example.ui.components.ModelSelectorBottomSheet
import com.example.ui.components.QuickPlusActionsSheet
import com.example.ui.components.SilverShimmerLoadingText
import com.example.ui.viewmodel.NucleoViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: NucleoViewModel,
    onNavigateToModels: () -> Unit,
    onNavigateToAssistants: () -> Unit,
    onNavigateToInferenceSettings: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val conversations by viewModel.allConversations.collectAsStateWithLifecycle()
    val assistants by viewModel.allAssistants.collectAsStateWithLifecycle()
    val modelSlots by viewModel.allModelSlots.collectAsStateWithLifecycle()
    val activeModelSlot by viewModel.activeModelSlot.collectAsStateWithLifecycle()
    val draftConfig by viewModel.draftNewChatConfig.collectAsStateWithLifecycle()
    val draftInput by viewModel.draftInput.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val engineStatus by viewModel.engineStatus.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    var showModelSheet by remember { mutableStateOf(false) }
    var showPlusSheet by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addAttachment(resolveAttachmentFromUri(context, uri, isImage = false))
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addAttachment(resolveAttachmentFromUri(context, uri, isImage = true))
        }
    }

    val currentAssistantId = activeConversation?.assistantId ?: draftConfig.assistantId
    val currentAssistantName = activeConversation?.assistantNameSnapshot ?: draftConfig.assistantName
    val isDefaultAssistant = currentAssistantId == "preset-geral" || currentAssistantId == null
    val readyModel = activeModelSlot?.takeIf { it.isInstalled && !it.localFilePath.isNullOrBlank() }

    LaunchedEffect(Unit) {
        viewModel.noModelPromptEvent.collect {
            showModelSheet = true
            snackbarHostState.showSnackbar("Escolha, baixe ou importe um modelo GGUF primeiro")
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        val mainChatContent: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AtomicTopBar(
                        showDrawerButton = !isWideScreen,
                        showNewChatButton = messages.isNotEmpty(),
                        activeModelName = readyModel?.displayName ?: "Escolher modelo",
                        activeModelFamily = readyModel?.family ?: "GGUF",
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenModelSelector = { showModelSheet = true },
                        onOpenInferenceSettings = onNavigateToInferenceSettings,
                        onNewChat = { viewModel.startNewChat() }
                    )

                    if (messages.isEmpty()) {
                        AtomicEmptyState(
                            activeAssistantName = currentAssistantName,
                            isDefaultAssistant = isDefaultAssistant,
                            isModelLoading = engineStatus == EngineState.INITIALIZING,
                            onSelectAssistantChip = { showPlusSheet = true },
                            onSelectStarterPrompt = { starter ->
                                viewModel.sendMessage(overrideText = starter)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(
                                items = messages,
                                key = { _, msg -> msg.id }
                            ) { index, message ->
                                val isStreamingThis = isGenerating && index == messages.lastIndex
                                AtomicMessageItem(
                                    message = message,
                                    isCurrentlyStreaming = isStreamingThis,
                                    showTelemetry = preferences.showTokenTelemetry,
                                    engineStatus = engineStatus,
                                    onCopyContent = { text ->
                                        clipboardManager.setText(AnnotatedString(text))
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Texto copiado")
                                        }
                                    }
                                )
                            }
                        }

                        if (engineStatus == EngineState.INITIALIZING && !isGenerating) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                SilverShimmerLoadingText(text = "Carregando modelo...")
                            }
                        }
                    }

                    AtomicChatInputBar(
                        value = draftInput,
                        attachments = pendingAttachments,
                        isGenerating = isGenerating,
                        onValueChange = viewModel::updateDraftInput,
                        onRemoveAttachment = viewModel::removeAttachment,
                        onSend = { viewModel.sendMessage() },
                        onStop = viewModel::stopGeneration,
                        onOpenPlusMenu = { showPlusSheet = true }
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp)
                )
            }
        }

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                AtomicDrawerContent(
                    conversations = conversations,
                    activeConversationId = activeConversation?.id,
                    onCloseDrawer = {},
                    onOpenAssistants = onNavigateToAssistants,
                    onOpenModels = onNavigateToModels,
                    onOpenSettings = onNavigateToSettings,
                    onSelectConversation = { id -> viewModel.selectConversation(id) },
                    onRenameConversation = viewModel::renameConversation,
                    onDeleteConversation = viewModel::deleteConversation,
                    modifier = Modifier
                        .width(290.dp)
                        .fillMaxHeight()
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(modifier = Modifier.weight(1f)) {
                    mainChatContent()
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(308.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        AtomicDrawerContent(
                            conversations = conversations,
                            activeConversationId = activeConversation?.id,
                            onCloseDrawer = { scope.launch { drawerState.close() } },
                            onOpenAssistants = {
                                scope.launch { drawerState.close() }
                                onNavigateToAssistants()
                            },
                            onOpenModels = {
                                scope.launch { drawerState.close() }
                                onNavigateToModels()
                            },
                            onOpenSettings = {
                                scope.launch { drawerState.close() }
                                onNavigateToSettings()
                            },
                            onSelectConversation = { id ->
                                viewModel.selectConversation(id)
                                scope.launch { drawerState.close() }
                            },
                            onRenameConversation = viewModel::renameConversation,
                            onDeleteConversation = viewModel::deleteConversation,
                            modifier = Modifier
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    }
                }
            ) {
                mainChatContent()
            }
        }
    }

    if (showModelSheet) {
        ModelSelectorBottomSheet(
            models = modelSlots,
            onSelectSlot = viewModel::selectModelSlot,
            onBrowseModels = onNavigateToModels,
            onDismiss = { showModelSheet = false }
        )
    }

    if (showPlusSheet) {
        QuickPlusActionsSheet(
            activeModelName = readyModel?.displayName ?: "Nenhum modelo",
            activeModelSupportsVision = readyModel?.supportsVision == true,
            onPickFile = {
                filePickerLauncher.launch(arrayOf("*/*"))
            },
            onPickImage = {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDismiss = { showPlusSheet = false }
        )
    }
}

private fun resolveAttachmentFromUri(
    context: Context,
    uri: Uri,
    isImage: Boolean
): ChatAttachment {
    var displayName = if (isImage) "imagem.jpg" else "arquivo"
    var sizeBytes = 0L

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex >= 0) {
                cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { displayName = it }
            }
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                sizeBytes = cursor.getLong(sizeIndex)
            }
        }
    }

    val mimeType = context.contentResolver.getType(uri)
        ?: if (isImage) "image/*" else "application/octet-stream"

    return ChatAttachment(
        displayName = displayName,
        mimeType = mimeType,
        uriString = uri.toString(),
        isImage = isImage,
        sizeBytes = sizeBytes
    )
}
