package com.willfp.ecoenchants.target

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.eco.core.registry.Registrable
import com.willfp.ecoenchants.plugin
import com.willfp.libreforge.slot.SlotType
import com.willfp.libreforge.slot.SlotTypes
import com.willfp.libreforge.slot.impl.SlotTypeAny
import org.bukkit.inventory.ItemStack
import java.util.Objects

interface EnchantmentTarget : Registrable {
    val id: String
    val displayName: String
    val slot: SlotType
    val items: List<TestableItem>

    /**
     * 该目标类型允许的最大附魔数量，-1 表示不限制。
     *
     * 对应 aiyatsbus 中每个 target 的 max（剑 12、镐 7、刷子 3 …）。
     * 一件物品同时命中多个目标时取最严格的那个，见 [EnchantmentTargets.getMaxEnchants]。
     */
    val maxEnchants: Int
        get() = -1

    /** 筛选界面里代表该类别的头颅材质（base64），取自 aiyatsbus 的 target.yml */
    val skull: String?
        get() = null

    fun matches(itemStack: ItemStack): Boolean {
        for (item in items) {
            if (item.matches(itemStack)) {
                return true
            }
        }
        return false
    }

    override fun getID(): String {
        return this.id
    }
}

class ConfiguredEnchantmentTarget(
    config: Config
) : EnchantmentTarget {
    override val id = config.getString("id")
    override val displayName = config.getFormattedString("display-name")

    override val skull: String? = config.getStringOrNull("skull")?.takeIf { it.isNotEmpty() }

    override val slot = SlotTypes[config.getString("slot")] ?: throw IllegalArgumentException(
        "Invalid slot type: ${config.getString("slot")}, options are ${
            SlotTypes.values().map { it.id }
        }"
    )

    override val items = config.getStrings("items")
        .map { Items.lookup(it) }
        .filterNot { it is EmptyTestableItem }

    // 用 getIntOrNull：getInt 在键缺失时返回 0，会把该类物品的附魔全部禁掉
    override val maxEnchants = config.getIntOrNull("max-enchants") ?: -1

    override fun equals(other: Any?): Boolean {
        if (other !is EnchantmentTarget) {
            return false
        }

        return this.id == other.id
    }

    override fun hashCode(): Int {
        return Objects.hash(this.id)
    }
}

internal object AllEnchantmentTarget : EnchantmentTarget {
    override val id = "all"
    override val displayName = plugin.langYml.getFormattedString("all")
    override val slot = SlotTypeAny
    override var items = emptyList<TestableItem>()
        private set

    fun updateItems() {
        items = EnchantmentTargets.values()
            .filterNot { it == this }
            .flatMap { it.items }
    }

    override fun equals(other: Any?): Boolean {
        return other is AllEnchantmentTarget
    }
}
