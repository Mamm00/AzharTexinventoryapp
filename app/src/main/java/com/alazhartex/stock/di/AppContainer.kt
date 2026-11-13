package com.alazhartex.stock.di

import android.content.Context
import com.alazhartex.stock.data.local.AppDatabase
import com.alazhartex.stock.data.local.DbProvider
import com.alazhartex.stock.data.repo.DebugRepository
import com.alazhartex.stock.data.repo.MastersSettingsRepository
import com.alazhartex.stock.data.repo.ReadRepository
import com.alazhartex.stock.data.repo.StockRepository
import com.alazhartex.stock.data.security.PasswordHasher
import com.alazhartex.stock.data.seed.SeedLoader
import com.alazhartex.stock.data.session.SessionManager
import com.alazhartex.stock.data.session.SystemAppClock
import com.alazhartex.stock.data.transfer.ExportImportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** Manual DI (no service locator magic) — one graph, owned by the Application (ADR.md). */
class AppContainer(private val context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val clock = SystemAppClock()

    private val sessionPrefs = context.getSharedPreferences("alazhartex.session", Context.MODE_PRIVATE)

    val database: AppDatabase get() = DbProvider.ensure(context)
    /** Supply a fresh instance reference at every access (repos hold this lambda, not the instance). */
    private val dbSupplier: () -> AppDatabase = { DbProvider.ensure(context) }

    val seedLoader by lazy { SeedLoader(context, dbSupplier, clock::wallMillis) }

    val sessionManager by lazy {
        SessionManager(
            sessionDao = { DbProvider.ensure(context).sessionDao() },
            auditWriter = { ctx, action, target, result, reason ->
                DbProvider.ensure(context).auditDao().insert(
                    com.alazhartex.stock.data.local.AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        at = ctx.wallMillis,
                        actorId = ctx.actorId,
                        action = action,
                        target = target,
                        result = result,
                        reason = reason,
                        idempotencyKey = ctx.idempotencyKey,
                    ),
                )
            },
            clock = clock,
            currentBootId = ::currentProcessBootId,
        )
    }

    val readRepository by lazy { ReadRepository(dbSupplier) }
    val overrideStore by lazy { com.alazhartex.stock.overrides.DashboardOverrideStore(context) }
    val stockRepository by lazy {
        StockRepository(dbSupplier, clock::wallMillis) { actorId, actorIsAdmin ->
            sessionManager.mutationSessionVerdict(actorId, actorIsAdmin)
        }
    }
    val mastersSettingsRepository by lazy { MastersSettingsRepository(dbSupplier, clock::wallMillis) }
    val debugRepository by lazy { DebugRepository(context, dbSupplier, clock::wallMillis, seedLoader) }
    val exportImportManager by lazy { ExportImportManager(context, dbSupplier, clock::wallMillis, APP_VERSION) }
    val passwordHasher = PasswordHasher // exposed for tooling/tests; UI never sees it

    /** UI state: Arabic-Indic digits toggle (persisted in app_settings, loaded at bootstrap). */
    val digitsFlow = MutableStateFlow(false)

    suspend fun loadPersistedPrefs() {
        digitsFlow.update { DbProvider.ensure(context).appSettingsDao().get("arabic_indic_digits") == "true" }
    }

    private val _bootstrapped = MutableStateFlow(false)
    val bootstrapped: StateFlow<Boolean> = _bootstrapped.asStateFlow()
    fun markBootstrapped() { _bootstrapped.update { true } }

    /** Fresh boot id per process start (RES-008: cold-boot sessions use wall clock). */
    fun rotateProcessBootId() {
        sessionPrefs.edit().putString(KEY_BOOT_ID, UUID.randomUUID().toString()).apply()
    }

    fun currentProcessBootId(): String =
        sessionPrefs.getString(KEY_BOOT_ID, null) ?: UUID.randomUUID().toString().also { rotateProcessBootId() }

    companion object {
        const val APP_VERSION = "1.4.0"
        private const val KEY_BOOT_ID = "process_boot_id"
    }
}
