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
 * - `journal/` — crash-safety journal: pending page/index copies plus a [JournalCommit] marker
 *
 * ## Crash-safe save
 *
 * A save is staged through the journal so a crash at any point never loses committed content:
 *
 * ```text
 * 1. write pending page + index copies into <docId>/journal/
 * 2. write journal/commit.json (the commit point)
 * 3. write the real page + index files (atomic per file)
 * 4. write manifest.json (the durable commit marker)
 * 5. delete the journal
 * ```
 *
 * If the process dies before step 2 the journal is ignored and the previous committed state
 * stands. If it dies after step 2, [load] finds the marker, validates the journal copies
 * against it, replays them over the main files, rebuilds the manifest, and reports recovery.
 * A marker whose listed files are missing or checksum-mismatched is discarded (the main state
 * from an earlier completed save remains authoritative).
 */
class PenlyStore(
    private val store: ContentStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Writes [document] (all pages, index, then manifest) through the journal. */
    fun save(document: Document) {
        val documentId = document.documentId
        val now = clock()
        val pageFiles =
            document.pages.associate { page ->
                pageFilePath(documentId, page.pageId) to PenlyFormat.encodePage(page, json)
            }
        val indexPath = indexPath(documentId)
        val indexJson =
            json.encodeToString(DocumentIndex.serializer(), DocumentIndex.from(document))
        val indexBytes = indexJson.toByteArray(Charsets.UTF_8)

        // 1. Stage pending copies into the journal.
        val journalPaths = ArrayList<String>()
        for ((path, bytes) in pageFiles) {
            store.put(journalPath(documentId, path), bytes)
            journalPaths += journalPath(documentId, path)
        }
        store.put(journalPath(documentId, indexPath), indexBytes)
        journalPaths += journalPath(documentId, indexPath)

        // 2. Commit point: the marker makes the journal authoritative. The journal only stages
        // pages and the index; assets are integrity-checked through the manifest below.
        val files = LinkedHashMap<String, String>()
        for (path in pageFiles.keys) {
            files[path] = store.checksum(journalPath(documentId, path))
        }
        files[indexPath] = store.checksum(journalPath(documentId, indexPath))
        val commit =
            JournalCommit(
                documentId = documentId,
                createdAtMillis = document.createdAtMillis,
                updatedAtMillis = now,
                files = files,
            )
        store.put(journalCommitPath(documentId), encode(commit))

        // 3. Write the real files (atomic per file).
        for ((path, bytes) in pageFiles) {
            store.put(path, bytes)
        }
        store.put(indexPath, indexBytes)

        // 4. Durable commit marker. Assets join the manifest (not the journal) so a corrupt
        // image is detected on load without ever blocking journal replay.
        includeAssets(documentId, files)
        val manifest =
            Manifest(
                documentId = documentId,
                createdAtMillis = document.createdAtMillis,
                updatedAtMillis = now,
                files = files,
            )
        store.put(manifestPath(documentId), encode(manifest))

        // 5. Journal is no longer needed.
        for (path in journalPaths) {
            store.delete(path)
        }
        store.delete(journalCommitPath(documentId))
    }

    /**
     * Loads the document at [documentId]. Every file listed in the manifest is checksum-verified;
     * any mismatch, missing file, or incompatible manifest yields [LoadResult.Failure]. Asset
     * (image) mismatches degrade to warnings instead of failures — one corrupt image must not
     * make the whole note unopenable. Unknown record types are preserved as opaque objects and
     * reported as warnings, never as failures. When an interrupted save left a valid journal the
     * journal is replayed first and the result reports [LoadResult.Success.recovered].
     */
    fun load(documentId: DocumentId): LoadResult {
        fun failure(message: String): LoadResult.Failure = LoadResult.Failure("document $documentId: $message")

        val recovered = replayJournal(documentId)
        val warnings = ArrayList<String>()

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
                    null
                }
            if (actual != expected) {
                if (isAssetPath(path)) {
                    warnings += "asset '$path' missing or corrupted"
                    continue
                }
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
        return LoadResult.Success(document = document, warnings = warnings, recovered = recovered)
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
     * ("assets/<name>"). Assets are included in the manifest's integrity metadata on the next
     * [save]; load verifies them as warnings rather than hard failures.
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

    /**
     * Replays a valid journal over the main files, rebuilds the manifest, and cleans the
     * journal. Returns true when recovery happened. A journal is only trusted when its marker
     * decodes and every listed copy exists with a matching checksum; otherwise it is ignored.
     */
    private fun replayJournal(documentId: DocumentId): Boolean {
        val markerPath = journalCommitPath(documentId)
        val markerBytes = store.open(markerPath) ?: return false
        val commit =
            try {
                json.decodeFromString(JournalCommit.serializer(), markerBytes.toString(Charsets.UTF_8))
            } catch (e: Exception) {
                return false
            }
        if (commit.documentId != documentId) {
            return false
        }
        for ((path, expected) in commit.files) {
            if (!store.exists(journalPath(documentId, path))) return false
            if (store.checksum(journalPath(documentId, path)) != expected) return false
        }
        for (path in commit.files.keys) {
            val bytes = store.open(journalPath(documentId, path)) ?: return false
            store.put(path, bytes)
        }
        val manifest =
            Manifest(
                documentId = documentId,
                createdAtMillis = commit.createdAtMillis,
                updatedAtMillis = commit.updatedAtMillis,
                files = commit.files,
            )
        store.put(manifestPath(documentId), encode(manifest))
        for (path in commit.files.keys) {
            store.delete(journalPath(documentId, path))
        }
        store.delete(markerPath)
        return true
    }

    /** Adds every file under `<documentId>/assets/` to [files] with its checksum. */
    private fun includeAssets(
        documentId: DocumentId,
        files: MutableMap<String, String>,
    ) {
        val assetDir = "${documentId.value}/assets"
        for (path in store.list(assetDir)) {
            if (store.exists(path)) {
                files[path] = store.checksum(path)
            }
        }
    }

    private fun isAssetPath(path: String): Boolean = path.contains("/assets/")

    private fun journalCommitPath(documentId: DocumentId): String = "${documentId.value}/journal/commit.json"

    private fun journalPath(
        documentId: DocumentId,
        mainPath: String,
    ): String = "${documentId.value}/journal/${mainPath.removePrefix("${documentId.value}/")}"

    private fun encode(commit: JournalCommit): ByteArray =
        json
            .encodeToString(
                JournalCommit.serializer(),
                commit,
            ).toByteArray(Charsets.UTF_8)

    private fun encode(manifest: Manifest): ByteArray =
        json
            .encodeToString(
                Manifest.serializer(),
                manifest,
            ).toByteArray(Charsets.UTF_8)

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
