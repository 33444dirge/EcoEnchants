package com.willfp.ecoenchants.gui

import com.willfp.eco.core.fast.fast
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.items.isEcoEmpty
import com.willfp.ecoenchants.display.getFormattedName
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchantLike

import com.willfp.ecoenchants.enchant.conflictsWithDeep
import com.willfp.ecoenchants.enchant.wrap
import com.willfp.ecoenchants.mechanics.infiniteIfNegative
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.target.EnchantmentTargets
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 铁砧模拟器。布局照搬 Aiyatsbus 的 core/ui/anvil.yml：
 *
 * ```
 * FFFFFFFFF
 * FAPBERFIF
 * FFFFFFFFF
 * ```
 *
 * 左右各放一件物品，中间给出合并结果，右侧说明每一条附魔为什么上不去。
 */
object AnvilSimGUI {
    private const val LEFT_ROW = 2
    private const val LEFT_COLUMN = 2
    private const val RIGHT_ROW = 2
    private const val RIGHT_COLUMN = 4

    private var menu: Menu? = null

    /** 结果槽和信息槽读的是同一次模拟，别算两遍 */
    private val outcomes = ConcurrentHashMap<UUID, Outcome>()

    internal fun reload() {
        menu = menu(3) {
            title = "&8铁砧模拟器 &7— 点击背包物品上传"

            // 不加这句的话 eco 会锁住主手所在的那一格，玩家拿不出手里正拿着的东西
            allowChangingHeldItem()

            fillBackground(3)

            setSlot(LEFT_ROW, LEFT_COLUMN, slot(ItemStack(Material.AIR)) { setCaptive(true) })
            setSlot(2, 3, slot(head(Heads.PLUS, "&e+")))
            setSlot(RIGHT_ROW, RIGHT_COLUMN, slot(ItemStack(Material.AIR)) { setCaptive(true) })
            setSlot(2, 5, slot(head(Heads.EQUALS, "&e=")))

            setSlot(2, 6, resultSlot())
            setSlot(2, 8, infoSlot())

            setSlot(1, 1, backSlot("主菜单") { MainMenu.open(it) })
            setSlot(3, 9, closeSlot())

            onRender { player, menu ->
                val left = menu.getCaptiveItem(player, LEFT_ROW, LEFT_COLUMN)
                val right = menu.getCaptiveItem(player, RIGHT_ROW, RIGHT_COLUMN)

                if (left == null || left.isEcoEmpty || right == null || right.isEcoEmpty) {
                    outcomes.remove(player.uniqueId)
                } else {
                    outcomes[player.uniqueId] = simulate(left, right)
                }
            }

            onClose { event, _ ->
                outcomes.remove(event.player.uniqueId)
                returnCaptives(event, LEFT_ROW to LEFT_COLUMN, RIGHT_ROW to RIGHT_COLUMN)
            }
        }
    }

    fun open(player: Player) {
        menu?.open(player)
    }

    // ------------------------------------------------------------ 结果

    private fun resultSlot(): Slot = slot({ player, _ ->
        val outcome = outcomes[player.uniqueId]

        when {
            outcome == null ->
                head(Heads.ANVIL, "&7结果", listOf("&8| &7两侧都放上物品后", "&8| &7这里会显示合并结果"))

            outcome.invalid != null ->
                ItemStackBuilder(Material.BARRIER)
                    .setDisplayName("&c无法合并")
                    .addLoreLines(listOf("&8| &c${outcome.invalid}"))
                    .build()

            // 一条附魔都没转移上去时，原版铁砧的输出格是空的。
            // 这里要是照原样把左边那件东西摆出来，看着就像「合成成功但没变化」
            outcome.applied.isEmpty() ->
                ItemStackBuilder(Material.BARRIER)
                    .setDisplayName("&c无法合并")
                    .addLoreLines(listOf("&8| &c没有任何附魔能转移到这件物品上", "&8| &7右侧的「拼合信息」写了逐条原因"))
                    .build()

            else -> outcome.result ?: FILLER
        }
    })

    private fun infoSlot(): Slot = slot({ player, _ ->
        val outcome = outcomes[player.uniqueId]

        val lore = when {
            outcome == null -> listOf("&8| &7等待放入物品……")

            outcome.invalid != null -> listOf(
                "&8| &7拼合检测 > &c不通过",
                "&8| &7原因 > &c${outcome.invalid}"
            )

            else -> buildList {
                add("&8| &7拼合检测 > ${if (outcome.applied.isNotEmpty()) "&a通过" else "&e无可转移的附魔"}")
                add("&8| &7可合并附魔 > &a${outcome.applied.size} &7个")
                add("&8| &7被拒绝 > &c${outcome.rejected.size} &7个")

                if (outcome.rejected.isNotEmpty()) {
                    add("&7")
                    add("&8| &7详情 > ")

                    // 只列前几条，免得 lore 长到看不清
                    outcome.rejected.entries.take(6).forEach { (enchant, reasons) ->
                        add("   ${enchant.getFormattedName(0)}")
                        reasons.forEach { add("     &c- &7$it") }
                    }

                    if (outcome.rejected.size > 6) {
                        add("   &7…… 还有 &c${outcome.rejected.size - 6} &7条")
                    }
                }
            }
        }

        head(Heads.ANVIL, "&b拼合信息", lore)
    })

    private class Outcome(
        val result: ItemStack?,
        val applied: Map<EcoEnchantLike, Int>,
        val rejected: Map<EcoEnchantLike, List<String>>,
        /** 整个组合就不成立时的说明，比如拿书去合并一把剑 */
        val invalid: String? = null
    )

    /**
     * 把右侧物品的附魔往左侧物品上套，逐个记录成功与失败的原因。
     *
     * 先按原版铁砧的规则判断这两件东西能不能放一起，再逐条附魔判定。
     */
    private fun simulate(left: ItemStack, right: ItemStack): Outcome {
        val leftIsBook = left.type == Material.ENCHANTED_BOOK
        val rightIsBook = right.type == Material.ENCHANTED_BOOK

        // 原版铁砧：左边是书时只能拿另一本书来合，附魔不会从装备倒流回书里
        if (leftIsBook && !rightIsBook) {
            return Outcome(null, emptyMap(), emptyMap(), "附魔书只能与另一本附魔书合并")
        }

        // 原版铁砧：普通书（minecraft:book）不能作为合成目标，
        // 否则整组书都会被附魔，还会产出原版不存在的带附魔普通书。
        if (left.type == Material.BOOK) {
            return Outcome(null, emptyMap(), emptyMap(), "普通书不能作为铁砧的合成目标")
        }

        // 原版铁砧：两件装备必须同种材质才能合并
        if (!leftIsBook && !rightIsBook && left.type != right.type) {
            return Outcome(null, emptyMap(), emptyMap(), "两件物品材质不同，无法合并")
        }

        val result = left.clone()
        val current = result.fast().getEnchants(true).toMutableMap()

        val applied = mutableMapOf<EcoEnchantLike, Int>()
        val rejected = mutableMapOf<EcoEnchantLike, List<String>>()

        for ((enchantment, level) in right.fast().getEnchants(true)) {
            // 用 wrap 而不是强转 EcoEnchant —— 原版附魔包出来是 VanillaEcoEnchantLike，
            // 之前那句 `as? EcoEnchant` 把锋利、保护这些全都跳过了
            val enchant = enchantment.wrap()

            val reasons = reasonsAgainst(enchant, result, current.keys)

            if (reasons.isNotEmpty()) {
                rejected[enchant] = reasons
                continue
            }

            // 同名附魔按原版规则升一级
            val existing = current[enchantment]
            val newLevel = when {
                existing == null -> level
                existing == level -> (level + 1).coerceAtMost(enchant.maximumLevel)
                else -> maxOf(existing, level)
            }

            current[enchantment] = newLevel
            applied[enchant] = newLevel
        }

        val meta = result.itemMeta

        // 附魔书的附魔存在 storedEnchants 里，用 addEnchant 会变成书自己带附魔
        if (meta is EnchantmentStorageMeta) {
            current.forEach { (enchantment, level) -> meta.addStoredEnchant(enchantment, level, true) }
            result.itemMeta = meta
        } else if (meta != null) {
            current.forEach { (enchantment, level) -> meta.addEnchant(enchantment, level, true) }
            result.itemMeta = meta
        }

        return Outcome(result, applied, rejected)
    }

    /**
     * 这个附魔为什么上不了这件物品。返回空列表代表可以安装。
     *
     * 详情界面的「物品检测」也用它。
     */
    internal fun reasonsAgainst(
        enchant: EcoEnchantLike,
        item: ItemStack,
        existing: Collection<Enchantment> = item.fast().getEnchants(true).keys
    ): List<String> = buildList {
        val self = enchant.enchantment
        val others = existing.filterNot { it.key == self.key }
        val isBook = item.type == Material.ENCHANTED_BOOK

        // 附魔书能存任何附魔，不受适用类别限制
        if (!isBook) {
            val fits = if (enchant is EcoEnchant) {
                enchant.targets.any { it.matches(item) }
            } else {
                @Suppress("DEPRECATION")
                self.canEnchantItem(item)
            }

            if (!fits) {
                val targets = if (enchant is EcoEnchant) {
                    "（需要 ${enchant.targets.joinToString("、") { it.displayName }}）"
                } else {
                    ""
                }

                add("物品类别不符$targets")
            }
        }

        val conflicting = others.filter { it.conflictsWithDeep(self) }

        if (enchant is EcoEnchant && enchant.conflictsWithEverything && others.isNotEmpty()) {
            add("与所有附魔冲突")
        } else if (conflicting.isNotEmpty()) {
            add("与 ${conflicting.joinToString("、") { it.wrap().getFormattedName(0) }} 冲突")
        }

        if (enchant is EcoEnchant && !enchant.hasRequiredEnchantments(others)) {
            add("缺少前置附魔 ${enchant.required.joinToString("、") { it.wrap().getFormattedName(0) }}")
        }

        val sameType = others.count { it.wrap().type == enchant.type }
        if (sameType >= enchant.type.limit) {
            add("${enchant.type.displayName}类附魔已达上限 ${enchant.type.limit}")
        }

        val globalLimit = plugin.configYml.getInt("anvil.enchant-limit").infiniteIfNegative()
        if (others.size >= globalLimit) {
            add("附魔总数已达上限 $globalLimit")
        }

        if (!isBook) {
            val maxEnchants = EnchantmentTargets.getMaxEnchants(item)
            if (maxEnchants > 0 && others.size >= maxEnchants) {
                add("该物品的词条数已满（$maxEnchants）")
            }
        }
    }
}
