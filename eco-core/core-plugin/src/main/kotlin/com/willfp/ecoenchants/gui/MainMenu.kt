package com.willfp.ecoenchants.gui

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot
import org.bukkit.entity.Player

/**
 * 附魔菜单的入口。布局照搬 Aiyatsbus 的 core/ui/menu.yml：
 *
 * ```
 * FFFFFFFFF
 *  A B C D
 * FFFFFFFFF
 * ```
 */
object MainMenu {
    private val menu: Menu by lazy {
        menu(3) {
            title = "&8更多附魔"

            for (row in 1..3) {
                for (column in 1..9) {
                    if (row != 2) {
                        setSlot(row, column, FILLER_SLOT)
                    }
                }
            }

            setSlot(
                2, 2, slot(
                    head(
                        Heads.SEARCH, "&b附魔大全", listOf(
                            "&8| &7在这里查询所有附魔",
                            "&8| &7通过以下条件&e筛选附魔",
                            "     &7- 品质",
                            "     &7- 物品类别",
                            "     &7- 分类/定位",
                            "     &7- 名称/描述"
                        )
                    )
                ) {
                    onLeftClick { event, _ -> afterInventoryClick(event, EnchantSearchGUI::open) }
                }
            )

            setSlot(
                2, 4, slot(
                    head(
                        Heads.ITEM_CHECK, "&b物品查询", listOf(
                            "&8| &7在这里查询物品上的附魔",
                            "&8| &7你可以点击附魔进入&e详细界面"
                        )
                    )
                ) {
                    onLeftClick { event, _ -> afterInventoryClick(event, ItemCheckGUI::open) }
                }
            )

            setSlot(
                2, 6, slot(
                    head(
                        Heads.ANVIL, "&c铁砧模拟器", listOf(
                            "&8| &7在这里预览铁砧拼合的结果",
                            "&8| &7或得知&e为什么不能进行拼合",
                            "     &7- 冲突",
                            "     &7- 依赖",
                            "     &7- 等级限制",
                            "     &7- 词条限制"
                        )
                    )
                ) {
                    onLeftClick { event, _ -> afterInventoryClick(event, AnvilSimGUI::open) }
                }
            )

            setSlot(
                2, 8, slot(
                    head(
                        Heads.FAVORITES, "&b收藏夹", listOf(
                            "&8| &7在这里查询收藏夹内的附魔",
                            "&8| &7可快速查询你收藏的附魔的信息"
                        )
                    )
                ) {
                    onLeftClick { event, _ -> afterInventoryClick(event, FavoritesGUI::open) }
                }
            )
        }
    }

    fun open(player: Player) = menu.open(player)
}
