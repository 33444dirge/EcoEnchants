package com.willfp.ecoenchants.gui

import com.willfp.eco.core.gui.GUIComponent
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.page.Page
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.items.isEcoEmpty
import com.willfp.ecoenchants.display.getFormattedName
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchantLike
import com.willfp.ecoenchants.enchant.parseYesOrNo
import com.willfp.ecoenchants.enchant.wrap
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.target.EnchantmentTargets
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * 附魔详情。布局照搬 Aiyatsbus 的 core/ui/enchant_info.yml：
 *
 * ```
 * FIFJFXZYF
 * FKFMFFFFF
 * FFFFFSDGF
 * FEEEEEEEF
 * FEEEEEEEF
 * FBCFLFPNF
 * ```
 *
 * 上方是附魔本身的各项信息与等级增减，中间三个按钮切换下方列表的内容
 * （冲突 / 依赖 / 同类），下方是对应的附魔列表。
 */
object EnchantInfoGUI {
    private const val STATE_ENCHANT = "info_enchant"
    private const val STATE_LEVEL = "info_level"
    private const val STATE_TAB = "info_tab"

    private const val TAB_CONFLICTS = "conflicts"
    private const val TAB_REQUIRED = "required"
    private const val TAB_RELATED = "related"

    private const val AREA_ROW = 4
    private const val AREA_COLUMN = 2

    private const val ITEM_ROW = 2
    private const val ITEM_COLUMN = 4

    /** 每个玩家从哪里进来的，决定「返回」按钮回哪 */
    private val backTargets = ConcurrentHashMap<UUID, (Player) -> Unit>()

    /** 下方列表每次渲染算一遍就够，14 个格子各算一次会把全表扫 14 遍 */
    private val related = resultCache<EcoEnchantLike>()

    private var menu: Menu? = null

    internal fun forget(player: UUID) {
        backTargets.remove(player)
    }

    internal fun reload() {
        val pane = RelatedPane()

        menu = menu(6) {
            title = "&8附魔详情"

            // 物品检测格要放装备，得放开主手那一格的操作
            allowChangingHeldItem()

            fillBackground(6)

            addComponent(AREA_ROW, AREA_COLUMN, pane)

            setSlot(1, 2, basicSlot())
            setSlot(1, 4, limitationsSlot())
            setSlot(1, 6, levelStepSlot(-1))
            setSlot(1, 7, levelSlot())
            setSlot(1, 8, levelStepSlot(1))
            setSlot(2, 2, otherSlot())

            // 物品检测：放进去一件装备，看这个附魔能不能上
            setSlot(ITEM_ROW, ITEM_COLUMN, slot(ItemStack(Material.AIR)) { setCaptive(true) })
            setSlot(3, 4, itemTestSlot())

            setSlot(3, 6, tabSlot(Heads.CONFLICTS, "&c冲突", TAB_CONFLICTS))
            setSlot(3, 7, tabSlot(Heads.REQUIRED, "&a依赖", TAB_REQUIRED))
            setSlot(3, 8, tabSlot(Heads.RELATED, "&e相关", TAB_RELATED))

            setSlot(6, 2, slot({ player, _ ->
                head(Heads.BACK, "&b返回")
            }) {
                onLeftClick { event, _ ->
                    // 不在这里还物品：切换菜单同样会触发本界面的 onClose，
                    // 那边已经还过一次，这里再还就翻倍了
                    afterInventoryClick(event) { player ->
                        (backTargets[player.uniqueId] ?: { MainMenu.open(it) })(player)
                    }
                }
            })

            setSlot(6, 3, closeSlot())
            setSlot(6, 5, favoriteSlot())
            addPager(6, 7, 8)

            onRender { player, menu ->
                related[player] = relatedOf(player, menu)
            }

            onClose { event, _ ->
                returnCaptives(event, ITEM_ROW to ITEM_COLUMN)
            }

            maxPages { player ->
                val total = related[player].size
                if (total == 0) 1 else ceil(total.toDouble() / pane.size).toInt()
            }
        }
    }

    fun open(player: Player, enchant: EcoEnchantLike, level: Int = enchant.maximumLevel, backTo: ((Player) -> Unit)? = null) {
        val menu = menu ?: return

        if (backTo != null) {
            backTargets[player.uniqueId] = backTo
        }

        menu.open(player)
        menu.setState(player, STATE_ENCHANT, enchant)
        menu.setState(player, STATE_LEVEL, level.coerceIn(1, enchant.maximumLevel))
        menu.setState(player, STATE_TAB, TAB_CONFLICTS)
        menu.setState(player, Page.PAGE_KEY, 1)
    }

    // ------------------------------------------------------------ 各按钮

    private fun enchantOf(player: Player, menu: Menu): EcoEnchantLike? =
        menu.getState<EcoEnchantLike>(player, STATE_ENCHANT)

    private fun levelOf(player: Player, menu: Menu): Int =
        menu.getState<Int>(player, STATE_LEVEL) ?: 1

    private fun basicSlot(): Slot = slot({ player, menu ->
        val enchant = enchantOf(player, menu) ?: return@slot FILLER
        enchantEntry(enchant, levelOf(player, menu), player)
    })

    private fun levelSlot(): Slot = slot({ player, menu ->
        val enchant = enchantOf(player, menu) ?: return@slot FILLER
        val level = levelOf(player, menu)

        head(
            Heads.LEVEL, "&7等级数据 &a$level", listOf(
                "&8| &7当前 > &e$level &7/ &c${enchant.maximumLevel}",
                "&7",
                "&8| &c点击&7还原至最大等级"
            )
        )
    }) {
        onLeftClick { event, _ ->
            val player = event.whoClicked as Player
            val menu = menu ?: return@onLeftClick
            val enchant = enchantOf(player, menu) ?: return@onLeftClick
            menu.setState(player, STATE_LEVEL, enchant.maximumLevel)
        }
    }

    private fun levelStepSlot(step: Int): Slot {
        val positive = step > 0

        return slot({ _, _ ->
            ItemStackBuilder(if (positive) Material.GREEN_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE)
                .setDisplayName(if (positive) "&a+1" else "&c-1")
                .addLoreLines(listOf("&8| &7点击使查询等级${if (positive) "&a+1" else "&c-1"}&7级"))
                .build()
        }) {
            onLeftClick { event, _ ->
                val player = event.whoClicked as Player
                val menu = menu ?: return@onLeftClick
                val enchant = enchantOf(player, menu) ?: return@onLeftClick

                val next = (levelOf(player, menu) + step).coerceIn(1, enchant.maximumLevel)
                menu.setState(player, STATE_LEVEL, next)
            }
        }
    }

    /** libreforge 的槽位 id 是英文，这里翻成中文用于「位置」一栏 */
    private val slotNames = mapOf(
        "any" to "任意",
        "armor" to "盔甲",
        "boots" to "靴子",
        "chestplate" to "胸甲",
        "hand" to "主手",
        "hands" to "双手",
        "helmet" to "头盔",
        "leggings" to "护腿",
        "mainhand" to "主手",
        "offhand" to "副手"
    )

    private fun slotDisplayName(id: String): String = slotNames[id] ?: id

    private fun limitationsSlot(): Slot = slot({ player, menu ->
        val enchant = enchantOf(player, menu) ?: return@slot FILLER
        val custom = enchant as? EcoEnchant

        val conflicts = if (custom?.conflictsWithEverything == true) {
            plugin.langYml.getFormattedString("all-conflicts")
        } else {
            conflictingEnchantments(enchant).joinToString("&7, ") { it.getFormattedName(0) }
                .ifEmpty { plugin.langYml.getFormattedString("no-conflicts") }
        }
        val targets = if (custom != null) {
            custom.targets.joinToString("&7, ") { "&f${it.displayName}" }
        } else {
            enchant.targetIds().mapNotNull { EnchantmentTargets[it] }
                .joinToString("&7, ") { "&f${it.displayName}" }
        }
        val slots = custom?.slots?.joinToString("&7, ") { "&f${slotDisplayName(it.id)}" } ?: "&f原版机制"
        val required = custom?.required?.joinToString("&7, ") { it.wrap().getFormattedName(0) }
            ?.ifEmpty { plugin.langYml.getFormattedString("no-required") }
            ?: plugin.langYml.getFormattedString("no-required")

        head(
            Heads.LIMITATIONS, "&6限制", listOf(
                "&8| &7对象 > $targets",
                "&8| &7位置 > $slots",
                "&8| &7冲突 > $conflicts",
                "&8| &7依赖 > $required",
                "&8| &7最高等级 > &c${enchant.maximumLevel}"
            )
        )
    })

    private fun otherSlot(): Slot = slot({ player, menu ->
        val enchant = enchantOf(player, menu) ?: return@slot FILLER
        val custom = enchant as? EcoEnchant
        @Suppress("DEPRECATION")
        val enchantable = if (custom != null) {
            custom.isObtainableThroughEnchanting && custom.enchantmentRarity.id != "artifact"
        } else {
            !enchant.enchantment.isTreasure
        }
        val discoverySource = if (custom == null) "&e原版机制" else false.parseYesOrNo()

        head(
            Heads.FILTER_TEXT, "&e其他属性", listOf(
                "&8| &7品质 > ${enchant.enchantmentRarity.formattedName}",
                "&8| &7类型 > ${enchant.type.format}${enchant.type.displayName}",
                "&8| &7附魔台 > ${enchantable.parseYesOrNo()}",
                "&8| &7村民交易 > $discoverySource",
                "&8| &7宝箱 > $discoverySource",
                "&8| &7钓鱼 > $discoverySource",
                "&8| &7怪物掉落 > $discoverySource",
                "&8| &7袭击 > $discoverySource"
            )
        )
    })

    private fun itemTestSlot(): Slot = slot({ player, menu ->
        val enchant = enchantOf(player, menu) ?: return@slot FILLER
        val item = menu.getCaptiveItem(player, ITEM_ROW, ITEM_COLUMN)

        val lore = if (item == null || item.isEcoEmpty) {
            listOf("&8| &7把物品放进左边的空格", "&8| &7即可检测本附魔能否安装")
        } else {
            val reasons = AnvilSimGUI.reasonsAgainst(enchant, item)

            if (reasons.isEmpty()) {
                listOf("&8| &7状态 > &a可以安装")
            } else {
                listOf("&8| &7状态 > &c不可安装") + reasons.map { "&8| &c- &7$it" }
            }
        }

        head(Heads.ITEM_TEST, "&e物品检测", lore)
    })

    private fun favoriteSlot(): Slot = slot({ player, menu ->
        val enchant = enchantOf(player, menu) ?: return@slot FILLER
        val favorited = Favorites.has(player, enchant.favoriteId())

        head(
            Heads.FAVORITES, "&b收藏夹", listOf(
                "&8| &7记不清楚想要什么附魔？",
                "&8| &7把它们收集起来，方便查阅",
                "&7",
                "&8| &7点击按钮将${if (favorited) "&c移出收藏夹" else "&a加入收藏夹"}"
            )
        )
    }) {
        onLeftClick { event, _ ->
            val player = event.whoClicked as Player
            val menu = menu ?: return@onLeftClick
            val enchant = enchantOf(player, menu) ?: return@onLeftClick
            Favorites.toggle(player, enchant.favoriteId())
        }
    }

    private fun tabSlot(texture: String, name: String, tab: String): Slot = slot({ player, menu ->
        val active = menu.getState<String>(player, STATE_TAB) == tab

        head(
            texture, "$name${if (active) " &8(当前)" else ""}", listOf(
                "&8| &7点击在下方显示",
                when (tab) {
                    TAB_CONFLICTS -> "&8| &7与本附魔冲突的附魔"
                    TAB_REQUIRED -> "&8| &7本附魔依赖的附魔"
                    else -> "&8| &7与本附魔同类的附魔"
                }
            )
        )
    }) {
        onLeftClick { event, _ ->
            val player = event.whoClicked as Player
            val menu = menu ?: return@onLeftClick
            menu.setState(player, STATE_TAB, tab)
            menu.setState(player, Page.PAGE_KEY, 1)
        }
    }

    // ------------------------------------------------------------ 下方列表

    private fun relatedOf(player: Player, menu: Menu): List<EcoEnchantLike> {
        val enchant = enchantOf(player, menu) ?: return emptyList()
        val custom = enchant as? EcoEnchant
        val all = allEnchantLikes()

        return when (menu.getState<String>(player, STATE_TAB)) {
            TAB_REQUIRED -> custom?.required?.map { it.wrap() }?.filter { it.isEnabled } ?: emptyList()

            TAB_RELATED -> all.filter {
                it.enchantment != enchant.enchantment && it.isEnabled && it.type == enchant.type
            }

            // 冲突是双向的：对方声明与我冲突也算
            else -> if (custom?.conflictsWithEverything == true) {
                all.filter { it.enchantment != enchant.enchantment && it.isEnabled }
            } else {
                conflictingEnchantments(enchant)
            }
        }
    }

    private fun conflictingEnchantments(enchant: EcoEnchantLike): List<EcoEnchantLike> =
        allEnchantLikes().filter { other ->
            other.isEnabled && other.enchantment != enchant.enchantment &&
                    (when (enchant) {
                        is EcoEnchant -> enchant.conflictsWith(other.enchantment)
                        else -> enchant.enchantment.conflictsWith(other.enchantment) ||
                                (other as? EcoEnchant)?.conflictsWith(enchant.enchantment) == true
                    })
        }

    /** 7×2 的关联附魔区 */
    private class RelatedPane : GUIComponent {
        val size = 14

        override fun getRows() = 2
        override fun getColumns() = 7

        override fun getSlotAt(row: Int, column: Int, player: Player, menu: Menu): Slot {
            val index = (column - 1) + (row - 1) * 7 + size * (menu.getPage(player) - 1)
            val entry = related[player].getOrNull(index) ?: return EMPTY_SLOT

            return slot({ p, _ -> enchantEntry(entry, entry.maximumLevel, p) }) {
                onLeftClick { event, _ ->
                    val clicker = event.whoClicked as Player
                    val current = enchantOf(clicker, menu)

                    afterInventoryClick(event) {
                        open(it, entry, backTo = { p ->
                            if (current != null) open(p, current) else MainMenu.open(p)
                        })
                    }
                }
            }
        }
    }
}
