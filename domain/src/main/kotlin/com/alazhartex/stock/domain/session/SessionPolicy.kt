package com.alazhartex.stock.domain.session

/**
 * Session semantics pinned by spec §9.4 (T8).
 *  - 12h window measured with ELAPSED_REALTIME_WAKEUP; persisted (issued_at_wall, issued_at_elapsed, boot_id) tuple.
 *  - boot_id match  -> use elapsed realtime delta (wall clock may jump, monotonic is authoritative).
 *  - boot_id changed -> wall-clock subtraction is authoritative (cold boot).
 *  - wall clock went backwards by > 5 minutes -> SESSION_CLOCK_ANOMALY refusal.
 */
object SessionPolicy {
    const val SESSION_DURATION_MILLIS: Long = 12L * 60L * 60L * 1000L
    const val RESUME_REISSUE_THRESHOLD_MILLIS: Long = 60L * 1000L
    const val CLOCK_ANOMALY_TOLERANCE_MILLIS: Long = 5L * 60L * 1000L

    enum class Verdict { ACTIVE, EXPIRED_REFRESHABLE, ANOMALY }
    enum class VerdictContext { ADMIN_MUTATION, ADMIN_FOREGROUND, KEEPER_MUTATION, KEEPER_FOREGROUND }

    data class SessionTuple(
        val issuedAtWall: Long,
        val issuedAtElapsed: Long,
        val bootId: String,
        val expiresAtWall: Long,
        val isActive: Boolean,
    )

    /**
     * @param nowWall wall-clock millis (System.currentTimeMillis)
     * @param nowElapsed elapsed-realtime millis (SystemClock.elapsedRealtime)
     * @param currentBootId persisted-or-provided boot identifier of the running device
     */
    fun evaluate(
        tuple: SessionTuple,
        nowWall: Long,
        nowElapsed: Long,
        currentBootId: String,
    ): Verdict {
        if (!tuple.isActive) return Verdict.EXPIRED_REFRESHABLE

        if (tuple.bootId == currentBootId) {
            // Monotonic clock is authoritative within a boot.
            val elapsed = nowElapsed - tuple.issuedAtElapsed
            if (elapsed >= SESSION_DURATION_MILLIS || nowWall >= tuple.expiresAtWall) {
                return Verdict.EXPIRED_REFRESHABLE
            }
            return Verdict.ACTIVE
        }

        // Cold boot: wall clock is authoritative.
        val wallDelta = nowWall - tuple.issuedAtWall
        val backwardsBy = tuple.issuedAtWall - nowWall
        if (backwardsBy > CLOCK_ANOMALY_TOLERANCE_MILLIS) return Verdict.ANOMALY
        return if (wallDelta >= SESSION_DURATION_MILLIS || nowWall >= tuple.expiresAtWall) {
            Verdict.EXPIRED_REFRESHABLE
        } else {
            Verdict.ACTIVE
        }
    }

    /**
     * Mutation-time resolution per role (§9.4):
     *  - ADMIN: expired -> silent reissue + proceed (caller writes session.refresh audit)
     *  - KEEPER: expired -> refusal (no refresh); ANOMALY -> refusal always
     */
    fun resolveForMutation(roleIsAdmin: Boolean, verdict: Verdict): MutationResolution = when (verdict) {
        Verdict.ACTIVE -> MutationResolution.PROCEED
        Verdict.EXPIRED_REFRESHABLE ->
            if (roleIsAdmin) MutationResolution.SILENT_REISSUE_AND_PROCEED else MutationResolution.REFUSE_EXPIRED
        Verdict.ANOMALY -> MutationResolution.REFUSE_ANOMALY
    }

    enum class MutationResolution { PROCEED, SILENT_REISSUE_AND_PROCEED, REFUSE_EXPIRED, REFUSE_ANOMALY }
}
