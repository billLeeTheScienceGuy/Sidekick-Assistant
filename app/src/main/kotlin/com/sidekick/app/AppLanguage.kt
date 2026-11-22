package com.sidekick.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/**
 * The language of SideKick's own screens and notifications. It is separate from the speech (listening) language.
 * Android 13+ keeps the choice in the system's per-app language setting; older versions keep it in a private preference.
 */
object AppLanguage {
    /** "" means "same as the phone". Tags match res/xml/locales_config.xml. */
    val tags = listOf("", "en", "ko", "zh-CN", "ar", "ru", "fr", "de")

    /** Each language's own name, shown the same way in every language. */
    fun nativeName(tag: String): String = when (tag) {
        "en" -> "English"; "ko" -> "한국어"; "zh-CN" -> "中文"; "ar" -> "العربية"
        "ru" -> "Русский"; "fr" -> "Français"; "de" -> "Deutsch"; else -> ""
    }

    private const val PREFS = "sidekick_app_language"
    private const val KEY = "tag"
    @Volatile private var appContext: Context? = null
    @Volatile private var chosen: String = ""
    @Volatile private var cachedContext: Pair<String, Context>? = null

    /** Call once from the Application, and again when the configuration may have changed. */
    fun refresh(context: Context) {
        val app = context.applicationContext ?: context
        appContext = app
        val tag = read(app)
        if (tag != chosen) cachedContext = null
        chosen = tag
    }

    fun current(): String = chosen

    private fun read(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val list = runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales }.getOrNull()
            if (list == null || list.isEmpty) return ""
            val language = list[0].language
            return tags.firstOrNull { it.isNotEmpty() && Locale.forLanguageTag(it).language == language } ?: ""
        }
        return runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") }.getOrNull().orEmpty()
            .takeIf { it in tags } ?: ""
    }

    /** Saves the choice. Screens in the foreground are recreated, so the new language shows at once without losing saved state. */
    fun set(context: Context, tag: String) {
        val safe = if (tag in tags) tag else ""
        val app = context.applicationContext ?: context
        appContext = app
        runCatching { app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, safe).apply() }
        chosen = safe
        cachedContext = null
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                app.getSystemService(LocaleManager::class.java)?.applicationLocales =
                    if (safe.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(safe)
            }
        } else {
            if (safe.isNotEmpty()) Locale.setDefault(Locale.forLanguageTag(safe))
            findActivity(context)?.recreate()
        }
    }

    private fun findActivity(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return null
    }

    /** Android 12 and older: put the chosen language on an activity's base context. Newer versions do this themselves. */
    fun wrap(base: Context): Context {
        refresh(base)
        if (Build.VERSION.SDK_INT >= 33 || chosen.isEmpty()) return base
        val locale = Locale.forLanguageTag(chosen)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** A context in the chosen language, for text built outside a screen (notifications, status lines). */
    fun localized(): Context? {
        val app = appContext ?: return null
        if (chosen.isEmpty()) return app
        cachedContext?.let { if (it.first == chosen) return it.second }
        val locale = Locale.forLanguageTag(chosen)
        val config = Configuration(app.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        val made = app.createConfigurationContext(config)
        cachedContext = chosen to made
        return made
    }
}

/** Text in the app language. Pass format arguments positionally (%1$s, %2$s). */
fun tr(@StringRes id: Int, vararg args: Any?): String {
    val context = AppLanguage.localized() ?: return ""
    return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
}

/** Text with a count in the app language (plural forms follow that language's rules). */
fun trn(@PluralsRes id: Int, count: Int, vararg args: Any?): String {
    val context = AppLanguage.localized() ?: return ""
    return context.resources.getQuantityString(id, count, *args)
}
