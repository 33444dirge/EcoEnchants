package com.willfp.ecoenchants.enchant

import com.google.common.collect.HashBiMap
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.ecoenchants.EcoEnchantsPlugin
import com.willfp.ecoenchants.display.getFormattedName
import com.willfp.ecoenchants.enchant.impl.EcoEnchantBase
import com.willfp.ecoenchants.enchant.impl.LibreforgeEcoEnchant
import com.willfp.ecoenchants.enchant.impl.hardcoded.EnchantmentPermanenceCurse
import com.willfp.ecoenchants.enchant.impl.hardcoded.EnchantmentRepairing
import com.willfp.ecoenchants.enchant.impl.hardcoded.EnchantmentReplenish
import com.willfp.ecoenchants.enchant.impl.hardcoded.EnchantmentSoulbound
import com.willfp.ecoenchants.enchant.registration.ModernEnchantmentRegistererProxy
import com.willfp.ecoenchants.integrations.EnchantRegistrations
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.rarity.EnchantmentRarities
import com.willfp.ecoenchants.target.EnchantmentTargets
import com.willfp.ecoenchants.type.EnchantmentTypes
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.RegistrableCategory
import org.bukkit.ChatColor

@Suppress("UNUSED")
object EcoEnchants : RegistrableCategory<EcoEnchant>("enchant", "enchants") {
    private val BY_NAME = HashBiMap.create<String, EcoEnchant>()

    override val shouldPreload = true

    override fun clear(plugin: LibreforgePlugin) {
        plugin as EcoEnchantsPlugin

        for (enchant in registry.values()) {
            plugin.enchantmentRegisterer.unregister(enchant)
            EnchantRegistrations.removeEnchant(enchant)
            BY_NAME.remove(ChatColor.stripColor(enchant.getFormattedName(0))?.lowercase())
        }

        registry.clear()
    }

    override fun beforeReload(plugin: LibreforgePlugin) {
        plugin.getProxy(ModernEnchantmentRegistererProxy::class.java).replaceRegistry()

        EnchantmentRarities.update()
        EnchantmentTargets.update()
        EnchantmentTypes.update()
    }

    override fun afterReload(plugin: LibreforgePlugin) {
        sendPrompts()
        registerHardcodedEnchantments()

        plugin.getProxy(ModernEnchantmentRegistererProxy::class.java).freezeRegistry()
    }

    override fun acceptPreloadConfig(plugin: LibreforgePlugin, id: String, config: Config) {
        plugin as EcoEnchantsPlugin

        if (!config.has("effects")) {
            return
        }

        try {
            val enchant = LibreforgeEcoEnchant(
                id,
                config
            )

            doRegister(enchant)
        } catch (e: MissingDependencyException) {
            // Ignore missing dependencies for preloaded enchants
        }
    }

    override fun acceptConfig(plugin: LibreforgePlugin, id: String, config: Config) {
        plugin as EcoEnchantsPlugin

        if (!config.has("effects")) {
            return
        }

        try {
            val enchant = LibreforgeEcoEnchant(
                id,
                config,
            )

            doRegister(enchant)
        } catch (e: MissingDependencyException) {
            addPluginPrompt(plugin, e.plugins)
        }
    }

    private fun doRegister(enchant: EcoEnchantBase) {
        val enchantment = plugin.enchantmentRegisterer.register(enchant)
        // Register delegated versions
        registry.register(enchantment as EcoEnchant)

        // BY_NAME 是 BiMap，值也必须唯一。若同一个附魔 ID 被注册两次而两份配置的
        // display-name 不同（例如插件自带一份、附属插件又提供一份覆盖版），
        // enchantmentRegisterer 会返回同一个附魔实例，直接 put 就会因
        // "value already present" 抛异常并中断该附魔的加载。
        // 先摘掉该实例的旧名字映射，让后加载的配置正常覆盖。
        BY_NAME.inverse().remove(enchantment)

        @Suppress("DEPRECATION")
        BY_NAME[ChatColor.stripColor(enchant.getFormattedName(0))?.lowercase()] = enchantment as EcoEnchant
        EnchantRegistrations.registerEnchantments()
    }

    private fun registerHardcodedEnchantments() {
        val hardcodedEnchantments = listOf(
            EnchantmentPermanenceCurse,
            EnchantmentRepairing,
            EnchantmentReplenish,
            EnchantmentSoulbound
        )

        for (enchantment in hardcodedEnchantments) {
            // Only register if not already registered (so hardcode can be overridden)
            if (enchantment.isPresent && registry[enchantment.id] == null) {
                doRegister(enchantment)
            }
        }
    }

    fun getByName(name: String?): EcoEnchant? {
        return if (name == null) {
            null
        } else BY_NAME[name.lowercase()]
    }
}
