package com.penly.core.document

import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.PageId
import kotlinx.serialization.Serializable

/**
 * The `document.json` payload: document metadata plus page references, without any page
 * objects (objects and their payloads live in the binary page files).
 *
 * Kept internal — it is a storage format detail, not part of the public API.
 */
@Serializable
internal data class DocumentIndex(
    val documentId: DocumentId,
    val title: String,
    val revision: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val pages: List<PageRef>,
) {
    companion object {
        fun from(document: Document): DocumentIndex =
            DocumentIndex(
                documentId = document.documentId,
                title = document.title,
                revision = document.revision,
                createdAtMillis = document.createdAtMillis,
                updatedAtMillis = document.updatedAtMillis,
                pages =
                    document.pages.map { page ->
                        PageRef(
                            pageId = page.pageId,
                            title = page.title,
                            revision = page.revision,
                            createdAtMillis = page.createdAtMillis,
                            updatedAtMillis = page.updatedAtMillis,
                        )
                    },
            )
    }
}

/** A page's metadata entry inside a [DocumentIndex]. */
@Serializable
internal data class PageRef(
    val pageId: PageId,
    val title: String,
    val revision: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
