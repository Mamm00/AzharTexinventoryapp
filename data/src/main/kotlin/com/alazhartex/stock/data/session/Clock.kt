package com.alazhartex.stock.data.session

/** Injectable clock (spec §9.5) — fixed instance in tests, System-backed in production. */
interface AppClock {
    fun wallMillis(): Long
    fun elapsedRealtimeMillis(): Long
}

class SystemAppClock : AppClock {
    override fun wallMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = android.os.SystemClock.elapsedRealtime()
}
