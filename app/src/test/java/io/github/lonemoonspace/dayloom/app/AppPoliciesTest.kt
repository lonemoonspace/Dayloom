package io.github.lonemoonspace.dayloom.app

import io.github.lonemoonspace.dayloom.app.home.CardOrderPolicy
import io.github.lonemoonspace.dayloom.app.home.CardOrderPolicy.Card
import io.github.lonemoonspace.dayloom.app.nav.DeepLinkPolicy
import io.github.lonemoonspace.dayloom.app.nav.DeepLinkPolicy.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPoliciesTest {

    private fun info(id: String, default: Boolean = true, ids: IntRange = IntRange.EMPTY) = ModulePolicy.ModuleInfo(id, default, ids)

    // ---- Registry / 注册表 ----

    @Test
    fun `the real registry is valid`() {
        val infos = ModuleRegistry.modules.map { ModulePolicy.ModuleInfo(it.id, it.defaultEnabled, it.notificationIds, it.splitFrom) }
        assertEquals(emptyList<String>(), ModulePolicy.problems(infos))
    }

    @Test
    fun `duplicate, malformed and reserved ids and overlapping notification ranges are reported`() {
        val problems = ModulePolicy.problems(
            listOf(
                info("weather", ids = 1_000..1_999),
                info("weather"),
                info("Bad-Id"),
                info("settings"),
                info("transit", ids = 1_500..2_499),
                info("news", ids = 2_500..2_599),
                info("brief", ids = 900..1_099),
            ),
        )
        assertTrue(problems.any { it.startsWith("duplicate module id: weather") })
        assertTrue(problems.any { it.startsWith("invalid module id: Bad-Id") })
        assertTrue(problems.any { it.startsWith("reserved module id: settings") })
        assertTrue(problems.any { it.startsWith("notification ids overlap: weather") && "transit" in it })
        assertTrue("adjacent ranges do not overlap", problems.none { "news" in it })
        assertTrue("the shell's own ids are off limits", problems.any { "brief" in it && "shell" in it })
    }

    @Test
    fun `explicit choices win over defaults and modules added later keep their default`() {
        val modules = listOf(info("a"), info("b", default = false), info("c"))
        assertEquals(listOf("a", "c"), ModulePolicy.enabledIds(modules, emptyMap()))
        assertEquals(listOf("b", "c"), ModulePolicy.enabledIds(modules, mapOf("a" to false, "b" to true)))
        assertEquals("unknown ids in the settings are ignored", listOf("a", "c"), ModulePolicy.enabledIds(modules, mapOf("gone" to true)))
    }

    @Test
    fun `a split-out module follows the former module's switch until its own is flipped`() {
        val modules = listOf(
            ModulePolicy.ModuleInfo("train", false, IntRange.EMPTY, splitFrom = "transit"),
            ModulePolicy.ModuleInfo("bus", false, IntRange.EMPTY, splitFrom = "transit"),
        )
        assertEquals(emptyList<String>(), ModulePolicy.enabledIds(modules, emptyMap()))
        assertEquals(listOf("train", "bus"), ModulePolicy.enabledIds(modules, mapOf("transit" to true)))
        assertEquals(listOf("train"), ModulePolicy.enabledIds(modules, mapOf("transit" to true, "bus" to false)))
        assertEquals(listOf("bus"), ModulePolicy.enabledIds(modules, mapOf("transit" to false, "bus" to true)))
    }

    @Test
    fun `a module cannot be split from one that is still registered`() {
        val problems = ModulePolicy.problems(listOf(info("transit"), ModulePolicy.ModuleInfo("train", false, IntRange.EMPTY, splitFrom = "transit")))
        assertEquals(listOf("train is split from transit, which is still registered"), problems)
    }

    @Test
    fun `notification ids are offsets into the module's range`() {
        assertEquals(5_000, ModulePolicy.notificationId("weather", 5_000..5_099, 0))
        assertEquals(5_099, ModulePolicy.notificationId("weather", 5_000..5_099, 99))
        assertThrows(IllegalArgumentException::class.java) { ModulePolicy.notificationId("weather", 5_000..5_099, 100) }
        assertThrows(IllegalArgumentException::class.java) { ModulePolicy.notificationId("weather", 5_000..5_099, -1) }
    }

    @Test
    fun `a module without a notification range gets a message that says so`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ModulePolicy.notificationId("weather", IntRange.EMPTY, 0)
        }
        assertEquals("module weather declares no notification ids (FeatureModule.notificationIds)", error.message)
    }

    // ---- Card order / 卡片顺序 ----

    @Test
    fun `a split-out card takes the place saved under its former key until it has its own`() {
        val cards = listOf(Card("w.a", 10), Card("train.commute", 20, formerKey = "transit.train"), Card("bus.commute", 30, formerKey = "transit.bus"))
        assertEquals(
            listOf("bus.commute", "w.a", "train.commute"),
            CardOrderPolicy.userOrder(cards, saved = listOf("transit.bus", "w.a", "transit.train")),
        )
        assertEquals(
            "a card saved under its own key ignores its former one",
            listOf("train.commute", "bus.commute", "w.a"),
            CardOrderPolicy.userOrder(cards, saved = listOf("train.commute", "transit.bus", "w.a", "transit.train")),
        )
    }

    @Test
    fun `saved order first, then the rest by default order, unknown keys dropped`() {
        val cards = listOf(Card("w.a", 30), Card("t.b", 10), Card("c.c", 20), Card("n.new", 5))
        assertEquals(
            listOf("c.c", "w.a", "n.new", "t.b"),
            CardOrderPolicy.userOrder(cards, saved = listOf("c.c", "gone.x", "w.a", "c.c")),
        )
        assertEquals(listOf("n.new", "t.b", "c.c", "w.a"), CardOrderPolicy.userOrder(cards, saved = emptyList()))
    }

    @Test
    fun `pinned cards are shown first without changing the user's order`() {
        val cards = listOf(Card("a", 1), Card("b", 2), Card("c", 3, pinnedToTop = true))
        assertEquals(listOf("c", "a", "b"), CardOrderPolicy.displayOrder(cards, saved = emptyList()))
        assertEquals(listOf("a", "b", "c"), CardOrderPolicy.userOrder(cards, saved = emptyList()))
    }

    @Test
    fun `move shifts one item and ignores invalid indices`() {
        val order = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), CardOrderPolicy.move(order, 0, 2))
        assertEquals(listOf("a", "d", "b", "c"), CardOrderPolicy.move(order, 3, 1))
        assertEquals(order, CardOrderPolicy.move(order, 0, 4))
        assertEquals(order, CardOrderPolicy.move(order, -1, 0))
    }

    // ---- Deep links / 深链 ----

    @Test
    fun `deep links resolve to settings, a module tab, or home`() {
        val tabs = setOf("football", "news")
        assertEquals(Destination.Settings, DeepLinkPolicy.resolve("dayloom", "settings", tabs))
        assertEquals(Destination.ModuleTab("news"), DeepLinkPolicy.resolve("dayloom", "news", tabs))
        assertEquals("a module without a tab opens home", Destination.Home, DeepLinkPolicy.resolve("dayloom", "weather", tabs))
        assertEquals(Destination.Home, DeepLinkPolicy.resolve("dayloom", "home", tabs))
        assertEquals(Destination.Home, DeepLinkPolicy.resolve("dayloom", null, tabs))
        assertNull("other schemes are not ours", DeepLinkPolicy.resolve("https", "news", tabs))
        assertNull(DeepLinkPolicy.resolve(null, null, tabs))
    }
}
