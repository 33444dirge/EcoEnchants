package com.willfp.ecoenchants.mechanics

import com.willfp.eco.core.fast.fast
import com.willfp.ecoenchants.plugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.VillagerAcquireTradeEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantRecipe
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import kotlin.random.Random

object VillagerSupport : Listener {
    @EventHandler
    fun onTrade(event: VillagerAcquireTradeEvent) {
        val original = event.recipe
        val result = original.result.clone()
        if (result.fast().getEnchants(true).isEmpty()) {
            return
        }

        if (!plugin.configYml.getBool("villager.enabled")) {
            event.isCancelled = true
            return
        }

        clearEnchantments(result)
        val selected = linkedMapOf<org.bukkit.enchantments.Enchantment, Int>()
        val amount = plugin.configYml.getInt("villager.amount").coerceAtLeast(0)
        val candidates = villagerEnchantCandidates()

        repeat(amount) {
            val pool = candidates.filter {
                it.canEnchant(result, selected.keys)
            }
            val candidate = pool.drawAiyatsbus() ?: return@repeat
            val configuredLimit = plugin.configYml.getInt("villager.max-level-limit")
            val limit = if (configuredLimit < 1) {
                candidate.maxLevel
            } else {
                minOf(candidate.maxLevel, configuredLimit)
            }.coerceAtLeast(1)
            selected[candidate.enchantment] = Random.nextInt(1, limit + 1)
        }

        if (selected.isEmpty()) {
            event.isCancelled = true
            return
        }

        applyEnchantments(result, selected)
        val recipe = MerchantRecipe(
            result,
            original.uses,
            original.maxUses,
            original.hasExperienceReward(),
            original.villagerExperience,
            original.priceMultiplier,
            original.demand,
            original.specialPrice,
            original.shouldIgnoreDiscounts()
        )
        recipe.ingredients = original.ingredients
        event.recipe = recipe
    }
}

private fun clearEnchantments(item: ItemStack) {
    val meta = item.itemMeta
    if (meta is EnchantmentStorageMeta) {
        for (enchant in meta.storedEnchants.keys.toSet()) {
            meta.removeStoredEnchant(enchant)
        }
    } else {
        for (enchant in meta.enchants.keys.toSet()) {
            meta.removeEnchant(enchant)
        }
    }
    item.itemMeta = meta
}

private fun applyEnchantments(
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
