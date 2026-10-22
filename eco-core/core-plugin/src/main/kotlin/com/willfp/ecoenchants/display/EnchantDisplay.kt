package com.willfp.ecoenchants.display

import com.willfp.eco.core.display.Display
import com.willfp.eco.core.display.DisplayModule
import com.willfp.eco.core.display.DisplayPriority
import com.willfp.eco.core.display.DisplayProperties
import com.willfp.eco.core.fast.FastItemStack
import com.willfp.eco.core.fast.fast
import com.willfp.ecoenchants.commands.CommandToggleDescriptions.seesEnchantmentDescriptions
import com.willfp.ecoenchants.display.EnchantSorter.sortForItemLore
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.wrap
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.target.EnchantmentTargets
import com.willfp.ecoenchants.target.EnchantmentTargets.isEnchantable
import com.willfp.libreforge.ItemProvidedHolder
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

// Works around HIDE_POTION_EFFECTS not existing in 1.20.5+
interface HideStoredEnchantsProxy {
    fun hideStoredEnchants(fis: FastItemStack)
    fun showStoredEnchants(fis: FastItemStack)
    fun areStoredEnchantsHidden(fis: FastItemStack): Boolean
}

@Suppress("DEPRECATION")
object EnchantDisplay : DisplayModule(plugin, DisplayPriority.HIGH) {
    private val hideStateKey =
        plugin.namespacedKeyFactory.create("ecoenchantlore-skip") // Same for backwards compatibility

    private val hse = plugin.getProxy(HideStoredEnchantsProxy::class.java)

    override fun display(
        itemStack: ItemStack,
        player: Player?,
        props: DisplayProperties,
        vararg args: Any
    ) {
        if (plugin.configYml.getBool("display.require-enchantable") && !itemStack.isEnchantable) {
            return
        }

        val fast = itemStack.fast()
        val pdc = fast.persistentDataContainer

        // Args represent hide enchants
        if (args[0] == true) {
            fast.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            if (itemStack.type == Material.ENCHANTED_BOOK) {
                hse.hideStoredEnchants(fast)
            }
            pdc.set(hideStateKey, PersistentDataType.INTEGER, 1)
            return
        } else {
            pdc.set(hideStateKey, PersistentDataType.INTEGER, 0)
        }

        val lore = fast.lore
        val enchantLore = mutableListOf<String>()

        // Get enchants mapped to EcoEnchantLike
        val unsorted = fast.getEnchants(true)
        val enchants = unsorted.sortForItemLore()

        val shouldCollapse = plugin.configYml.getBool("display.collapse.enabled") &&
                enchants.size > plugin.configYml.getInt("display.collapse.threshold")

        val shouldDescribe = (plugin.configYml.getBool("display.descriptions.enabled") &&
                enchants.size <= plugin.configYml.getInt("display.descriptions.threshold")
                && player?.seesEnchantmentDescriptions ?: true)

        val formattedNames = mutableMapOf<DisplayableEnchant, String>()

        val notMetLines = mutableListOf<String>()

        for ((enchant, level) in enchants) {
            var showNotMet = false
            if (player != null && enchant is EcoEnchant) {
                val enchantLevel = enchant.getLevel(level)
                val holder = ItemProvidedHolder(enchantLevel, itemStack)

                val enchantNotMetLines = holder.getNotMetLines(player).map { Display.PREFIX + it }
                notMetLines.addAll(enchantNotMetLines)

                if (enchantNotMetLines.isNotEmpty() || holder.isShowingAnyNotMet(player)) {
                    showNotMet = true
                }
            }

            formattedNames[DisplayableEnchant(enchant.wrap(), level)] =
                enchant.wrap().getFormattedName(level, showNotMet = showNotMet)
        }

        if (shouldCollapse) {
            val perLine = plugin.configYml.getInt("display.collapse.per-line")
            val delimiter = plugin.configYml.getFormattedString("display.collapse.delimiter")

            // 合并显示时同样保留描述（aiyatsbus 的 combine.layout 是
            // "{previous_1}, {previous_2}{subsequent_1}{subsequent_2}"，
            // 即名字并排后，本行各附魔的描述依次跟在下方）。
            for (chunk in formattedNames.entries.chunked(perLine)) {
                enchantLore.add(
                    Display.PREFIX + chunk.joinToString(delimiter) { it.value }
                )

                if (shouldDescribe) {
                    for ((displayable, _) in chunk) {
                        val (enchant, level) = displayable

                        enchantLore.addAll(
                            enchant.getFormattedDescription(level, player)
                                .filter { it.isNotEmpty() }
                                .map { Display.PREFIX + it }
                        )
                    }
                }
            }
        } else {
            for ((displayable, formattedName) in formattedNames) {
                val (enchant, level) = displayable

                enchantLore.add(Display.PREFIX + formattedName)

                if (shouldDescribe) {
                    enchantLore.addAll(
                        enchant.getFormattedDescription(level, player)
                        .filter { it.isNotEmpty() }.map { Display.PREFIX + it })
                }
            }
        }

        // 剩余附魔词条数（对应 aiyatsbus 的 capability_line）。
        // 只在物品本身有附魔数上限、且已附魔时显示，避免污染普通物品的 lore。
        if (plugin.configYml.getBool("display.capacity.enabled") && enchants.isNotEmpty()) {
            val maxEnchants = EnchantmentTargets.getMaxEnchants(itemStack)

            if (maxEnchants > 0) {
                val remaining = (maxEnchants - enchants.size).coerceAtLeast(0)

                enchantLore.add(
                    Display.PREFIX + plugin.configYml.getFormattedString("display.capacity.format")
                        .replace("%capacity%", remaining.toString())
                        .replace("%used%", enchants.size.toString())
                        .replace("%max%", maxEnchants.toString())
                )
            }
        }

        fast.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        if (itemStack.type == Material.ENCHANTED_BOOK) {
            hse.hideStoredEnchants(fast)
        }

        // 附魔块与物品原 lore 之间的分隔空行（对应 aiyatsbus lore_formation 里的 "&r "）。
        // 仅在两边都非空时插入 —— aiyatsbus 的 without_lore 分支同样没有这一行，
        // 否则没有 lore 的物品尾部会多出一行空白。
        // 必须走 getFormattedString：getString 不会把 & 转成 §，配置里的 "&r " 会原样
        // 当作纯文本塞进 lore，客户端就把 &r 三个字符直接画出来了
        val separator = plugin.configYml.getFormattedString("display.separator")
        val separatorLine = if (
            separator.isNotEmpty() && enchantLore.isNotEmpty() && lore.isNotEmpty()
        ) {
            listOf(Display.PREFIX + separator)
        } else {
            emptyList()
        }

        if (plugin.configYml.getBool("display.enchantments-below-lore")) {
            fast.lore = lore + separatorLine + enchantLore + notMetLines
        } else {
            fast.lore = enchantLore + separatorLine + lore + notMetLines
        }
    }

    override fun revert(itemStack: ItemStack) {
        if (plugin.configYml.getBool("display.require-enchantable") && !itemStack.isEnchantable) {
            return
        }

        val fast = itemStack.fast()
        val pdc = fast.persistentDataContainer

        if (pdc.hideState != 1) {
            fast.removeItemFlags(ItemFlag.HIDE_ENCHANTS)

            if (itemStack.type == Material.ENCHANTED_BOOK) {
                hse.showStoredEnchants(fast)
            }
        }

        pdc.remove(hideStateKey)
    }

    override fun generateVarArgs(itemStack: ItemStack): Array<Any> {
        val fast = itemStack.fast()

        return when (fast.hideState) {
            1 -> arrayOf(true)
            0 -> arrayOf(false)
            else -> arrayOf(
                fast.hasItemFlag(ItemFlag.HIDE_ENCHANTS)
                        || hse.areStoredEnchantsHidden(fast)
            )
        }
    }

    private val FastItemStack.hideState: Int
        get() = this.persistentDataContainer.hideState

    private val PersistentDataContainer.hideState: Int
        get() = this.get(hideStateKey, PersistentDataType.INTEGER) ?: -1
}
