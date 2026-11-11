package com.alazhartex.stock.domain

import com.alazhartex.stock.domain.palette.PaletteResolver
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * v1.1 (delta §10.1, §14 D20/D21, §15 T27/T28).
 * D20/D25: recomputation over the committed seed must byte-match palette-report.txt.
 * D21: every seeded colour's chosen on-colour contrast >= 4.5 (WCAG AA normal text).
 * Determinism: same input -> same output; no Locale/TimeZone/clock/Random influence.
 */
class PaletteResolverTest {

    @Test
    fun fnv1a_pinned_vectors() {
        assertEquals(2166136261u, PaletteResolver.stableHash32(""))
        assertEquals(3826002220u, PaletteResolver.stableHash32("a"))
        assertEquals(1447629737u, PaletteResolver.stableHash32("C001"))
        assertEquals(2149221560u, PaletteResolver.stableHash32("C208"))
        assertEquals(3372527090u, PaletteResolver.stableHash32("alazhartex.seed"))
    }

    @Test
    fun resolve_is_deterministic_per_call() {
        val a = PaletteResolver.resolve("silk", "D", "C001")
        val b = PaletteResolver.resolve("silk", "D", "C001")
        assertEquals(a, b)
    }

    @Test
    fun argb_is_opaque_and_hex_formats_six_digits() {
        val sw = PaletteResolver.resolve("cotton", "A", "C004")
        assertEquals(0xFF, (sw.argb ushr 24) and 0xFF)
        assertTrue(sw.hex().matches(Regex("#[0-9A-F]{6}")))
        assertTrue(sw.onHex() == "#000000" || sw.onHex() == "#FFFFFF")
    }

    @Test
    fun unknown_family_and_grade_are_rejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PaletteResolver.resolve("cashmere", "A", "C999")
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PaletteResolver.resolve("silk", "E", "C999")
        }
    }

    @Test
    fun every_seeded_colour_matches_committed_palette_report_and_meets_contrast() {
        val seed = findFile("seed-alazhartex.json") ?: run { assumeTrue("seed artifact not found", false); return }
        val report = findFile("palette-report.txt") ?: run { assumeTrue("palette report not found", false); return }

        val reportLines = report.readLines()
        assertEquals("alazhartex-palette-report v1", reportLines[0])
        assertEquals("generator=alazhartex-generator/1.0.0", reportLines[1])
        val expectedCount = reportLines[2].removePrefix("colours=").toInt()
        val rows = reportLines.drop(3).filter { it.isNotBlank() }
        assertEquals(expectedCount, rows.size)

        val seedColours = LinkedHashMap<String, JSONObject>()
        val root = JSONObject(seed.readText())
        val colours = root.getJSONArray("colours")
        for (i in 0 until colours.length()) {
            val c = colours.getJSONObject(i)
            seedColours[c.getString("id")] = c
        }

        var minContrast = Double.MAX_VALUE
        for (line in rows) {
            val parts = line.split(',')
            assertEquals("report line must have 4 fields: $line", 4, parts.size)
            val c = seedColours[parts[0]] ?: error("report id not in seed: ${parts[0]}")
            val sw = PaletteResolver.resolve(c.getString("family"), c.getString("grade"), c.getString("code"))
            assertEquals("hex mismatch for ${c.getString("code")}", sw.hex(), parts[1])
            assertEquals("on-colour mismatch for ${c.getString("code")}", sw.onHex(), parts[2])
            val ratio = Math.round(sw.contrastRatio * 10000.0) / 10000.0
            assertEquals("contrast ratio mismatch for ${c.getString("code")}", ratio, parts[3].toDouble(), 0.00005)
            minContrast = minOf(minContrast, sw.contrastRatio)
        }
        assertTrue("min contrast $minContrast must be >= 4.5 (WCAG AA)", minContrast >= 4.5)
    }

    @Test
    fun gradient_stops_wrap_and_keep_five_entries() {
        val stops = PaletteResolver.gradientStopHues(0)
        assertEquals(listOf(340, 350, 0, 10, 20), stops)
        assertEquals(5, PaletteResolver.gradientStopHues(350).size)
        assertTrue(PaletteResolver.gradientStopHues(350).all { it in 0..359 })
    }

    private fun findFile(name: String): File? {
        for (candidate in listOf(File(name), File("..", name), File("../..", name))) {
            if (candidate.isFile) return candidate
        }
        return null
    }
}
