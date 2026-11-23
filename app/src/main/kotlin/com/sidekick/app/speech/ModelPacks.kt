package com.sidekick.app.speech

import com.sidekick.app.R
import com.sidekick.app.tr

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
    val labels: Map<String, String> get() = linkedMapOf("whisper" to tr(R.string.whisper_languages), "sensevoice" to tr(R.string.korean_chinese), "omni" to tr(R.string.auto_detect_multilingual), "english" to tr(R.string.english_accuracy_auto_detect), "russian" to tr(R.string.russian_accuracy_auto_detect), "korean" to tr(R.string.korean_names_time_check))
    /** What each speech file is for, shown next to its size. "Base" packs make a language work; the rest improve it. */
    val usedFor: Map<String, String> get() = mapOf(
        "whisper" to tr(R.string.base_french_german_russian_arabic_english),
        "sensevoice" to tr(R.string.base_korean_chinese_also_used_by),
        "omni" to tr(R.string.base_arabic_and_auto_detect_french),
        "english" to tr(R.string.better_accuracy_english_in_auto_detect),
        "russian" to tr(R.string.better_accuracy_russian),
        "korean" to tr(R.string.better_accuracy_korean_names_and_times))
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
    fun bundled(context: Context, pack: String): Boolean = catalog(context).filter { it.pack == pack }.all { assetModel(context, it.name) }
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
    /** Presence of each file, remembered until a pack is added, removed or switched (revision changes). */
    @Volatile private var presentCache: Pair<Int, MutableMap<String, Boolean>> = -1 to java.util.concurrent.ConcurrentHashMap()
    @Volatile private var assetModels: Set<String>? = null
    /** Models built into this app (the list never changes while the app runs, so it is read once). */
    private fun assetModel(context: Context, name: String): Boolean =
        (assetModels ?: context.assets.list("models").orEmpty().toSet().also { assetModels = it }).contains(name)
    private fun present(context: Context, name: String): Boolean {
        val rev = revision.value
        val cache = presentCache.let { (r, map) -> if (r == rev) map else java.util.concurrent.ConcurrentHashMap<String, Boolean>().also { presentCache = rev to it } }
        return cache.getOrPut(name) { presentNow(context, name) }
    }
    private fun presentNow(context: Context, name: String): Boolean {
        val asset = catalog(context).find { it.name == name }
        val old = File(context.noBackupFilesDir, name)
        if (old.isFile && (asset == null || old.length() == asset.bytes)) return true
        if (asset != null && store(context).file(asset).let { it.isFile && it.length() == asset.bytes }) return true
        return assetModel(context, name)
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
        val input = context.contentResolver.openInputStream(uri) ?: error(tr(R.string.could_not_open_model_pack))
        val id = input.use { store(context).install(it) { ensureActive() } }
        revision.value++; id
    } } }
    fun ensure(context: Context, name: String, expected: String): File {
        // Fingerprinting a 240-366 MB file takes seconds: a file already checked is remembered by path, size and
        // modification time (only this app can write these private files), so it is read again only when it changed.
        val verified = context.getSharedPreferences("verified-models", Context.MODE_PRIVATE)
        fun hash(file: File): String {
            val key = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
            verified.getString(file.absolutePath, null)?.let { if (it.substringBeforeLast('|') == key) return it.substringAfterLast('|') }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream -> val b = ByteArray(131072); while (true) { val n = stream.read(b); if (n < 0) break; digest.update(b, 0, n) } }
            return digest.digest().joinToString("") { "%02x".format(it) }.also { verified.edit().putString(file.absolutePath, "$key|$it").apply() }
        }
        val old = File(context.noBackupFilesDir, name)
        if (old.isFile && hash(old) == expected) return old
        catalog(context).find { it.name == name }?.let { asset -> store(context).file(asset).let { file ->
            if (file.isFile && file.length() == asset.bytes && hash(file) == expected) return file
        } }
        val temporary = File(context.noBackupFilesDir, "$name.install")
        try {
            check(assetModel(context, name)) { tr(R.string.missing_speech_model_import_its_model) }
            try { context.assets.open("models/$name").use { input -> temporary.outputStream().use(input::copyTo) } }
            catch (_: java.io.FileNotFoundException) { error(tr(R.string.missing_speech_model_import_its_model)) }
            check(hash(temporary) == expected) { tr(R.string.speech_model_checksum_mismatch) }
            check(temporary.renameTo(old)) { tr(R.string.could_not_install_speech_model) }
            return old
        } finally { temporary.delete() }
    }
}
