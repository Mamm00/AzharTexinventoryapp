package com.alazhartex.stock.domain

import com.alazhartex.stock.domain.session.SessionPolicy
import com.alazhartex.stock.domain.session.SessionPolicy.MutationResolution
import com.alazhartex.stock.domain.session.SessionPolicy.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

/** T8 — continuous-use refresh, cold-boot survival, clock-anomaly refusal (spec §9.4). */
class SessionPolicyTest {
    private val base = SessionPolicy.SessionTuple(
        issuedAtWall = 1_000_000L,
        issuedAtElapsed = 50_000L,
        bootId = "boot-A",
        expiresAtWall = 1_000_000L + SessionPolicy.SESSION_DURATION_MILLIS,
        isActive = true,
    )

    @Test
    fun fresh_session_is_active() {
        assertEquals(Verdict.ACTIVE, SessionPolicy.evaluate(base, 1_000_000L + 1000L, 51_000L, "boot-A"))
    }

    @Test
    fun continuous_use_past_12h_refreshes_for_admin_only() {
        val elapsed = base.issuedAtElapsed + SessionPolicy.SESSION_DURATION_MILLIS + 1L
        val verdict = SessionPolicy.evaluate(base, base.issuedAtWall + SessionPolicy.SESSION_DURATION_MILLIS + 1L, elapsed, "boot-A")
        assertEquals(Verdict.EXPIRED_REFRESHABLE, verdict)
        assertEquals(MutationResolution.SILENT_REISSUE_AND_PROCEED, SessionPolicy.resolveForMutation(true, verdict))
        assertEquals(MutationResolution.REFUSE_EXPIRED, SessionPolicy.resolveForMutation(false, verdict))
    }

    @Test
    fun cold_boot_survives_within_12h_by_wall_clock() {
        val verdict = SessionPolicy.evaluate(base, base.issuedAtWall + 60_000L, 500L, "boot-B")
        assertEquals(Verdict.ACTIVE, verdict)
    }

    @Test
    fun cold_boot_past_12h_refuses() {
        val verdict = SessionPolicy.evaluate(base, base.issuedAtWall + SessionPolicy.SESSION_DURATION_MILLIS + 1000L, 500L, "boot-B")
        assertEquals(Verdict.EXPIRED_REFRESHABLE, verdict)
    }

    @Test
    fun backwards_wall_clock_beyond_tolerance_is_anomaly() {
        val backwards = base.issuedAtWall + 60_000L // issued in "future" relative to now
        val tuple = base.copy(issuedAtWall = backwards)
        val verdict = SessionPolicy.evaluate(tuple, backwards - SessionPolicy.CLOCK_ANOMALY_TOLERANCE_MILLIS - 1L, 500L, "boot-B")
        assertEquals(Verdict.ANOMALY, verdict)
        assertEquals(MutationResolution.REFUSE_ANOMALY, SessionPolicy.resolveForMutation(true, verdict))
        assertEquals(MutationResolution.REFUSE_ANOMALY, SessionPolicy.resolveForMutation(false, verdict))
    }

    @Test
    fun backwards_wall_clock_within_tolerance_is_not_anomaly() {
        val backwards = base.issuedAtWall + 60_000L
        val tuple = base.copy(issuedAtWall = backwards)
        val verdict = SessionPolicy.evaluate(tuple, backwards - SessionPolicy.CLOCK_ANOMALY_TOLERANCE_MILLIS + 1000L, 500L, "boot-B")
        // within tolerance; still valid time range -> ACTIVE
        assertEquals(Verdict.ACTIVE, verdict)
    }

    @Test
    fun inactive_session_refuses_refresh_path() {
        val verdict = SessionPolicy.evaluate(base.copy(isActive = false), base.issuedAtWall + 1000L, base.issuedAtElapsed + 1000L, "boot-A")
        assertEquals(Verdict.EXPIRED_REFRESHABLE, verdict)
    }
}
