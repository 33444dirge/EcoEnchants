package com.willfp.ecoenchants.rarity

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.registry.Registrable
import java.util.*

@Suppress("DEPRECATION")
class EnchantmentRarity(
    internal val config: Config
) : Registrable {
    val id = config.getString("id")
    val displayName = config.getFormattedString("display-name")

    /**
     * 该品质的显示格式（颜色）。留空则回退到附魔类型的 format。
     *
     * 原版按 type 着色，同类型附魔颜色一致；配置本项后改为按品质着色（aiyatsbus 风格）。
     */
    val format: String? = config.getStringOrNull("format")?.takeIf { it.isNotEmpty() }

    /** 筛选界面里代表该品质的头颅材质（base64），取自 aiyatsbus 的 rarity.yml */
    val skull: String? = config.getStringOrNull("skull")?.takeIf { it.isNotEmpty() }

    val tableChance = config.getDouble("table-chance")
    val minimumLevel = config.getInt("minimum-level")
    val villagerChance = config.getDouble("villager-chance")
    val lootChance = config.getDouble("loot-chance")

    /**
     * 加权抽选中该品质的权重（aiyatsbus 风格）。
     *
     * 选取流程改为「先按品质权重抽品质，再从该品质中按附魔权重抽附魔」，
     * 因此某品质下附魔数量的多寡不再影响它出现的概率。
     * 未配置时回退到 table-chance，以兼容旧配置。
     */
    val weight = config.getIntOrNull("weight") ?: tableChance.toInt()

    /** 该品质能否从对应来源获得。对应 aiyatsbus 的 accessibleFromTable / Villager / Loot。 */
    val fromTable = config.getBoolOrNull("from-table") ?: (tableChance > 0)
    val fromVillager = config.getBoolOrNull("from-villager") ?: (villagerChance > 0)
    val fromLoot = config.getBoolOrNull("from-loot") ?: (lootChance > 0)

    override fun getID(): String {
        return id
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is EnchantmentRarity) {
            return false
        }

        return other.id == this.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    override fun toString(): String {
        return "EnchantmentRarity{$id}"
    }
}
