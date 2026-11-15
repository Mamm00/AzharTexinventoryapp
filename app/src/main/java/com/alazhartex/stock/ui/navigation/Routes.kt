package com.alazhartex.stock.ui.navigation

/** Every destination (spec §4.5 baseline + Dashboard Settings admin screen). */
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
    const val DASHBOARD_SETTINGS = "dashboard_settings"
    const val PROFILE = "profile"
    const val STOCK_DETAIL = "stock_detail"
    const val INVENTORY_DETAIL = "inventory_detail"
    fun stockDetail(shedId: String) = "$STOCK_DETAIL/$shedId"
    fun inventoryDetail(mappingId: String) = "$INVENTORY_DETAIL/$mappingId"
    val ALL = listOf(GUIDE, STOCK, INVENTORY, MOVEMENTS, COLOURS, CROSS_VIEW, SWATCH_BOOK, DASHBOARD, REPORTS, MASTERS, FABRICS_EDIT, AUDIT, SETTINGS, DASHBOARD_SETTINGS, PROFILE)
}
