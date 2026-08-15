package com.penly.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "objects",
    indices = [Index(value = ["pageId"])],
)
data class ObjectEntity(
    @PrimaryKey val objectId: String,
    val pageId: String,
    val objectType: String,
    val payloadRef: String?,
    val zIndex: Int,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val rotationDegrees: Float,
    val scaleX: Float,
    val scaleY: Float,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val revision: Long,
)
