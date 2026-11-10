package com.alazhartex.stock.domain

import com.alazhartex.stock.domain.gauge.GaugeBucket
import com.alazhartex.stock.domain.gauge.Utilisation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** T19 — boundary pinning (spec §9.3). */
class UtilisationTest {
    @Test
    fun wh625_scale_test_expression_is_exactly_175_point_2() {
        assertEquals(BigDecimal("175.2"), Utilisation.wh625ScaleTestExpression())
        assertEquals("175.2", Utilisation.percentOneDp(5255, 3000)?.toPlainString())
        assertEquals(GaugeBucket.RED, Utilisation.bucket(5255, 3000))
    }

    @Test
    fun buckets_pin_boundaries() {
        assertEquals(GaugeBucket.GREEN, Utilisation.bucket(0, 1000))
        assertEquals(GaugeBucket.GREEN, Utilisation.bucket(499, 1000)) // 49.90 < 50.00
        assertEquals(GaugeBucket.AMBER, Utilisation.bucket(500, 1000)) // 50.00
        assertEquals(GaugeBucket.AMBER, Utilisation.bucket(850, 1000)) // 85.00
        assertEquals(GaugeBucket.RED, Utilisation.bucket(851, 1000))  // 85.10
    }

    @Test
    fun bucket_boundary_rounding_half_up_at_two_decimals() {
        // 4999/10000 = 49.99% -> GREEN ; 5000/10000 = 50.00 -> AMBER
        assertEquals(GaugeBucket.GREEN, Utilisation.bucket(4999, 10000))
        assertEquals(GaugeBucket.AMBER, Utilisation.bucket(5000, 10000))
        // 8500/10000 = 85.00 -> AMBER ; 8501/10000 = 85.01 -> RED
        assertEquals(GaugeBucket.AMBER, Utilisation.bucket(8500, 10000))
        assertEquals(GaugeBucket.RED, Utilisation.bucket(8501, 10000))
    }

    @Test
    fun empty_shed_renders_zero_percent_not_no_data() {
        assertEquals("0", Utilisation.percentOneDp(0, 1000)?.toPlainString())
        assertEquals(GaugeBucket.GREEN, Utilisation.bucket(0, 1000))
    }

    @Test
    fun capacity_zero_renders_em_dash_not_infinity_not_zero() {
        assertNull(Utilisation.percentOneDp(5, 0))
        assertNull(Utilisation.bucket(5, 0))
        assertEquals(BigDecimal.ZERO, Utilisation.percentTwoDp(5, 0))
    }

    @Test
    fun display_drops_trailing_point_zero() {
        assertEquals("50", Utilisation.percentOneDp(500, 1000)?.toPlainString())
        assertEquals("85.1", Utilisation.percentOneDp(851, 1000)?.toPlainString())
        assertEquals("175.2", Utilisation.percentOneDp(5255, 3000)?.toPlainString())
        assertEquals("100", Utilisation.percentOneDp(1000, 1000)?.toPlainString())
    }

    @Test
    fun negative_inputs_refused() {
        assertThrows(IllegalArgumentException::class.java) { Utilisation.percentTwoDp(-1, 100) }
        assertThrows(IllegalArgumentException::class.java) { Utilisation.percentTwoDp(1, -100) }
    }

    @Test
    fun closing_zero_is_legal() {
        assertTrue(Utilisation.percentOneDp(0, 3000) != null)
    }
}
