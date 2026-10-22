package com.willfp.ecoenchants.gui

import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import com.willfp.eco.core.data.profile
import com.willfp.eco.core.fast.fast
import com.willfp.eco.core.gui.menu.MenuBuilder
import com.willfp.eco.core.gui.page.PageChanger
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.EnchantedBookBuilder
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.util.formatEco
import com.willfp.eco.util.lineWrap
import com.willfp.ecoenchants.EcoEnchantsScheduler
import com.willfp.ecoenchants.display.HideStoredEnchantsProxy
import com.willfp.ecoenchants.display.getFormattedDescription
import com.willfp.ecoenchants.display.getFormattedName
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchantLike
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.enchant.wrap
import com.willfp.ecoenchants.target.EnchantmentTargets
import com.willfp.ecoenchants.plugin
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Aiyatsbus 风格附魔菜单的共用部件。
 *
 * 布局与文案照搬自 Aiyatsbus 的 core/ui 界面配置，实现是本插件自己的，
 * 全部走 eco 的菜单 API，不引入额外依赖。
 */

/** 各按钮的头颅材质，取自 Aiyatsbus 的界面配置 */
internal object Heads {
    const val CLOSE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjc1NDgzNjJhMjRjMGZhODQ1M2U0ZDkzZTY4YzU5NjlkZGJkZTU3YmY2NjY2YzAzMTljMWVkMWU4NGQ4OTA2NSJ9fX0="
    const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzk3OGYzNWM2Mzg2MGQ2OTRmZWU0YWIzYzM2OTRmZGU0MDQ2Yzk2NmU0YzFiZjA2NjVmODg3NDhmOWY4MDI1In19fQ=="
    const val PREVIOUS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjllYTFkODYyNDdmNGFmMzUxZWQxODY2YmNhNmEzMDQwYTA2YzY4MTc3Yzc4ZTQyMzE2YTEwOThlNjBmYjdkMyJ9fX0="
    const val NEXT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODI3MWE0NzEwNDQ5NWUzNTdjM2U4ZTgwZjUxMWE5ZjEwMmIwNzAwY2E5Yjg4ZTg4Yjc5NWQzM2ZmMjAxMDVlYiJ9fX0="

    const val SEARCH = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmM0NDViMDA3YjU1MWM0YTAyNjQ4YmIzODA1MTc3NmJiMGVkMGJiMGQ5MDk3ODIwMjI1ZWJlZmFmNWExZWMzZSJ9fX0="
    const val ITEM_CHECK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWFjZTZiMTgwZjMwYmFlZTJiOTdjN2QwNmY2NDY0ZjcyNDJiNjRjZmMyNzJiZjZlNWE2YjJhNjNkM2JiMjczNyJ9fX0="
    const val ANVIL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjkyZDVkZjgwNWMyMzkwMjJmZTFiNDVmOTQwODgyYmY0MGI1NTk2NzE5MzdkYzcxZmJjOTZmNjMwMjUwZWJjNCJ9fX0="
    const val FAVORITES = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTYzMzBhNGEyMmZmNTU4NzFmYzhjNjE4ZTQyMWEzNzczM2FjMWRjYWI5YzhlMWE0YmI3M2FlNjQ1YTRhNGUifX19"

    const val FILTER_RARITY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzg2YjgwMjZhNjU5NmQ5MjJiM2I5ZTI0NWM2YjNmYWYzMTdhY2Q0ZjNjOTU3YTU4ZmE4NTNhZjY2MzJhOWViMiJ9fX0="
    const val FILTER_TARGET = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjZlYTIxMzU4Mzg0NjE1MzQzNzJmMmRhNmM4NjJkMjFjZDVmM2QyYzcxMTlmMmJiNjc0YmJkNDI3OTEifX19"
    const val FILTER_TYPE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzkyYTBhMzY5Yzg5ZTRiYTJjOTNkMWNhN2Y1YzY1MDkwOTVlMGI5YmUyNWI3MzIxNmVhODgyZGM2NzFjMWE3NiJ9fX0="
    const val FILTER_TEXT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmEyYWZhN2JiMDYzYWMxZmYzYmJlMDhkMmM1NThhN2RmMmUyYmFjZGYxNWRhYzJhNjQ2NjJkYzQwZjhmZGJhZCJ9fX0="
    const val RESET = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2JkYjUyMWZkODM1ZDkyMTIxM2UzODNmOWQ2MzkwZTQyNTU4NDllY2RiN2ViNTA4YzQzZmFjMDczZmU4YjgyYSJ9fX0="

    const val LEVEL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTdlZDY2ZjVhNzAyMDlkODIxMTY3ZDE1NmZkYmMwY2EzYmYxMWFkNTRlZDVkODZlNzVjMjY1ZjdlNTAyOWVjMSJ9fX0="
    const val CONFLICTS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTFkYTNjZWNlMWI3ZGRlZjMwMzgyMWRkODljY2I3NGExNjU5NmNmZTE5MGQzYTMwYTg0NDYzYTI3Mjc4NzE0MiJ9fX0="
    const val REQUIRED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTIyNzVhOTQ1NmM0ZTE1Yzc3Y2UxNTk0YjNmYmUxZGM1MmI4ZDc1OWQyNmQ0MjIyOWI3Zjk5ZjM2MmQ1NDEyMiJ9fX0="
    const val RELATED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzUzYjRkMTEzMzg3NThmMzdmZjIwMmYwZjk5NmI0YzUxOGJmZjM4OTdhZWRkZmEyZDg0NjY0NmYxYzNjMDcyZSJ9fX0="
    const val LIMITATIONS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTFjMGU2OWYyY2RiNTQyNmFhZWQ3MTFjZTAzZWE2ZDcwOWM5MmU5YmViODRhZDFkMjJiNTg0YzQ2MWQ1MzM1ZCJ9fX0="
    const val ITEM_TEST = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzBjN2VlYjU3MmUwYzEyMjcxYmUwMGRkMDc0MGQzMWVjMDViYWI2YWNjNDNhNTYwNDhmMTgyMmUxYzNhY2Y4NSJ9fX0="

    const val UPLOAD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjViOTVkYTEyODE2NDJkYWE1ZDAyMmFkYmQzZTdjYjY5ZGMwOTQyYzgxY2Q2M2JlOWMzODU3ZDIyMmUxYzhkOSJ9fX0="
    const val MODE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjVlODRmOTNlNzkzMTJhMWUyYzUzNDQyYWM1YTA4YTk2NTNjNDYxMTg0ODAxYjBjODA5OGE3M2VkMDdkNmNlNyJ9fX0="
    const val PLUS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDdhMGZjNmRjZjczOWMxMWZlY2U0M2NkZDE4NGRlYTc5MWNmNzU3YmY3YmQ5MTUzNmZkYmM5NmZhNDdhY2ZiIn19fQ=="
    const val EQUALS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzAzMDgyZjAzM2Y5NzI0Y2IyMmZlMjdkMGRlNDk3NTA5MDMzNTY0MWVlZTVkOGQ5MjdhZGY1YThiNjdmIn19fQ=="
}

/** 头颅按钮。材质查找结果会被缓存，菜单每次渲染都调用也不会有额外开销。 */
internal fun head(texture: String, name: String, lore: List<String> = emptyList()): ItemStack =
    ItemStackBuilder(headBase(texture))
        .setDisplayName(name)
        .addLoreLines(lore.formatEco())
        .build()

private val headCache = ConcurrentHashMap<String, ItemStack>()

private fun headBase(texture: String): ItemStack =
    headCache.getOrPut(texture) { Items.lookup("player_head texture:$texture").item }.clone()

/** 带皮肤的头；没给材质就退回一颗普通头颅 */
internal fun skullOf(texture: String?): ItemStack =
    if (texture.isNullOrEmpty()) ItemStack(Material.PLAYER_HEAD) else headBase(texture)

/** 界面的黑色玻璃底板 */
internal val FILLER: ItemStack = ItemStackBuilder(Material.BLACK_STAINED_GLASS_PANE)
    .setDisplayName("&7")
    .build()

internal val FILLER_SLOT: Slot = slot(FILLER)

/**
 * 列表区没内容时用的空格子。
 *
 * 不能拿玻璃底板顶上 —— Aiyatsbus 的界面里中间那块列表区是留白的，
 * 铺满玻璃会把「中间空、四周玻璃」的观感整个改掉。
 */
internal val EMPTY_SLOT: Slot = slot(ItemStack(Material.AIR))

internal fun closeSlot(): Slot = slot(head(Heads.CLOSE, "&c关闭")) {
    onLeftClick { event, _ ->
        afterInventoryClick(event) { it.closeInventory() }
    }
}

/** Finish the cancelled transaction before closing or replacing its inventory. */
internal fun afterInventoryClick(event: InventoryClickEvent, action: (Player) -> Unit) {
    event.isCancelled = true
    val player = event.whoClicked as? Player ?: return
    val inventory = event.view.topInventory

    EcoEnchantsScheduler.runAtPlayer(player) {
        // Several clicks can queue in one tick. Only act on the original view.
        if (player.isOnline && player.openInventory.topInventory == inventory) {
            action(player)
        }
    }
}

/**
 * 铺满底板。
 *
 * 必须走 setMask —— 它落在 BACKGROUND 层，而 setSlot / addComponent 都在 MIDDLE 层。
 * 早先用 setSlot 逐格铺底板，结果底板和按钮同层打架，两侧的筛选按钮和翻页按钮全被盖住了。
 * 用 mask 之后上层组件天然盖过底板，也就不需要再挖空位置。
 */
internal fun MenuBuilder.fillBackground(rows: Int) {
    setMask(
        FillerMask(Material.BLACK_STAINED_GLASS_PANE, *Array(rows) { "111111111" })
    )
}

/** 上一页 / 下一页。翻到头时 eco 会自动把按钮变为不可用状态。 */
internal fun MenuBuilder.addPager(row: Int, previousColumn: Int, nextColumn: Int) {
    addComponent(
        row, previousColumn,
        PageChanger(head(Heads.PREVIOUS, "&e上一页"), PageChanger.Direction.BACKWARDS)
    )

    addComponent(
        row, nextColumn,
        PageChanger(head(Heads.NEXT, "&e下一页"), PageChanger.Direction.FORWARDS)
    )
}

internal fun backSlot(name: String, open: (Player) -> Unit): Slot =
    slot(head(Heads.BACK, "&b返回 $name")) {
        onLeftClick { event, _ -> afterInventoryClick(event, open) }
    }

// ---------------------------------------------------------------- 附魔条目

/**
 * 列表里的单个附魔。四个界面（附魔大全 / 收藏夹 / 物品查询 / 详情页关联列表）共用。
 *
 * 按附魔与等级缓存 —— 这些物品的内容只取决于这两者，而列表每次翻页都要重建一屏。
 */
internal fun enchantEntry(enchant: EcoEnchantLike, level: Int, player: Player): ItemStack =
    entryCache.getOrPut(enchant.enchantment.key.key to level) {
        // 照搬 aiy：条目是「按品质着色的玩家头颅」，而不是附魔书图标
        // （aiy EnchantSearchUI 用 PLAYER_HEAD + enchant.rarity.skull）
        ItemStackBuilder(skullOf(enchant.enchantmentRarity.skull))
            .setDisplayName(enchant.getFormattedName(level))
            .addLoreLines(
                listOf(
                    "&8| &7附魔 > ${enchant.getFormattedName(0)}",
                    "&8| &7等级 > &e$level &7/ &c${enchant.maximumLevel}",
                    "&8| &7品质 > ${enchant.enchantmentRarity.formattedName}"
                ).formatEco()
            )
            .addLoreLines(
                enchant.getFormattedDescription(level, player)
                    .flatMap { it.lineWrap(32, true) }
                    .mapIndexed { index, line -> if (index == 0) "&8| &7简介 > $line" else "&8|      $line" }
            )
            .addLoreLines(listOf("&7", "&8| &7点击查看详细信息").formatEco())
            .build()
    }.clone()

private val entryCache = ConcurrentHashMap<Pair<String, Int>, ItemStack>()

/** 品质名带上它自己的颜色，没配 format 时退回附魔类型的颜色 */
internal val com.willfp.ecoenchants.rarity.EnchantmentRarity.formattedName: String
    get() = "${this.format ?: ""}${this.displayName}"

/**
 * 附魔大全 / 筛选器的统一数据源：自定义附魔 + 原版附魔。
 *
 * 照搬 aiy 需要一并展示原版附魔（否则「定位/组」里那几组原版附魔全是空的），
 * 所以这里把两者都包成 [EcoEnchantLike]。原版只取 vanillaenchants.yml 里有登记的，
 * 免得把服务器上其它插件注册的附魔也算进来。
 */
internal fun allEnchantLikes(): List<EcoEnchantLike> {
    val custom = EcoEnchants.values().filter { it.isEnabled }
    val vanilla = Enchantment.values()
        .filter { it !is EcoEnchant && EcoEnchants.getByID(it.key.key) == null }
        .filter { plugin.vanillaEnchantsYml.getSubsectionOrNull(it.key.key) != null }
        .map { it.wrap() }
    return custom.toList() + vanilla
}

/**
 * 该附魔适用的物品类别 id。自定义附魔直接读配置的 targets；原版附魔没有配置目标，
 * 就拿每个类别的样品物品试 [Enchantment.canEnchantItem] 反推。
 */
internal fun EcoEnchantLike.targetIds(): List<String> =
    if (this is EcoEnchant) {
        this.targets.map { it.id }
    } else {
        EnchantmentTargets.values().filter { target ->
            target.id != "all" && target.items.any { item ->
                runCatching { enchantment.canEnchantItem(item.item) }.getOrDefault(false)
            }
        }.map { it.id }
    }

internal fun clearGuiCaches() {
    entryCache.clear()
    headCache.clear()
}

// ---------------------------------------------------------------- 列表缓存

/**
 * 一次渲染只算一遍列表。
 *
 * 菜单的每个格子都会各调一次 `getSlotAt`，翻页按钮又要问一次总页数；
 * 如果每处都现算，一屏 20 格就是 21 次全量遍历。统一在 `onRender` 里算好放这里。
 */
internal class ResultCache<T> {
    private val map = ConcurrentHashMap<UUID, List<T>>()

    operator fun get(player: Player): List<T> = map[player.uniqueId] ?: emptyList()

    operator fun set(player: Player, value: List<T>) {
        map[player.uniqueId] = value
    }

    fun forget(player: UUID) {
        map.remove(player)
    }

    internal companion object {
        private val all = mutableListOf<ResultCache<*>>()

        fun track(cache: ResultCache<*>): ResultCache<*> {
            all.add(cache)
            return cache
        }

        fun forgetAll(player: UUID) {
            all.forEach { it.forget(player) }
        }
    }
}

internal fun <T> resultCache(): ResultCache<T> {
    @Suppress("UNCHECKED_CAST")
    return ResultCache.track(ResultCache<T>()) as ResultCache<T>
}

// ---------------------------------------------------------------- 筛选状态

/**
 * 附魔大全的筛选条件。
 *
 * 存在内存里而不是菜单状态里，因为筛选界面是独立的菜单，
 * 玩家在那边勾选完再回到列表，跨菜单要能读到同一份。
 */
internal object SearchFilters {
    /**
     * 一组三态选择：选中要看的、划掉不看的、其余不表态。
     *
     * Aiyatsbus 的筛选器就是这么用的 —— 左击「只看」、右击「不看」、Shift 清除，
     * 只做二态的话「排除某几个品质」这种用法就没法表达。
     */
    class Selection {
        private val states = LinkedHashMap<String, Boolean>()

        /** true = 只看，false = 不看，null = 未表态 */
        operator fun get(id: String): Boolean? = states[id]

        // 左键直接设成「只看」、右键直接设成「不看」，不做来回切换 ——
        // Aiyatsbus 那边就是 clearFilter 之后 addFilter，点几次结果都一样
        fun include(id: String) {
            states[id] = true
        }

        fun exclude(id: String) {
            states[id] = false
        }

        /** Shift 点击清掉的是这一项 */
        fun remove(id: String) {
            states.remove(id)
        }

        /** 重置按钮清掉的是整组 */
        fun clear() = states.clear()

        val included: Set<String>
            get() = states.filterValues { it }.keys

        val excluded: Set<String>
            get() = states.filterValues { !it }.keys

        val isEmpty: Boolean
            get() = states.isEmpty()

        /** 某个附魔的取值集合能否通过本组筛选 */
        fun accepts(values: Collection<String>): Boolean {
            if (excluded.any { it in values }) {
                return false
            }

            return included.isEmpty() || included.any { it in values }
        }

        /**
         * 供按钮上的摘要文字使用。[nameOf] 把内部 id 映射成中文显示名
         * （不传就直接显示 id）——否则按钮 lore 里会是 chestplate/helmet 这种英文。
         */
        fun summary(nameOf: (String) -> String = { it }): String {
            if (isEmpty) {
                return "&7（未筛选）"
            }

            return buildList {
                included.forEach { add("&a✓${nameOf(it)}") }
                excluded.forEach { add("&c✗${nameOf(it)}") }
            }.joinToString("&7, ")
        }
    }

    class Filters {
        val rarities = Selection()
        val targets = Selection()
        val groups = Selection()
        var include: String? = null
        var exclude: String? = null
    }

    private val states = ConcurrentHashMap<UUID, Filters>()

    operator fun get(player: Player): Filters = states.getOrPut(player.uniqueId) { Filters() }

    fun forget(player: UUID) {
        states.remove(player)
    }
}

// ---------------------------------------------------------------- 收藏夹

/**
 * 收藏键：自定义附魔保留原有 id，原版附魔使用完整命名空间键以避免同名冲突。
 */
internal fun EcoEnchantLike.favoriteId(): String =
    if (this is EcoEnchant) this.id else this.enchantment.key.toString()

/** 收藏的附魔 id，随玩家档案持久化 */
internal object Favorites {
    private val key = PersistentDataKey(
        NamespacedKey(plugin, "favorite_enchants"),
        PersistentDataKeyType.STRING_LIST,
        emptyList<String>()
    )

    /** eco 要求持久化键在插件加载阶段注册，这里给主类一个显式的触发点 */
    fun register() {
        // 访问 key 即完成注册（PersistentDataKey 的构造函数会自行登记）
        key.key
    }

    fun list(player: Player): List<String> = player.profile.read(key)

    fun has(player: Player, id: String): Boolean = id in list(player)

    /** @return 操作后是否处于已收藏状态 */
    fun toggle(player: Player, id: String): Boolean {
        val current = list(player).toMutableList()
        val added = if (id in current) {
            current.remove(id)
            false
        } else {
            current.add(id)
            true
        }

        player.profile.write(key, current)
        return added
    }
}
