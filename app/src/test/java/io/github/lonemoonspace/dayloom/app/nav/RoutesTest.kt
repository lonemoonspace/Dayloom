package io.github.lonemoonspace.dayloom.app.nav

import io.github.lonemoonspace.dayloom.app.ModuleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
