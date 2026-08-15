package com.penly.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PageObjectType {
    INK,
    TEXT,
    IMAGE,
    SHAPE,
    EMBEDDED,
    OPAQUE,
}
