package com.alazhartex.stock.ui

import androidx.annotation.StringRes
import com.alazhartex.stock.R

data class GuideSection(@StringRes val titleRes: Int, @StringRes val bodyRes: Int)

/** Guide screen content pinned by CONTENT.md §4 — six sections, @StringRes keys only. */
object GuideContent {
    val sections: List<GuideSection> = listOf(
        GuideSection(R.string.guide_s1_title, R.string.guide_p1),
        GuideSection(R.string.guide_s2_title, R.string.guide_p2),
        GuideSection(R.string.guide_s3_title, R.string.guide_p3),
        GuideSection(R.string.guide_s4_title, R.string.guide_p4),
        GuideSection(R.string.guide_s5_title, R.string.guide_p5),
        GuideSection(R.string.guide_s6_title, R.string.guide_p6),
    )
}
