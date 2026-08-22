package com.penly.core.document

import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.storage.InMemoryContentStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * [PenlyStore] documents may be saved from a background dispatcher while another component
 * loads (page open, test polling). save() and load() both touch the crash-safety journal, so
 * they are serialized by an internal lock; this suite hammers both concurrently and fails if
 * any call observes a half-finished commit protocol (e.g. checksumming a journal copy that a
 * concurrent save has just cleaned up).
 */
class PenlyStoreConcurrencyTest {
    @Test
    fun concurrentSaveAndLoad_neverThrowsAndAlwaysLoadsCommittedState() {
        val document = freshDocument()
        val store = PenlyStore(InMemoryContentStore())
        store.save(document)

        val errors = Collections.synchronizedList(mutableListOf<Exception>())
        val threads =
            (0 until THREADS).map { index ->
                Thread {
                    repeat(ITERATIONS_PER_THREAD) { round ->
                        try {
                            if (index % 2 == 0) {
                                store.save(document.copy(revision = round.toLong()))
                            } else {
                                val result = store.load(document.documentId)
                                assertTrue(
                                    "load during concurrent saves must succeed",
                                    result is LoadResult.Success,
                                )
                            }
                        } catch (e: Exception) {
                            errors += e
                        }
                    }
                }
            }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertTrue("concurrent save/load threw: ${errors.take(3)}", errors.isEmpty())
        val final = assertLoadSuccess(store.load(document.documentId))
        assertEquals("final committed state must be readable", document.documentId, final.document.documentId)
    }

    private fun assertLoadSuccess(result: LoadResult): LoadResult.Success {
        assertTrue("expected LoadResult.Success, got $result", result is LoadResult.Success)
        return result as LoadResult.Success
    }

    private fun freshDocument(): Document {
        val documentId = DocumentId("concurrency-doc")
        return Document(
            documentId = documentId,
            title = "Concurrency",
            pages =
                listOf(
                    Page(
                        pageId = PageId("concurrency-page"),
                        documentId = documentId,
                        createdAtMillis = 1L,
                        updatedAtMillis = 1L,
                    ),
                ),
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
        )
    }

    private companion object {
        const val THREADS: Int = 4

        const val ITERATIONS_PER_THREAD: Int = 60
    }
}
