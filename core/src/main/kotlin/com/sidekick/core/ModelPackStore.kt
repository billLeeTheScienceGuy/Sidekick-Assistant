package com.sidekick.core

import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

data class PackAsset(val pack: String, val name: String, val bytes: Long, val sha256: String) {
    init {
        require(pack.matches(Regex("[a-z][a-z0-9-]{0,30}")))
        require(name.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,100}")))
        require(bytes in 1..1_000_000_000 && sha256.matches(Regex("[0-9a-f]{64}")))
    }
}
object ModelPackCatalog {
    fun parse(text: String): List<PackAsset> = text.lineSequence().filter { it.isNotBlank() }.map {
        val p = it.split('\t'); require(p.size == 4)
        require(p[0].matches(Regex("[a-z][a-z0-9-]{0,30}")))
        require(p[1].matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,100}")))
        require(p[2].toLong() in 1..1_000_000_000 && p[3].matches(Regex("[0-9a-f]{64}")))
        PackAsset(p[0], p[1], p[2].toLong(), p[3])
    }.toList().also { require(it.isNotEmpty() && it.distinctBy { a -> a.name }.size == it.size) }
}

/** All archive paths come from the bundled catalog; imported metadata cannot authorize new files. */
class ModelPackStore(private val root: File, private val assets: List<PackAsset>) {
    fun file(asset: PackAsset): File {
        val folder = File(root, asset.pack)
        val backup = File(root, "${asset.pack}.previous")
        if (!folder.exists() && backup.isDirectory) backup.renameTo(folder)
        return File(folder, asset.name)
    }
    @Synchronized fun install(input: InputStream, cancelled: () -> Unit = {}): String {
        root.mkdirs()
        root.listFiles()?.filter { it.name.matches(Regex("\\.import-[0-9a-f-]{36}")) }?.forEach { check(it.deleteRecursively()) }
        val stage = File(root, ".import-${UUID.randomUUID()}").also { check(it.mkdir()) }
        try {
            return ZipInputStream(input).use { zip ->
                val header = zip.nextEntry ?: error("Empty model pack")
                require(header.name == "sidekick-pack.txt" && !header.isDirectory) { "Choose a SideKick model pack." }
                val metadata = ByteArrayOutputStream()
                while (true) { val b = zip.read(); if (b < 0) break; require(metadata.size() < 256); metadata.write(b) }
                val lines = metadata.toString("UTF-8").trim().lines()
                require(lines.size == 2 && lines[0] == "SideKick model pack v1") { "Unsupported model pack." }
                val id = lines[1]
                val expected = assets.filter { it.pack == id }.associateBy { it.name }
                require(expected.isNotEmpty()) { "Unknown model pack." }
                val seen = mutableSetOf<String>()
                var licenseSeen = false
                while (true) {
                    cancelled()
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "LICENSE.txt") {
                        require(!licenseSeen && !entry.isDirectory); licenseSeen = true
                        FileOutputStream(File(stage, "LICENSE.txt")).use { out ->
                            var total = 0
                            while (true) { val b = zip.read(); if (b < 0) break; require(++total <= 65536); out.write(b) }
                        }
                        continue
                    }
                    val asset = expected[entry.name] ?: error("Unexpected model-pack file.")
                    require(!entry.isDirectory && seen.add(entry.name)) { "Duplicate model-pack file." }
                    val digest = MessageDigest.getInstance("SHA-256"); var size = 0L
                    FileOutputStream(File(stage, asset.name)).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            cancelled(); val count = zip.read(buffer); if (count < 0) break
                            size += count; require(size <= asset.bytes) { "Model-pack file is too large." }
                            digest.update(buffer, 0, count); output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                    require(size == asset.bytes && digest.digest().joinToString("") { "%02x".format(it) } == asset.sha256) { "Model verification failed." }
                }
                require(seen == expected.keys) { "Model pack is incomplete." }; cancelled()
                val target = File(root, id); val backup = File(root, "$id.previous")
                if (!target.exists() && backup.exists()) check(backup.renameTo(target))
                if (backup.exists()) check(backup.deleteRecursively())
                if (target.exists()) check(target.renameTo(backup)) { "Could not replace model pack." }
                if (!stage.renameTo(target)) { if (backup.exists()) backup.renameTo(target); error("Could not install model pack.") }
                backup.deleteRecursively()
                id
            }
        } finally { stage.deleteRecursively() }
    }
}
