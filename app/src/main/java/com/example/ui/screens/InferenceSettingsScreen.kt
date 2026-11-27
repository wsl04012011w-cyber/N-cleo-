package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.GenerationParameters
import com.example.ui.viewmodel.NucleoViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun InferenceSettingsScreen(
    viewModel: NucleoViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val draftConfig by viewModel.draftNewChatConfig.collectAsStateWithLifecycle()

    val currentSystemPrompt = activeConversation?.activeSystemPrompt ?: draftConfig.systemPrompt
    val currentParams = activeConversation?.let {
        GenerationParameters(
            temperature = it.temperature,
            topP = it.topP,
            topK = it.topK,
            repeatPenalty = it.repeatPenalty,
            maxTokens = it.maxTokens
        )
    } ?: draftConfig.parameters

    var promptText by remember(currentSystemPrompt) { mutableStateOf(currentSystemPrompt) }
    var temperature by remember(currentParams) { mutableFloatStateOf(currentParams.temperature) }
    var repeatPenalty by remember(currentParams) { mutableFloatStateOf(currentParams.repeatPenalty) }
    var topP by remember(currentParams) { mutableFloatStateOf(currentParams.topP) }
    var topK by remember(currentParams) { mutableIntStateOf(currentParams.topK) }
    var maxTokens by remember(currentParams) { mutableIntStateOf(currentParams.maxTokens) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp)) {
            // Top bar idêntica à captura Inference settings
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .minimumInteractiveComponentSize()
                        .testTag("inference_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Ajustes de inferência",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Estes valores são aplicados na conversa atual ou quando um assistente não possui ajustes próprios.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AtomicParameterCard(
                    title = "Temperatura",
                    valueDisplay = String.format(Locale.US, "%.2f", temperature),
                    minLabel = "0.00",
                    maxLabel = "2.00",
                    value = temperature,
                    range = 0f..2f,
                    tickCount = 15,
                    description = "Valores maiores produzem respostas mais variadas. Valores menores deixam as respostas mais focadas e determinísticas.",
                    onValueChange = { temperature = (it * 20f).roundToInt() / 20f }
                )

                AtomicParameterCard(
                    title = "Penalidade de repetição",
                    valueDisplay = String.format(Locale.US, "%.2f", repeatPenalty),
                    minLabel = "1.00",
                    maxLabel = "1.50",
                    value = repeatPenalty,
                    range = 1.0f..1.5f,
                    tickCount = 11,
                    description = "Desencoraja o modelo a repetir as mesmas palavras. Valores maiores reduzem repetições.",
                    onValueChange = { repeatPenalty = (it * 20f).roundToInt() / 20f }
                )

                AtomicParameterCard(
                    title = "Top-P (Nucleus)",
                    valueDisplay = String.format(Locale.US, "%.2f", topP),
                    minLabel = "0.10",
                    maxLabel = "1.00",
                    value = topP,
                    range = 0.1f..1.0f,
                    tickCount = 10,
                    description = "Restringe a escolha de tokens ao conjunto cuja probabilidade acumulada atinge o limite definido.",
                    onValueChange = { topP = (it * 20f).roundToInt() / 20f }
                )

                AtomicParameterCard(
                    title = "Top-K",
                    valueDisplay = topK.toString(),
                    minLabel = "1",
                    maxLabel = "100",
                    value = topK.toFloat(),
                    range = 1f..100f,
                    tickCount = 11,
                    description = "Limita a seleção aos K próximos tokens mais prováveis em cada etapa da geração.",
                    onValueChange = { topK = it.roundToInt().coerceIn(1, 100) }
                )

                // Card de Prompt de Sistema ativo
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Prompt de sistema ativo",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            minLines = 3,
                            maxLines = 7,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sheet_system_prompt_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.restoreAssistantDefaults { restoredPrompt, restoredParams ->
                                promptText = restoredPrompt
                                temperature = restoredParams.temperature
                                repeatPenalty = restoredParams.repeatPenalty
                                topP = restoredParams.topP
                                topK = restoredParams.topK
                                maxTokens = restoredParams.maxTokens
                            }
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Restaurar")
                    }

                    Button(
                        onClick = {
                            viewModel.applyPromptAndParameters(
                                systemPrompt = promptText,
                                params = GenerationParameters(
                                    temperature = temperature,
                                    topP = topP,
                                    topK = topK,
                                    repeatPenalty = repeatPenalty,
                                    maxTokens = maxTokens
                                )
                            )
                            onBack()
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("apply_params_button")
                    ) {
                        Text("Aplicar")
                    }
                }
            }
        }
    }
}

@Composable
private fun AtomicParameterCard(
    title: String,
    valueDisplay: String,
    minLabel: String,
    maxLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tickCount: Int,
    description: String,
    onValueChange: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = valueDisplay,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            AtomicTickSlider(
                value = value,
                range = range,
                tickCount = tickCount,
                onValueChange = onValueChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = minLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = maxLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AtomicTickSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tickCount: Int,
    onValueChange: (Float) -> Unit
) {
    val span = (range.endInclusive - range.start).coerceAtLeast(0.001f)
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val thumbWidth = 24.dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val thumbWidthPx = with(density) { thumbWidth.toPx() }
        val usableWidthPx = (totalWidthPx - thumbWidthPx).coerceAtLeast(1f)

        val updateFromOffset: (Float) -> Unit = { xPx ->
            val newFrac = ((xPx - thumbWidthPx / 2f) / usableWidthPx).coerceIn(0f, 1f)
            onValueChange(range.start + newFrac * span)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerInput(range) {
                    detectTapGestures { offset -> updateFromOffset(offset.x) }
                }
                .pointerInput(range) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        updateFromOffset(change.position.x)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(tickCount) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 2.dp,
            modifier = Modifier
                .offset { IntOffset((fraction * usableWidthPx).roundToInt(), 0) }
                .size(width = thumbWidth, height = 32.dp)
        ) {}
    }
}
