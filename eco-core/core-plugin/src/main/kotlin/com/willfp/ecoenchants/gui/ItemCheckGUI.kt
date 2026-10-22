package com.willfp.ecoenchants.gui

import com.willfp.eco.core.fast.fast
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.page.Page
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.isEcoEmpty
import com.willfp.ecoenchants.display.EnchantSorter.sortForDisplay
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.wrap
import com.willfp.ecoenchants.target.EnchantmentTargets.applicableEnchantments
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.math.ceil

/**
 * 物品查询。布局照搬 Aiyatsbus 的 core/ui/item_check.yml：
 *
 * ```
 * FFFFFFFFF
 * FIFEEEEEF
 * FFFEEEEEF
 * FMFEEEEEF
 * FFFBCFPNF
 * ```
 *
 * 左上放物品，右侧列出它的附魔；左下切换「已有附魔 / 可安装的附魔」两种模式。
 */
object ItemCheckGUI {
    private const val AREA_ROW = 2
    private const val AREA_COLUMN = 4

    private const val ITEM_ROW = 2
    private const val ITEM_COLUMN = 2

    private const val STATE_MODE = "item_check_mode"

    private const val MODE_OWNED = "owned"
    private const val MODE_AVAILABLE = "available"

    private val results = resultCache<EcoEnchant>()

    private var menu: Menu? = null

    internal fun reload() {
        val pane = EnchantPane(rows = 3, columns = 5, source = results) { player, enchant ->
            EnchantInfoGUI.open(player, enchant) { ItemCheckGUI.open(it) }
        }

        menu = menu(5) {
            title = "&8物品查询 &7— 点击背包物品上传"

            // 不加这句的话 eco 会锁住主手所在的那一格，玩家拿不出手里正拿着的东西
            allowChangingHeldItem()

            fillBackground(5)

            addComponent(AREA_ROW, AREA_COLUMN, pane)

            setSlot(ITEM_ROW, ITEM_COLUMN, slot(ItemStack(Material.AIR)) { setCaptive(true) })
            setSlot(4, 2, modeSlot())

            setSlot(5, 4, backSlot("主菜单") { MainMenu.open(it) })
            setSlot(5, 5, closeSlot())
            addPager(5, 7, 8)

            onRender { player, menu ->
                results[player] = resultsFor(player, menu)
            }

            onClose { event, _ ->
                returnCaptives(event, ITEM_ROW to ITEM_COLUMN)
            }

            maxPages { player ->
                val total = results[player].size
                if (total == 0) 1 else ceil(total.toDouble() / pane.size).toInt()
            }
        }
    }

    fun open(player: Player) {
        val menu = menu ?: return
        menu.open(player)
        menu.setState(player, Page.PAGE_KEY, 1)

        if (menu.getState<String>(player, STATE_MODE) == null) {
            menu.setState(player, STATE_MODE, MODE_OWNED)
        }
    }

    private fun resultsFor(player: Player, menu: Menu): List<EcoEnchant> {
        val item = menu.getCaptiveItem(player, ITEM_ROW, ITEM_COLUMN)

        if (item == null || item.isEcoEmpty) {
            return emptyList()
        }

        return when (menu.getState<String>(player, STATE_MODE)) {
            MODE_AVAILABLE -> item.applicableEnchantments
                .filter { it.enchantment !in item.fast().getEnchants(true).keys }
                .map { it.enchantment }
                .sortForDisplay()
                .mapNotNull { it.wrap() as? EcoEnchant }

            else -> item.fast().getEnchants(true).keys
                .sortForDisplay()
                .mapNotNull { it.wrap() as? EcoEnchant }
                .filter { it.isEnabled }
        }
    }

    private fun modeSlot(): Slot = slot({ player, menu ->
        val owned = menu.getState<String>(player, STATE_MODE) != MODE_AVAILABLE

        head(
            Heads.MODE, "&b查询模式", listOf(
                "&8| &7点击即可切换模式",
                "      &7> ${if (owned) "&a已有附魔" else "&7已有附魔"}",
                "      &7> ${if (owned) "&7可安装的附魔" else "&a可安装的附魔"}"
            )
        )
    }) {
        onLeftClick { event, _ ->
            val player = event.whoClicked as Player
            val menu = menu ?: return@onLeftClick

            val next = if (menu.getState<String>(player, STATE_MODE) == MODE_AVAILABLE) {
                MODE_OWNED
            } else {
                MODE_AVAILABLE
            }

            menu.setState(player, STATE_MODE, next)
            menu.setState(player, Page.PAGE_KEY, 1)
        }
    }
}
