package com.penly.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Page(
    val pageId: PageId,
    val documentId: DocumentId,
    val title: String = "Page 1",
    val objects: List<PageObject> = emptyList(),
    val revision: Long = 0,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

@Serializable
data class Document(
    val documentId: DocumentId,
    val title: String,
    val pages: List<Page> = emptyList(),
    val revision: Long = 0,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)
