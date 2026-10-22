package com.willfp.ecoenchants.gui

import com.willfp.eco.core.gui.GUIComponent
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.page.Page
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.ecoenchants.gui.SearchFilters.Selection
import com.willfp.ecoenchants.rarity.EnchantmentRarities
import com.willfp.ecoenchants.target.EnchantmentTargets
import org.bukkit.Material
import org.bukkit.entity.Player
import kotlin.math.ceil

/**
 * 品质 / 物品类别 / 类型三个筛选界面。三者结构相同，只是选项来源不同：
 *
 * ```
 * FFFFFFFFF
 * FTTTTTTTF
 * FTTTTTTTF
 * FFFFRFFFF
 *  BC   PN
 * ```
 *
 * 交互照搬 Aiyatsbus：左击「只看」、右击「不看」、Shift 点击清除该项，
 * 中间的 R 一键重置整组条件。选项名后面带该分类下的附魔数量。
 */
object FilterGUI {
    private const val AREA_ROW = 2
    private const val AREA_COLUMN = 2

    /**
     * 一个可勾选的筛选项。[amount] 是该分类下的附魔数，为 0 的选项不会出现。
     *
     * 图标统一用玩家头颅，与 Aiyatsbus 的筛选界面一致 —— 那边所有选项都是
     * PLAYER_HEAD，而不是拿剑斧镐这些实物当图标。
     */
    internal class Option(
        val id: String,
        val display: String,
        val amount: Int,
        /** 未表态时头颅用的材质；没配就是一颗没有皮肤的头 */
        val skull: String?
    )

    private var rarityMenu: Menu? = null
    private var targetMenu: Menu? = null
    private var groupMenu: Menu? = null

    /** 选项在重载时算一次就够，不随玩家变化 */
    private var rarities: List<Option> = emptyList()
    private var targets: List<Option> = emptyList()
    private var groups: List<Option> = emptyList()

    internal fun reload() {
        // 统一数据源：自定义 + 原版附魔。筛选计数必须与附魔大全展示的集合一致
        val all = allEnchantLikes()
        // targetIds 对原版附魔要逐一试装，算一次缓存起来，别在计数里反复求
        val targetIdsOf = all.associateWith { it.targetIds() }

        rarities = EnchantmentRarities.values().map { rarity ->
            Option(
                rarity.id,
                "${rarity.format ?: "&f"}${rarity.displayName}",
                all.count { it.enchantmentRarity.id == rarity.id },
                rarity.skull
            )
        }.filter { it.amount > 0 }

        targets = EnchantmentTargets.values()
            .filter { it.id != "all" }
            .map { target ->
                Option(
                    target.id,
                    "&f${target.displayName}",
                    all.count { target.id in targetIdsOf.getValue(it) },
                    target.skull
                )
            }
            .filter { it.amount > 0 }

        // 定位/组：照搬 aiy 的 group.yml，空组不显示
        groups = Groups.all.map { group ->
            Option(
                group.id,
                "&f${group.displayName}",
                all.count { group.matches(it) },
                group.skull
            )
        }.filter { it.amount > 0 }

        rarityMenu = build("&8筛选器: 品质", "此品质", { rarities }) { SearchFilters[it].rarities }
        targetMenu = build("&8筛选器: 物品类别", "适合此类物品", { targets }) { SearchFilters[it].targets }
        groupMenu = build("&8筛选器: 定位/组", "此定位", { groups }) { SearchFilters[it].groups }
    }

    fun openRarity(player: Player) = open(rarityMenu, player)
    fun openTarget(player: Player) = open(targetMenu, player)
    fun openGroup(player: Player) = open(groupMenu, player)

    private fun open(menu: Menu?, player: Player) {
        menu ?: return
        menu.open(player)
        menu.setState(player, Page.PAGE_KEY, 1)
    }

    private fun build(
        menuTitle: String,
        what: String,
        options: () -> List<Option>,
        selection: (Player) -> Selection
    ): Menu {
        val pane = OptionPane(what, options, selection)

        return menu(5) {
            title = menuTitle

            fillBackground(5)
            addComponent(AREA_ROW, AREA_COLUMN, pane)

            setSlot(
                4, 5, slot(head(Heads.RESET, "&c重置", listOf("&8| &7点击清除当前的筛选条件"))) {
                    onLeftClick { event, _ ->
                        val player = event.whoClicked as Player
                        selection(player).clear()
                    }
                }
            )

            setSlot(5, 2, backSlot("附魔大全") { EnchantSearchGUI.open(it) })
            setSlot(5, 3, closeSlot())
            addPager(5, 7, 8)

            maxPages {
                val total = options().size
                if (total == 0) 1 else ceil(total.toDouble() / pane.size).toInt()
            }
        }
    }

    /** 7×2 的选项区 */
    private class OptionPane(
        private val what: String,
        private val options: () -> List<Option>,
        private val selection: (Player) -> Selection
    ) : GUIComponent {
        val size = 14

        override fun getRows() = 2
        override fun getColumns() = 7

        override fun getSlotAt(row: Int, column: Int, player: Player, menu: Menu): Slot {
            val index = (column - 1) + (row - 1) * 7 + size * (menu.getPage(player) - 1)
            val option = options().getOrNull(index) ?: return EMPTY_SLOT

            return slot({ p, _ ->
                // 与 Aiyatsbus 的 FilterRarityUI 一致：选中换成绿玻璃、排除换成红玻璃，
                // 没表态才是那颗带皮肤的头
                val base = when (selection(p)[option.id]) {
                    true -> ItemStackBuilder(Material.LIME_STAINED_GLASS_PANE)
                    false -> ItemStackBuilder(Material.RED_STAINED_GLASS_PANE)
                    null -> ItemStackBuilder(skullOf(option.skull))
                }

                base.setDisplayName("${option.display} &7(&a${option.amount}&7)")
                    .addLoreLines(
                        listOf(
                            "&8| &7左击 &a✓只看${what}的附魔",
                            "&8| &7Shift + 点击 &e清除当前筛选",
                            "&8| &7右击 &c✗不看${what}的附魔"
                        )
                    )
                    .build()
            }) {
                onLeftClick { event, _ ->
                    selection(event.whoClicked as Player).include(option.id)
                }

                onRightClick { event, _ ->
                    selection(event.whoClicked as Player).exclude(option.id)
                }

                // Shift 点击是独立的 ClickType，不会落进 onLeftClick / onRightClick。
                // 清掉的是这一项，整组重置是中间那个按钮的事
                onShiftLeftClick { event, _ ->
                    selection(event.whoClicked as Player).remove(option.id)
                }

                onShiftRightClick { event, _ ->
                    selection(event.whoClicked as Player).remove(option.id)
                }
            }
        }
    }
}
