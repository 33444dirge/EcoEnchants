package com.willfp.ecoenchants.enchant

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.fast.fast
import com.willfp.eco.core.placeholder.InjectablePlaceholder
import com.willfp.eco.core.placeholder.PlaceholderInjectable
import com.willfp.eco.core.placeholder.StaticPlaceholder
import com.willfp.eco.core.placeholder.context.placeholderContext
import com.willfp.eco.util.NumberUtils
import com.willfp.ecoenchants.EcoEnchantsPlugin
import com.willfp.ecoenchants.display.DescriptionPlaceholder
import com.willfp.ecoenchants.mechanics.infiniteIfNegative
import com.willfp.ecoenchants.rarity.EnchantmentRarity
import com.willfp.ecoenchants.target.EnchantmentTargets
import com.willfp.ecoenchants.type.EnchantmentType
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

interface EcoEnchantLike {
    /**
     * The config.
     */
    val config: Config

    /**
     * The max enchantment level.
     */
    val maximumLevel: Int

    /**
     * The plugin.
     */
    val plugin: EcoEnchantsPlugin

    /**
     * The bukkit enchantment.
     */
    val enchantment: Enchantment

    /**
     * Whether this enchantment may currently be used or newly applied.
     * Missing `enabled` keeps the upstream default behavior.
     */
    val isEnabled: Boolean
        get() = config.getBoolOrNull("enabled") ?: true

    /**
     * The raw display name.
     */
    val rawDisplayName: String

    /**
     * The enchantment type.
     */
    val type: EnchantmentType

    /**
     * The enchantment rarity.
     */
    val enchantmentRarity: EnchantmentRarity

    /**
     * Get if this enchantment can be applied to [item].
     */
    fun canEnchantItem(
        item: ItemStack,
        additionalEnchantments: Collection<Enchantment> = emptyList()
    ): Boolean {
        if (!isEnabled) {
            return false
        }

        val enchants = (item.fast().getEnchants(true).keys + additionalEnchantments)
            .distinctBy { it.key }

        if (enchants.count { it.wrap().type == this.type } >= this.type.limit) {
            return false
        }

        if (enchants.any { enchant -> enchant.conflictsWithDeep(this.enchantment) }) {
            return false
        }

        if (this is EcoEnchant && !this.hasRequiredEnchantments(enchants)) {
            return false
        }

        // 只有附魔书是「容器」：它本身不是最终载体，能存下任何附魔。
        // 普通书（minecraft:book）不是容器，也不属于任何附魔目标，所以不能在这里放行，
        // 否则会产出原版不存在的「带附魔的普通书」，并且整组一起被附魔。
        val isEnchantedBook = item.type == Material.ENCHANTED_BOOK

        val enchantLimit = if (isEnchantedBook) {
            plugin.configYml.getInt("anvil.book-enchant-limit")
        } else {
            plugin.configYml.getInt("anvil.enchant-limit")
        }.infiniteIfNegative()

        if (enchants.size >= enchantLimit) {
            return false
        }

        if (isEnchantedBook) {
            return true
        }

        // 按目标类型的附魔数上限（targets.yml 的 max-enchants），与上面的全局上限取严。
        // 放在附魔书判断之后：附魔书是容器而非最终载体，不应受目标上限约束。
        val maxEnchants = EnchantmentTargets.getMaxEnchants(item)
        if (maxEnchants > 0 && enchants.size >= maxEnchants) {
            return false
        }

        return if (this is EcoEnchant) {
            this.targets.any { it.matches(item) }
        } else {
            enchantment.canEnchantItem(item)
        }
    }

    /**
     * Get the raw description for the enchantment.
     */
    fun getRawDescription(level: Int, player: Player?): List<String> {
        // Fetch custom placeholders other than %placeholder%
        val uncompiledPlaceholders = config.getSubsection("placeholders").getKeys(false).associateWith {
            config.getString("placeholders.$it")
        }.toMutableMap()

        // Add %placeholder% placeholder in
        uncompiledPlaceholders["placeholder"] = config.getString("placeholder")

        // Evaluate each placeholder
        val placeholders = uncompiledPlaceholders.map { (id, expr) ->
            DescriptionPlaceholder(
                id,
                NumberUtils.evaluateExpression(
                    expr,
                    placeholderContext(
                        player = player,
                        injectable = object : PlaceholderInjectable {
                            override fun getPlaceholderInjections(): List<InjectablePlaceholder> {
                                return listOf(
                                    StaticPlaceholder(
                                        "level",
                                    ) { level.toString() }
                                )
                            }

                            override fun addInjectablePlaceholder(p0: MutableIterable<InjectablePlaceholder>) {
                                // Do nothing
                            }

                            override fun clearInjectedPlaceholders() {
                                // Do nothing
                            }
                        }
                    )
                )
            )
        }

        // Handle both single-string and list format for description
        val rawDescription: List<String> = when (val descriptionValue = config.get("description")) {
            is String -> listOf(descriptionValue)
            is List<*> -> descriptionValue.filterIsInstance<String>()
            else -> emptyList()
        }

        // Apply placeholders to description
        return rawDescription.map { line ->
            var rawDescriptionFinal = line
            for (placeholder in placeholders) {
                rawDescriptionFinal = rawDescriptionFinal.replace("%${placeholder.id}%", NumberUtils.format(placeholder.value))
            }
            rawDescriptionFinal
        }
    }
}
