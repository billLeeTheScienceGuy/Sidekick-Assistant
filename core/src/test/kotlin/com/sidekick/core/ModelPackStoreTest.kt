package com.sidekick.core

import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.*
import kotlin.test.*

class ModelPackStoreTest {
    private val bytes = "verified fake model".toByteArray()
    private val asset = PackAsset("test", "model.bin", bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    private fun zip(entries: List<Pair<String, ByteArray>>): InputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> entries.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() } }
        return ByteArrayInputStream(out.toByteArray())
    }
    private val header get() = "sidekick-pack.txt" to "SideKick model pack v1\ntest\n".toByteArray()
    private fun fixture(block: (File, ModelPackStore) -> Unit) {
        val root = Files.createTempDirectory("sidekick-pack-").toFile()
        try { block(root, ModelPackStore(root, listOf(asset))) } finally { root.deleteRecursively() }
    }
    @Test fun `complete packs install atomically and a failed repair preserves existing weights`() = fixture { root, store ->
        assertEquals("test", store.install(zip(listOf(header, asset.name to bytes))))
        assertContentEquals(bytes, store.file(asset).readBytes())
        assertFails { store.install(zip(listOf(header, asset.name to "bad".toByteArray()))) }
        assertContentEquals(bytes, store.file(asset).readBytes())
        assertFalse(root.listFiles()!!.any { it.name.startsWith(".import-") })
    }
    @Test fun `path traversal unexpected files oversized files and incomplete packs are rejected`() = fixture { root, store ->
        for (entries in listOf(listOf(header, "../escape" to bytes), listOf(header, "/absolute" to bytes),
            listOf(header), listOf(header, asset.name to (bytes + bytes)), listOf(header, "extra.bin" to bytes))) {
            assertFails { store.install(zip(entries)) }
            assertFalse(store.file(asset).exists())
            assertTrue(root.listFiles()!!.isEmpty())
        }
    }
    @Test fun `cancellation leaves no partial pack and interrupted replacement can recover`() = fixture { root, store ->
        assertFails { store.install(zip(listOf(header, asset.name to bytes))) { throw InterruptedIOException("cancelled") } }
        assertTrue(root.listFiles()!!.isEmpty())
        val orphan = File(root, ".import-11111111-1111-1111-1111-111111111111").apply { mkdir(); File(this, "partial").writeText("interrupted") }
        store.install(zip(listOf(header, asset.name to bytes)))
        assertFalse(orphan.exists())
        assertTrue(File(root, "test").renameTo(File(root, "test.previous")))
        assertContentEquals(bytes, store.file(asset).readBytes())
    }
    @Test fun `catalog prevents unsafe paths duplicates and invalid checksums`() {
        val valid = "test\tmodel.bin\t${asset.bytes}\t${asset.sha256}"
        assertEquals(listOf(asset), ModelPackCatalog.parse(valid))
        for (invalid in listOf(valid + "\n" + valid, valid.replace("model.bin", "../model.bin"), valid.replace("test\t", "../test\t"), valid.replace(asset.sha256, "00")))
            assertFails { ModelPackCatalog.parse(invalid) }
    }
}
