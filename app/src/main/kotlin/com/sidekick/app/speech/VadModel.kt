package com.sidekick.app.speech

import android.content.Context
import java.io.File
import java.security.MessageDigest

object VadModel {
    const val HASH = "29940d98d42b91fbd05ce489f3ecf7c72f0a42f027e4875919a28fb4c04ea2cf"
    @Synchronized fun install(context: Context): String {
        val target = File(context.noBackupFilesDir, "ggml-silero-v5.1.2.bin")
        fun valid(file: File): Boolean = file.isFile && file.inputStream().use { stream ->
            val hash = MessageDigest.getInstance("SHA-256")
            val bytes = ByteArray(8192)
            while (true) { val n = stream.read(bytes); if (n < 0) break; hash.update(bytes, 0, n) }
            hash.digest().joinToString("") { "%02x".format(it) } == HASH
        }
        if (!valid(target)) {
            val temp = File(context.noBackupFilesDir, "vad-model.download")
            try {
                context.assets.open("models/ggml-silero-v5.1.2.bin").use { source -> temp.outputStream().use(source::copyTo) }
                check(valid(temp)) { "Speech detector checksum mismatch." }
                check(temp.renameTo(target)) { "Could not install speech detector." }
            } finally { temp.delete() }
        }
        return target.absolutePath
    }
}
