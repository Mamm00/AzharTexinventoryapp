package com.alazhartex.stock.ui.navigation

/** All twelve destinations (spec §4.5). */
object Routes {
    const val GUIDE = "guide"
    const val STOCK = "stock"
    const val INVENTORY = "inventory"
    const val MOVEMENTS = "movements"
    const val COLOURS = "colours"
    const val CROSS_VIEW = "crossview"
    const val SWATCH_BOOK = "swatchbook"
    const val DASHBOARD = "dashboard"
    const val REPORTS = "reports"
    const val MASTERS = "masters"
    const val FABRICS_EDIT = "fabrics_edit"
    const val AUDIT = "audit"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    val ALL = listOf(GUIDE, STOCK, INVENTORY, MOVEMENTS, COLOURS, CROSS_VIEW, SWATCH_BOOK, DASHBOARD, REPORTS, MASTERS, FABRICS_EDIT, AUDIT, SETTINGS, PROFILE)
}
