package com.penly.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentEntity::class, PageEntity::class, ObjectEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PenlyDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    abstract fun pageDao(): PageDao

    abstract fun objectDao(): ObjectDao
}
