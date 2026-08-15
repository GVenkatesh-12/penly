package com.penly.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PageDao {
    @Upsert
    fun upsert(page: PageEntity)

    @Query("SELECT * FROM pages WHERE documentId = :documentId")
    fun getByDocument(documentId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE pageId = :pageId")
    fun getById(pageId: String): PageEntity?
}
