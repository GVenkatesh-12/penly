package com.penly.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    indices = [Index(value = ["documentId"])],
)
data class PageEntity(
    @PrimaryKey val pageId: String,
    val documentId: String,
    val title: String,
    val revision: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
