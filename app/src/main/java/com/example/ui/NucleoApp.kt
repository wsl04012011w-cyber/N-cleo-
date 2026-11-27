package com.example.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.Routes
import com.example.ui.screens.AssistantsScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.InferenceSettingsScreen
import com.example.ui.screens.ModelsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.NucleoViewModel

@Composable
fun NucleoApp(
    viewModel: NucleoViewModel
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CHAT
    ) {
        composable(Routes.CHAT) {
            ChatScreen(
                viewModel = viewModel,
                onNavigateToModels = { navController.navigate(Routes.MODELS) },
                onNavigateToAssistants = { navController.navigate(Routes.ASSISTANTS) },
                onNavigateToInferenceSettings = { navController.navigate(Routes.INFERENCE_SETTINGS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.MODELS) {
            ModelsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onStartChatWithSlot = { slotId ->
                    viewModel.selectModelSlot(slotId)
                    viewModel.startNewChat()
                    navController.popBackStack(Routes.CHAT, inclusive = false)
                }
            )
        }

        composable(Routes.ASSISTANTS) {
            AssistantsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onUseAssistantInNewChat = { assistant ->
                    viewModel.startNewChat(withAssistant = assistant)
                    navController.popBackStack(Routes.CHAT, inclusive = false)
                }
            )
        }

        composable(Routes.INFERENCE_SETTINGS) {
            InferenceSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenInferenceSettings = { navController.navigate(Routes.INFERENCE_SETTINGS) },
                onOpenAssistants = { navController.navigate(Routes.ASSISTANTS) },
                onOpenModels = { navController.navigate(Routes.MODELS) }
            )
        }
    }
}
