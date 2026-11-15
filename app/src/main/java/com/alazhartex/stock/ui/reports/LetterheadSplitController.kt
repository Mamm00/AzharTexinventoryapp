package com.alazhartex.stock.ui.reports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Phase of the letterhead split-button leading segment. */
enum class SplitPhase { Idle, Working, Done }

/**
 * v1.8.0 controller behind the letterhead split button. Two separate idempotent actions,
 * both routed through the EXISTING Settings/Reports flows (nothing here writes new state):
 *  - generate(): guards re-entry by keying off the phase; the wrapped [work] is whatever
 *    the Reports screen already does (buildCsv + writeCsvFile + preview) — no new mutation path.
 *  - setLetterhead(): persists the same "profile.letterhead_enabled" key the Settings
 *    letterhead section owns; rewriting the same value is inherently idempotent.
 */
class LetterheadSplitController(private val scope: CoroutineScope) {

    private val _phase = MutableStateFlow(SplitPhase.Idle)
    val phase: StateFlow<SplitPhase> = _phase.asStateFlow()

    fun generate(work: suspend () -> Unit) {
        if (_phase.value != SplitPhase.Idle) return
        scope.launch {
            _phase.value = SplitPhase.Working
            try {
                work()
            } finally {
                _phase.value = SplitPhase.Done
                delay(1400)
                _phase.value = SplitPhase.Idle
            }
        }
    }

    fun setLetterhead(enabled: Boolean, persist: suspend (Boolean) -> Unit) {
        scope.launch { persist(enabled) }
    }
}
