package com.penly.core.document

import com.penly.core.model.DocumentId
import com.penly.core.model.PageId

/**
 * Lightweight library-row projection of a stored document. Built from the
 * `document.json` index only — page payloads are never decoded for the library.
 */
data class DocumentSummary(
    val documentId: DocumentId,
    val title: String,
    val section: String?,
    val favorite: Boolean,
    val trashed: Boolean,
    val pageCount: Int,
    /** Id of the first page (thumbnail source); null only for a malformed empty index. */
    val firstPageId: PageId?,
    val revision: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
