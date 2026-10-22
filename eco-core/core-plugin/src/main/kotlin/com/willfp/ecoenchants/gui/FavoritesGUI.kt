package com.willfp.ecoenchants.gui

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.page.Page
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchantLike
import com.willfp.ecoenchants.enchant.EcoEnchants
import org.bukkit.entity.Player
import kotlin.math.ceil

/**
 * 收藏夹。布局照搬 Aiyatsbus 的 core/ui/favorites.yml：
 *
 * ```
 * FFFFFFFFF
 * FFEEEEEFF
 * FFEEEEEFF
 * FFEEEEEFF
 * FFEEEEEFF
 * BFFPFNFFC
 * ```
 */
object FavoritesGUI {
    private const val AREA_ROW = 2
    private const val AREA_COLUMN = 3
    private val results = resultCache<EcoEnchantLike>()

    private var menu: Menu? = null

    internal fun reload() {
        val pane = EnchantPane(rows = 4, columns = 5, source = results) { player, enchant ->
            EnchantInfoGUI.open(player, enchant) { FavoritesGUI.open(it) }
        }

        menu = menu(6) {
            title = "&8附魔收藏夹"

            fillBackground(6)
            addComponent(AREA_ROW, AREA_COLUMN, pane)

            setSlot(6, 1, backSlot("主菜单") { MainMenu.open(it) })
            setSlot(6, 9, closeSlot())
            addPager(6, 4, 6)

            onRender { player, _ ->
                results[player] = favorites(player)
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
    }

    /** 收藏的附魔；已经被删掉的 id 直接跳过，不用清理存档 */
    private fun favorites(player: Player): List<EcoEnchantLike> {
        val vanillaById = allEnchantLikes()
            .filterNot { it is EcoEnchant }
            .associateBy { it.favoriteId() }

        return Favorites.list(player)
            .mapNotNull { id -> EcoEnchants[id] ?: vanillaById[id] }
            .filter { it.isEnabled }
            .distinctBy { it.enchantment.key }
    }
}
