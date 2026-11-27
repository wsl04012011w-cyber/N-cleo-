package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AssistantPresetEntity::class,
        ModelSlotEntity::class,
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class NucleoDatabase : RoomDatabase() {
    abstract fun assistantPresetDao(): AssistantPresetDao
    abstract fun modelSlotDao(): ModelSlotDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: NucleoDatabase? = null

        fun getInstance(context: Context): NucleoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NucleoDatabase::class.java,
                    "nucleo_chat.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
