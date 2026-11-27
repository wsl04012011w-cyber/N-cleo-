package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AssistantPresetEntity
import com.example.ui.navigation.AssistantStyleRegistry
import com.example.ui.viewmodel.AssistantFilter
import com.example.ui.viewmodel.NucleoViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AssistantsScreen(
    viewModel: NucleoViewModel,
    onBack: () -> Unit,
    onUseAssistantInNewChat: (AssistantPresetEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val assistants by viewModel.filteredAssistants.collectAsStateWithLifecycle()
    val searchQuery by viewModel.assistantSearchQuery.collectAsStateWithLifecycle()
    val activeFilter by viewModel.assistantFilter.collectAsStateWithLifecycle()

    var editingAssistant by remember { mutableStateOf<AssistantPresetEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<AssistantPresetEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp)) {
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
                            .testTag("assistants_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Assistentes & Prompts",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = {
                        editingAssistant = null
                        isCreatingNew = true
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("create_assistant_fab")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Novo assistente",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

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
                                text = "Buscar assistentes ou prompts...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = viewModel::updateAssistantSearch,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("assistant_search_field")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AssistantFilter.entries) { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { viewModel.setAssistantFilter(filter) },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("assistant_filter_${filter.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (assistants.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhum assistente encontrado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)
                ) {
                    items(assistants, key = { it.id }) { assistant ->
                        AtomicAssistantCard(
                            assistant = assistant,
                            onUseInChat = { onUseAssistantInNewChat(assistant) },
                            onToggleFavorite = { viewModel.toggleAssistantFavorite(assistant) },
                            onEdit = {
                                editingAssistant = assistant
                                isCreatingNew = false
                            },
                            onDuplicate = { viewModel.duplicateAssistant(assistant) },
                            onDelete = { deleteCandidate = assistant }
                        )
                    }
                }
            }
        }
    }

    if (isCreatingNew || editingAssistant != null) {
        AssistantEditorBottomSheet(
            existing = editingAssistant,
            onDismiss = {
                isCreatingNew = false
                editingAssistant = null
            },
            onSave = { name, desc, prompt, iconKey, accentTag, temp, topP, topK, repeat ->
                viewModel.saveAssistant(
                    existingId = editingAssistant?.id,
                    name = name,
                    description = desc,
                    systemPrompt = prompt,
                    iconKey = iconKey,
                    accentTag = accentTag,
                    temperature = temp,
                    topP = topP,
                    topK = topK,
                    repeatPenalty = repeat,
                    isBuiltIn = editingAssistant?.isBuiltIn ?: false,
                    isFavorite = editingAssistant?.isFavorite ?: false
                )
                isCreatingNew = false
                editingAssistant = null
            }
        )
    }

    deleteCandidate?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Excluir assistente") },
            text = { Text("Deseja remover \"${target.name}\" da sua biblioteca local?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAssistant(target.id)
                        deleteCandidate = null
                    }
                ) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun AtomicAssistantCard(
    assistant: AssistantPresetEntity,
    onUseInChat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = AssistantStyleRegistry.iconFor(assistant.iconKey),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assistant.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = assistant.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (assistant.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favoritar assistente",
                        tint = if (assistant.isFavorite) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = assistant.systemPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Editar assistente",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Duplicar assistente",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!assistant.isBuiltIn) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Excluir assistente",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Button(
                    onClick = onUseInChat,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("use_assistant_${assistant.id}")
                ) {
                    Text("Usar no chat", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantEditorBottomSheet(
    existing: AssistantPresetEntity?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        description: String,
        systemPrompt: String,
        iconKey: String,
        accentTag: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var description by remember(existing) { mutableStateOf(existing?.description ?: "") }
    var systemPrompt by remember(existing) { mutableStateOf(existing?.systemPrompt ?: "") }
    var iconKey by remember(existing) { mutableStateOf(existing?.iconKey ?: "ATOM") }
    var accentTag by remember(existing) { mutableStateOf(existing?.accentTag ?: "CYAN") }
    var temperature by remember(existing) { mutableFloatStateOf(existing?.defaultTemperature ?: 0.7f) }
    var topP by remember(existing) { mutableFloatStateOf(existing?.defaultTopP ?: 0.9f) }
    var topK by remember(existing) { mutableIntStateOf(existing?.defaultTopK ?: 40) }
    var repeatPenalty by remember(existing) { mutableFloatStateOf(existing?.defaultRepeatPenalty ?: 1.1f) }

    val promptQuickSnippets = remember {
        listOf(
            "Direto" to "Responda em português do Brasil de forma objetiva, sem introduções longas.",
            "Código" to "Forneça código limpo, comentários úteis e explique os trade-offs técnicos.",
            "Didático" to "Explique passo a passo usando exemplos práticos e linguagem acessível."
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = if (existing == null) "Novo Assistente" else "Editar Assistente",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do assistente") },
                placeholder = { Text("Ex.: Especialista Android") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_assistant_name")
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrição curta") },
                placeholder = { Text("Para que serve este assistente?") },
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_assistant_description")
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("Prompt de sistema") },
                placeholder = { Text("Defina papel, tom, idioma e regras de resposta...") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_assistant_prompt")
            )

            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(promptQuickSnippets) { (label, snippet) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            systemPrompt = if (systemPrompt.isBlank()) snippet
                            else "${systemPrompt.trim()}\n$snippet"
                        },
                        label = { Text("+ $label") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Ícone", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistantStyleRegistry.iconOptions.forEach { (key, label) ->
                    val selected = iconKey == key
                    Surface(
                        onClick = { iconKey = key },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = AssistantStyleRegistry.iconFor(key),
                            contentDescription = label,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = String.format(Locale.US, "Temperatura padrão: %.2f", temperature),
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0f..2f
            )

            Text(
                text = String.format(Locale.US, "Top-P: %.2f  •  Top-K: %d  •  Repeat: %.2f", topP, topK, repeatPenalty),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = topP,
                onValueChange = { topP = it },
                valueRange = 0.1f..1f
            )
            Slider(
                value = topK.toFloat(),
                onValueChange = { topK = it.roundToInt() },
                valueRange = 1f..100f
            )

            Spacer(modifier = Modifier.height(14.dp))

            val canSave = name.isNotBlank() && systemPrompt.isNotBlank()
            Button(
                onClick = {
                    onSave(
                        name,
                        description.ifBlank { "Assistente personalizado em Português (BR)." },
                        systemPrompt,
                        iconKey,
                        accentTag,
                        temperature,
                        topP,
                        topK,
                        repeatPenalty
                    )
                },
                enabled = canSave,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("save_assistant_button")
            ) {
                Text(if (existing == null) "Criar Assistente" else "Salvar Alterações")
            }
        }
    }
}
