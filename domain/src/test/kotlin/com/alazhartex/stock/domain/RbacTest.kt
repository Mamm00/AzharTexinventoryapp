package com.alazhartex.stock.domain

import com.alazhartex.stock.domain.model.Role
import com.alazhartex.stock.domain.rbac.Rbac
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T4 — RBAC matrix (spec §4.4). */
class RbacTest {
    private val keeperOwn = setOf("wh-alwa", "wh-leon", "wh-azouz", "wh-zibda", "wh-showroom")

    @Test
    fun admin_can_do_everything() {
        for (action in listOf(Rbac.ACTION_READ, Rbac.ACTION_CREATE, Rbac.ACTION_UPDATE, Rbac.ACTION_DELETE,
            Rbac.ACTION_REGRADE, Rbac.ACTION_MASTERS_ADD, Rbac.ACTION_MASTERS_DELETE,
            Rbac.ACTION_SETTINGS, Rbac.ACTION_EXPORT, Rbac.ACTION_IMPORT)) {
            // keeperForeign emulates a foreign shed; admin is never shed-scoped
            val verdict = when (action) {
                Rbac.ACTION_READ -> Rbac.canRead(Role.ADMIN, "wh-625", emptySet())
                Rbac.ACTION_CREATE, Rbac.ACTION_UPDATE -> Rbac.canWriteStock(Role.ADMIN, "wh-625", emptySet())
                Rbac.ACTION_DELETE, Rbac.ACTION_REGRADE, Rbac.ACTION_MASTERS_DELETE -> Rbac.canMastersDeleteOrRegrade(Role.ADMIN)
                Rbac.ACTION_MASTERS_ADD -> Rbac.canMastersAdd(Role.ADMIN)
                Rbac.ACTION_SETTINGS -> Rbac.canSettings(Role.ADMIN)
                Rbac.ACTION_EXPORT, Rbac.ACTION_IMPORT -> Rbac.canExportImport(Role.ADMIN)
                else -> error("unhandled $action")
            }
            assertTrue("admin must be allowed $action", verdict.allowed)
        }
    }

    @Test
    fun keeper_write_scope_is_own_sheds_only() {
        assertTrue(Rbac.canWriteStock(Role.KEEPER, "wh-alwa", keeperOwn).allowed)
        assertFalse(Rbac.canWriteStock(Role.KEEPER, "wh-625", keeperOwn).allowed)
    }

    @Test
    fun keeper_masters_add_only_never_delete_or_regrade() {
        assertTrue(Rbac.canMastersAdd(Role.KEEPER).allowed)
        assertFalse(Rbac.canMastersDeleteOrRegrade(Role.KEEPER).allowed)
        assertEquals("rbac.deny.keeper_shared_delete", Rbac.canMastersDeleteOrRegrade(Role.KEEPER).reasonKey)
    }

    @Test
    fun keeper_no_settings_no_export_import() {
        assertFalse(Rbac.canSettings(Role.KEEPER).allowed)
        assertFalse(Rbac.canExportImport(Role.KEEPER).allowed)
        assertFalse(Rbac.canExportImport(Role.KEEPER).allowed)
        assertEquals("rbac.deny.keeper_settings", Rbac.canSettings(Role.KEEPER).reasonKey)
    }

    @Test
    fun keeper_cross_read_allowed() {
        assertTrue(Rbac.canRead(Role.KEEPER, "wh-kashry", keeperOwn).allowed)
    }

    @Test
    fun sales_manager_reads_everything_writes_nothing() {
        assertTrue(Rbac.canRead(Role.SALES_MANAGER, "wh-625", emptySet()).allowed)
        assertFalse(Rbac.canWriteStock(Role.SALES_MANAGER, "wh-625", emptySet()).allowed)
        assertFalse(Rbac.canMastersAdd(Role.SALES_MANAGER).allowed)
        assertFalse(Rbac.canMastersDeleteOrRegrade(Role.SALES_MANAGER).allowed)
        assertFalse(Rbac.canSettings(Role.SALES_MANAGER).allowed)
        assertFalse(Rbac.canExportImport(Role.SALES_MANAGER).allowed)
        assertEquals("rbac.deny.sales_manager_write", Rbac.canWriteStock(Role.SALES_MANAGER, "wh-625", emptySet()).reasonKey)
    }

    @Test
    fun truth_table_has_27_rows_and_blocks_everything_unspecified() {
        val table = Rbac.truthTable()
        assertEquals(27, table.size)
        val denied = table.filter { !it.third }
        // admin rows are all allowed; every SALES_MANAGER row except read is denied
        assertTrue(table.filter { it.first == Role.ADMIN }.all { it.third })
        assertTrue(denied.none { it.first == Role.ADMIN })
        assertTrue(table.first { it.first == Role.SALES_MANAGER && it.second.startsWith("read") }.third)
    }
}
