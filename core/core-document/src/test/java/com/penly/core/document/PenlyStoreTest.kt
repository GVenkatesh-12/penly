package com.penly.core.document

import com.penly.core.geometry.Rect
import com.penly.core.geometry.Transform
import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.InkObject
import com.penly.core.model.Manifest
import com.penly.core.model.ObjectId
import com.penly.core.model.OpaqueObject
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.model.PageTemplate
import com.penly.core.model.TextObject
import com.penly.core.storage.InMemoryContentStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class PenlyStoreTest {
    private val store = InMemoryContentStore()
    private val penlyStore = PenlyStore(store)

    @Test
    fun roundTrip_preservesObjectsIncludingPayloadBytes() {
        val inkPayload1 = byteArrayOf(0x01, 0x02, 0x03, 0x7F.toByte(), 0x80.toByte(), 0xFF.toByte())
        val inkPayload2 = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val opaquePayload = byteArrayOf(0x0A, 0x0B, 0x0C)

        val ink1 =
            InkObject(
                objectId = ObjectId("ink-1"),
                transform = Transform.IDENTITY,
                bounds = Rect(1f, 2f, 31f, 42f),
                zIndex = 1,
                visibility = false,
                createdAtMillis = 111L,
                updatedAtMillis = 222L,
                revision = 3,
                payloadRef = "ink-1",
                brushId = "PEN",
                colorArgb = 0xFF112233.toInt(),
                size = 5f,
                opacity = 1f,
                payload = inkPayload1,
            )
        val ink2 =
            InkObject(
                objectId = ObjectId("ink-2"),
                bounds = Rect(0f, 0f, 12f, 7f),
                zIndex = 0,
                createdAtMillis = 333L,
                updatedAtMillis = 444L,
                revision = 1,
                payloadRef = "ink-2",
                brushId = "HIGHLIGHTER",
                colorArgb = 0xFFFFC300.toInt(),
                size = 24f,
                opacity = 0.5f,
                payload = inkPayload2,
            )
        val text =
            TextObject(
                objectId = ObjectId("text-1"),
                bounds = Rect(10f, 10f, 210f, 60f),
                createdAtMillis = 555L,
                updatedAtMillis = 666L,
                revision = 2,
                payloadRef = null,
                text = "hello",
                fontSize = 24f,
                colorArgb = 0xFF445566.toInt(),
            )
        val opaque =
            OpaqueObject(
                objectId = ObjectId("opaque-1"),
                bounds = Rect(1f, 2f, 3f, 4f),
                createdAtMillis = 777L,
                updatedAtMillis = 888L,
                revision = 4,
                payloadRef = null,
                kind = "future-widget",
                payload = opaquePayload,
            )

        val document =
            Document(
                documentId = DocumentId("doc-1"),
                title = "My Page",
                pages =
                    listOf(
                        Page(
                            pageId = PageId("page-1"),
                            documentId = DocumentId("doc-1"),
                            title = "Page 1",
                            objects = listOf(ink1, text, opaque, ink2),
                            revision = 7,
                            createdAtMillis = 100L,
                            updatedAtMillis = 999L,
                        ),
                    ),
                revision = 9,
                createdAtMillis = 100L,
                updatedAtMillis = 999L,
            )

        penlyStore.save(document)
        val result = penlyStore.load(DocumentId("doc-1"))

        assertTrue("expected Success, got $result", result is LoadResult.Success)
        val success = result as LoadResult.Success
        assertTrue("expected no warnings, got ${success.warnings}", success.warnings.isEmpty())

        val loaded = success.document
        assertEquals(document.documentId, loaded.documentId)
        assertEquals(document.title, loaded.title)
        assertEquals(document.revision, loaded.revision)
        assertEquals(document.createdAtMillis, loaded.createdAtMillis)
        assertEquals(document.updatedAtMillis, loaded.updatedAtMillis)

        val loadedPage = loaded.pages.single()
        assertEquals(document.pages.single().pageId, loadedPage.pageId)
        assertEquals(document.pages.single().title, loadedPage.title)
        assertEquals(7L, loadedPage.revision)
        assertEquals(100L, loadedPage.createdAtMillis)
        assertEquals(999L, loadedPage.updatedAtMillis)

        val objects = loadedPage.objects
        assertEquals(4, objects.size)

        val loadedInk1 = objects[0] as InkObject
        assertEquals(ink1.copy(payload = null), loadedInk1.copy(payload = null))
        assertNotNull(loadedInk1.payload)
        assertTrue(loadedInk1.payload!!.contentEquals(inkPayload1))

        val loadedText = objects[1] as TextObject
        assertEquals(text, loadedText)

        val loadedOpaque = objects[2] as OpaqueObject
        assertEquals(opaque.copy(payload = null), loadedOpaque.copy(payload = null))
        assertEquals("future-widget", loadedOpaque.kind)
        assertNotNull(loadedOpaque.payload)
        assertTrue(loadedOpaque.payload!!.contentEquals(opaquePayload))

        val loadedInk2 = objects[3] as InkObject
        assertEquals(ink2.copy(payload = null), loadedInk2.copy(payload = null))
        assertTrue(loadedInk2.payload!!.contentEquals(inkPayload2))
    }

    @Test
    fun checksumMismatch_returnsFailure() {
        val document = singleInkPageDocument()
        penlyStore.save(document)

        val pageId =
            document.pages
                .single()
                .pageId.value
        val pagePath = "${document.documentId.value}/pages/page-$pageId.bin"
        store.put(pagePath, byteArrayOf(0x00, 0x01, 0x02, 0x03))

        val result = penlyStore.load(document.documentId)
        assertTrue("expected Failure, got $result", result is LoadResult.Failure)
        assertTrue((result as LoadResult.Failure).reason.contains("checksum mismatch"))
    }

    @Test
    fun missingManifest_returnsFailure() {
        val result = penlyStore.load(DocumentId("does-not-exist"))
        assertTrue("expected Failure, got $result", result is LoadResult.Failure)
    }

    @Test
    fun unknownRecordType_decodesAsOpaqueObject_andResavesLosslessly() {
        val documentId = DocumentId("opaque-doc")
        val pageId = PageId("opaque-page")
        val rawJson =
            """{"type":"future-widget","objectId":"widget-1","kind":"future-widget",""" +
                """"createdAtMillis":5,"updatedAtMillis":6,"revision":2}"""
        val jsonBytes = rawJson.toByteArray(Charsets.UTF_8)
        val payload = byteArrayOf(0x0A, 0x0B, 0x0C)
        store.put("${documentId.value}/pages/page-${pageId.value}.bin", craftedPageFile(jsonBytes, payload))
        store.put(
            "${documentId.value}/document.json",
            Json
                .encodeToString(
                    DocumentIndex.serializer(),
                    DocumentIndex(
                        documentId = documentId,
                        title = "Opaque Doc",
                        revision = 0,
                        createdAtMillis = 5L,
                        updatedAtMillis = 6L,
                        pages = listOf(PageRef(pageId, "Page 1", 0, 5L, 6L)),
                    ),
                ).toByteArray(Charsets.UTF_8),
        )
        val indexPath = "${documentId.value}/document.json"
        val pagePath = "${documentId.value}/pages/page-${pageId.value}.bin"
        val manifest =
            Manifest(
                documentId = documentId,
                createdAtMillis = 5L,
                updatedAtMillis = 6L,
                files =
                    linkedMapOf(
                        indexPath to store.checksum(indexPath),
                        pagePath to store.checksum(pagePath),
                    ),
            )
        store.put(
            "${documentId.value}/manifest.json",
            Json.encodeToString(Manifest.serializer(), manifest).toByteArray(Charsets.UTF_8),
        )

        val result = penlyStore.load(documentId)
        assertTrue("expected Success, got $result", result is LoadResult.Success)
        val success = result as LoadResult.Success
        assertFalse("expected an opaque fallback warning", success.warnings.isEmpty())
        val opaque =
            success.document.pages
                .single()
                .objects
                .single() as OpaqueObject
        assertEquals("future-widget", opaque.kind)
        assertEquals("widget-1", opaque.objectId.value)
        assertTrue(opaque.payload!!.contentEquals(payload))

        // Re-saving keeps the opaque data lossless.
        penlyStore.save(success.document)
        val reloaded = penlyStore.load(documentId) as LoadResult.Success
        val opaqueAgain =
            reloaded.document.pages
                .single()
                .objects
                .single() as OpaqueObject
        assertEquals("future-widget", opaqueAgain.kind)
        assertEquals("widget-1", opaqueAgain.objectId.value)
        assertTrue(opaqueAgain.payload!!.contentEquals(payload))
    }

    @Test
    fun roundTrip_preservesTemplateAndLibraryFields() {
        val documentId = DocumentId("doc-library")
        val document =
            Document(
                documentId = documentId,
                title = "Field notes",
                favorite = true,
                trashed = false,
                section = "Research",
                pages =
                    listOf(
                        Page(
                            pageId = PageId("page-grid"),
                            documentId = documentId,
                            title = "Page 1",
                            template = PageTemplate.GRID,
                        ),
                        Page(
                            pageId = PageId("page-cornell"),
                            documentId = documentId,
                            title = "Page 2",
                            template = PageTemplate.CORNELL,
                        ),
                    ),
            )

        penlyStore.save(document)
        val loaded = penlyStore.load(documentId)

        assertTrue(loaded is LoadResult.Success)
        val restored = (loaded as LoadResult.Success).document
        assertEquals(document.favorite, restored.favorite)
        assertEquals(document.trashed, restored.trashed)
        assertEquals(document.section, restored.section)
        assertEquals(PageTemplate.GRID, restored.pages[0].template)
        assertEquals(PageTemplate.CORNELL, restored.pages[1].template)
    }

    @Test
    fun listSummaries_reflectsIndexMetadata_andSortsMostRecentFirst() {
        penlyStore.save(
            Document(
                documentId = DocumentId("doc-old"),
                title = "Old",
                section = "Work",
                pages = listOf(Page(pageId = PageId("p1"), documentId = DocumentId("doc-old"))),
                updatedAtMillis = 100L,
            ),
        )
        penlyStore.save(
            Document(
                documentId = DocumentId("doc-new"),
                title = "New",
                favorite = true,
                trashed = true,
                pages =
                    listOf(
                        Page(pageId = PageId("p2"), documentId = DocumentId("doc-new")),
                        Page(pageId = PageId("p3"), documentId = DocumentId("doc-new")),
                    ),
                updatedAtMillis = 200L,
            ),
        )

        val summaries = penlyStore.listSummaries()

        assertEquals(listOf("doc-new", "doc-old"), summaries.map { it.documentId.value })
        val newer = summaries[0]
        assertEquals("New", newer.title)
        assertTrue(newer.favorite)
        assertTrue(newer.trashed)
        assertNull(newer.section)
        assertEquals(2, newer.pageCount)
        assertEquals(PageId("p2"), newer.firstPageId)
        val older = summaries[1]
        assertEquals("Work", older.section)
        assertFalse(older.favorite)
        assertEquals(1, older.pageCount)
    }

    @Test
    fun listSummaries_skipsCorruptIndex_butKeepsHealthyDocuments() {
        penlyStore.save(singleInkPageDocument())
        penlyStore.save(Document(documentId = DocumentId("doc-broken"), title = "Broken"))
        store.put("doc-broken/document.json", "{not json".toByteArray(Charsets.UTF_8))

        val summaries = penlyStore.listSummaries()

        assertEquals(listOf("doc-single"), summaries.map { it.documentId.value })
    }

    @Test
    fun thumbnails_roundTripThroughTheStore() {
        val documentId = DocumentId("doc-thumb")
        val pageId = PageId("page-thumb")

        assertNull(penlyStore.openThumbnail(documentId, pageId))
        penlyStore.putThumbnail(documentId, pageId, byteArrayOf(1, 2, 3))

        assertTrue(penlyStore.openThumbnail(documentId, pageId)!!.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun deleteDocument_removesEveryFile_underTheDocumentDirectory() {
        penlyStore.save(singleInkPageDocument())
        val other =
            Document(
                documentId = DocumentId("doc-other"),
                title = "Other",
                pages = listOf(Page(pageId = PageId("page-o"), documentId = DocumentId("doc-other"))),
            )
        penlyStore.save(other)
        penlyStore.putThumbnail(DocumentId("doc-single"), PageId("page-single"), byteArrayOf(9))

        penlyStore.deleteDocument(DocumentId("doc-single"))

        assertTrue(store.list("doc-single").isEmpty())
        assertTrue(penlyStore.listDocuments().none { it.value == "doc-single" })
        assertTrue(penlyStore.load(DocumentId("doc-single")) is LoadResult.Failure)
        assertEquals("Other", penlyStore.load(DocumentId("doc-other")).let { (it as LoadResult.Success).document.title })
    }

    private fun singleInkPageDocument(): Document {
        val documentId = DocumentId("doc-single")
        return Document(
            documentId = documentId,
            title = "Single",
            pages =
                listOf(
                    Page(
                        pageId = PageId("page-single"),
                        documentId = documentId,
                        title = "Page 1",
                        objects =
                            listOf(
                                InkObject(
                                    objectId = ObjectId("ink-single"),
                                    brushId = "PEN",
                                    colorArgb = 0xFF000000.toInt(),
                                    size = 5f,
                                    opacity = 1f,
                                    payload = byteArrayOf(0x01),
                                ),
                            ),
                    ),
                ),
        )
    }

    private fun craftedPageFile(
        jsonBytes: ByteArray,
        payload: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(PenlyFormat.MAGIC)
        out.write(PenlyFormat.VERSION.toInt())
        writeInt(out, 1)
        out.write(0x00)
        val idBytes = "widget-1".toByteArray(Charsets.UTF_8)
        writeShort(out, idBytes.size)
        out.write(idBytes)
        writeInt(out, jsonBytes.size)
        out.write(jsonBytes)
        writeInt(out, payload.size)
        out.write(payload)
        return out.toByteArray()
    }

    private fun writeShort(
        out: ByteArrayOutputStream,
        value: Int,
    ) {
        out.write(value ushr 8 and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeInt(
        out: ByteArrayOutputStream,
        value: Int,
    ) {
        out.write(value ushr 24 and 0xFF)
        out.write(value ushr 16 and 0xFF)
        out.write(value ushr 8 and 0xFF)
        out.write(value and 0xFF)
    }
}
