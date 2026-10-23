package com.willfp.ecoenchants.target

import com.willfp.ecoenchants.EcoEnchantsPlugin
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.recipe.parts.UnrestrictedMaterialTestableItem
import com.willfp.eco.core.recipe.parts.ModifiedTestableItem
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.willfp.ecoenchants.target.EnchantmentTargets.isEnchantable

class EnchantmentTargetsTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setupPlugin() {
            val plugin = mockk<EcoEnchantsPlugin>(relaxed = true)
            every { plugin.targetsYml.getSubsections("targets") } returns emptyList()
            every { plugin.langYml.getFormattedString("all") } returns "All"
            every { plugin.configYml.getInt("anvil.book-enchant-limit") } returns 7
            Class.forName("com.willfp.ecoenchants.EcoEnchantsPluginKt")
                .getDeclaredField("plugin").apply { isAccessible = true }.set(null, plugin)
        }
    }

    @BeforeEach
    fun resetTargets() {
        EnchantmentTargets.clear()
        EnchantmentTargets.register(AllEnchantmentTarget)
    }

    private fun item(type: Material = Material.DIAMOND_SWORD) = mockk<ItemStack>().also {
        every { it.type } returns type
    }

    private fun target(id: String, limit: Int, matches: (ItemStack) -> Boolean): EnchantmentTarget {
        val target = mockk<EnchantmentTarget>()
        every { target.getID() } returns id
        every { target.id } returns id
        every { target.maxEnchants } returns limit
        every { target.matches(any()) } answers { matches(firstArg()) }
        every { target.onRegister() } returns Unit
        every { target.onRemove() } returns Unit
        EnchantmentTargets.register(target)
        return target
    }

    @Test
    fun `overlapping targets keep the strictest positive limit and ignore all`() {
        target("sword", 8) { true }
        target("hands", 3) { true }
        target("unlimited", -1) { true }
        target("zero", 0) { true }
        target("other", 1) { false }
        val item = item()
        assertTrue(item.isEnchantable)
        assertEquals(3, EnchantmentTargets.getMaxEnchants(item))
    }

    @Test
    fun `custom component predicates are rechecked after changes without material caching`() {
        var matches = false
        target("custom", 2) { matches }
        val item = item()
        assertFalse(item.isEnchantable)
        assertEquals(-1, EnchantmentTargets.getMaxEnchants(item))
        matches = true
        assertTrue(item.isEnchantable)
        assertEquals(2, EnchantmentTargets.getMaxEnchants(item))
        matches = false
        assertFalse(item.isEnchantable)
        assertEquals(-1, EnchantmentTargets.getMaxEnchants(item))
    }

    @Test
    fun `books use the configured book limit and are always enchantable`() {
        val target = target("custom", 1) { error("Book must not need a target scan") }
        for (material in listOf(Material.BOOK, Material.ENCHANTED_BOOK)) {
            val item = item(material)
            assertTrue(item.isEnchantable)
            assertEquals(7, EnchantmentTargets.getMaxEnchants(item))
        }
        verify(exactly = 0) { target.matches(any()) }
    }

    @Test
    fun `registry removal and same-id replacement take effect immediately`() {
        target("custom", 2) { true }
        val item = item()
        assertTrue(item.isEnchantable)
        assertEquals(2, EnchantmentTargets.getMaxEnchants(item))
        EnchantmentTargets.remove("custom")
        assertFalse(item.isEnchantable)
        assertEquals(-1, EnchantmentTargets.getMaxEnchants(item))
        target("custom", 5) { true }
        assertEquals(5, EnchantmentTargets.getMaxEnchants(item))
        target("custom", 1) { false }
        assertFalse(item.isEnchantable)
        assertEquals(-1, EnchantmentTargets.getMaxEnchants(item))
    }

    private fun configured(vararg matchers: TestableItem): ConfiguredEnchantmentTarget {
        val config = mockk<Config>(relaxed = true)
        every { config.getString("id") } returns "configured"
        every { config.getString("slot") } returns "mainhand"
        every { config.getIntOrNull("max-enchants") } returns 4
        every { config.getStrings("items") } returns matchers.indices.map { "fixture_$it" }
        mockkStatic(Items::class)
        try {
            matchers.forEachIndexed { index, matcher -> every { Items.lookup("fixture_$index") } returns matcher }
            return ConfiguredEnchantmentTarget(config)
        } finally {
            unmockkStatic(Items::class)
        }
    }

    @Test
    fun `unrestricted vanilla rules match the same materials`() {
        val target = configured(UnrestrictedMaterialTestableItem(Material.DIAMOND_SWORD),
            UnrestrictedMaterialTestableItem(Material.BRUSH))
        assertTrue(target.matches(item(Material.DIAMOND_SWORD)))
        assertTrue(target.matches(item(Material.BRUSH)))
        assertFalse(target.matches(item(Material.STONE)))
        EnchantmentTargets.register(target)
        assertTrue(item().isEnchantable)
        assertFalse(item(Material.STONE).isEnchantable)
        assertEquals(4, EnchantmentTargets.getMaxEnchants(item()))
        assertEquals(-1, EnchantmentTargets.getMaxEnchants(item(Material.STONE)))
        // Configured target equality uses only ID; replacement still needs new rules.
        EnchantmentTargets.register(configured(UnrestrictedMaterialTestableItem(Material.STONE)))
        assertFalse(item().isEnchantable)
        assertTrue(item(Material.STONE).isEnchantable)
        assertEquals(4, EnchantmentTargets.getMaxEnchants(item(Material.STONE)))
    }

    @Test
    fun `custom matchers and subclasses keep their full predicate`() {
        var allow = false
        val subclass = object : UnrestrictedMaterialTestableItem(Material.DIAMOND_SWORD) {
            override fun matches(itemStack: ItemStack?): Boolean = allow && super.matches(itemStack)
        }
        val custom = mockk<TestableItem>()
        every { custom.matches(any()) } answers { allow && firstArg<ItemStack>().type == Material.STONE }
        val target = configured(subclass, custom)
        EnchantmentTargets.register(target)
        assertFalse(target.matches(item()))
        assertFalse(target.matches(item(Material.STONE)))
        assertFalse(item().isEnchantable)
        allow = true
        assertTrue(target.matches(item()))
        assertTrue(target.matches(item(Material.STONE)))
        assertFalse(target.matches(item(Material.BRUSH)))
        assertTrue(item().isEnchantable)
        assertTrue(item(Material.STONE).isEnchantable)
        assertEquals(4, EnchantmentTargets.getMaxEnchants(item()))
    }

    @Test
    fun `modified vanilla rules narrow candidates but still evaluate their predicate`() {
        var allow = false
        val wrapped = ModifiedTestableItem(UnrestrictedMaterialTestableItem(Material.DIAMOND_SWORD),
            { allow }, item())
        EnchantmentTargets.register(configured(wrapped))
        assertFalse(item().isEnchantable)
        assertEquals(-1, EnchantmentTargets.getMaxEnchants(item()))
        allow = true
        assertTrue(item().isEnchantable)
        assertEquals(4, EnchantmentTargets.getMaxEnchants(item()))
        assertFalse(item(Material.STONE).isEnchantable)
        allow = false
        assertFalse(item().isEnchantable)
    }
}
