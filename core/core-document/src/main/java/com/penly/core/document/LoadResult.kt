package com.penly.core.document

import com.penly.core.model.Document

/**
 * Outcome of loading a document from a [PenlyStore].
 */
sealed interface LoadResult {
    /** The document was loaded; [warnings] lists non-fatal issues (e.g. opaque fallbacks). */
    data class Success(
        val document: Document,
        val warnings: List<String>,
    ) : LoadResult

    /** The document could not be loaded; [reason] describes the failure. */
    data class Failure(
        val reason: String,
    ) : LoadResult
}
