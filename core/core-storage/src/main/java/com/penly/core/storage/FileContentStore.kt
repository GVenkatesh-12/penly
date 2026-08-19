package com.penly.core.storage

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * File-backed [ContentStore] rooted at [root]. Missing files yield null from [open], [delete]
 * and [move] no-op on a missing source, [checksum] throws [FileNotFoundException] when absent.
 *
 * [put] is crash-safe: bytes are written to a temporary sibling file, fsynced, and atomically
 * renamed over the target, so a crash mid-write never leaves a torn file behind. The parent
 * directory is fsynced best-effort so the rename itself survives power loss where the
 * platform permits it.
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
        val tmp = File(file.parentFile, "${file.name}.tmp")
        writeAndFsync(tmp, bytes)
        moveAtomically(tmp, file)
        fsyncDir(file.parentFile)
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
        moveAtomically(source, target)
        fsyncDir(target.parentFile)
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

    /** Writes [bytes] to [file] and fsyncs it to durable storage before returning. */
    private fun writeAndFsync(
        file: File,
        bytes: ByteArray,
    ) {
        FileOutputStream(file).use { out ->
            val channel = out.channel
            channel.write(java.nio.ByteBuffer.wrap(bytes))
            channel.force(true)
        }
    }

    /** Renames [from] over [to] atomically, falling back to a plain move if unsupported. */
    private fun moveAtomically(
        from: File,
        to: File,
    ) {
        try {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Fsyncs [dir] so the rename above survives power loss; best-effort where unsupported. */
    private fun fsyncDir(dir: File?) {
        if (dir == null) return
        try {
            FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (e: IOException) {
            // Directory fsync is unavailable on some platforms; the rename is still atomic.
        }
    }
}
