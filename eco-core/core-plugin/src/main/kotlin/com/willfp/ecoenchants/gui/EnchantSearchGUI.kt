package com.willfp.ecoenchants.gui

import com.willfp.eco.core.gui.GUIComponent
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.page.Page
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.util.formatEco
import com.willfp.ecoenchants.display.EnchantSorter.sortForDisplay
import com.willfp.ecoenchants.display.getFormattedName
import com.willfp.ecoenchants.enchant.EcoEnchantLike
import com.willfp.ecoenchants.enchant.wrap
import com.willfp.ecoenchants.rarity.EnchantmentRarities
import com.willfp.ecoenchants.target.EnchantmentTargets
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import kotlin.math.ceil

/**
 * 附魔大全。布局照搬 Aiyatsbus 的 core/ui/enchant_search.yml：
 *
 * ```
 * FFFFFFFFF
 * FFEEEEEFF
 * WFEEEEEFY
 * XFEEEEEFZ
 * FFEEEEEFF
 * BFFPFNFFC
 * ```
 *
 * 左右两列是四个筛选入口，中间 5×4 是附魔列表。
 */
object EnchantSearchGUI {
    private const val AREA_ROW = 2
    private const val AREA_COLUMN = 3

    /** 每次渲染算一遍，格子和翻页按钮都读这里。含原版附魔，故用 EcoEnchantLike */
    private val results = resultCache<EcoEnchantLike>()

    private var menu: Menu? = null

    internal fun reload() {
        val pane = EnchantPane(rows = 4, columns = 5, source = results) { player, enchant ->
            EnchantInfoGUI.open(player, enchant) { EnchantSearchGUI.open(it) }
        }

        menu = menu(6) {
            title = "&8附魔大全 &7— 两侧按钮可筛选"

            fillBackground(6)
            addComponent(AREA_ROW, AREA_COLUMN, pane)

            setSlot(3, 1, filterButton(Heads.FILTER_RARITY, "&b筛选 &7>> &e按品质", "品质", FilterKind.RARITY))
            setSlot(4, 1, filterButton(Heads.FILTER_TARGET, "&b筛选 &7>> &e按物品类别", "物品类别", FilterKind.TARGET))
            setSlot(3, 9, filterButton(Heads.FILTER_TYPE, "&b筛选 &7>> &e按定位/组", "定位/组", FilterKind.GROUP))
            setSlot(4, 9, textFilterButton())

            setSlot(6, 1, backSlot("主菜单") { MainMenu.open(it) })
            setSlot(6, 9, closeSlot())
            addPager(6, 4, 6)

            onRender { player, menu ->
                results[player] = matching(player)

                // 筛选变严格后，原来的页码可能已经越界
                if (menu.getPage(player) > maxPageOf(player, pane.size)) {
                    menu.setState(player, Page.PAGE_KEY, 1)
                }
            }

            maxPages { player -> maxPageOf(player, pane.size) }
        }
    }

    fun open(player: Player) {
        menu?.open(player)
    }

    private fun maxPageOf(player: Player, perPage: Int): Int {
        val total = results[player].size
        return if (total == 0) 1 else ceil(total.toDouble() / perPage).toInt()
    }

    /** 当前筛选条件下的附魔，按显示顺序排列（含原版附魔） */
    internal fun matching(player: Player): List<EcoEnchantLike> {
        val filters = SearchFilters[player]

        val matched = allEnchantLikes().filter { enchant ->
            filters.rarities.accepts(listOf(enchant.enchantmentRarity.id)) &&
                    filters.targets.accepts(enchant.targetIds()) &&
                    filters.groups.accepts(Groups.idsContaining(enchant)) &&
                    (filters.include?.let { enchant.matchesText(it, player) } ?: true) &&
                    (filters.exclude?.let { !enchant.matchesText(it, player) } ?: true)
        }

        return matched.map { it.enchantment }
            .sortForDisplay()
            .map { it.wrap() }
    }

    /** 名字、id 或描述里出现该关键字（忽略颜色代码与大小写） */
    private fun EcoEnchantLike.matchesText(text: String, player: Player): Boolean {
        val keyword = text.lowercase()

        @Suppress("DEPRECATION")
        val name = ChatColor.stripColor(this.getFormattedName(0))?.lowercase() ?: ""

        if (keyword in name || keyword in this.enchantment.key.key) {
            return true
        }

        @Suppress("DEPRECATION")
        return this.getRawDescription(0, player).any {
            ChatColor.stripColor(it.formatEco())?.lowercase()?.contains(keyword) == true
        }
    }

    private enum class FilterKind { RARITY, TARGET, GROUP }

    private fun FilterKind.selectionOf(player: Player): SearchFilters.Selection = when (this) {
        FilterKind.RARITY -> SearchFilters[player].rarities
        FilterKind.TARGET -> SearchFilters[player].targets
        FilterKind.GROUP -> SearchFilters[player].groups
    }

    private fun FilterKind.openGui(player: Player) = when (this) {
        FilterKind.RARITY -> FilterGUI.openRarity(player)
        FilterKind.TARGET -> FilterGUI.openTarget(player)
        FilterKind.GROUP -> FilterGUI.openGroup(player)
    }

    /** 把筛选项的内部 id 翻成中文显示名，用于按钮 lore 的「当前筛选」摘要 */
    private fun FilterKind.displayNameOf(id: String): String = when (this) {
        FilterKind.RARITY -> EnchantmentRarities[id]?.displayName ?: id
        FilterKind.TARGET -> EnchantmentTargets[id]?.displayName ?: id
        FilterKind.GROUP -> Groups[id]?.displayName ?: id
    }

    private fun filterButton(texture: String, name: String, what: String, kind: FilterKind): Slot =
        slot({ player, _ ->
            head(
                texture, name, listOf(
                    "&8| &7当前筛选的$what > ",
                    "&7    ${kind.selectionOf(player).summary { kind.displayNameOf(it) }}",
                    "&7",
                    "&8| &7点击选择显示或不显示特定${what}的附魔",
                    "&8| &7Shift + 点击清除当前筛选"
                )
            )
        }) {
            onLeftClick { event, _ -> afterInventoryClick(event) { kind.openGui(it) } }

            // Shift 点击是独立的 ClickType（SHIFT_LEFT / SHIFT_RIGHT），
            // 不会走 onLeftClick —— 之前就是漏了这两个处理器，所以清不掉筛选
            onShiftLeftClick { event, _ ->
                kind.selectionOf(event.whoClicked as Player).clear()
            }

            onShiftRightClick { event, _ ->
                kind.selectionOf(event.whoClicked as Player).clear()
            }
        }

    private fun textFilterButton(): Slot = slot({ player, _ ->
        val filters = SearchFilters[player]

        head(
            Heads.FILTER_TEXT, "&b筛选 &7>> &e按名字/描述", listOf(
                "&8| &7只看含有 > &a${filters.include ?: "（无）"}",
                "&8| &7不看含有 > &c${filters.exclude ?: "（无）"}",
                "&7",
                "&8| &7左击 &a✓只看含有特定字段的附魔",
                "&8| &7右击 &c✗不看含有特定字段的附魔",
                "&8| &7Shift + 点击 &e清除当前筛选"
            )
        )
    }) {
        onLeftClick { event, _ ->
            afterInventoryClick(event) { player ->
                ChatInput.await(player, "&e请在聊天栏输入要&a只看&e的关键字，输入 &ccancel &e取消") { input ->
                    SearchFilters[player].include = input
                    open(player)
                }
            }
        }

        onRightClick { event, _ ->
            afterInventoryClick(event) { player ->
                ChatInput.await(player, "&e请在聊天栏输入要&c排除&e的关键字，输入 &ccancel &e取消") { input ->
                    SearchFilters[player].exclude = input
                    open(player)
                }
            }
        }

        onShiftLeftClick { event, _ -> clearText(event.whoClicked as Player) }
        onShiftRightClick { event, _ -> clearText(event.whoClicked as Player) }
    }

    private fun clearText(player: Player) {
        SearchFilters[player].include = null
        SearchFilters[player].exclude = null
    }
}

/**
 * 附魔列表区域。
 *
 * 只负责按页取出对应的附魔并渲染，条目本身由 [enchantEntry] 缓存，
 * 所以翻页不会反复构造相同的物品。
 */
internal class EnchantPane<T : EcoEnchantLike>(
    private val rows: Int,
    private val columns: Int,
    private val source: ResultCache<T>,
    private val onClick: (Player, T) -> Unit
) : GUIComponent {
    val size = rows * columns

    override fun getRows() = rows
    override fun getColumns() = columns

    override fun getSlotAt(row: Int, column: Int, player: Player, menu: Menu): Slot {
        val index = (column - 1) + (row - 1) * columns + size * (menu.getPage(player) - 1)
        val enchant = source[player].getOrNull(index) ?: return EMPTY_SLOT

        return slot({ p, _ -> enchantEntry(enchant, enchant.maximumLevel, p) }) {
            onLeftClick { event, _ -> afterInventoryClick(event) { onClick(it, enchant) } }
        }
    }
}
