package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MessageRole
import com.example.engine.ChatAttachment
import com.example.engine.EngineState
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@Composable
fun SilverShimmerLoadingText(
    text: String = "Carregando modelo...",
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
    modifier: Modifier = Modifier
) {
    val baseColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val midSilver = Color(0xFFC2C7D1)
    val brightSilver = Color(0xFFF5F7FA)

    val infiniteTransition = rememberInfiniteTransition(label = "silver_shimmer")
    val progress by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_sweep"
    )

    var textWidthPx by remember { mutableFloatStateOf(320f) }
    val bandWidth = (textWidthPx * 0.40f).coerceAtLeast(84f)
    val startX = progress * (textWidthPx + bandWidth) - bandWidth

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            midSilver,
            brightSilver,
            midSilver,
            baseColor
        ),
        start = Offset(startX, 0f),
        end = Offset(startX + bandWidth, 0f)
    )

    Text(
        text = text,
        style = style.copy(brush = shimmerBrush),
        onTextLayout = { layout ->
            val measured = layout.size.width.toFloat()
            if (measured > 0f) {
                textWidthPx = measured
            }
        },
        modifier = modifier
    )
}

@Composable
fun AtomicAsteriskEmblem(
    color: Color = MaterialTheme.colorScheme.onBackground,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barWidth = w * 0.20f
        val barHeight = h * 0.86f
        val angles = listOf(0f, 45f, 90f, 135f)
        for (angle in angles) {
            rotate(degrees = angle, pivot = Offset(w / 2f, h / 2f)) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset((w - barWidth) / 2f, (h - barHeight) / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}

@Composable
fun ModelPrismIcon(
    color: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.86f, h * 0.54f)
            lineTo(w * 0.5f, h * 0.92f)
            lineTo(w * 0.14f, h * 0.54f)
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = w * 0.11f)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.12f),
            end = Offset(w * 0.5f, h * 0.88f),
            strokeWidth = w * 0.09f
        )
    }
}

@Composable
fun DrawerLinesIcon(
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.15f, size.height * 0.36f),
            end = Offset(size.width * 0.85f, size.height * 0.36f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.15f, size.height * 0.64f),
            end = Offset(size.width * 0.62f, size.height * 0.64f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun AtomicTopBar(
    showDrawerButton: Boolean,
    showNewChatButton: Boolean,
    activeModelName: String,
    activeModelFamily: String,
    onOpenDrawer: () -> Unit,
    onOpenModelSelector: () -> Unit,
    onOpenInferenceSettings: () -> Unit,
    onNewChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (showDrawerButton) {
            Surface(
                onClick = onOpenDrawer,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("open_history_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    DrawerLinesIcon(color = MaterialTheme.colorScheme.onSurface)
                }
            }
        } else {
            Spacer(modifier = Modifier.size(44.dp))
        }

        Surface(
            onClick = onOpenModelSelector,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .heightIn(min = 42.dp)
                .widthIn(max = 220.dp)
                .testTag("top_model_selector_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModelBrandIcon(
                    family = activeModelFamily,
                    size = 17.dp,
                    tintFallback = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = activeModelName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "Selecionar modelo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onOpenInferenceSettings,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("open_params_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Ajustes de inferência",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (showNewChatButton) {
                Surface(
                    onClick = onNewChat,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("new_chat_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Novo chat",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AtomicEmptyState(
    activeAssistantName: String,
    isDefaultAssistant: Boolean,
    isModelLoading: Boolean = false,
    onSelectAssistantChip: () -> Unit,
    onSelectStarterPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val starters = remember {
        listOf(
            "Surpreenda-me",
            "Resumir um texto",
            "Planejar meu dia",
            "Escrever código",
            "Revisar e-mail"
        )
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                AtomicAsteriskEmblem(
                    color = MaterialTheme.colorScheme.onBackground,
                    size = 38.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Como posso ajudar?",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        if (!isDefaultAssistant) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                onClick = onSelectAssistantChip,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeAssistantName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        AnimatedVisibility(visible = isModelLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                SilverShimmerLoadingText(
                    text = "Carregando modelo...",
                    modifier = Modifier.testTag("loading_model_shimmer")
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(starters.size) { index ->
                val prompt = starters[index]
                Surface(
                    onClick = { onSelectStarterPrompt(prompt) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("starter_prompt_$index")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AtomicMessageItem(
    message: MessageEntity,
    isCurrentlyStreaming: Boolean,
    showTelemetry: Boolean,
    onCopyContent: (String) -> Unit,
    engineStatus: EngineState = EngineState.GENERATING
) {
    val isUser = message.role == MessageRole.USER
    val infiniteTransition = rememberInfiniteTransition(label = "stream_cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    if (isUser) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceHighOrContainer(),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (message.attachmentsSummary.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = message.attachmentsSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (message.content.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    } else {
        var expandedDetails by remember { mutableStateOf(true) }
        val split = remember(message.content, isCurrentlyStreaming) {
            splitAssistantContent(message.content, isCurrentlyStreaming)
        }
        val showCursorInThinking = isCurrentlyStreaming &&
            split.mainBody.isBlank() &&
            split.collapsibleBody.isNotBlank()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (split.headerTitle.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .clickable { expandedDetails = !expandedDetails }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = split.headerTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expandedDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Alternar detalhes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = expandedDetails && split.collapsibleBody.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, top = 6.dp, bottom = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .heightIn(min = 32.dp)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        val thinkingText = buildString {
                            append(split.collapsibleBody)
                            if (showCursorInThinking) {
                                append(if (cursorAlpha > 0.5f) " ▌" else " ")
                            }
                        }
                        Text(
                            text = thinkingText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isCurrentlyStreaming && split.mainBody.isBlank() && split.headerTitle.isBlank()) {
                if (engineStatus == EngineState.INITIALIZING) {
                    SilverShimmerLoadingText(
                        text = "Carregando modelo...",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                } else {
                    Text(
                        text = "Gerando resposta...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val displayText = buildString {
                    append(split.mainBody)
                    if (isCurrentlyStreaming && split.mainBody.isNotBlank()) {
                        append(if (cursorAlpha > 0.5f) " ▌" else " ")
                    }
                }
                if (displayText.isNotBlank()) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (!isCurrentlyStreaming && message.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = { onCopyContent(message.content) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copiar mensagem",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (showTelemetry && message.tokenCountEstimate > 0) {
                        val sec = message.durationMs / 1000.0
                        val tokSec = if (sec > 0.05) message.tokenCountEstimate / sec else 0.0
                        val stats = if (tokSec > 0.0) {
                            String.format(Locale.US, "%d tok • %.1f tok/s", message.tokenCountEstimate, tokSec)
                        } else {
                            "${message.tokenCountEstimate} tok"
                        }
                        Text(
                            text = stats,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.material3.ColorScheme.surfaceHighOrContainer(): Color =
    surfaceContainerHigh

private data class AssistantSectionSplit(
    val headerTitle: String,
    val collapsibleBody: String,
    val mainBody: String
)

private fun splitAssistantContent(
    content: String,
    isCurrentlyStreaming: Boolean
): AssistantSectionSplit {
    val structuralMarker = "[Modo Estrutural • Engine desacoplada]"
    if (content.startsWith(structuralMarker)) {
        val body = content.removePrefix(structuralMarker).trimStart()
        val splitToken = "Quando conectarmos a engine real"
        val idx = body.indexOf(splitToken)
        val header = if (isCurrentlyStreaming) "Modo Estrutural..." else "Modo Estrutural"
        return if (idx >= 0) {
            AssistantSectionSplit(header, body.substring(0, idx).trim(), body.substring(idx).trim())
        } else {
            AssistantSectionSplit(header, body, "")
        }
    }

    val openTag = "<think>"
    val closeTag = "</think>"
    val openIdx = content.indexOf(openTag)
    val closeIdx = content.indexOf(closeTag)

    if (closeIdx >= 0) {
        val thinkStart = if (openIdx in 0 until closeIdx) openIdx + openTag.length else 0
        val thinkingBody = content.substring(thinkStart, closeIdx).trim()
        val answerBody = content.substring(closeIdx + closeTag.length).trimStart()
        val header = if (isCurrentlyStreaming && answerBody.isBlank()) "Pensando..." else "Pensamento"
        return AssistantSectionSplit(header, thinkingBody, answerBody)
    }

    if (openIdx >= 0) {
        val beforeThink = content.substring(0, openIdx).trim()
        val thinkingBody = content.substring(openIdx + openTag.length).trimStart()
        val header = if (isCurrentlyStreaming) "Pensando..." else "Pensamento"
        return AssistantSectionSplit(header, thinkingBody, beforeThink)
    }

    return AssistantSectionSplit("", "", content)
}

@Composable
fun AtomicChatInputBar(
    value: String,
    attachments: List<ChatAttachment>,
    isGenerating: Boolean,
    onValueChange: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenPlusMenu: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 10.dp)
        ) {
            if (attachments.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp, end = 6.dp)
                ) {
                    items(attachments, key = { it.id }) { attachment ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (attachment.isImage) Icons.Outlined.Image else Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = attachment.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 150.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .clickable { onRemoveAttachment(attachment.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remover anexo",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 26.dp, max = 120.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Pergunte qualquer coisa",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_input_field")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenPlusMenu,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("assistant_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Anexar arquivo ou imagem",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isGenerating) {
                    Surface(
                        onClick = onStop,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("stop_generation_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Parar geração",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    val canSend = value.isNotBlank() || attachments.isNotEmpty()
                    Surface(
                        onClick = onSend,
                        enabled = canSend,
                        shape = CircleShape,
                        color = if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("send_message_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = "Enviar mensagem",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AtomicDrawerContent(
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    onCloseDrawer: () -> Unit,
    onOpenAssistants: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuConvId by remember { mutableStateOf<String?>(null) }
    var renamingTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AtomicAsteriskEmblem(
                    color = MaterialTheme.colorScheme.onSurface,
                    size = 22.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Núcleo Chat",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = onCloseDrawer,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Fechar menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        DrawerActionRow(
            icon = Icons.Filled.Psychology,
            label = "Assistentes & Prompts",
            testTag = "drawer_assistants_button",
            onClick = onOpenAssistants
        )
        DrawerActionRow(
            icon = Icons.Filled.Memory,
            label = "Modelos",
            testTag = "drawer_models_button",
            onClick = onOpenModels
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Histórico",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    text = "Sem conversas salvas ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val isSelected = conv.id == activeConversationId
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.surfaceContainer
                                    else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = { onSelectConversation(conv.id) },
                                    onLongClick = { menuConvId = conv.id }
                                )
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = conv.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                IconButton(
                                    onClick = { menuConvId = conv.id },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreHoriz,
                                        contentDescription = "Opções da conversa",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = menuConvId == conv.id,
                            onDismissRequest = { menuConvId = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Renomear") },
                                onClick = {
                                    menuConvId = null
                                    renamingTarget = conv
                                    renameText = conv.title
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Excluir", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuConvId = null
                                    onDeleteConversation(conv.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(6.dp))

        DrawerActionRow(
            icon = Icons.Filled.Settings,
            label = "Ajustes",
            testTag = "drawer_settings_button",
            onClick = onOpenSettings
        )
    }

    renamingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingTarget = null },
            title = { Text("Renomear conversa") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Título") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameConversation(target.id, renameText)
                        renamingTarget = null
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingTarget = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun DrawerActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
