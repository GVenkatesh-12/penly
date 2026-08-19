package com.penly.core.document

import com.penly.core.geometry.Rect
import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.ImageObject
import com.penly.core.model.ObjectId
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.model.TextObject
import com.penly.core.storage.InMemoryContentStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Workflow-level store tests: the sequences of save/load the editor actually performs in a
 * real session (edit-save cycles, empty pages, missing assets, rapid saves), asserting the
 * user-visible contract: content survives, nothing is lost, nothing unexpected blocks.
 */
class PenlyStoreWorkflowTest {
    private val store = InMemoryContentStore()
    private val penlyStore = PenlyStore(store)

    @Test
    fun repeatedEditSaveCycles_accumulateWithoutLoss() {
        val documentId = DocumentId("doc-cycles")
        penlyStore.save(emptyDocument(documentId))

        // Simulate the editor's full-page rewrite after each mutation: load, add one text
        // object, save. Twenty cycles must accumulate twenty objects, in order.
        repeat(20) { cycle ->
            val loaded = (penlyStore.load(documentId) as LoadResult.Success).document
            val page = loaded.pages.single()
            val extra =
                TextObject(
                    objectId = ObjectId("text-$cycle"),
                    bounds = Rect(0f, cycle * 10f, 100f, cycle * 10f + 30f),
                    text = "note $cycle",
                    fontSize = 20f,
                    colorArgb = 0xFF000000.toInt(),
                )
            penlyStore.save(
                loaded.copy(
                    pages = listOf(page.copy(objects = page.objects + extra, revision = page.revision + 1)),
                    revision = loaded.revision + 1,
                ),
            )
        }

        val final = (penlyStore.load(documentId) as LoadResult.Success).document
        val objects = final.pages.single().objects
        assertEquals(20, objects.size)
        assertEquals("note 0", (objects[0] as TextObject).text)
        assertEquals("note 19", (objects[19] as TextObject).text)
    }

    @Test
    fun emptyPage_roundTripsCleanly() {
        val documentId = DocumentId("doc-empty")
        penlyStore.save(emptyDocument(documentId))

        val result = penlyStore.load(documentId)
        assertTrue("expected Success, got $result", result is LoadResult.Success)
        val success = result as LoadResult.Success
        assertTrue("expected no warnings, got ${success.warnings}", success.warnings.isEmpty())
        val objectCount =
            success
                .document
                .pages
                .single()
                .objects
                .size
        assertEquals(0, objectCount)
    }

    @Test
    fun missingAsset_degradesToWarningNotFailure() {
        val documentId = DocumentId("doc-asset")
        penlyStore.putAsset(documentId, "photo.img", byteArrayOf(0x01, 0x02, 0x03))
        val base = emptyDocument(documentId)
        val image =
            ImageObject(
                objectId = ObjectId("img-1"),
                bounds = Rect(0f, 0f, 10f, 10f),
                payloadRef = "assets/photo.img",
                mimeType = "image/png",
            )
        val page = base.pages.single()
        penlyStore.save(base.copy(pages = listOf(page.copy(objects = listOf(image)))))

        // The image file disappears (e.g. user cleared app cache): the note must still open.
        store.delete("${documentId.value}/assets/photo.img")

        val result = penlyStore.load(documentId)
        assertTrue("expected Success, got $result", result is LoadResult.Success)
        val success = result as LoadResult.Success
        assertTrue("expected an asset warning", success.warnings.any { it.contains("asset") })
        val objectCount =
            success
                .document
                .pages
                .single()
                .objects
                .size
        assertEquals(1, objectCount)
    }

    @Test
    fun listDocuments_returnsOnlyManifestBearingDirectories() {
        penlyStore.save(emptyDocument(DocumentId("aaa-first")))
        penlyStore.save(emptyDocument(DocumentId("bbb-second")))
        // Orphan directory with files but no manifest: must not be listed.
        store.put("zzz-orphan/stray.bin", byteArrayOf(0x01))

        val ids = penlyStore.listDocuments().map { it.value }
        assertEquals(listOf("aaa-first", "bbb-second"), ids)
    }

    @Test
    fun rapidSequentialSaves_lastStateWinsAndLeavesNoResidue() {
        val documentId = DocumentId("doc-rapid")
        repeat(25) { index ->
            penlyStore.save(emptyDocument(documentId).copy(title = "title-$index", revision = index.toLong()))
        }

        val loaded = (penlyStore.load(documentId) as LoadResult.Success).document
        assertEquals("title-24", loaded.title)
        assertEquals(24L, loaded.revision)
        assertTrue("journal must be cleaned up", store.list(documentId.value).none { it.contains("journal") })
    }

    private fun emptyDocument(documentId: DocumentId): Document {
        val now = 1000L
        return Document(
            documentId = documentId,
            title = "Untitled",
            pages =
                listOf(
                    Page(
                        pageId = PageId("page-${documentId.value}"),
                        documentId = documentId,
                        title = "Page 1",
                        objects = emptyList(),
                        revision = 1,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    ),
                ),
            revision = 1,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    }
}
