package com.alazhartex.stock.ui

import androidx.compose.runtime.compositionLocalOf
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Digits rendering (spec §9.3/§9.21, default OFF -> Western digits everywhere). */
val LocalArabicIndicDigits = compositionLocalOf { false }

private val WESTERN = Locale.forLanguageTag("en-US-u-nu-latn")
private val ARABIC_INDIC = Locale.forLanguageTag("ar-EG-u-nu-arab")
private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun integerString(value: Long, arabicIndic: Boolean): String =
    NumberFormat.getIntegerInstance(if (arabicIndic) ARABIC_INDIC else WESTERN).format(value)

fun percentDisplay(oneDp: String?, arabicIndic: Boolean): String {
    val raw = oneDp ?: "—"
    if (!arabicIndic || raw == "—") return raw
    val nf = NumberFormat.getNumberInstance(ARABIC_INDIC)
    val parsed = raw.toBigDecimalOrNull() ?: return raw
    return nf.format(parsed)
}

fun dateString(epochMillis: Long): String = DATE.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
fun dateTimeString(epochMillis: Long): String = DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
fun todayInCairo(): String = DATE.format(java.time.LocalDate.now(ZoneId.of("Africa/Cairo")))
