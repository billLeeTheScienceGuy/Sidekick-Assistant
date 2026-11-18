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
    val labels = linkedMapOf("whisper" to "Whisper languages", "sensevoice" to "Korean & Chinese", "omni" to "Auto-detect & multilingual", "english" to "English accuracy (Auto-detect)", "russian" to "Russian accuracy (Auto-detect)", "korean" to "Korean names & time check")
    /** What each speech file is for, shown next to its size. "Base" packs make a language work; the rest improve it. */
    val usedFor = mapOf(
        "whisper" to "Base: French, German, Russian, Arabic; English Recommended",
        "sensevoice" to "Base: Korean, Chinese; also used by Auto-detect",
        "omni" to "Base: Arabic and Auto-detect; French, German, Russian Recommended",
        "english" to "Better accuracy: English in Auto-detect",
        "russian" to "Better accuracy: Russian",
        "korean" to "Better accuracy: Korean names and times")
    @Volatile private var catalogCache: List<PackAsset>? = null
    fun catalog(context: Context) = catalogCache ?: context.assets.open("model-packs.tsv").bufferedReader().use { ModelPackCatalog.parse(it.readText()) }.also { catalogCache = it }
    private fun store(context: Context) = ModelPackStore(File(context.noBackupFilesDir, "model-packs"), catalog(context))
    private fun use(context: Context) = context.getSharedPreferences("model-pack-use", Context.MODE_PRIVATE)
    /** A pack can stay installed but unused: SideKick then works as if it were not there. */
    fun inUse(context: Context, pack: String): Boolean = use(context).getBoolean(pack, true)
    fun setInUse(context: Context, pack: String, value: Boolean) { use(context).edit().putBoolean(pack, value).apply(); revision.value++ }
    fun bytes(context: Context, pack: String): Long = catalog(context).filter { it.pack == pack }.sumOf { it.bytes }
    /** Files on the phone, whether or not the pack is in use. */
    fun installed(context: Context, pack: String): Boolean = catalog(context).filter { it.pack == pack }.let { rows -> rows.isNotEmpty() && rows.all { present(context, it.name) } }
    /** Part of this app build itself (test builds): can be turned off but not removed. */
    fun bundled(context: Context, pack: String): Boolean = catalog(context).filter { it.pack == pack }.all { context.assets.list("models").orEmpty().contains(it.name) }
    /** Deletes a downloaded pack's files (not ones built into the app). Returns the bytes freed. */
    suspend fun remove(context: Context, pack: String): Long = gate.withLock { SpeechEngine.modelMaintenance { withContext(Dispatchers.IO) {
        var freed = 0L
        catalog(context).filter { it.pack == pack }.forEach { asset ->
            listOf(store(context).file(asset), File(context.noBackupFilesDir, asset.name)).forEach { f -> if (f.isFile) { freed += f.length(); f.delete() } }
        }
        revision.value++; freed
    } } }
    fun available(context: Context, name: String): Boolean {
        val asset = catalog(context).find { it.name == name }
        if (asset != null && !inUse(context, asset.pack)) return false
        return present(context, name)
    }
    private fun present(context: Context, name: String): Boolean {
        val asset = catalog(context).find { it.name == name }
        val old = File(context.noBackupFilesDir, name)
        if (old.isFile && (asset == null || old.length() == asset.bytes)) return true
        if (asset != null && store(context).file(asset).let { it.isFile && it.length() == asset.bytes }) return true
        return context.assets.list("models").orEmpty().contains(name)
    }
    fun required(language: SpeechLanguage, quality: SpeechQuality): Set<String> = LanguageReadiness.required(language, quality)
    fun installedPacks(context: Context): Set<String> = labels.keys.filter { installed(context, it) }.toSet()
    fun packsInUse(context: Context): Set<String> = labels.keys.filter { installed(context, it) && inUse(context, it) }.toSet()
    fun sizes(context: Context): Map<String, Long> = labels.keys.associateWith { bytes(context, it) }
    fun status(context: Context, language: SpeechLanguage, quality: SpeechQuality) =
        LanguageReadiness.status(language, quality, installedPacks(context), packsInUse(context), sizes(context))
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
