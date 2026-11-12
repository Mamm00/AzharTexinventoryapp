package com.alazhartex.stock.domain

import com.alazhartex.stock.domain.palette.CustomSwatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomSwatchTest {

    @Test
    fun `parse accepts uppercase and lowercase hex`() {
        assertNotNull(CustomSwatch.parse("#FF8800"))
        assertNotNull(CustomSwatch.parse("#ff8800"))
        assertEquals("#FF8800", CustomSwatch.parse("#ff8800")?.hex())
    }

    @Test
    fun `parse accepts 8-digit hex and drops alpha`() {
        assertEquals("#112233", CustomSwatch.parse("#80112233")?.hex())
    }

    @Test
    fun `parse rejects malformed input`() {
        assertNull(CustomSwatch.parse(""))
        assertNull(CustomSwatch.parse("#FFF"))
        assertNull(CustomSwatch.parse("#GGGGGG"))
        assertNull(CustomSwatch.parse("FF8800"))
        assertNull(CustomSwatch.parse(null))
        assertNull(CustomSwatch.parse("#FF8800123"))
    }

    @Test
    fun `normalizeHex canonicalizes case`() {
        assertEquals("#A1B2C3", CustomSwatch.normalizeHex("#a1b2c3"))
        assertEquals("#A1B2C3", CustomSwatch.normalizeHex("  #A1B2C3  "))
        assertNull(CustomSwatch.normalizeHex("not a color"))
    }

    @Test
    fun `isValidHex mirrors parse`() {
        assertTrue(CustomSwatch.isValidHex("#000000"))
        assertTrue(CustomSwatch.isValidHex("#FFFFFF"))
        assertFalse(CustomSwatch.isValidHex("#12345"))
        assertFalse(CustomSwatch.isValidHex(null))
    }

    @Test
    fun `on-colour is black on light colours and white on dark colours`() {
        assertEquals(0xFF000000.toInt(), CustomSwatch.onColourFor(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), CustomSwatch.onColourFor(0xFF000000.toInt()))
        // mid-grey picks exactly one deterministically and never crashes
        val mid = CustomSwatch.onColourFor(0xFF808080.toInt())
        assertTrue(mid == 0xFF000000.toInt() || mid == 0xFFFFFFFF.toInt())
    }

    @Test
    fun `parse produces opaque alpha`() {
        assertEquals(0xFF000000.toInt() and 0xFF000000.toInt(), CustomSwatch.parse("#123456")?.argb?.and(0xFF000000.toInt()))
    }
}
