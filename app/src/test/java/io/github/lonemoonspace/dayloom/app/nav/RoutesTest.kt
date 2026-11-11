package io.github.lonemoonspace.dayloom.app.nav

import io.github.lonemoonspace.dayloom.app.ModuleRegistry
import io.github.lonemoonspace.dayloom.app.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    @Test
    fun `every module tab has its own route that maps back to the module`() {
        val ids = ModuleRegistry.modules.map { it.id }
        val routes = ids.map(Routes::module)
        // One route per module: a shared pattern made tabs restore each other's state. / 每个模块一个路由：共用模式会让标签页恢复彼此的状态。
        assertEquals(ids.size, routes.toSet().size)
        assertEquals(ids, routes.map(Routes::moduleIdOf))
        assertNull(Routes.moduleIdOf(Routes.HOME))
        assertNull(Routes.moduleIdOf(Routes.SETTINGS))
        assertNull(Routes.moduleIdOf(null))
    }

    @Test
    fun `settings pages and module settings pages map back and count as the settings tab`() {
        SettingsPage.entries.forEach { page ->
            val route = Routes.settingsPage(page)
            assertEquals(page, Routes.settingsPageOf(route))
            assertNull(Routes.settingsModuleOf(route))
            assertTrue(Routes.isSettings(route))
        }
        ModuleRegistry.modules.map { it.id }.forEach { id ->
            val route = Routes.settingsModule(id)
            assertEquals(id, Routes.settingsModuleOf(route))
            assertNull("not mistaken for a page", Routes.settingsPageOf(route))
            assertNull("not mistaken for the module's tab", Routes.moduleIdOf(route))
            assertTrue(Routes.isSettings(route))
        }
        assertTrue(Routes.isSettings(Routes.SETTINGS))
        assertFalse(Routes.isSettings(Routes.HOME))
        assertFalse(Routes.isSettings(Routes.module("news")))
        assertNull(Routes.settingsPageOf(Routes.SETTINGS))
    }

    @Test
    fun `a module page opened with the gear belongs to that module's tab`() {
        val page = Routes.settingsModule("news")
        assertEquals("news", Routes.gearPageOf(page, Routes.module("news")))
        assertNull("opened from the settings list", Routes.gearPageOf(page, Routes.SETTINGS))
        assertNull("another module's tab", Routes.gearPageOf(page, Routes.module("football")))
        assertNull("not a module page", Routes.gearPageOf(Routes.settingsPage(SettingsPage.ABOUT), Routes.module("news")))
    }
}
