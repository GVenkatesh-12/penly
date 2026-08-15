package com.penly.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DocumentDao {
    @Upsert
    fun upsert(document: DocumentEntity)

    @Query("SELECT * FROM documents")
    fun getAll(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE documentId = :id")
    fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents ORDER BY updatedAtMillis DESC LIMIT 1")
    fun latest(): DocumentEntity?
}
