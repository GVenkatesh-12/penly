package com.penly.core.model

import kotlinx.serialization.Serializable

/**
 * The .penly envelope. [files] maps relative path -> "sha256:<hex>" checksum.
 */
@Serializable
data class Manifest(
    val format: String = "penly",
    val formatVersion: Int = 1,
    val minimumReaderVersion: Int = 1,
    val documentId: DocumentId,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val files: Map<String, String> = emptyMap(),
)
