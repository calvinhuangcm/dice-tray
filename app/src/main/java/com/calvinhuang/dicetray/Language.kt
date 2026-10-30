package com.calvinhuang.dicetray

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import androidx.annotation.StringRes
import java.util.Locale

/** Languages exposed by the app-level language picker. */
enum class AppLanguage(
    @StringRes val labelRes: Int,
    val localeTag: String,
) {
    SYSTEM(R.string.language_system, ""),
    TRADITIONAL_CHINESE(R.string.language_zh_tw, "zh-TW"),
    SIMPLIFIED_CHINESE(R.string.language_zh_cn, "zh-CN"),
    HONG_KONG_CHINESE(R.string.language_zh_hk, "zh-HK"),
    ENGLISH(R.string.language_en, "en"),
    JAPANESE(R.string.language_ja, "ja"),
}

/**
 * Stores the optional app language override and applies it before the Activity is created.
 * Keeping this small and independent from the settings DataStore lets locale resources load
 * during the very first composition as well as after a process restart.
 */
object LanguageManager {
    private const val PREFERENCES = "dice_language"
    private const val LANGUAGE_OVERRIDE = "language_override"

    fun current(context: Context): AppLanguage {
        val tag = context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LANGUAGE_OVERRIDE, null)
            ?: return AppLanguage.SYSTEM

        return AppLanguage.entries.firstOrNull { it.localeTag == tag } ?: AppLanguage.SYSTEM
    }

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                if (language == AppLanguage.SYSTEM) {
                    remove(LANGUAGE_OVERRIDE)
                } else {
                    putString(LANGUAGE_OVERRIDE, language.localeTag)
                }
            }
    }

    /** Wraps the base context before Activity resources are created. */
    fun wrap(base: Context): Context {
        val language = current(base)
        if (language == AppLanguage.SYSTEM) return base

        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language.localeTag))
        return base.createConfigurationContext(configuration)
    }
}
