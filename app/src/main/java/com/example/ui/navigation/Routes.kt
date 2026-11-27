package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentRose

object Routes {
    const val CHAT = "chat"
    const val ASSISTANTS = "assistants"
    const val MODELS = "models"
    const val INFERENCE_SETTINGS = "inference_settings"
    const val SETTINGS = "settings"
}

enum class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CHAT(
        route = Routes.CHAT,
        label = "Chat",
        selectedIcon = Icons.Filled.ChatBubble,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
        testTag = "nav_tab_chat"
    ),
    ASSISTANTS(
        route = Routes.ASSISTANTS,
        label = "Assistentes",
        selectedIcon = Icons.Filled.Psychology,
        unselectedIcon = Icons.Outlined.Psychology,
        testTag = "nav_tab_assistants"
    ),
    MODELS(
        route = Routes.MODELS,
        label = "Modelos",
        selectedIcon = Icons.Filled.Memory,
        unselectedIcon = Icons.Outlined.Memory,
        testTag = "nav_tab_models"
    ),
    SETTINGS(
        route = Routes.SETTINGS,
        label = "Ajustes",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
}

object AssistantStyleRegistry {
    val iconOptions = listOf(
        "ATOM" to "Núcleo",
        "CODE" to "Código",
        "EDIT" to "Redação",
        "SCHOOL" to "Tutor",
        "SUMMARIZE" to "Resumo"
    )

    val accentOptions = listOf(
        "CYAN" to "Ciano",
        "EMERALD" to "Esmeralda",
        "AMBER" to "Âmbar",
        "INDIGO" to "Índigo",
        "ROSE" to "Rosa"
    )

    fun iconFor(key: String): ImageVector = when (key) {
        "CODE" -> Icons.Filled.Code
        "EDIT" -> Icons.Filled.EditNote
        "SCHOOL" -> Icons.Filled.School
        "SUMMARIZE" -> Icons.Filled.Description
        else -> Icons.Filled.AutoAwesome
    }

    fun accentColorFor(tag: String): Color = when (tag) {
        "EMERALD" -> AccentEmerald
        "AMBER" -> AccentAmber
        "INDIGO" -> AccentIndigo
        "ROSE" -> AccentRose
        else -> AccentCyan
    }
}
