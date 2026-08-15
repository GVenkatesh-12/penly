package com.penly.core.storage

import java.io.File
import java.io.FileNotFoundException

/**
 * File-backed [ContentStore] rooted at [root]. Missing files yield null from [open], [delete]
 * and [move] no-op on a missing source, [checksum] throws [FileNotFoundException] when absent.
 */
class FileContentStore(
    private val root: File,
) : ContentStore {
    override fun put(
        path: String,
        bytes: ByteArray,
    ) {
        ContentStorePaths.validate(path)
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    override fun open(path: String): ByteArray? {
        ContentStorePaths.validate(path)
        val file = resolve(path)
        return if (file.isFile) file.readBytes() else null
    }

    override fun move(
        from: String,
        to: String,
    ) {
        ContentStorePaths.validate(from)
        ContentStorePaths.validate(to)
        val source = resolve(from)
        if (!source.isFile) return
        val target = resolve(to)
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            throw IllegalStateException("move failed: $from -> $to")
        }
    }

    override fun delete(path: String) {
        ContentStorePaths.validate(path)
        val file = resolve(path)
        if (file.isFile) file.delete()
    }

    override fun exists(path: String): Boolean {
        ContentStorePaths.validate(path)
        return resolve(path).isFile
    }

    override fun checksum(path: String): String {
        ContentStorePaths.validate(path)
        val file = resolve(path)
        if (!file.isFile) throw FileNotFoundException(path)
        return "sha256:" + ContentStorePaths.sha256Hex(file.readBytes())
    }

    override fun list(dir: String): List<String> {
        ContentStorePaths.validate(dir, allowRoot = true)
        val directory = resolve(dir)
        val entries = directory.listFiles() ?: return emptyList()
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        return entries.map { prefix + it.name }.sorted()
    }

    private fun resolve(path: String): File = File(root, path)
}
