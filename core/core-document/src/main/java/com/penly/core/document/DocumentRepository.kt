package com.penly.core.document

import com.penly.core.common.PenlyIds
import com.penly.core.database.DocumentDao
import com.penly.core.database.DocumentEntity
import com.penly.core.database.ObjectDao
import com.penly.core.database.ObjectEntity
import com.penly.core.database.PageDao
import com.penly.core.database.PageEntity
import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.Page
import com.penly.core.model.PageId

/**
 * Application-facing document service: creates documents, persists them through
 * [PenlyStore], and mirrors the document graph into Room metadata (documents, pages,
 * and per-object spatial index rows) for querying.
 */
class DocumentRepository(
    private val penlyStore: PenlyStore,
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val objectDao: ObjectDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Creates a new empty document with a single blank page, stamped with the current time. */
    fun createDocument(title: String = "Untitled"): Document {
        val now = clock()
        val documentId = DocumentId(PenlyIds.newId())
        val pageId = PageId(PenlyIds.newId())
        val page =
            Page(
                pageId = pageId,
                documentId = documentId,
                title = "Page 1",
                objects = emptyList(),
                revision = 0,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        return Document(
            documentId = documentId,
            title = title,
            pages = listOf(page),
            revision = 0,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    }

    /**
     * Persists [document], bumping its revision and update time (and each page's) on every save,
     * then mirrors the metadata into Room. Returns the saved (bumped) document.
     */
    fun saveDocument(document: Document): Document {
        val now = clock()
        val savedPages =
            document.pages.map { page ->
                page.copy(
                    revision = page.revision + 1,
                    updatedAtMillis = now,
                )
            }
        val saved =
            document.copy(
                pages = savedPages,
                revision = document.revision + 1,
                updatedAtMillis = now,
            )
        penlyStore.save(saved)
        upsertMetadata(saved)
        return saved
    }

    /** Loads [documentId] and refreshes Room metadata from the loaded document on success. */
    fun loadDocument(documentId: DocumentId): LoadResult {
        val result = penlyStore.load(documentId)
        if (result is LoadResult.Success) {
            upsertMetadata(result.document)
        }
        return result
    }

    /** Returns the most recently updated document id, or null when the store has no documents. */
    fun latestDocumentId(): DocumentId? = documentDao.latest()?.documentId?.let(::DocumentId)

    private fun upsertMetadata(document: Document) {
        documentDao.upsert(
            DocumentEntity(
                documentId = document.documentId.value,
                title = document.title,
                createdAtMillis = document.createdAtMillis,
                updatedAtMillis = document.updatedAtMillis,
                revision = document.revision,
            ),
        )
        for (page in document.pages) {
            pageDao.upsert(
                PageEntity(
                    pageId = page.pageId.value,
                    documentId = page.documentId.value,
                    title = page.title,
                    revision = page.revision,
                    createdAtMillis = page.createdAtMillis,
                    updatedAtMillis = page.updatedAtMillis,
                ),
            )
            objectDao.deleteForPage(page.pageId.value)
            objectDao.upsertAll(
                page.objects.map { obj ->
                    ObjectEntity(
                        objectId = obj.objectId.value,
                        pageId = page.pageId.value,
                        objectType = obj.type.name,
                        payloadRef = obj.payloadRef,
                        zIndex = obj.zIndex,
                        minX = obj.bounds.left,
                        minY = obj.bounds.top,
                        maxX = obj.bounds.right,
                        maxY = obj.bounds.bottom,
                        rotationDegrees = obj.transform.rotationDegrees,
                        scaleX = obj.transform.scaleX,
                        scaleY = obj.transform.scaleY,
                        createdAtMillis = obj.createdAtMillis,
                        updatedAtMillis = obj.updatedAtMillis,
                        revision = obj.revision,
                    )
                },
            )
        }
    }
}
