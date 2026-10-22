package com.willfp.ecoenchants.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.util.Vector

/** Return real input items before logout saves the player's inventory. */
internal fun returnCaptives(event: InventoryCloseEvent, vararg positions: Pair<Int, Int>) {
    val player = event.player as? Player ?: return
    val inventory = event.inventory

    // Read the inventory being closed. eco may already point renderedInventory at a new menu.
    val captives = positions.distinct().mapNotNull { (row, column) ->
        val index = (row - 1) * 9 + column - 1
        if (index !in 0 until inventory.size) return@mapNotNull null
        val item = inventory.getItem(index)?.takeUnless { it.type == Material.AIR } ?: return@mapNotNull null
        index to item.clone()
    }
    if (captives.isEmpty()) return

    // Clear the input slots before settling them, so a repeated close cannot return them twice.
    captives.forEach { (index, _) -> inventory.setItem(index, null) }

    // A collated DropQueue runs next tick, after the disconnect save. Settle synchronously.
    val leftovers = player.inventory.addItem(*captives.map { it.second }.toTypedArray())
    if (leftovers.isNotEmpty()) {
        // Stay in the player's owned Folia region, including at a region boundary.
        val location = player.location
        leftovers.values.forEach { item ->
            player.world.dropItem(location, item).velocity = Vector()
        }
    }
}
