package com.penly.core.document

import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.Manifest
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.storage.ContentStore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists [Document]s in a [ContentStore] using the Penly format.
 *
 * Each document lives in a top-level directory `<documentId>/`:
 * - `pages/page-<pageId>.bin` — binary page file (page metadata + objects with payloads)
 * - `document.json` — [DocumentIndex] (document metadata + page refs, no objects)
 * - `manifest.json` — [Manifest] with per-file sha256 checksums, written last as the commit marker
 */
class PenlyStore(
    private val store: ContentStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Writes [document] (all pages, index, then manifest) into the store. */
    fun save(document: Document) {
        val documentId = document.documentId
        val files = LinkedHashMap<String, String>()
        for (page in document.pages) {
            val path = pageFilePath(documentId, page.pageId)
            store.put(path, PenlyFormat.encodePage(page, json))
            files[path] = store.checksum(path)
        }
        val indexPath = indexPath(documentId)
        val indexJson =
            json.encodeToString(DocumentIndex.serializer(), DocumentIndex.from(document))
        store.put(indexPath, indexJson.toByteArray(Charsets.UTF_8))
        files[indexPath] = store.checksum(indexPath)
        val manifest =
            Manifest(
                documentId = documentId,
                createdAtMillis = document.createdAtMillis,
                updatedAtMillis = clock(),
                files = files,
            )
        val manifestJson = json.encodeToString(Manifest.serializer(), manifest)
        store.put(manifestPath(documentId), manifestJson.toByteArray(Charsets.UTF_8))
    }

    /**
     * Loads the document at [documentId]. Every file listed in the manifest is checksum-verified;
     * any mismatch, missing file, or incompatible manifest yields [LoadResult.Failure]. Unknown
     * record types are preserved as opaque objects and reported as warnings, never as failures.
     */
    fun load(documentId: DocumentId): LoadResult {
        fun failure(message: String): LoadResult.Failure = LoadResult.Failure("document $documentId: $message")

        val manifestBytes = store.open(manifestPath(documentId))
        if (manifestBytes == null) {
            return failure("manifest not found at '${manifestPath(documentId)}'")
        }
        val manifest =
            try {
                json.decodeFromString(Manifest.serializer(), manifestBytes.toString(Charsets.UTF_8))
            } catch (e: Exception) {
                return failure("corrupt manifest: ${e.message}")
            }
        if (manifest.format != FORMAT_NAME) {
            return failure("unsupported format '${manifest.format}'")
        }
        if (manifest.formatVersion != PenlyFormat.FORMAT_VERSION) {
            return failure("unsupported format version ${manifest.formatVersion}")
        }
        if (manifest.minimumReaderVersion > PenlyFormat.FORMAT_VERSION) {
            return failure("requires reader version ${manifest.minimumReaderVersion}")
        }
        for ((path, expected) in manifest.files) {
            val actual =
                try {
                    store.checksum(path)
                } catch (e: Exception) {
                    return failure("missing file '$path'")
                }
            if (actual != expected) {
                return failure("checksum mismatch for '$path'")
            }
        }
        val indexBytes = store.open(indexPath(documentId))
        if (indexBytes == null) {
            return failure("index file missing")
        }
        val index =
            try {
                val jsonString = indexBytes.toString(Charsets.UTF_8)
                json.decodeFromString(DocumentIndex.serializer(), jsonString)
            } catch (e: Exception) {
                return failure("corrupt index: ${e.message}")
            }
        val warnings = ArrayList<String>()
        val pages =
            index.pages.map { ref ->
                val path = pageFilePath(documentId, ref.pageId)
                val bytes = store.open(path)
                if (bytes == null) {
                    return failure("page file missing '$path'")
                }
                val decoded =
                    try {
                        PenlyFormat.decodePage(bytes, json)
                    } catch (e: Exception) {
                        return failure("corrupt page file '$path': ${e.message}")
                    }
                warnings.addAll(decoded.warnings)
                Page(
                    pageId = ref.pageId,
                    documentId = documentId,
                    title = ref.title,
                    objects = decoded.objects,
                    revision = ref.revision,
                    createdAtMillis = ref.createdAtMillis,
                    updatedAtMillis = ref.updatedAtMillis,
                )
            }
        val document =
            Document(
                documentId = documentId,
                title = index.title,
                pages = pages,
                revision = index.revision,
                createdAtMillis = index.createdAtMillis,
                updatedAtMillis = index.updatedAtMillis,
            )
        return LoadResult.Success(document = document, warnings = warnings)
    }

    /** Returns the ids of all documents present in the store (those with a manifest). */
    fun listDocuments(): List<DocumentId> {
        return store
            .list("")
            .mapNotNull { entry ->
                val id = entry.removeSuffix("/")
                if (id.isEmpty() || id.contains('/')) return@mapNotNull null
                if (store.exists("$id/manifest.json")) DocumentId(id) else null
            }.sortedBy { it.value }
    }

    /**
     * Writes an imported asset (e.g. an image) at `<documentId>/assets/<name>` and returns the
     * relative reference stored on an [com.penly.core.model.ImageObject]'s payloadRef
     * ("assets/<name>"). Assets are written by the editor at insert time and are NOT listed in
     * the manifest (integrity checks for assets land in Phase 4).
     */
    fun putAsset(
        documentId: DocumentId,
        name: String,
        bytes: ByteArray,
    ): String {
        val path = "${documentId.value}/assets/$name"
        store.put(path, bytes)
        return "assets/$name"
    }

    /** Returns the bytes of an asset referenced by [payloadRef] ("assets/<name>"), or null. */
    fun openAsset(
        documentId: DocumentId,
        payloadRef: String,
    ): ByteArray? = store.open("${documentId.value}/$payloadRef")

    private fun manifestPath(documentId: DocumentId): String = "${documentId.value}/manifest.json"

    private fun indexPath(documentId: DocumentId): String = "${documentId.value}/document.json"

    private fun pageFilePath(
        documentId: DocumentId,
        pageId: PageId,
    ): String = "${documentId.value}/pages/page-${pageId.value}.bin"

    private companion object {
        const val FORMAT_NAME: String = "penly"
    }
}
