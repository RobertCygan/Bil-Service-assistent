package app.servicepassport.android

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The language chosen inside the app. From Android 13 the system applies it everywhere. On older versions only the
 * screens get it, so background work (a notification) asks here for a context in the chosen language.
 */
object AppLanguage {
    private const val PREFERENCES = "app_language"
    private const val TAG = "tag"
    private fun stored(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun choose(context: Context, tag: String) {
        stored(context).edit().putString(TAG, tag).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Called from a screen: remembers a choice made before this record existed. */
    fun adopt(context: Context) {
        if (stored(context).contains(TAG)) return
        val current = AppCompatDelegate.getApplicationLocales()
        if (!current.isEmpty) stored(context).edit().putString(TAG, current.toLanguageTags()).apply()
    }

    /**
     * The app is made for drivers in Norway. On a phone set to neither Norwegian nor English it starts in Norwegian
     * instead of English; the switch at the top of the screen changes it at any time. A choice already made is kept.
     */
    fun startInNorwegianUnlessThePhoneFits(context: Context, phoneLanguage: String = Resources.getSystem().configuration.locales[0].language) {
        if (stored(context).contains(TAG) || !AppCompatDelegate.getApplicationLocales().isEmpty) return
        if (phoneLanguage !in setOf("en", "nb", "no")) choose(context, "nb-NO")
    }

    /** A context whose texts are in the chosen language. Without a choice the phone's language applies, as on screen. */
    fun localized(context: Context, sdk: Int = Build.VERSION.SDK_INT): Context {
        if (sdk >= 33) return context
        val tag = stored(context).getString(TAG, null)?.substringBefore(',')?.takeIf { it.isNotBlank() } ?: return context
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return context.createConfigurationContext(configuration)
    }
}
