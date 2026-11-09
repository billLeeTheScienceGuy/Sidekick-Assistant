package com.sidekick.app.speech

import android.content.Context
import android.net.Uri
import com.sidekick.core.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ModelPacks {
    val revision = MutableStateFlow(0)
    private val gate = Mutex()
    val labels = linkedMapOf("whisper" to "Whisper multilingual + Base", "sensevoice" to "Korean / Chinese Adaptive", "omni" to "Automatic / Arabic + longer French / German / Russian Adaptive")
    fun catalog(context: Context) = context.assets.open("model-packs.tsv").bufferedReader().use { ModelPackCatalog.parse(it.readText()) }
    private fun store(context: Context) = ModelPackStore(File(context.noBackupFilesDir, "model-packs"), catalog(context))
    fun available(context: Context, name: String): Boolean {
        val asset = catalog(context).find { it.name == name }
        val old = File(context.noBackupFilesDir, name)
        if (old.isFile && (asset == null || old.length() == asset.bytes)) return true
        if (asset != null && store(context).file(asset).let { it.isFile && it.length() == asset.bytes }) return true
        return context.assets.list("models").orEmpty().contains(name)
    }
    fun required(language: SpeechLanguage, quality: SpeechQuality): Set<String> = when {
        quality.usesSenseVoice(language) -> setOf("sensevoice")
        quality == SpeechQuality.ADAPTIVE && language in setOf(SpeechLanguage.ARABIC, SpeechLanguage.AUTO) -> setOf("omni")
        quality == SpeechQuality.ADAPTIVE && language in setOf(SpeechLanguage.FRENCH, SpeechLanguage.GERMAN, SpeechLanguage.RUSSIAN) -> setOf("whisper", "omni")
        language == SpeechLanguage.ENGLISH && quality == SpeechQuality.FAST -> emptySet()
        else -> setOf("whisper")
    }
    fun missing(context: Context, language: SpeechLanguage, quality: SpeechQuality): Set<String> = required(language, quality).filter { id ->
        catalog(context).filter { it.pack == id }.any { !available(context, it.name) }
    }.toSet()
    suspend fun importPack(context: Context, uri: Uri): String = gate.withLock { SpeechEngine.modelMaintenance { withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Could not open model pack.")
        val id = input.use { store(context).install(it) { ensureActive() } }
        revision.value++; id
    } } }
    fun ensure(context: Context, name: String, expected: String): File {
        fun hash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream -> val b = ByteArray(131072); while (true) { val n = stream.read(b); if (n < 0) break; digest.update(b, 0, n) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        val old = File(context.noBackupFilesDir, name)
        if (old.isFile && hash(old) == expected) return old
        catalog(context).find { it.name == name }?.let { asset -> store(context).file(asset).let { file ->
            if (file.isFile && file.length() == asset.bytes && hash(file) == expected) return file
        } }
        val temporary = File(context.noBackupFilesDir, "$name.install")
        try {
            check(context.assets.list("models").orEmpty().contains(name)) { "Missing speech model. Import its model pack in Get started or Settings, or choose English Fast." }
            try { context.assets.open("models/$name").use { input -> temporary.outputStream().use(input::copyTo) } }
            catch (_: java.io.FileNotFoundException) { error("Missing speech model. Import its model pack in Get started or Settings, or choose English Fast.") }
            check(hash(temporary) == expected) { "Speech model checksum mismatch." }
            check(temporary.renameTo(old)) { "Could not install speech model." }
            return old
        } finally { temporary.delete() }
    }
}
