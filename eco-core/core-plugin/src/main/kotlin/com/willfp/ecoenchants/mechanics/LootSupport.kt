package com.willfp.ecoenchants.mechanics

import com.willfp.eco.core.fast.fast
import com.willfp.ecoenchants.enchant.DiscoveryType
import com.willfp.ecoenchants.plugin
import org.bukkit.entity.Entity
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.world.LootGenerateEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta

object LootSupport : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onGenerate(event: LootGenerateEvent) {
        if (!plugin.configYml.getBool("loot.enabled")) {
            return
        }

        val player = event.entity as? Player
        if (player == null) {
            event.loot.removeIf { it.fast().getEnchants(true).isNotEmpty() }
            return
        }

        val discoveryType = event.discoveryType()
        event.loot.replaceAll { item ->
            if (item.fast().getEnchants(true).isEmpty()) {
                item
            } else {
                enchantLoot(player, item, discoveryType)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) {
        if (!plugin.configYml.getBool("loot.fish-enabled")) {
            return
        }
        if (event.state != PlayerFishEvent.State.CAUGHT_FISH) {
            return
        }

        val caught = event.caught as? Item ?: return
        if (caught.itemStack.fast().getEnchants(true).isNotEmpty()) {
            caught.itemStack = enchantLoot(event.player, caught.itemStack, DiscoveryType.FISHING)
        }
    }

    private fun enchantLoot(
        player: Player,
        origin: ItemStack,
        discoveryType: DiscoveryType
    ): ItemStack {
        val vanillaMode = plugin.configYml.getBool("loot.vanilla-mode")
        val result = if (vanillaMode) origin.clone() else ItemStack(origin.type)
        val selected = linkedMapOf<org.bukkit.enchantments.Enchantment, Int>()
        val button = plugin.configYml.getInt("loot.cost")
        val bonus = plugin.configYml.getInt("loot.bonus")
        val amount = calculateAiyatsbusAmount("loot", button, player) + if (vanillaMode) 0 else 1
        val pool = lootEnchantCandidates(discoveryType).filter {
            it.canEnchant(result, selected.keys)
        }

        repeat(amount) {
            val candidate = pool.drawAiyatsbus() ?: return@repeat
            if (!candidate.canEnchant(result, selected.keys)) {
                return@repeat
            }
            selected[candidate.enchantment] = calculateAiyatsbusLevel(
                "loot",
                candidate,
                button,
                bonus,
                player
            )
        }

        applyLootEnchantments(result, selected)
        return result
    }
}

private fun applyLootEnchantments(
    item: ItemStack,
    enchantments: Map<org.bukkit.enchantments.Enchantment, Int>
) {
    val meta = item.itemMeta
    if (meta is EnchantmentStorageMeta) {
        for ((enchant, level) in enchantments) {
            meta.addStoredEnchant(enchant, level, true)
        }
    } else {
        for ((enchant, level) in enchantments) {
            meta.addEnchant(enchant, level, true)
        }
    }
    item.itemMeta = meta
}

private fun LootGenerateEvent.discoveryType(): DiscoveryType {
    val key = lootTable.key.key
    return when {
        "hero_of_the_village" in key -> DiscoveryType.RAIDS
        inventoryHolder is Entity -> DiscoveryType.MOB_DROPS
        else -> DiscoveryType.CHESTS
    }
}
