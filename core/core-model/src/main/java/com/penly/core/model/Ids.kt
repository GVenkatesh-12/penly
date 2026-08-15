package com.penly.core.model

import kotlinx.serialization.Serializable

/**
 * Identifiers are UUID strings generated via [com.penly.core.common.PenlyIds.newId].
 *
 * Value classes serialize as plain strings in kotlinx.serialization.
 */
@Serializable
@JvmInline
value class ObjectId(
    val value: String,
)

@Serializable
@JvmInline
value class PageId(
    val value: String,
)

@Serializable
@JvmInline
value class DocumentId(
    val value: String,
)
