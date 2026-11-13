package com.alazhartex.stock

import android.app.Application
import com.alazhartex.stock.di.AppContainer
import com.alazhartex.stock.permissions.AlAzharNotifier
import kotlinx.coroutines.launch

/**
 * Process entry. Bootstraps (off the main thread): seed apply + admin auto-session (spec §9.4).
 * UI shows a splash until [AppContainer.bootstrapped] flips true.
 */
class AlAzharApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AlAzharNotifier.ensureChannel(this)
        container = AppContainer(this)
        container.rotateProcessBootId()
        container.applicationScope.launch {
            container.seedLoader.seedIfNeeded()
            container.sessionManager.ensureAdminSessionOnForeground()
            container.loadPersistedPrefs()
            container.markBootstrapped()
        }
    }
}
