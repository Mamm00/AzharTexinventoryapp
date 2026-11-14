package com.alazhartex.stock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.alazhartex.stock.ui.AlAzharRoot
import com.alazhartex.stock.ui.theme.AlAzharTexTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapWithLocale(newBase))
    }

    /** Cold-start prompt for notifications (API 33+). If the user denies, nothing breaks — snackbars still work. */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* status shown in Settings */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
