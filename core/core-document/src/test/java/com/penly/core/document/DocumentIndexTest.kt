package com.penly.core.document

import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.InkObject
import com.penly.core.model.ObjectId
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.storage.InMemoryContentStore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentIndexTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun indexRoundTrip_viaDto() {
        val index =
            DocumentIndex(
                documentId = DocumentId("doc-1"),
                title = "My Document",
                revision = 3,
                createdAtMillis = 100L,
                updatedAtMillis = 200L,
                pages =
                    listOf(
                        PageRef(PageId("page-1"), "Page 1", 2, 100L, 150L),
                        PageRef(PageId("page-2"), "Page 2", 1, 120L, 180L),
                    ),
            )

        val encoded = json.encodeToString(DocumentIndex.serializer(), index)
        val decoded = json.decodeFromString(DocumentIndex.serializer(), encoded)

        assertEquals(index, decoded)
        assertTrue(encoded.contains("\"documentId\":\"doc-1\""))
        assertTrue(encoded.contains("\"pageId\":\"page-2\""))
        // The index never carries objects — it only references pages.
        assertFalse(encoded.contains("\"objects\""))
    }

    @Test
    fun storeWritesDocumentIndex_toDocumentJson() {
        val store = InMemoryContentStore()
        val forge = PaperForgeStore(store)
        val documentId = DocumentId("doc-index")
        val document =
            Document(
                documentId = documentId,
                title = "Indexed",
                revision = 4,
                createdAtMillis = 11L,
                updatedAtMillis = 22L,
                pages =
                    listOf(
                        Page(
                            pageId = PageId("page-index"),
                            documentId = documentId,
                            title = "Page 1",
                            objects =
                                listOf(
                                    InkObject(
                                        objectId = ObjectId("ink-index"),
                                        brushId = "MARKER",
                                        colorArgb = 0xFF0077B6.toInt(),
                                        size = 14f,
                                        opacity = 1f,
                                        payload = byteArrayOf(0x01),
                                    ),
                                ),
                            revision = 1,
                            createdAtMillis = 11L,
                            updatedAtMillis = 22L,
                        ),
                    ),
            )

        forge.save(document)

        val bytes =
            store.open("${documentId.value}/document.json")
                ?: throw AssertionError("document.json missing after save")
        val index =
            json.decodeFromString(DocumentIndex.serializer(), bytes.toString(Charsets.UTF_8))
        assertEquals(document.documentId, index.documentId)
        assertEquals(document.title, index.title)
        assertEquals(document.revision, index.revision)
        assertEquals(document.createdAtMillis, index.createdAtMillis)
        assertEquals(document.updatedAtMillis, index.updatedAtMillis)
        val pageRef = index.pages.single()
        assertEquals(document.pages.single().pageId, pageRef.pageId)
        assertEquals(document.pages.single().title, pageRef.title)
        assertEquals(document.pages.single().revision, pageRef.revision)
        assertEquals(document.pages.single().createdAtMillis, pageRef.createdAtMillis)
        assertEquals(document.pages.single().updatedAtMillis, pageRef.updatedAtMillis)
    }
}
