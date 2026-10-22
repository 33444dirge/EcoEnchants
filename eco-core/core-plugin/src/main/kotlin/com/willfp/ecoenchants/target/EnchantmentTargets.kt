package com.willfp.ecoenchants.target

import com.github.benmanes.caffeine.cache.Caffeine
import com.willfp.eco.core.items.HashedItem
import com.willfp.eco.core.registry.Registry
import com.willfp.eco.core.recipe.parts.UnrestrictedMaterialTestableItem
import com.willfp.eco.core.recipe.parts.MaterialTestableItem
import com.willfp.eco.core.recipe.parts.ModifiedTestableItem
import com.willfp.eco.core.items.TestableItem
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.plugin
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.concurrent.TimeUnit
import java.util.EnumMap

object EnchantmentTargets : Registry<EnchantmentTarget>() {
    @Volatile
    private var matchingSnapshot: MatchingSnapshot? = null

    private class MatchingSnapshot(val source: Set<EnchantmentTarget>) {
        val candidates = EnumMap<Material, MutableList<EnchantmentTarget>>(Material::class.java)
        val fallback = mutableListOf<EnchantmentTarget>()

        private fun requiredMaterial(item: TestableItem): Material? = when (item.javaClass) {
            MaterialTestableItem::class.java, UnrestrictedMaterialTestableItem::class.java ->
                (item as MaterialTestableItem).material
            ModifiedTestableItem::class.java -> requiredMaterial((item as ModifiedTestableItem).handle)
            else -> null
        }

        init {
            for (target in source) {
                if (target.id.equals("all", ignoreCase = true)) continue
                // These exact eco classes prove a necessary material, not a final
                // match. Always run matches(), including every modifier/custom check.
                val items = (target as? ConfiguredEnchantmentTarget)?.items
                val materials = items?.map { requiredMaterial(it) }
                if (materials == null || materials.any { it == null }) {
                    fallback.add(target)
                    continue
                }
                for (material in materials.filterNotNull().distinct()) {
                    candidates.getOrPut(material) { mutableListOf() }.add(target)
                }
            }
        }
    }

    private fun matching(): MatchingSnapshot {
        // eco publishes a new immutable values set on registry changes. Compare
        // identities, not target equality: same-ID replacement must rebuild too.
        val current = values()
        val cached = matchingSnapshot
        return if (cached != null && cached.source === current) cached
        else MatchingSnapshot(current).also { matchingSnapshot = it }
    }

    init {
        register(AllEnchantmentTarget)
        update()
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

        val matching = matching()
        return minimumLimit(item, matching.fallback,
            minimumLimit(item, matching.candidates[item.type].orEmpty(), -1))
    }

    private fun minimumLimit(item: ItemStack, targets: List<EnchantmentTarget>, initial: Int): Int {
        var limit = initial
        for (target in targets) {
            if (!target.matches(item)) continue
            val max = target.maxEnchants
            if (max > 0 && (limit < 0 || max < limit)) {
                limit = max
            }
        }
        return limit
    }

    val ItemStack.isEnchantable: Boolean
        get() {
            if (this.type == Material.BOOK || this.type == Material.ENCHANTED_BOOK) return true
            val matching = matching()
            return matching.candidates[this.type].orEmpty().any { it.matches(this) } ||
                matching.fallback.any { it.matches(this) }
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

private val canEnchantCache = Caffeine.newBuilder()
    .expireAfterAccess(5, TimeUnit.SECONDS)
    .build<HashedItem, List<EcoEnchant>>()
