package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.download.DownloadStatus
import com.example.data.download.ModelDownloadState
import com.example.data.local.ModelSlotEntity
import com.example.ui.components.ModelBrandIcon
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.TagAmberBg
import com.example.ui.theme.TagAmberFg
import com.example.ui.theme.TagGreenBg
import com.example.ui.theme.TagGreenFg
import com.example.ui.theme.TagIndigoBg
import com.example.ui.theme.TagIndigoFg
import com.example.ui.theme.TagRed
import com.example.ui.theme.TagTealBg
import com.example.ui.theme.TagTealFg
import com.example.ui.viewmodel.NucleoViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ModelsScreen(
    viewModel: NucleoViewModel,
    onBack: () -> Unit,
    onStartChatWithSlot: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val models by viewModel.filteredModelSlots.collectAsStateWithLifecycle()
    val searchQuery by viewModel.modelSearchQuery.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()

    var showAddCustomDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val installedModels = remember(models) { models.filter { it.isInstalled } }
    val popularModels = remember(models) { models.filter { !it.isInstalled } }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 720.dp)) {
                // Top bar fiel à captura Models
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("models_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Modelos",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    IconButton(
                        onClick = { showAddCustomDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("add_custom_gguf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Adicionar link GGUF",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Barra de busca arredondada
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Modelo, formato ou quantização",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = viewModel::updateModelSearch,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("models_search_field")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (installedModels.isNotEmpty()) {
                        item {
                            Text(
                                text = "Modelos Instalados",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(installedModels, key = { it.id }) { slot ->
                            AtomicModelCard(
                                slot = slot,
                                downloadState = downloadStates[slot.id],
                                onStartChat = { onStartChatWithSlot(slot.id) },
                                onDownload = { viewModel.startModelDownload(slot.id) },
                                onCancelDownload = { viewModel.cancelModelDownload(slot.id) },
                                onUninstall = { viewModel.uninstallModelSlot(slot.id) },
                                onCopyLink = { url ->
                                    clipboardManager.setText(AnnotatedString(url))
                                    scope.launch { snackbarHostState.showSnackbar("Link GGUF copiado") }
                                },
                                onOpenLink = { url ->
                                    runCatching {
                                        val repoUrl = url.substringBefore("/resolve/")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
                                    }
                                }
                            )
                        }
                    }

                    if (popularModels.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Modelos Populares",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(popularModels, key = { it.id }) { slot ->
                            AtomicModelCard(
                                slot = slot,
                                downloadState = downloadStates[slot.id],
                                onStartChat = { onStartChatWithSlot(slot.id) },
                                onDownload = { viewModel.startModelDownload(slot.id) },
                                onCancelDownload = { viewModel.cancelModelDownload(slot.id) },
                                onUninstall = null,
                                onCopyLink = { url ->
                                    clipboardManager.setText(AnnotatedString(url))
                                    scope.launch { snackbarHostState.showSnackbar("Link GGUF copiado") }
                                },
                                onOpenLink = { url ->
                                    runCatching {
                                        val repoUrl = url.substringBefore("/resolve/")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddCustomDialog) {
        AddCustomGgufDialog(
            onDismiss = { showAddCustomDialog = false },
            onConfirm = { name, url, quant ->
                viewModel.addAndDownloadCustomGguf(name, url, quant)
                showAddCustomDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AtomicModelCard(
    slot: ModelSlotEntity,
    downloadState: ModelDownloadState?,
    onStartChat: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onUninstall: (() -> Unit)?,
    onCopyLink: (String) -> Unit,
    onOpenLink: (String) -> Unit
) {
    val tags = remember(slot.tagsCsv) {
        slot.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val isDownloading = downloadState?.status == DownloadStatus.DOWNLOADING
    val isError = downloadState?.status == DownloadStatus.ERROR
    val sizeText = if (slot.downloadedBytes > 0L) {
        formatBytes(slot.downloadedBytes)
    } else {
        slot.sizeLabel
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    ModelBrandIcon(
                        family = slot.family,
                        size = 22.dp,
                        tintFallback = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = slot.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "• $sizeText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 34.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "GGUF",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = slot.quantizationLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Link real Hugging Face GGUF
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onOpenLink(slot.downloadUrl) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = slot.repoSlug,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { onCopyLink(slot.downloadUrl) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copiar link GGUF",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = slot.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 34.dp)
            )

            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.padding(start = 34.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        ModelTagChip(tag = tag)
                    }
                }
            }

            // Barra de progresso real do download GGUF
            AnimatedVisibility(visible = isDownloading && downloadState != null) {
                if (downloadState != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 34.dp, top = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val progressLabel = if (downloadState.totalBytes > 0L) {
                                val pct = (downloadState.progress * 100f).toInt().coerceIn(0, 100)
                                "${formatBytes(downloadState.bytesDownloaded)} / ${formatBytes(downloadState.totalBytes)} • $pct%"
                            } else {
                                "${formatBytes(downloadState.bytesDownloaded)} baixados..."
                            }
                            Text(
                                text = progressLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (downloadState.speedBytesPerSec > 0L) {
                                Text(
                                    text = "${formatBytes(downloadState.speedBytesPerSec)}/s",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (downloadState.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { downloadState.progress },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .testTag("download_progress_${slot.id}")
                            )
                        } else {
                            LinearProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .testTag("download_progress_${slot.id}")
                            )
                        }
                    }
                }
            }

            if (isError && downloadState?.errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = downloadState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = TagRed,
                    modifier = Modifier.padding(start = 34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    isDownloading -> {
                        OutlinedButton(
                            onClick = onCancelDownload,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("cancel_download_${slot.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancelar download", style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    slot.isInstalled -> {
                        Button(
                            onClick = onStartChat,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("select_model_${slot.id}")
                        ) {
                            Text("Iniciar chat", style = MaterialTheme.typography.titleSmall)
                        }

                        if (slot.localFilePath == null) {
                            Surface(
                                onClick = onDownload,
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("download_gguf_${slot.id}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "Baixar arquivo GGUF real",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        if (onUninstall != null) {
                            Surface(
                                onClick = onUninstall,
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Remover modelo",
                                        tint = TagRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("download_model_${slot.id}")
                        ) {
                            Text(
                                text = if (isError) "Tentar novamente" else "Baixar",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelTagChip(tag: String) {
    val (bg, fg) = when {
        tag.contains("rápido", true) || tag.contains("recomendado", true) -> TagGreenBg to TagGreenFg
        tag.contains("diário", true) || tag.contains("equilibrado", true) -> TagAmberBg to TagAmberFg
        tag.contains("visão", true) || tag.contains("código", true) -> TagTealBg to TagTealFg
        else -> TagIndigoBg to TagIndigoFg
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AddCustomGgufDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, quant: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var quant by remember { mutableStateOf("Q4_K_M") }

    val isValid = name.isNotBlank() && url.trim().startsWith("https://")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar GGUF por link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Cole o link direto de um arquivo .gguf no Hugging Face para baixar no aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do modelo") },
                    placeholder = { Text("Ex.: Qwen 2.5 (1.5B)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL direta (.gguf)") },
                    placeholder = { Text("https://huggingface.co/.../resolve/main/model.gguf") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = quant,
                    onValueChange = { quant = it },
                    label = { Text("Quantização") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(name, url, quant) }
            ) {
                Text("Baixar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}
