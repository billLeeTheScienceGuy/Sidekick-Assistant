package com.sidekick.core

import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

/** Release check (skipped unless packs were built): every published .skmodels file installs with the app's own importer. */
class ModelPackReleaseTest {
    @Test fun `built packs install with the app importer`() {
        val packs = File("../.deps/model-packs").listFiles { f -> f.name.endsWith(".skmodels") }.orEmpty()
        assumeTrue(packs.isNotEmpty())
        val catalog = ModelPackCatalog.parse(File("../app/src/main/assets/model-packs.tsv").readText())
        val root = kotlin.io.path.createTempDirectory("pack-release-").toFile()
        try {
            val store = ModelPackStore(root, catalog)
            for (pack in packs.sortedBy { it.name }) {
                val id = pack.inputStream().buffered().use { store.install(it) }
                assertEquals(pack.name.removeSuffix(".skmodels"), id)
                for (asset in catalog.filter { it.pack == id }) assertEquals(asset.bytes, store.file(asset).length(), asset.name)
                println("installed $id")
            }
        } finally { root.deleteRecursively() }
    }
}
