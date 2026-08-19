package com.penly.core.document

import com.penly.core.model.DocumentId
import kotlinx.serialization.Serializable

/**
 * The journal commit marker: written last inside `<documentId>/journal/` after every pending
 * page/index copy is durable. Its presence with all listed files intact means the interrupted
 * save can be replayed ([PenlyStore] recovery). [files] maps main-file path -> checksum of the
 * journal copy (identical bytes), so recovery can rebuild the manifest without re-encoding.
 *
 * Kept internal — it is a storage format detail, not part of the public API.
 */
@Serializable
internal data class JournalCommit(
    val documentId: DocumentId,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val files: Map<String, String>,
)
