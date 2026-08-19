package com.penly.core.document

import com.penly.core.geometry.Rect
import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.InkObject
import com.penly.core.model.ObjectId
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.storage.ContentStore
import com.penly.core.storage.InMemoryContentStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 exit criterion: automated fault injection demonstrates that common crash/power-loss
 * simulations never lose committed content. A [CrashInjectingStore] wraps a real store and
 * throws at a configurable mutation, simulating process death at every point of a save; loading
 * must then yield either the previous committed state or the fully committed new state — never
 * a failure, never a partial document.
 */
class PenlyStoreCrashSafetyTest {
    private val delegate = InMemoryContentStore()
    private val crashStore = CrashInjectingStore(delegate)
    private val penlyStore = PenlyStore(crashStore)

    @Test
    fun crashAtEveryPointOfSave_neverLosesCommittedContent() {
        val first = documentWith("first", 1)
        penlyStore.save(first)
        val second = documentWith("second", 2)

        for (crashAfter in 0..16) {
            crashStore.crashAfter = crashAfter
            try {
                penlyStore.save(second)
            } catch (e: SimulatedCrash) {
                // process death simulated at mutation #crashAfter
            } finally {
                crashStore.crashAfter = -1
            }

            val result = penlyStore.load(first.documentId)
            assertTrue(
                "crashAfter=$crashAfter: expected Success, got $result",
                result is LoadResult.Success,
            )
            val loaded = (result as LoadResult.Success).document
            val expectedRevisions = setOf(first.revision, second.revision)
            assertTrue(
                "crashAfter=$crashAfter: revision ${loaded.revision} not in $expectedRevisions",
                loaded.revision in expectedRevisions,
            )
            val page = loaded.pages.single()
            assertEquals(
                "crashAfter=$crashAfter: page count must match a committed state",
                page.objects.size,
                if (loaded.revision == second.revision) 2 else 1,
            )
        }
    }

    @Test
    fun crashDuringJournalStaging_keepsPreviousState() {
        val first = documentWith("first", 1)
        penlyStore.save(first)
        crashStore.crashAfter = 0 // first mutation is a journal copy

        try {
            penlyStore.save(documentWith("second", 2))
        } catch (e: SimulatedCrash) {
            // expected
        }
        crashStore.crashAfter = -1 // the app "restarted"; loads must run without crashes

        val result = penlyStore.load(first.documentId)
        assertTrue("expected Success, got $result", result is LoadResult.Success)
        val success = result as LoadResult.Success
        assertFalse("no recovery without a committed journal", success.recovered)
        assertEquals("previous committed state must survive", first.revision, success.document.revision)
        val page = success.document.pages.single()
        assertEquals(1, page.objects.size)
    }

    @Test
    fun crashAfterJournalCommit_replaysNewState() {
        val first = documentWith("first", 1)
        penlyStore.save(first)
        val second = documentWith("second", 2)
        crashStore.crashAfter = 3 // journal page+index+marker written, then death on main write

        try {
            penlyStore.save(second)
        } catch (e: SimulatedCrash) {
            // expected
        }
        crashStore.crashAfter = -1 // the app "restarted"; loads must run without crashes

        val result = penlyStore.load(first.documentId)
        assertTrue("expected Success, got $result", result is LoadResult.Success)
        val success = result as LoadResult.Success
        assertTrue("journal replay must be reported", success.recovered)
        val page = success.document.pages.single()
        assertEquals("committed new state must be replayed", second.revision, success.document.revision)
        assertEquals(2, page.objects.size)
        assertEquals("second", success.document.title)
    }

    @Test
    fun crashDuringManifestWrite_replaysNewState() {
        val first = documentWith("first", 1)
        penlyStore.save(first)
        val second = documentWith("second", 2)
        crashStore.crashAfter = 5 // manifest put is the 6th mutation (0-based)

        try {
            penlyStore.save(second)
        } catch (e: SimulatedCrash) {
            // expected
        }
        crashStore.crashAfter = -1 // the app "restarted"; loads must run without crashes

        val result = penlyStore.load(first.documentId)
        assertTrue("expected Success, got $result", result is LoadResult.Success)
        val success = result as LoadResult.Success
        val page = success.document.pages.single()
        assertTrue(success.recovered)
        assertEquals(second.revision, success.document.revision)
        assertEquals(2, page.objects.size)
    }

    @Test
    fun crashAfterManifestWrite_stateIsFullyCommitted() {
        val first = documentWith("first", 1)
        penlyStore.save(first)
        val second = documentWith("second", 2)
        crashStore.crashAfter = 6 // manifest already durable; death during journal cleanup

        try {
            penlyStore.save(second)
        } catch (e: SimulatedCrash) {
            // expected
        }
        crashStore.crashAfter = -1 // the app "restarted"; loads must run without crashes

        val result = penlyStore.load(first.documentId)
        assertTrue("expected Success, got $result", result is LoadResult.Success)
        val success = result as LoadResult.Success
        val page = success.document.pages.single()
        assertEquals("committed state must not regress", second.revision, success.document.revision)
        assertEquals(2, page.objects.size)
    }

    @Test
    fun corruptJournal_isIgnoredAndPreviousStateLoads() {
        val first = documentWith("first", 1)
        penlyStore.save(first)

        // A stale journal whose marker lists a copy that no longer exists must be ignored.
        val docDir = first.documentId.value
        delegate.put("$docDir/journal/pages/page-stale.bin", byteArrayOf(0x01))
        delegate.put(
            "$docDir/journal/commit.json",
            (
                """{"documentId":{"value":"${first.documentId.value}"},"createdAtMillis":0,""" +
                    """"updatedAtMillis":0,"files":{"$docDir/pages/page-stale.bin":"sha256:deadbeef"}}"""
            ).toByteArray(Charsets.UTF_8),
        )

        val result = penlyStore.load(first.documentId)
        assertTrue("expected Success, got $result", result is LoadResult.Success)
        val success = result as LoadResult.Success
        assertFalse("stale journal must not trigger recovery", success.recovered)
        assertEquals(first.revision, success.document.revision)
    }

    @Test
    fun corruptAsset_loadsWithWarningInsteadOfFailing() {
        val document = documentWith("assets", 1)
        penlyStore.save(document)
        penlyStore.putAsset(document.documentId, "pic.img", byteArrayOf(0x01, 0x02))
        penlyStore.save(document) // manifest now lists the asset
        delegate.put("${document.documentId.value}/assets/pic.img", byteArrayOf(0xFF.toByte(), 0xFF.toByte()))

        val result = penlyStore.load(document.documentId)
        assertTrue("expected Success, got $result", result is LoadResult.Success)
        val success = result as LoadResult.Success
        assertTrue(
            "asset corruption must be reported as a warning, got ${success.warnings}",
            success.warnings.any { it.contains("assets/pic.img") },
        )
        assertEquals(document.revision, success.document.revision)
    }

    @Test
    fun successfulSave_leavesNoJournalResidue() {
        val document = documentWith("clean", 1)
        penlyStore.save(document)
        penlyStore.save(documentWith("clean", 2))

        val children = delegate.list(document.documentId.value)
        assertFalse("journal must be cleaned up, got $children", children.any { it.contains("journal") })
    }

    @Test
    fun recovery_thenNextSave_cleansJournal() {
        val first = documentWith("first", 1)
        penlyStore.save(first)
        crashStore.crashAfter = 4
        try {
            penlyStore.save(documentWith("second", 2))
        } catch (e: SimulatedCrash) {
            // expected
        }
        crashStore.crashAfter = -1

        val recovered = penlyStore.load(first.documentId) as LoadResult.Success
        assertTrue(recovered.recovered)

        penlyStore.save(recovered.document)
        val reloaded = penlyStore.load(first.documentId) as LoadResult.Success
        assertFalse("no recovery after a clean save", reloaded.recovered)
        assertFalse(
            "journal must be gone after a clean save",
            delegate.list(first.documentId.value).any { it.contains("journal") },
        )
    }

    private fun documentWith(
        title: String,
        strokeCount: Int,
    ): Document {
        val documentId = DocumentId("crash-doc")
        val pageId = PageId("crash-page")
        val objects =
            (1..strokeCount).map { i ->
                InkObject(
                    objectId = ObjectId("ink-$i"),
                    bounds = Rect(i.toFloat(), 0f, i + 10f, 10f),
                    payloadRef = "ink-$i",
                    brushId = "PEN",
                    colorArgb = 0xFF000000.toInt(),
                    size = 5f,
                    opacity = 1f,
                    payload = byteArrayOf(i.toByte()),
                )
            }
        val revision = strokeCount.toLong()
        return Document(
            documentId = documentId,
            title = title,
            pages =
                listOf(
                    Page(
                        pageId = pageId,
                        documentId = documentId,
                        title = "Page 1",
                        objects = objects,
                        revision = revision,
                        createdAtMillis = 100L,
                        updatedAtMillis = 200L,
                    ),
                ),
            revision = revision,
            createdAtMillis = 100L,
            updatedAtMillis = 200L,
        )
    }
}

/** Simulates process death: every mutation is counted and throws once the budget is spent. */
private class CrashInjectingStore(
    private val delegate: ContentStore,
) : ContentStore {
    var crashAfter: Int = -1
    private var mutations = 0

    override fun put(
        path: String,
        bytes: ByteArray,
    ) {
        maybeCrash()
        delegate.put(path, bytes)
    }

    override fun open(path: String): ByteArray? = delegate.open(path)

    override fun move(
        from: String,
        to: String,
    ) {
        maybeCrash()
        delegate.move(from, to)
    }

    override fun delete(path: String) {
        maybeCrash()
        delegate.delete(path)
    }

    override fun exists(path: String): Boolean = delegate.exists(path)

    override fun checksum(path: String): String = delegate.checksum(path)

    override fun list(dir: String): List<String> = delegate.list(dir)

    private fun maybeCrash() {
        if (crashAfter >= 0 && mutations++ >= crashAfter) {
            throw SimulatedCrash()
        }
    }
}

/** Thrown in place of process death; callers must treat it as "the app was killed". */
private class SimulatedCrash : RuntimeException("simulated process death")
