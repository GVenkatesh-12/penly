package com.penly.core.model

import com.penly.core.geometry.Rect
import com.penly.core.geometry.Transform
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Polymorphic root of all objects placed on a page. Common metadata is declared here and
 * repeated as constructor parameters on every subclass; subclasses carry a derived [type]
 * property (transient — the JSON discriminator is the @SerialName) and the binary [payload]
 * is [Transient] — the paperforge page file carries it out-of-band.
 */
@Serializable
sealed class PageObject {
    abstract val objectId: ObjectId
    abstract val transform: Transform
    abstract val bounds: Rect
    abstract val zIndex: Int
    abstract val visibility: Boolean
    abstract val createdAtMillis: Long
    abstract val updatedAtMillis: Long
    abstract val revision: Long
    abstract val payloadRef: String?

    abstract val type: PageObjectType
    abstract val payload: ByteArray?
}

@Serializable
@SerialName("ink")
data class InkObject(
    override val objectId: ObjectId,
    override val transform: Transform = Transform.IDENTITY,
    override val bounds: Rect = Rect(0f, 0f, 0f, 0f),
    override val zIndex: Int = 0,
    override val visibility: Boolean = true,
    override val createdAtMillis: Long = 0L,
    override val updatedAtMillis: Long = 0L,
    override val revision: Long = 0,
    override val payloadRef: String? = null,
    val brushId: String,
    val colorArgb: Int,
    val size: Float,
    val opacity: Float,
    @Transient override val payload: ByteArray? = null,
) : PageObject() {
    // brushId values match PenTool.name: "PEN", "PENCIL", "MARKER", "HIGHLIGHTER".
    @Transient override val type: PageObjectType = PageObjectType.INK
}

@Serializable
@SerialName("text")
data class TextObject(
    override val objectId: ObjectId,
    override val transform: Transform = Transform.IDENTITY,
    override val bounds: Rect = Rect(0f, 0f, 0f, 0f),
    override val zIndex: Int = 0,
    override val visibility: Boolean = true,
    override val createdAtMillis: Long = 0L,
    override val updatedAtMillis: Long = 0L,
    override val revision: Long = 0,
    override val payloadRef: String? = null,
    val text: String,
    val fontSize: Float,
    val colorArgb: Int,
) : PageObject() {
    @Transient override val type: PageObjectType = PageObjectType.TEXT

    @Transient override val payload: ByteArray? = null
}

@Serializable
@SerialName("image")
data class ImageObject(
    override val objectId: ObjectId,
    override val transform: Transform = Transform.IDENTITY,
    override val bounds: Rect = Rect(0f, 0f, 0f, 0f),
    override val zIndex: Int = 0,
    override val visibility: Boolean = true,
    override val createdAtMillis: Long = 0L,
    override val updatedAtMillis: Long = 0L,
    override val revision: Long = 0,
    override val payloadRef: String? = null,
    val mimeType: String,
) : PageObject() {
    // payloadRef points at the stored asset.
    @Transient override val type: PageObjectType = PageObjectType.IMAGE

    @Transient override val payload: ByteArray? = null
}

@Serializable
@SerialName("shape")
data class ShapeObject(
    override val objectId: ObjectId,
    override val transform: Transform = Transform.IDENTITY,
    override val bounds: Rect = Rect(0f, 0f, 0f, 0f),
    override val zIndex: Int = 0,
    override val visibility: Boolean = true,
    override val createdAtMillis: Long = 0L,
    override val updatedAtMillis: Long = 0L,
    override val revision: Long = 0,
    override val payloadRef: String? = null,
    val shapeKind: String,
    val strokeColorArgb: Int,
    val fillColorArgb: Int? = null,
    val strokeWidth: Float,
) : PageObject() {
    @Transient override val type: PageObjectType = PageObjectType.SHAPE

    @Transient override val payload: ByteArray? = null
}

@Serializable
@SerialName("embedded")
data class EmbeddedObject(
    override val objectId: ObjectId,
    override val transform: Transform = Transform.IDENTITY,
    override val bounds: Rect = Rect(0f, 0f, 0f, 0f),
    override val zIndex: Int = 0,
    override val visibility: Boolean = true,
    override val createdAtMillis: Long = 0L,
    override val updatedAtMillis: Long = 0L,
    override val revision: Long = 0,
    override val payloadRef: String? = null,
    val kind: String,
    val mimeType: String,
) : PageObject() {
    @Transient override val type: PageObjectType = PageObjectType.EMBEDDED

    @Transient override val payload: ByteArray? = null
}

@Serializable
@SerialName("opaque")
data class OpaqueObject(
    override val objectId: ObjectId,
    override val transform: Transform = Transform.IDENTITY,
    override val bounds: Rect = Rect(0f, 0f, 0f, 0f),
    override val zIndex: Int = 0,
    override val visibility: Boolean = true,
    override val createdAtMillis: Long = 0L,
    override val updatedAtMillis: Long = 0L,
    override val revision: Long = 0,
    override val payloadRef: String? = null,
    val kind: String,
    @Transient override val payload: ByteArray? = null,
) : PageObject() {
    // Forward-compat carrier for unknown object types; payload preserves the raw record
    // bytes so re-saving is lossless.
    @Transient override val type: PageObjectType = PageObjectType.OPAQUE
}
