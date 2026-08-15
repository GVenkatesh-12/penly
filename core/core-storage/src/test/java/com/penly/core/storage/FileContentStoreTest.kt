package com.penly.core.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileNotFoundException

class FileContentStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun store(): FileContentStore = FileContentStore(tempFolder.root)

    @Test
    fun putAndOpen_roundTrip() {
        val store = store()
        store.put("a/b.txt", "hello".toByteArray())
        assertArrayEquals("hello".toByteArray(), store.open("a/b.txt"))
    }

    @Test
    fun put_overwritesExistingContent() {
        val store = store()
        store.put("f.txt", "first".toByteArray())
        store.put("f.txt", "second".toByteArray())
        assertArrayEquals("second".toByteArray(), store.open("f.txt"))
    }

    @Test
    fun open_missingFile_returnsNull() {
        assertNull(store().open("missing.txt"))
    }

    @Test
    fun delete_removesFile() {
        val store = store()
        store.put("x.txt", byteArrayOf(1))
        store.delete("x.txt")
        assertFalse(store.exists("x.txt"))
    }

    @Test
    fun delete_missingFile_isNoOp() {
        val store = store()
        store.delete("nope.txt")
        assertFalse(store.exists("nope.txt"))
    }

    @Test
    fun move_createsParentsAndMoves() {
        val store = store()
        store.put("from.txt", "data".toByteArray())
        store.move("from.txt", "nested/dir/to.txt")
        assertFalse(store.exists("from.txt"))
        assertArrayEquals("data".toByteArray(), store.open("nested/dir/to.txt"))
    }

    @Test
    fun move_missingSource_isNoOp() {
        val store = store()
        store.move("missing.txt", "dest.txt")
        assertFalse(store.exists("dest.txt"))
    }

    @Test
    fun move_overwritesExistingTarget() {
        val store = store()
        store.put("a.txt", "a".toByteArray())
        store.put("b.txt", "b".toByteArray())
        store.move("a.txt", "b.txt")
        assertFalse(store.exists("a.txt"))
        assertArrayEquals("a".toByteArray(), store.open("b.txt"))
    }

    @Test
    fun exists_reflectsPutAndDelete() {
        val store = store()
        assertFalse(store.exists("f.txt"))
        store.put("f.txt", byteArrayOf(1))
        assertTrue(store.exists("f.txt"))
    }

    @Test
    fun checksum_matchesSha256Format() {
        val store = store()
        val bytes = "penly".toByteArray()
        store.put("c.bin", bytes)
        val sum = store.checksum("c.bin")
        assertTrue(sum.startsWith("sha256:"))
        assertEquals(64, sum.removePrefix("sha256:").length)
        assertTrue(Regex("[0-9a-f]{64}").matches(sum.removePrefix("sha256:")))
    }

    @Test
    fun checksum_missingFile_throwsFileNotFound() {
        assertThrows(FileNotFoundException::class.java) { store().checksum("absent.txt") }
    }

    @Test
    fun list_returnsRelativeChildPaths() {
        val store = store()
        store.put("a/one.txt", byteArrayOf(1))
        store.put("a/two.txt", byteArrayOf(1))
        store.put("b.txt", byteArrayOf(1))
        assertEquals(listOf("a", "b.txt"), store.list(""))
        assertEquals(listOf("a/one.txt", "a/two.txt"), store.list("a"))
    }

    @Test
    fun list_missingDir_returnsEmpty() {
        assertEquals(emptyList<String>(), store().list("nope"))
    }

    @Test
    fun pathTraversal_rejected() {
        val store = store()
        for (path in listOf("../x", "/x", "a/../../x", "..")) {
            assertThrows(IllegalArgumentException::class.java) { store.put(path, byteArrayOf(1)) }
            assertThrows(IllegalArgumentException::class.java) { store.open(path) }
            assertThrows(IllegalArgumentException::class.java) { store.delete(path) }
            assertThrows(IllegalArgumentException::class.java) { store.move(path, "ok.txt") }
            assertThrows(IllegalArgumentException::class.java) { store.move("ok.txt", path) }
            assertThrows(IllegalArgumentException::class.java) { store.exists(path) }
            assertThrows(IllegalArgumentException::class.java) { store.checksum(path) }
            assertThrows(IllegalArgumentException::class.java) { store.list(path) }
        }
    }
}
