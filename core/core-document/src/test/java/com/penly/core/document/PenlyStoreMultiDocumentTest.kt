package com.penly.core.document

import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.InkObject
import com.penly.core.model.ObjectId
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.storage.InMemoryContentStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PenlyStoreMultiDocumentTest {
    private val store = InMemoryContentStore()
    private val penlyStore = PenlyStore(store)

    @Test
    fun listDocuments_returnsAllSavedDocuments() {
        val docA = singleInkPage("doc-a", "Doc A", "ink-a")
        val docB = singleInkPage("doc-b", "Doc B", "ink-b")

        penlyStore.save(docA)
        penlyStore.save(docB)

        val ids = penlyStore.listDocuments()
        assertEquals(setOf(DocumentId("doc-a"), DocumentId("doc-b")), ids.toSet())
        assertEquals(2, ids.size)
    }

    @Test
    fun loadingOneDocument_doesNotSeeTheOthersPages() {
        penlyStore.save(singleInkPage("doc-a", "Doc A", "ink-a"))
        penlyStore.save(singleInkPage("doc-b", "Doc B", "ink-b"))

        val result = penlyStore.load(DocumentId("doc-a"))
        assertTrue("expected Success, got $result", result is LoadResult.Success)
        val loaded = (result as LoadResult.Success).document

        assertEquals(DocumentId("doc-a"), loaded.documentId)
        assertEquals("Doc A", loaded.title)
        assertEquals(1, loaded.pages.size)
        val objectIds =
            loaded.pages
                .single()
                .objects
                .map { it.objectId.value }
        assertEquals(listOf("ink-a"), objectIds)
    }

    @Test
    fun listDocuments_isEmpty_forEmptyStore() {
        assertTrue(penlyStore.listDocuments().isEmpty())
    }

    private fun singleInkPage(
        documentIdValue: String,
        title: String,
        objectIdValue: String,
    ): Document {
        val documentId = DocumentId(documentIdValue)
        return Document(
            documentId = documentId,
            title = title,
            pages =
                listOf(
                    Page(
                        pageId = PageId("page-$objectIdValue"),
                        documentId = documentId,
                        title = "Page 1",
                        objects =
                            listOf(
                                InkObject(
                                    objectId = ObjectId(objectIdValue),
                                    brushId = "PEN",
                                    colorArgb = 0xFF000000.toInt(),
                                    size = 5f,
                                    opacity = 1f,
                                    payload = byteArrayOf(0x01, 0x02),
                                ),
                            ),
                    ),
                ),
        )
    }
}
