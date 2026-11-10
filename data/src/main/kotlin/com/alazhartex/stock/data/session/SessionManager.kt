package com.alazhartex.stock.data.session

import com.alazhartex.stock.data.local.SessionDao
import com.alazhartex.stock.data.local.SessionEntity
import com.alazhartex.stock.data.repo.WriteContext
import com.alazhartex.stock.domain.session.SessionPolicy
import java.util.UUID

/**
 * Session lifecycle (spec §9.4, RES-008).
 *  - No login UI anywhere; admin is auto-signed-in on every cold start.
 *  - App foreground (first boot or resume after > 1 minute): missing/expired admin session ->
 *    silent re-issue + one audit row action=session.reissue.
 *  - Mutation past 12h: silent re-issue + one audit row action=session.refresh, then proceed.
 *  - Keeper sessions: strict 12h, no refresh (data model only; never created by the app shell).
 *  - boot_id changes on every process start; cold-boot path uses wall clock (RES-008).
 */
class SessionManager(
    private val sessionDao: SessionDao,
    private val auditWriter: suspend (WriteContext, action: String, target: String, result: String, reason: String?) -> Unit,
    private val clock: AppClock,
    private val currentBootId: () -> String,
) {
    suspend fun ensureAdminSessionOnForeground() {
        val boot = currentBootId()
        val existing = sessionDao.latestActive("admin")
        if (existing == null) {
            issueSession("admin", boot)
            auditWriter(writeContextFor("admin", "session.reissue"), "session.reissue", "admin", "ALLOWED", "missing_session")
            return
        }
        val verdict = SessionPolicy.evaluate(
            SessionPolicy.SessionTuple(existing.issuedAtWall, existing.issuedAtElapsed, existing.bootId, existing.expiresAtWall, existing.isActive),
            clock.wallMillis(),
            clock.elapsedRealtimeMillis(),
            boot,
        )
        when (verdict) {
            SessionPolicy.Verdict.ACTIVE -> Unit
            SessionPolicy.Verdict.EXPIRED_REFRESHABLE -> {
                sessionDao.deactivateFor("admin")
                issueSession("admin", boot)
                auditWriter(writeContextFor("admin", "session.reissue"), "session.reissue", "admin", "ALLOWED", "expired_reissue")
            }
            SessionPolicy.Verdict.ANOMALY -> {
                // Never brick the app (§9.4): refuse the reissue path, keep the UI usable, audit BLOCKED.
                auditWriter(writeContextFor("admin", "session.reissue"), "session.reissue", "admin", "BLOCKED", "SESSION_CLOCK_ANOMALY")
            }
        }
    }

    /** Called by the transaction runner BEFORE every mutation. Returns the action to take. */
    suspend fun mutationSessionVerdict(actorId: String, actorIsAdmin: Boolean): SessionPolicy.MutationResolution {
        val boot = currentBootId()
        val existing = sessionDao.latestActive(actorId)
        if (existing == null) {
            return if (actorIsAdmin) {
                issueSession("admin", boot)
                auditWriter(writeContextFor(actorId, "session.refresh"), "session.refresh", actorId, "ALLOWED", "silent_refresh_missing")
                SessionPolicy.MutationResolution.PROCEED
            } else {
                SessionPolicy.MutationResolution.REFUSE_EXPIRED
            }
        }
        val verdict = SessionPolicy.evaluate(
            SessionPolicy.SessionTuple(existing.issuedAtWall, existing.issuedAtElapsed, existing.bootId, existing.expiresAtWall, existing.isActive),
            clock.wallMillis(),
            clock.elapsedRealtimeMillis(),
            boot,
        )
        val resolution = SessionPolicy.resolveForMutation(actorIsAdmin, verdict)
        if (resolution == SessionPolicy.MutationResolution.SILENT_REISSUE_AND_PROCEED) {
            sessionDao.deactivateFor(actorId)
            issueSession(actorId, boot)
            auditWriter(writeContextFor(actorId, "session.refresh"), "session.refresh", actorId, "ALLOWED", "silent_refresh")
            return SessionPolicy.MutationResolution.PROCEED
        }
        return resolution
    }

    /** Session-expiry countdown for My Profile screen. */
    suspend fun adminSessionExpiresAtWall(): Long? = sessionDao.latestActive("admin")?.expiresAtWall

    private suspend fun issueSession(accountId: String, bootId: String): SessionEntity {
        val now = clock.wallMillis()
        val row = SessionEntity(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            issuedAtWall = now,
            issuedAtElapsed = clock.elapsedRealtimeMillis(),
            bootId = bootId,
            expiresAtWall = now + SessionPolicy.SESSION_DURATION_MILLIS,
            isActive = true,
        )
        sessionDao.insert(row)
        return row
    }

    private fun writeContextFor(actorId: String, action: String) =
        WriteContext(actorId = actorId, idempotencyKey = UUID.randomUUID().toString(), action = action, wallMillis = clock.wallMillis())

    /** Debug-only account switch for androidTest (RES-022); never exposed in release builds. */
    suspend fun debugSwitchAccountForTests(accountId: String) {
        val boot = currentBootId()
        sessionDao.deactivateFor(accountId)
        issueSession(accountId, boot)
    }
}
