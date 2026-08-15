package com.penly.core.storage

import java.security.MessageDigest

/**
 * Content-addressed blob store for document files.
 *
 * Paths are ALWAYS relative, use `/` separators, are never absolute and never contain `..`
 * segments. Violations throw [IllegalArgumentException].
 */
interface ContentStore {
    /** Writes [bytes] at [path], overwriting any existing content. Creates parent dirs. */
    fun put(
        path: String,
        bytes: ByteArray,
    )

    /** Returns the bytes at [path], or null if the file does not exist. */
    fun open(path: String): ByteArray?

    /** Moves the file at [from] to [to], creating parent dirs. No-op if [from] is missing. */
    fun move(
        from: String,
        to: String,
    )

    /** Deletes the file at [path]. No-op if it does not exist. */
    fun delete(path: String)

    /** Returns true if a file exists at [path]. */
    fun exists(path: String): Boolean

    /** Returns "sha256:<64 lowercase hex>" for the file at [path], or throws if absent. */
    fun checksum(path: String): String

    /** Returns relative child paths (files and dirs) directly under [dir]; empty if absent. */
    fun list(dir: String): List<String>
}

/** Shared path validation and checksum helpers for [ContentStore] implementations. */
internal object ContentStorePaths {
    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * Validates a store path. Empty is only allowed when [allowRoot] is set (the store root,
     * used by [ContentStore.list]).
     */
    fun validate(
        path: String,
        allowRoot: Boolean = false,
    ) {
        if (path.isEmpty()) {
            if (allowRoot) return
            throw IllegalArgumentException("path must not be empty")
        }
        if (path.startsWith('/')) {
            throw IllegalArgumentException("path must be relative, got: $path")
        }
        for (segment in path.split('/')) {
            if (segment == "..") {
                throw IllegalArgumentException("path must not contain '..', got: $path")
            }
        }
    }

    /** Lowercase hex SHA-256 of [bytes]. */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX[value ushr 4])
            builder.append(HEX[value and 0xF])
        }
        return builder.toString()
    }
}
