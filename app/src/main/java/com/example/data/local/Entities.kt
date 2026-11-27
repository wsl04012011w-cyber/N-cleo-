package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "assistants")
data class AssistantPresetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val systemPrompt: String,
    val iconKey: String = "ATOM",
    val accentTag: String = "CYAN",
    val defaultTemperature: Float = 0.7f,
    val defaultTopP: Float = 0.9f,
    val defaultTopK: Int = 40,
    val defaultRepeatPenalty: Float = 1.1f,
    val isBuiltIn: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "model_slots")
data class ModelSlotEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val family: String,
    val quantizationLabel: String,
    val parameterSizeLabel: String,
    val sizeLabel: String,
    val tagsCsv: String,
    val downloadUrl: String,
    val repoSlug: String,
    val localFilePath: String? = null,
    val downloadedBytes: Long = 0L,
    val isInstalled: Boolean = false,
    val supportsVision: Boolean = false,
    val contextLength: Int,
    val estimatedRamGb: Float,
    val description: String,
    val isActive: Boolean = false,
    val engineState: String = "STRUCTURAL_READY"
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val assistantId: String?,
    val assistantNameSnapshot: String,
    val activeSystemPrompt: String,
    val modelSlotId: String,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 1024,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: String, // "USER" | "ASSISTANT" | "SYSTEM_NOTE"
    val content: String,
    val attachmentsSummary: String = "",
    val modelLabel: String = "",
    val tokenCountEstimate: Int = 0,
    val durationMs: Long = 0L,
    val isError: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

object MessageRole {
    const val USER = "USER"
    const val ASSISTANT = "ASSISTANT"
    const val SYSTEM_NOTE = "SYSTEM_NOTE"
}

object ModelEngineSlotState {
    const val STRUCTURAL_READY = "STRUCTURAL_READY"
    const val NOT_INSTALLED = "NOT_INSTALLED"
    const val EXTERNAL_ENGINE_REQUIRED = "EXTERNAL_ENGINE_REQUIRED"
}
