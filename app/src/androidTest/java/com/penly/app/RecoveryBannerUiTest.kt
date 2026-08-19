package com.penly.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.penly.core.common.PenlyIds
import com.penly.core.document.PenlyStore
import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.storage.FileContentStore
import com.penly.feature.editor.editorScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Crash-recovery UX: when an interrupted save left a valid journal on disk, opening the
 * editor must show the "Recovered unsaved changes" banner; a corrupt journal must be
 * ignored silently and never block the note from opening.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryBannerUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "recovery-banner")
    private val contentStore = FileContentStore(dir)
    private val store = PenlyStore(contentStore)

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun interruptedSave_showsRecoveryBanner() {
        val document = saveDocument()
        fabricateJournal(document)

        composeRule.setContent {
            editorScreen(store = store)
        }
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) { bannerVisible() }
    }

    @Test
    fun cleanStore_opensWithoutRecoveryBanner() {
        saveDocument()

        composeRule.setContent {
            editorScreen(store = store)
        }
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) { loadFinished() }
        assertBannerAbsent()
    }

    @Test
    fun corruptJournalResidue_isIgnoredWithoutBanner() {
        val document = saveDocument()
        contentStore.put("${document.documentId.value}/journal/commit.json", "not-a-journal".toByteArray())

        composeRule.setContent {
            editorScreen(store = store)
        }
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) { loadFinished() }
        assertBannerAbsent()
    }

    private fun saveDocument(): Document {
        val now = System.currentTimeMillis()
        val documentId = DocumentId(PenlyIds.newId())
        val page =
            Page(
                pageId = PageId(PenlyIds.newId()),
                documentId = documentId,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        val document =
            Document(
                documentId = documentId,
                title = "Untitled",
                pages = listOf(page),
                revision = 1,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        store.save(document)
        return document
    }

    /**
     * Copies the main files into the journal and writes a valid commit marker, exactly as an
     * interrupted save would leave behind (crash after the commit point, before cleanup).
     */
    private fun fabricateJournal(document: Document) {
        val docDir = document.documentId.value
        val pageId = document.pages.single().pageId
        val pageMain = "$docDir/pages/page-${pageId.value}.bin"
        val indexMain = "$docDir/document.json"
        val pageJournal = "$docDir/journal/pages/page-${pageId.value}.bin"
        val indexJournal = "$docDir/journal/document.json"

        contentStore.put(pageJournal, contentStore.open(pageMain)!!)
        contentStore.put(indexJournal, contentStore.open(indexMain)!!)

        val files =
            listOf(
                pageMain to contentStore.checksum(pageJournal),
                indexMain to contentStore.checksum(indexJournal),
            )
        val filesJson =
            files.joinToString(",") { (path, sum) ->
                "\"$path\":\"$sum\""
            }
        val commit =
            """{"documentId":"${document.documentId.value}","createdAtMillis":1,""" +
                """"updatedAtMillis":2,"files":{$filesJson}}"""
        contentStore.put("$docDir/journal/commit.json", commit.toByteArray())
    }

    /** The load finished when the editor's history is a fresh, empty stack. */
    private fun loadFinished(): Boolean {
        val config = composeRule.onNodeWithText("Undo").fetchSemanticsNode().config
        return config.contains(SemanticsProperties.Disabled)
    }

    private fun bannerVisible(): Boolean {
        val nodes = composeRule.onAllNodesWithText("Recovered unsaved changes").fetchSemanticsNodes()
        return nodes.isNotEmpty()
    }

    private fun bannerAbsent(): Boolean {
        val nodes = composeRule.onAllNodesWithText("Recovered unsaved changes").fetchSemanticsNodes()
        return nodes.isEmpty()
    }

    private fun assertBannerAbsent() {
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) { bannerAbsent() }
    }

    private companion object {
        const val LOAD_TIMEOUT_MILLIS: Long = 10_000L
    }
}
