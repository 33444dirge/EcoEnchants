package com.willfp.ecoenchants.gui

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CaptiveReturnsTest {
    @Test
    fun `disconnect close returns input before leaving the close handler and only once`() {
        val event = mockk<InventoryCloseEvent>()
        val player = mockk<Player>()
        val top = mockk<Inventory>()
        val bottom = mockk<PlayerInventory>()
        val sword = mockk<ItemStack>()
        val copy = mockk<ItemStack>()
        var input: ItemStack? = sword

        every { event.player } returns player
        every { event.inventory } returns top
        every { top.size } returns 27
        every { top.getItem(10) } answers { input }
        every { top.setItem(10, null) } answers { input = null }
        every { sword.type } returns Material.DIAMOND_SWORD
        every { sword.clone() } returns copy
        every { player.inventory } returns bottom
        every { bottom.addItem(*anyVararg()) } returns hashMapOf()

        returnCaptives(event, 2 to 2, 2 to 2)
        assertNull(input)
        returnCaptives(event, 2 to 2)

        verify(exactly = 1) { bottom.addItem(*anyVararg()) }
        verifyOrder {
            top.setItem(10, null)
            bottom.addItem(*anyVararg())
        }
    }

    @Test
    fun `full inventory drops only the leftovers in the same close handler`() {
        val event = mockk<InventoryCloseEvent>()
        val player = mockk<Player>()
        val top = mockk<Inventory>()
        val bottom = mockk<PlayerInventory>()
        val world = mockk<World>()
        val dropped = mockk<Item>(relaxed = true)
        val sword = mockk<ItemStack>()
        val copy = mockk<ItemStack>()
        val location = Location(world, 0.0, 64.0, 0.0)

        every { event.player } returns player
        every { event.inventory } returns top
        every { top.size } returns 27
        every { top.getItem(10) } returns sword
        every { top.setItem(10, null) } returns Unit
        every { sword.type } returns Material.DIAMOND_SWORD
        every { sword.clone() } returns copy
        every { copy.type } returns Material.DIAMOND_SWORD
        every { player.inventory } returns bottom
        every { bottom.addItem(*anyVararg()) } returns hashMapOf(0 to copy)
        every { player.location } returns location
        every { player.world } returns world
        every { world.dropItem(any(), any()) } returns dropped

        returnCaptives(event, 2 to 2)

        verify(exactly = 1) { world.dropItem(location, copy) }
        verify(exactly = 1) { dropped.velocity = any() }
    }
}
