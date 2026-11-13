package com.alazhartex.stock.data.local

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Central owner of the process-wide [AppDatabase] singleton (v1.3.6).
 *
 * Why this exists: Room instances cache their own connection pools and observation state.
 * Export/import rewrites the DB file on disk; bulk operations and external tools may change
 * it behind the app's back. Neither is visible to an instance that was opened before the
 * change — screens kept showing stale "active rolls" until the process was restarted.
 *
 * [refresh] closes the current instance and rebuilds it from the DB file, then bumps
 * [dataVersion]. Screens that collect flows keyed off [dataVersion] (Stock, Dashboard)
 * re-run their queries against the new instance automatically; every
 * [com.alazhartex.stock.data.repo.TransactionRunner] commit bumps [dataVersion] too, so
 * in-app mutations are also reflected everywhere without manual refresh plumbing.
 *
 * [notifyDbChanged] is the same-instance lightweight path (invalidation tracker only) for
 * callers that cannot afford an instance swap; it still bumps [dataVersion].
 *
 * All database construction in the process flows through [ensure]/[refresh] so configuration
 * (migrations + trigger callback) is defined exactly once.
 */
object DbProvider {
    private val _database = MutableStateFlow<AppDatabase?>(null)
    val observe: StateFlow<AppDatabase?> = _database.asStateFlow()

    private val _dataVersion = MutableStateFlow(0L)

    /** Monotonic invalidation token. Bumped on every committed write and on every rebuild. */
    val dataVersion: StateFlow<Long> = _dataVersion.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    private val lock = Any()

    /** Returns the current instance, building it from disk on first use. Idempotent. */
    fun ensure(context: Context): AppDatabase {
        appContext = context.applicationContext
        _database.value?.let { return it }
        return synchronized(lock) {
            _database.value ?: build(context.applicationContext).also { _database.value = it }
        }
    }

    /**
     * Close the current instance and rebuild it from the DB file on disk.
     * Call on Dispatchers.IO. If the rebuild throws (e.g. file is unreadable) the previous
     * instance stays in place and the exception propagates to the caller (screens keep
     * working on the old data; the failure is surfaced, never silently swallowed).
     */
    fun refresh(context: Context) {
        val newDb = try {
            build(context.applicationContext)
        } catch (t: Throwable) {
            android.util.Log.e("DbProvider", "database rebuild failed — keeping previous instance", t)
            _dataVersion.update { it + 1 }
            throw t
        }
        appContext = context.applicationContext
        val old = synchronized(lock) {
            val prev = _database.value
            _database.value = newDb
            prev
        }
        try {
            old?.close()
        } catch (t: Throwable) {
            android.util.Log.w("DbProvider", "failed to close previous instance during refresh", t)
        }
        _dataVersion.update { it + 1 }
    }

    /** [refresh] using the application context captured at [ensure]/[refresh] time. */
    fun refresh() {
        val ctx = appContext
        if (ctx == null) {
            android.util.Log.w("DbProvider", "refresh() called before ensure(context) — bumping version only")
            bumpVersion()
            return
        }
        refresh(ctx)
    }

    /**
     * Same-instance invalidation + version bump. For callers that cannot swap the instance.
     * Room's InvalidationTracker public surface has shifted across releases, so the tracker
     * kick is resolved by reflection and failure is non-fatal — the monotonic [dataVersion]
     * bump is what actually re-keys the Compose loaders and is always applied.
     */
    suspend fun notifyDbChanged() {
        val db = _database.value
        if (db != null) {
            try {
                val tracker = db.invalidationTracker
                val kick = tracker.javaClass.methods.firstOrNull {
                    it.name == "refreshVersionsAsync" || it.name == "refreshAsync"
                }
                kick?.invoke(tracker)
            } catch (t: Throwable) {
                android.util.Log.w("DbProvider", "invalidation tracker kick failed (non-fatal)", t)
            }
        }
        bumpVersion()
    }

    /** In-memory invalidation token only; does not touch the database. */
    fun bumpVersion() {
        _dataVersion.update { it + 1 }
    }

    private fun build(appContext: Context): AppDatabase =
        Room.databaseBuilder(appContext, AppDatabase::class.java, "alazhartex.db")
            .addCallback(AppDatabase.TriggersCallback())
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
}
