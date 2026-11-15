package com.alazhartex.stock.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * v1.8.0 haptics, user-initiated actions ONLY. Ambient drift status, background refresh
 * and passive data changes never call these. All calls delegate to View.performHapticFeedback,
 * which honors the system haptics setting (disabled → silent no-op) — so every function is
 * safe to call unconditionally from user gestures. Usage: `val view = LocalView.current`,
 * then `Haptics.detent(view)` inside the click/drag handler.
 */
object Haptics {

    /** Small tick — selection moved one detent (tab, chip, stepper). */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Warning — destructive step reached (two-stage confirm armed). */
    fun warning(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Confirm — commit succeeded (export/import done, save done). */
    fun confirm(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            },
        )
    }

    /** Detent — discrete snap point (segmented selection, FAB open, swatch select). */
    fun detent(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
}
