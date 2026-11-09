package com.alazhartex.domain.rbac

import com.alazhartex.domain.model.Role

/**
 * RBAC truth table (spec §4.4), enforced in the data layer — never the UI.
 * Actions map to the audit "action" vocabulary. KEEPER write scope is limited to assigned sheds.
 * Masters: ADMIN full CRUD; KEEPER add-only (never delete/regrade); SALES_MANAGER read-only.
 * Settings / Export / Import: ADMIN only. Read: everyone reads; KEEPER cross-read is allowed.
 */
object Rbac {
    const val ACTION_READ = "read"
    const val ACTION_CREATE = "create"
    const val ACTION_UPDATE = "update"
    const val ACTION_DELETE = "delete"
    const val ACTION_REGRADE = "regrade"
    const val ACTION_MASTERS_ADD = "masters.add"
    const val ACTION_MASTERS_DELETE = "masters.delete"
    const val ACTION_SETTINGS = "settings"
    const val ACTION_EXPORT = "export"
    const val ACTION_IMPORT = "import"

    data class Verdict(val allowed: Boolean, val reasonKey: String? = null)

    fun isAssignedKeeper(role: Role, warehouseId: String, assignments: Set<String>): Boolean =
        role == Role.KEEPER && warehouseId in assignments

    fun canRead(role: Role, warehouseId: String, assignments: Set<String>): Verdict =
        when (role) {
            Role.ADMIN, Role.SALES_MANAGER -> Verdict(true)
            Role.KEEPER -> Verdict(true) // cross-read permitted (own + all sheds)
        }

    fun canWriteStock(role: Role, warehouseId: String, assignments: Set<String>): Verdict =
        when (role) {
            Role.ADMIN -> Verdict(true)
            Role.KEEPER -> if (warehouseId in assignments) {
                Verdict(true)
            } else {
                Verdict(false, "rbac.deny.keeper_foreign_shed")
            }
            Role.SALES_MANAGER -> Verdict(false, "rbac.deny.sales_manager_write")
        }

    fun canMastersAdd(role: Role): Verdict =
        when (role) {
            Role.ADMIN, Role.KEEPER -> Verdict(true)
            Role.SALES_MANAGER -> Verdict(false, "rbac.deny.sales_manager_masters")
        }

    fun canMastersDeleteOrRegrade(role: Role): Verdict =
        when (role) {
            Role.ADMIN -> Verdict(true)
            Role.KEEPER -> Verdict(false, "rbac.deny.keeper_shared_delete")
            Role.SALES_MANAGER -> Verdict(false, "rbac.deny.sales_manager_masters")
        }

    fun canSettings(role: Role): Verdict =
        when (role) {
            Role.ADMIN -> Verdict(true)
            Role.KEEPER -> Verdict(false, "rbac.deny.keeper_settings")
            Role.SALES_MANAGER -> Verdict(false, "rbac.deny.sales_manager_settings")
        }

    fun canExportImport(role: Role): Verdict =
        when (role) {
            Role.ADMIN -> Verdict(true)
            Role.KEEPER -> Verdict(false, "rbac.deny.keeper_export_import")
            Role.SALES_MANAGER -> Verdict(false, "rbac.deny.sales_manager_export_import")
        }

    /** Deterministic full truth table for tests (T4): (role x action x shedScope) -> allowed */
    fun truthTable(): List<Triple<Role, String, Boolean>> {
        val rows = mutableListOf<Triple<Role, String, Boolean>>()
        for (role in Role.entries) {
            rows += Triple(role, "$ACTION_READ:wh-alwa", canRead(role, "wh-alwa", emptySet()).allowed)
            rows += Triple(role, "$ACTION_CREATE:wh-625", canWriteStock(role, "wh-625", setOf("wh-625")).allowed)
            rows += Triple(role, "$ACTION_CREATE:wh-alwa-foreign", canWriteStock(role, "wh-alwa", emptySet()).allowed)
            rows += Triple(role, ACTION_MASTERS_ADD, canMastersAdd(role).allowed)
            rows += Triple(role, ACTION_MASTERS_DELETE, canMastersDeleteOrRegrade(role).allowed)
            rows += Triple(role, ACTION_REGRADE, canMastersDeleteOrRegrade(role).allowed)
            rows += Triple(role, ACTION_SETTINGS, canSettings(role).allowed)
            rows += Triple(role, ACTION_EXPORT, canExportImport(role).allowed)
            rows += Triple(role, ACTION_IMPORT, canExportImport(role).allowed)
        }
        return rows
    }
}
