package com.alazhartex.stock.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alazhartex.stock.data.local.DbProvider
import com.alazhartex.stock.data.repo.DashboardDto
import com.alazhartex.stock.data.repo.ReadRepository
import com.alazhartex.stock.data.repo.ShedCardDto
import com.alazhartex.stock.data.repo.TopColourDto
import com.alazhartex.stock.domain.gauge.GaugeBucket
import com.alazhartex.stock.domain.gauge.Utilisation
import com.alazhartex.stock.overrides.DashboardOverrideStore
import com.alazhartex.stock.overrides.DashboardOverrideValues
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** Which overridden field disagrees with the live SQL sums (drives the dashboard warning). */
enum class OverrideField { TOTAL_ROLLS, ACTIVE_ROLLS, INACTIVE_ROLLS, CAPACITY, UTILISATION }

/**
 * Everything the Dashboard renders, pre-resolved to ONE source of truth:
 *  - overrides DISABLED -> live SQL sums (inventory closing ledger + INACTIVE rolls + warehouse capacity)
 *  - overrides ENABLED  -> manual DataStore values, falling back per-field to live SQL sums when blank
 *
 * Resolution chain in override mode (keeps the identity  Total = Active + Inactive  coherent):
 *  inactive    = override ?: sql
 *  total       = override ?: (active + inactive)
 *  active      = override ?: (total − inactive)   — "Active = Total − Inactive" (Task 3)
 *  capacity    = override ?: sql
 *  utilisation = override ?: (total × 100 / capacity), capacity == 0 -> "—" (zero-division guard)
 */
data class DashboardUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val totalRolls: Long = 0,
    val activeRolls: Long = 0,
    val inactiveRolls: Long = 0,
    val capacityRolls: Long = 0,
    /** Raw 1dp utilisation string ("42.5") or null when capacity == 0 — format via percentDisplay(). */
    val percentDisplay: String? = null,
    /** Utilisation as a 0-based fraction for the progress bar (percent / 100). */
    val percentFraction: Float = 0f,
    val bucket: GaugeBucket? = null,
    val driftViolations: Int = 0,
    val topSheds: List<ShedCardDto> = emptyList(),
    val topColours: List<TopColourDto> = emptyList(),
    val overridesEnabled: Boolean = false,
    /** Non-empty only in override mode — every manual value that disagrees with the live SQL sums. */
    val overrideFieldDiffs: List<OverrideField> = emptyList(),
)

/** utilisation bucket thresholds, §9.3 (<50 GREEN, 50..=85 AMBER, >85 RED), from an arbitrary percent. */
private fun bucketForPercent(percent: BigDecimal): GaugeBucket {
    val p = percent.setScale(2, RoundingMode.HALF_UP)
    return when {
        p < BigDecimal("50.00") -> GaugeBucket.GREEN
        p <= BigDecimal("85.00") -> GaugeBucket.AMBER
        else -> GaugeBucket.RED
    }
}

private fun percentToDisplay(value: BigDecimal): String =
    value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

class DashboardViewModel(
    private val readRepository: ReadRepository,
    private val overrideStore: DashboardOverrideStore,
    dbVersion: StateFlow<Long> = DbProvider.dataVersion,
) : ViewModel() {

    /** Last successfully loaded SQL snapshot — reused (with an error note) if a later query fails. */
    private var lastGoodAuto: DashboardDto? = null

    /**
     * Recomputes on any committed write (DbProvider.dataVersion) and on any override edit,
     * so adding/removing rolls or flipping the toggle updates the KPIs instantly.
     */
    val uiState: StateFlow<DashboardUiState> =
        combine(overrideStore.overrides, dbVersion) { overrides, _ -> overrides }
            .mapLatest { overrides -> buildState(overrides) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private suspend fun buildState(o: DashboardOverrideValues): DashboardUiState {
        val auto = runCatching { readRepository.dashboard() }.getOrElse { cause ->
            val cached = lastGoodAuto
            return if (cached != null) {
                resolve(overrides = o, auto = cached, loadError = cause.message)
            } else {
                DashboardUiState(loading = false, loadError = cause.message ?: "Dashboard query failed")
            }
        }
        lastGoodAuto = auto
        return resolve(overrides = o, auto = auto, loadError = null)
    }

    private fun resolve(overrides: DashboardOverrideValues, auto: DashboardDto, loadError: String?): DashboardUiState {
        val enabled = overrides.enabled

        // --- Effective values (SQL by default, manual per-field when enabled) ---
        val inactive = if (enabled) overrides.inactiveRolls ?: auto.inactiveRolls else auto.inactiveRolls
        val capacity = if (enabled) overrides.capacityRolls ?: auto.totalCapacity else auto.totalCapacity
        val activeSeed = if (enabled) overrides.activeRolls ?: auto.activeRolls else auto.activeRolls
        val total = if (enabled) overrides.totalRolls ?: (activeSeed + inactive) else auto.totalRolls
        val active = if (enabled && overrides.activeRolls == null && overrides.totalRolls != null) {
            // An explicitly typed Total takes precedence for the derived Active (Task 3: Active = Total − Inactive).
            (overrides.totalRolls - inactive).coerceAtLeast(0L)
        } else {
            activeSeed
        }

        // --- Utilisation = (Total / Capacity) x 100, zero-capacity safe; manual percent wins in override mode ---
        val sqlPercent = Utilisation.percentOneDp(auto.totalRolls, auto.totalCapacity)
        val effectivePercent: BigDecimal? = when {
            enabled && overrides.utilisationPercent != null ->
                BigDecimal(overrides.utilisationPercent).setScale(1, RoundingMode.HALF_UP)
            capacity == 0L -> null
            else -> Utilisation.percentOneDp(total, capacity)
        }
        val bucket = effectivePercent?.let { bucketForPercent(it) }
            ?: if (capacity == 0L) null else Utilisation.bucket(total, capacity)

        // --- Override vs live SQL sums (Task 4): every manual value that disagrees ---
        val diffs = if (!enabled) {
            emptyList()
        } else {
            buildList {
                if (overrides.totalRolls != null && overrides.totalRolls != auto.totalRolls) add(OverrideField.TOTAL_ROLLS)
                if (overrides.activeRolls != null && overrides.activeRolls != auto.activeRolls) add(OverrideField.ACTIVE_ROLLS)
                if (overrides.inactiveRolls != null && overrides.inactiveRolls != auto.inactiveRolls) add(OverrideField.INACTIVE_ROLLS)
                if (overrides.capacityRolls != null && overrides.capacityRolls != auto.totalCapacity) add(OverrideField.CAPACITY)
                val sqlUtil = sqlPercent?.toDouble()
                if (overrides.utilisationPercent != null &&
                    (sqlUtil == null || kotlin.math.abs(overrides.utilisationPercent - sqlUtil) > 0.05)
                ) {
                    add(OverrideField.UTILISATION)
                }
            }
        }

        return DashboardUiState(
            loading = false,
            loadError = loadError,
            totalRolls = total,
            activeRolls = active,
            inactiveRolls = inactive,
            capacityRolls = capacity,
            percentDisplay = effectivePercent?.let(::percentToDisplay),
            percentFraction = effectivePercent?.let { (it.toFloat() / 100f) } ?: 0f,
            bucket = bucket,
            driftViolations = auto.driftViolations,
            topSheds = auto.topSheds,
            topColours = auto.topColours,
            overridesEnabled = enabled,
            overrideFieldDiffs = diffs,
        )
    }
}

class DashboardViewModelFactory(
    private val readRepository: ReadRepository,
    private val overrideStore: DashboardOverrideStore,
    private val dbVersion: StateFlow<Long> = DbProvider.dataVersion,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DashboardViewModel::class.java)) { "unknown viewModel $modelClass" }
        return DashboardViewModel(readRepository, overrideStore, dbVersion) as T
    }
}
