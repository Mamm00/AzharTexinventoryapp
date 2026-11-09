package com.alazhartex.stock

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.alazhartex.stock.ui.AlAzharRoot
import com.alazhartex.stock.ui.theme.AlAzharTexTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapWithLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlAzharTexTheme {
                AlAzharRoot(application as AlAzharApp)
            }
        }
    }

    /** UI-lane locale (RES: ADR) — SharedPreferences only carries the language; all data stays in Room. */
    private fun wrapWithLocale(base: Context): Context {
        val prefs = base.getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE)
        val lang = prefs.getString(KEY_LANGUAGE, "ar") ?: "ar"
        val locale = if (lang.startsWith("ar")) Locale.forLanguageTag("ar-EG") else Locale.ENGLISH
        val config: Configuration = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    companion object {
        const val LOCALE_PREFS = "alazhartex.session"
        const val KEY_LANGUAGE = "language"
    }
}
