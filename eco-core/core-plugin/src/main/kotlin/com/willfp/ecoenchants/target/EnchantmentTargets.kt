package com.willfp.ecoenchants.target

import com.github.benmanes.caffeine.cache.Caffeine
import com.willfp.eco.core.items.HashedItem
import com.willfp.eco.core.registry.Registry
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.plugin
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.concurrent.TimeUnit

object EnchantmentTargets : Registry<EnchantmentTarget>() {
    init {
        register(AllEnchantmentTarget)
        update()
    }

    private fun getForItem(item: ItemStack): List<EnchantmentTarget> {
        return values()
            .filter { !it.id.equals("all", ignoreCase = true) }
            .filter { it.matches(item) }
    }

    /**
     * 该物品允许的最大附魔数量，-1 表示不限制。
     *
     * 一件物品可能同时命中多个目标（如胸甲既是 chestplate 又属于 armor），
     * 此时取所有已配置上限中最小的，即最严格者生效。
     */
    fun getMaxEnchants(item: ItemStack): Int {
        if (item.type == Material.BOOK || item.type == Material.ENCHANTED_BOOK) {
            return plugin.configYml.getInt("anvil.book-enchant-limit")
        }

        // canEnchantItem 会对每个附魔各调一次，而 getForItem 要遍历全部目标及其物品列表，
        // 不缓存的话铁砧每次操作都是 O(附魔数 × 目标数 × 物品数)
        return maxEnchantsCache.get(HashedItem.of(item)) {
            getForItem(item)
                .map { it.maxEnchants }
                .filter { it > 0 }
                .minOrNull() ?: -1
        }
    }

    val ItemStack.isEnchantable: Boolean
        get() = enchantableCache.get(HashedItem.of(this)) {
            getForItem(this).isNotEmpty() || this.type == Material.BOOK || this.type == Material.ENCHANTED_BOOK
        }

    val ItemStack.applicableEnchantments: List<EcoEnchant>
        get() = canEnchantCache.get(HashedItem.of(this)) {
            EcoEnchants.values().filter { it.canEnchantItem(this) }
        }

    @JvmStatic
    fun update() {
        for (target in values()) {
            if (target is AllEnchantmentTarget) {
                continue
            }
            remove(target)
        }

        for (config in plugin.targetsYml.getSubsections("targets")) {
            register(ConfiguredEnchantmentTarget(config))
        }

        AllEnchantmentTarget.updateItems()
    }
}

private val enchantableCache = Caffeine.newBuilder()
    .expireAfterAccess(5, TimeUnit.SECONDS)
    .build<HashedItem, Boolean>()

private val canEnchantCache = Caffeine.newBuilder()
    .expireAfterAccess(5, TimeUnit.SECONDS)
    .build<HashedItem, List<EcoEnchant>>()

private val maxEnchantsCache = Caffeine.newBuilder()
    .expireAfterAccess(5, TimeUnit.SECONDS)
    .build<HashedItem, Int>()
