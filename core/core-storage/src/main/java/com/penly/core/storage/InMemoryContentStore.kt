package com.penly.core.storage

import java.io.FileNotFoundException

/**
 * In-memory [ContentStore] backed by a [LinkedHashMap]. Mirrors [FileContentStore] semantics:
 * missing files yield null from [open], [delete] and [move] no-op on a missing source,
 * [checksum] throws [FileNotFoundException] when absent. [list] reports both stored files and
 * the directory entries implied by nested paths.
 */
class InMemoryContentStore : ContentStore {
    private val files = LinkedHashMap<String, ByteArray>()

    override fun put(
        path: String,
        bytes: ByteArray,
    ) {
        ContentStorePaths.validate(path)
        files[path] = bytes.copyOf()
    }

    override fun open(path: String): ByteArray? {
        ContentStorePaths.validate(path)
        return files[path]?.copyOf()
    }

    override fun move(
        from: String,
        to: String,
    ) {
        ContentStorePaths.validate(from)
        ContentStorePaths.validate(to)
        val bytes = files.remove(from) ?: return
        files[to] = bytes
    }

    override fun delete(path: String) {
        ContentStorePaths.validate(path)
        files.remove(path)
    }

    override fun exists(path: String): Boolean {
        ContentStorePaths.validate(path)
        return files.containsKey(path)
    }

    override fun checksum(path: String): String {
        ContentStorePaths.validate(path)
        val bytes = files[path] ?: throw FileNotFoundException(path)
        return "sha256:" + ContentStorePaths.sha256Hex(bytes)
    }

    override fun list(dir: String): List<String> {
        ContentStorePaths.validate(dir, allowRoot = true)
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        val children = LinkedHashSet<String>()
        for (key in files.keys) {
            if (key == dir || !key.startsWith(prefix)) continue
            val rest = key.substring(prefix.length)
            val slash = rest.indexOf('/')
            children.add(prefix + if (slash < 0) rest else rest.substring(0, slash))
        }
        return children.sorted()
    }
}
