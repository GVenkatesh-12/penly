package com.penly.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ObjectDao {
    @Upsert
    fun upsertAll(objects: List<ObjectEntity>)

    @Query("SELECT * FROM objects WHERE pageId = :pageId")
    fun getByPage(pageId: String): List<ObjectEntity>

    @Query("DELETE FROM objects WHERE pageId = :pageId")
    fun deleteForPage(pageId: String)
}
