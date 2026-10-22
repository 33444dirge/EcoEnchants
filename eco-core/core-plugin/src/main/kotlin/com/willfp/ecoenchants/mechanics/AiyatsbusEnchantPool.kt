package com.willfp.ecoenchants.mechanics

import com.willfp.ecoenchants.enchant.DiscoveryType
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.eco.core.fast.fast
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.rarity.EnchantmentRarities
import com.willfp.ecoenchants.rarity.EnchantmentRarity
import org.bukkit.Material
import org.bukkit.Registry
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.math.roundToInt
import kotlin.random.Random

internal data class AiyatsbusEnchantCandidate(
    val enchantment: Enchantment,
    val ecoEnchant: EcoEnchant?,
    val rarity: EnchantmentRarity,
    val drawWeight: Int
) {
    val maxLevel: Int
        get() = ecoEnchant?.maximumLevel ?: enchantment.maxLevel

    fun canEnchant(item: ItemStack, existing: Collection<Enchantment>): Boolean {
        val custom = ecoEnchant
        if (custom != null) {
            return custom.canEnchantItem(item, existing)
        }

        val current = item.fast().getEnchants(true).keys + existing
        if (current.any { it.conflictsWith(enchantment) || enchantment.conflictsWith(it) }) {
            return false
        }

        return item.type == Material.ENCHANTED_BOOK || enchantment.canEnchantItem(item)
    }
}

private val tableTreasureEnchantments = setOf(
    "binding_curse",
    "frost_walker",
    "mending",
    "soul_speed",
    "swift_sneak",
    "vanishing_curse",
    "wind_burst"
)

private val nonDiscoverableVanillaEnchantments = setOf(
    "soul_speed",
    "swift_sneak",
    "wind_burst"
)

private val explicitlyTradeableVanillaEnchantments = setOf(
    "aqua_affinity",
    "bane_of_arthropods",
    "binding_curse",
    "blast_protection",
    "channeling",
    "depth_strider",
    "efficiency",
    "feather_falling",
    "fire_aspect",
    "fire_protection",
    "flame",
    "fortune",
    "frost_walker",
    "impaling",
    "infinity",
    "knockback",
    "looting",
    "loyalty",
    "luck_of_the_sea",
    "lure",
    "mending",
    "multishot",
    "piercing",
    "power",
    "projectile_protection",
    "protection",
    "punch",
    "quick_charge",
    "respiration",
    "riptide",
    "sharpness",
    "silk_touch",
    "smite",
    "sweeping",
    "sweeping_edge",
    "thorns",
    "unbreaking",
    "vanishing_curse",
    "density",
    "breach"
)

internal fun tableEnchantCandidates(player: Player): List<AiyatsbusEnchantCandidate> {
    return allEnchantCandidates().filter { candidate ->
        val custom = candidate.ecoEnchant
        if (custom != null) {
            custom.isObtainableThroughEnchanting &&
                    candidate.rarity.fromTable &&
                    candidate.rarity.id != "artifact" &&
                    player.hasPermission("ecoenchants.fromtable.${custom.id}") &&
                    !custom.config.getBool("treasure")
        } else {
            candidate.enchantment.key.key !in tableTreasureEnchantments
        }
    }
}

internal fun villagerEnchantCandidates(): List<AiyatsbusEnchantCandidate> {
    return allEnchantCandidates().filter { candidate ->
        candidate.ecoEnchant == null &&
                candidate.enchantment.key.key in explicitlyTradeableVanillaEnchantments
    }
}

internal fun lootEnchantCandidates(discoveryType: DiscoveryType): List<AiyatsbusEnchantCandidate> {
    return allEnchantCandidates().filter { candidate ->
        val custom = candidate.ecoEnchant
        if (custom != null) {
            custom.isObtainableThrough(discoveryType) &&
                    candidate.rarity.fromLoot &&
                    candidate.rarity.id != "artifact"
        } else {
            candidate.enchantment.key.key !in nonDiscoverableVanillaEnchantments
        }
    }
}

private fun allEnchantCandidates(): List<AiyatsbusEnchantCandidate> {
    val custom = EcoEnchants.values().map {
        AiyatsbusEnchantCandidate(
            it.enchantment,
            it,
            it.enchantmentRarity,
            it.drawWeight
        )
    }
    val customKeys = custom.mapTo(HashSet()) { it.enchantment.key }

    val vanilla = Registry.ENCHANTMENT.asSequence().mapNotNull { enchantment ->
        if (enchantment.key in customKeys) {
            return@mapNotNull null
        }

        val rarityID = plugin.vanillaEnchantsYml
            .getSubsectionOrNull(enchantment.key.key)
            ?.getString("rarity")
            ?: return@mapNotNull null
        val rarity = EnchantmentRarities[rarityID] ?: return@mapNotNull null

        AiyatsbusEnchantCandidate(enchantment, null, rarity, 1)
    }

    return custom + vanilla
}

internal fun Collection<AiyatsbusEnchantCandidate>.drawAiyatsbus(
    random: Random = Random.Default,
    countRarityPopulation: Boolean = false
): AiyatsbusEnchantCandidate? {
    if (isEmpty()) {
        return null
    }

    val byRarity = groupBy { it.rarity }
    val rarity = byRarity.entries
        .associate { (rarity, candidates) ->
            rarity to rarity.weight * if (countRarityPopulation) candidates.size else 1
        }
        .selectByWeight(random)
        ?: return null

    return byRarity[rarity]
        ?.associateWith { it.drawWeight }
        ?.selectByWeight(random)
}

private fun <T> Map<T, Int>.selectByWeight(random: Random): T? {
    val total = values.sumOf { it.coerceAtLeast(0) }
    if (total <= 0) {
        return null
    }

    val target = random.nextInt(total)
    var accumulator = 0
    return entries.firstOrNull {
        accumulator += it.value.coerceAtLeast(0)
        accumulator > target
    }?.key
}

internal fun calculateAiyatsbusAmount(configRoot: String, button: Int, player: Player): Int {
    val multiplier = listOf(
        1.0,
        plugin.configYml.getDouble("$configRoot.vip-chance-multiplier")
            .takeIf { player.hasPermission(plugin.configYml.getString("$configRoot.vip-permission")) }
            ?: 1.0,
        plugin.configYml.getDouble("$configRoot.svip-chance-multiplier")
            .takeIf { player.hasPermission(plugin.configYml.getString("$configRoot.svip-permission")) }
            ?: 1.0
    ).max()

    var amount = 0
    for (baseChance in plugin.configYml.getDoubles("$configRoot.more-enchant-chances")) {
        if (Random.nextDouble() <= (baseChance * button * multiplier).coerceAtLeast(0.0)) {
            amount++
        } else {
            break
        }
    }
    return amount
}

internal fun calculateAiyatsbusLevel(
    configRoot: String,
    candidate: AiyatsbusEnchantCandidate,
    button: Int,
    bonus: Int,
    player: Player,
    random: Random = Random.Default,
    subtractRandom: Boolean = true
): Int {
    val maxLevel = candidate.maxLevel.coerceAtLeast(1)
    val configuredLimit = plugin.configYml.getInt("$configRoot.max-level-limit")
    val limit = if (configuredLimit < 1) maxLevel else minOf(maxLevel, configuredLimit)
    if (player.hasPermission(plugin.configYml.getString("$configRoot.full-level-permission"))) {
        return limit
    }

    val randomOffset = if (subtractRandom) random.nextDouble() else 0.0
    return (limit * button / 3.0 * (bonus + 1.0) / 17.0 - randomOffset)
        .roundToInt()
        .coerceIn(1, limit)
}
