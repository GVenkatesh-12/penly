package com.penly.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val documentId: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val revision: Long,
)
