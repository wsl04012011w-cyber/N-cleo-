package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistantPresetDao {
    @Query("SELECT * FROM assistants ORDER BY isFavorite DESC, isBuiltIn DESC, updatedAt DESC")
    fun observeAllAssistants(): Flow<List<AssistantPresetEntity>>

    @Query("SELECT * FROM assistants WHERE id = :id LIMIT 1")
    suspend fun getAssistantById(id: String): AssistantPresetEntity?

    @Query("SELECT * FROM assistants ORDER BY isFavorite DESC, isBuiltIn DESC, createdAt ASC LIMIT 1")
    suspend fun getDefaultAssistant(): AssistantPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assistant: AssistantPresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assistants: List<AssistantPresetEntity>)

    @Update
    suspend fun update(assistant: AssistantPresetEntity)

    @Query("UPDATE assistants SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM assistants WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM assistants")
    suspend fun count(): Int
}

@Dao
interface ModelSlotDao {
    @Query("SELECT * FROM model_slots ORDER BY isActive DESC, estimatedRamGb ASC")
    fun observeAllModelSlots(): Flow<List<ModelSlotEntity>>

    @Query("SELECT * FROM model_slots WHERE isActive = 1 LIMIT 1")
    fun observeActiveModelSlot(): Flow<ModelSlotEntity?>

    @Query("SELECT * FROM model_slots WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveModelSlot(): ModelSlotEntity?

    @Query("SELECT * FROM model_slots WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ModelSlotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(slot: ModelSlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(slots: List<ModelSlotEntity>)

    @Query("UPDATE model_slots SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE model_slots SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: String)

    @Query("UPDATE model_slots SET isInstalled = :installed, localFilePath = :localFilePath, downloadedBytes = :downloadedBytes WHERE id = :id")
    suspend fun updateInstallState(id: String, installed: Boolean, localFilePath: String?, downloadedBytes: Long)

    @Query("DELETE FROM model_slots WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM model_slots")
    suspend fun count(): Int
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observeConversationById(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM conversations")
    fun observeCount(): Flow<Int>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesForConversationOnce(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, tokenCountEstimate = :tokenCount, durationMs = :durationMs WHERE id = :id")
    suspend fun updateStreamingMessage(id: String, content: String, tokenCount: Int, durationMs: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM messages")
    fun observeTotalMessageCount(): Flow<Int>
}
