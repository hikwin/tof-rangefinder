package win.hik.tofchizi.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "tof_prefs"
    private const val KEY_LANGUAGE = "app_language" // auto, zh, en

    fun onAttach(context: Context): Context {
        val lang = getPersistedData(context, "auto")
        if (lang == "auto") return context
        return setLocale(context, lang)
    }

    fun getLanguage(context: Context): String {
        return getPersistedData(context, "auto")
    }

    fun setLocale(context: Context, language: String): Context {
        persist(context, language)
        if (language == "auto") return context

        val locale = when (language) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> Locale.ENGLISH // Fallback
        }
        return updateResources(context, locale)
    }

    private fun getPersistedData(context: Context, defaultLanguage: String): String {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return preferences.getString(KEY_LANGUAGE, defaultLanguage) ?: defaultLanguage
    }

    private fun persist(context: Context, language: String) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences.edit().putString(KEY_LANGUAGE, language).apply()
    }

    private fun updateResources(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val res = context.resources
        val config = Configuration(res.configuration)
        
        config.setLocale(locale)
        val localeList = LocaleList(locale)
        LocaleList.setDefault(localeList)
        config.setLocales(localeList)

        return context.createConfigurationContext(config)
    }
}
