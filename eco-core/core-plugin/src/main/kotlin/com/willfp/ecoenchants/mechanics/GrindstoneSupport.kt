package com.willfp.ecoenchants.mechanics

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent
import com.willfp.eco.core.fast.fast
import com.willfp.ecoenchants.enchant.wrap
import com.willfp.ecoenchants.plugin
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.PrepareGrindstoneEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
object GrindstoneSupport : Listener {
    private val pendingRefunds = ConcurrentHashMap<UUID, Int>()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepare(event: PrepareGrindstoneEvent) {
        if (!plugin.configYml.getBool("grindstone.enabled")) {
            return
        }

        val player = event.viewers.firstOrNull() as? Player ?: return
        // Taking the output immediately prepares an empty result before the
        // grindstone XP orb is picked up. Keep the calculated refund until
        // that pickup event consumes it, matching Aiyatsbus' lifecycle.
        val result = event.result?.clone() ?: return

        val toKeep = mutableMapOf<Enchantment, Int>()
        val topRefund = collectResult(event.inventory.upperItem, player, toKeep)
        val bottomRefund = collectResult(event.inventory.lowerItem, player, toKeep)

        if (toKeep.isNotEmpty() && result.type == Material.BOOK) {
            result.type = Material.ENCHANTED_BOOK
        }
        replaceEnchantments(result, toKeep)

        pendingRefunds[player.uniqueId] = topRefund + bottomRefund
        event.result = result
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onExperiencePickup(event: PlayerPickupExperienceEvent) {
        if (!plugin.configYml.getBool("grindstone.enabled")) {
            return
        }

        val orb = event.experienceOrb
        if (orb.spawnReason != ExperienceOrb.SpawnReason.GRINDSTONE) {
            return
        }

        val playerId = orb.triggerEntityId ?: return
        val refund = pendingRefunds.remove(playerId)
        if (refund == null) {
            orb.remove()
            event.isCancelled = true
            return
        }

        orb.experience = refund
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        pendingRefunds.remove(event.player.uniqueId)
    }

    fun reload() {
        pendingRefunds.clear()
    }

    private fun collectResult(
        item: ItemStack?,
        player: Player,
        toKeep: MutableMap<Enchantment, Int>
    ): Int {
        if (item == null || item.type == Material.AIR) {
            return 0
        }

        var refund = 0.0
        for ((enchant, level) in item.fast().getEnchants(true)) {
            val wrapped = enchant.wrap()
            if (wrapped.type.noGrindstone) {
                toKeep[enchant] = maxOf(toKeep[enchant] ?: 0, level)
                continue
            }

            val maxLevel = wrapped.maximumLevel.coerceAtLeast(1)
            val bonus = plugin.configYml.getDoubleOrNull(
                "grindstone.rarity-bonus.${wrapped.enchantmentRarity.id}"
            ) ?: plugin.configYml.getDouble("grindstone.default-rarity-bonus")
            val value = plugin.configYml.getDouble("grindstone.base-experience") *
                    level / maxLevel * bonus

            refund = if (plugin.configYml.getBool("grindstone.accumulation")) {
                refund + value
            } else {
                maxOf(refund, value)
            }
        }

        val multiplier = listOf(
            1.0,
            plugin.configYml.getDouble("grindstone.vip-multiplier")
                .takeIf { player.hasPermission(plugin.configYml.getString("grindstone.vip-permission")) }
                ?: 1.0,
            plugin.configYml.getDouble("grindstone.svip-multiplier")
                .takeIf { player.hasPermission(plugin.configYml.getString("grindstone.svip-permission")) }
                ?: 1.0
        ).max()

        return (refund * multiplier).roundToInt().coerceAtLeast(0)
    }

    private fun replaceEnchantments(item: ItemStack, enchants: Map<Enchantment, Int>) {
        val meta = item.itemMeta ?: return
        if (meta is EnchantmentStorageMeta) {
            for (enchant in meta.storedEnchants.keys.toSet()) {
                meta.removeStoredEnchant(enchant)
            }
            for ((enchant, level) in enchants) {
                meta.addStoredEnchant(enchant, level, true)
            }
        } else {
            for (enchant in meta.enchants.keys.toSet()) {
                meta.removeEnchant(enchant)
            }
            for ((enchant, level) in enchants) {
                meta.addEnchant(enchant, level, true)
            }
        }
        item.itemMeta = meta
    }
}
