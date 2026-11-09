package com.alazhartex.domain.gauge

import java.math.BigDecimal
import java.math.RoundingMode

/** Gauge buckets pinned by spec §9.3. */
enum class GaugeBucket { GREEN, AMBER, RED }

/**
 * Utilisation math, pinned exactly (§9.3, T19):
 *  - capacity == 0 -> null (UI renders "—")
 *  - buckets compare on the 2-decimal HALF_UP percentage: < 50.00 GREEN; 50.00..=85.00 AMBER; > 85.00 RED
 *  - display percent: HALF_UP to 1 decimal, trailing ".0" dropped ("175.2%", "50%", "85.1%")
 *  - closing == 0 is legal and yields 0%
 */
object Utilisation {
    private val HUNDRED = BigDecimal("100")

    fun percentTwoDp(activeRolls: Long, capacityRolls: Long): BigDecimal {
        require(activeRolls >= 0) { "active rolls must be >= 0" }
        require(capacityRolls >= 0) { "capacity must be >= 0" }
        if (capacityRolls == 0L) return BigDecimal.ZERO
        return BigDecimal(activeRolls)
            .divide(BigDecimal(capacityRolls), 4, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(2, RoundingMode.HALF_UP)
    }

    fun percentOneDp(activeRolls: Long, capacityRolls: Long): BigDecimal? {
        if (capacityRolls == 0L) return null
        return BigDecimal(activeRolls)
            .divide(BigDecimal(capacityRolls), 4, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(1, RoundingMode.HALF_UP)
            .stripTrailingZeros()
    }

    fun bucket(activeRolls: Long, capacityRolls: Long): GaugeBucket? {
        if (capacityRolls == 0L) return null
        val pct = percentTwoDp(activeRolls, capacityRolls)
        val fifty = BigDecimal("50.00")
        val eightyFive = BigDecimal("85.00")
        return when {
            pct.compareTo(fifty) < 0 -> GaugeBucket.GREEN
            pct.compareTo(eightyFive) <= 0 -> GaugeBucket.AMBER
            else -> GaugeBucket.RED
        }
    }

    /** §9.3 exact expression for the wh-625 scale fixture: 175.2 */
    fun wh625ScaleTestExpression(): BigDecimal =
        BigDecimal(5255).divide(BigDecimal(3000), 4, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(1, RoundingMode.HALF_UP)
            .stripTrailingZeros()
}
