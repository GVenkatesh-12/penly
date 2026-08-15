package com.penly.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenlyDatabaseTest {
    private lateinit var db: PenlyDatabase
    private lateinit var documentDao: DocumentDao
    private lateinit var pageDao: PageDao
    private lateinit var objectDao: ObjectDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PenlyDatabase::class.java).build()
        documentDao = db.documentDao()
        pageDao = db.pageDao()
        objectDao = db.objectDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndReadBackDocumentPageAndObjects() {
        val document =
            DocumentEntity(
                documentId = "doc-1",
                title = "Meeting notes",
                createdAtMillis = 1_000L,
                updatedAtMillis = 2_000L,
                revision = 3L,
            )
        val page =
            PageEntity(
                pageId = "page-1",
                documentId = "doc-1",
                title = "Page 1",
                revision = 2L,
                createdAtMillis = 1_000L,
                updatedAtMillis = 1_500L,
            )
        val objects =
            listOf(
                ObjectEntity(
                    objectId = "obj-1",
                    pageId = "page-1",
                    objectType = "INK",
                    payloadRef = null,
                    zIndex = 0,
                    minX = 1.5f,
                    minY = 2.5f,
                    maxX = 3.5f,
                    maxY = 4.5f,
                    rotationDegrees = 30f,
                    scaleX = 1f,
                    scaleY = 2f,
                    createdAtMillis = 1_000L,
                    updatedAtMillis = 1_100L,
                    revision = 1L,
                ),
                ObjectEntity(
                    objectId = "obj-2",
                    pageId = "page-1",
                    objectType = "TEXT",
                    payloadRef = "obj-2",
                    zIndex = 1,
                    minX = -1f,
                    minY = -2f,
                    maxX = 10f,
                    maxY = 20f,
                    rotationDegrees = 0f,
                    scaleX = 1f,
                    scaleY = 1f,
                    createdAtMillis = 1_000L,
                    updatedAtMillis = 1_200L,
                    revision = 2L,
                ),
            )

        documentDao.upsert(document)
        pageDao.upsert(page)
        objectDao.upsertAll(objects)

        assertEquals(document, documentDao.getById("doc-1"))
        assertEquals(setOf(document), documentDao.getAll().toSet())
        assertEquals(document, documentDao.latest())
        assertEquals(setOf(page), pageDao.getByDocument("doc-1").toSet())
        assertEquals(page, pageDao.getById("page-1"))
        assertEquals(objects.toSet(), objectDao.getByPage("page-1").toSet())
    }

    @Test
    fun latestReturnsMostRecentlyUpdatedDocument() {
        val older =
            DocumentEntity(
                documentId = "doc-old",
                title = "Older",
                createdAtMillis = 1_000L,
                updatedAtMillis = 1_000L,
                revision = 1L,
            )
        val newer =
            DocumentEntity(
                documentId = "doc-new",
                title = "Newer",
                createdAtMillis = 1_000L,
                updatedAtMillis = 5_000L,
                revision = 1L,
            )
        documentDao.upsert(older)
        documentDao.upsert(newer)

        assertEquals(newer, documentDao.latest())
        assertNull(documentDao.getById("missing"))
        assertNull(pageDao.getById("missing"))
    }

    @Test
    fun deleteForPageRemovesOnlyThatPagesObjects() {
        documentDao.upsert(DocumentEntity("doc-1", "Notes", 1_000L, 1_000L, 1L))
        pageDao.upsert(PageEntity("page-a", "doc-1", "Page A", 1L, 1_000L, 1_000L))
        pageDao.upsert(PageEntity("page-b", "doc-1", "Page B", 1L, 1_000L, 1_000L))
        objectDao.upsertAll(
            listOf(objectEntity("obj-a1", "page-a"), objectEntity("obj-a2", "page-a")),
        )
        val objectsB = listOf(objectEntity("obj-b1", "page-b"))
        objectDao.upsertAll(objectsB)

        objectDao.deleteForPage("page-a")

        assertTrue(objectDao.getByPage("page-a").isEmpty())
        assertEquals(objectsB, objectDao.getByPage("page-b"))
    }

    private fun objectEntity(
        objectId: String,
        pageId: String,
    ) = ObjectEntity(
        objectId = objectId,
        pageId = pageId,
        objectType = "INK",
        payloadRef = null,
        zIndex = 0,
        minX = 0f,
        minY = 0f,
        maxX = 1f,
        maxY = 1f,
        rotationDegrees = 0f,
        scaleX = 1f,
        scaleY = 1f,
        createdAtMillis = 1_000L,
        updatedAtMillis = 1_000L,
        revision = 1L,
    )
}
