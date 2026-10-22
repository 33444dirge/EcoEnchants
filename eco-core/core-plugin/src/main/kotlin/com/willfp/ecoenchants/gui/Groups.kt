package com.willfp.ecoenchants.gui

import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchantLike
import com.willfp.ecoenchants.enchant.EcoEnchants

/**
 * 附魔「定位/组」——照搬 Aiyatsbus 的 enchants/group.yml（nereus 未覆盖，用的就是默认那 7 组）。
 *
 * aiy 的组基本围绕原版附魔和品质划分，所以第三个筛选要有内容，附魔大全必须一并列出原版附魔
 * （见 [allEnchantLikes]）。这 7 组在 nereus 里从不改动，直接写死即可，省掉一整套配置/重载管线。
 *
 * 归属判定（[Group.matches]）与 aiy 的 Group 一致：命中显式 enchants 列表、或命中某个 rarities。
 * 额外加一个 [Group.vanilla] 标记表示「所有原版附魔」，免得把 44 个 key 硬抄进来还随版本漂移。
 */
internal object Groups {

    /** aiy Group 的默认头颅材质（group.yml 没有单独配 skull，7 组共用这一颗） */
    private const val DEFAULT_SKULL =
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzRiODlhZDA2ZDMxOGYwYWUxZWVhZjY2MGZlYTc4YzM0ZWI1NWQwNWYwMWUxY2Y5OTlmMzMxZmIzMmQzODk0MiJ9fX0="

    internal class Group(
        val id: String,
        val displayName: String,
        val enchants: Set<String> = emptySet(),
        val rarities: Set<String> = emptySet(),
        /** true = 匹配所有原版（非自定义）附魔 */
        val vanilla: Boolean = false,
        val skull: String = DEFAULT_SKULL
    ) {
        fun matches(enchant: EcoEnchantLike): Boolean {
            val key = enchant.enchantment.key.key
            if (vanilla && enchant !is EcoEnchant) return true
            if (key in enchants) return true
            return enchant.enchantmentRarity.id in rarities
        }
    }

    val all: List<Group> = listOf(
        Group(
            "vanilla_damage", "原版增伤类附魔",
            enchants = setOf("sharpness", "density", "impaling", "breach", "smite", "bane_of_arthropods")
        ),
        Group(
            "vanilla_defense", "原版防御类附魔",
            enchants = setOf("protection", "fire_protection", "blast_protection", "projectile_protection")
        ),
        Group(
            "vanilla_movement", "原版移动机制类附魔",
            enchants = setOf("depth_strider", "soul_speed", "frost_walker", "swift_sneak")
        ),
        Group("vanilla", "原版附魔", vanilla = true),
        Group("curse", "诅咒类附魔", rarities = setOf("curse")),
        // aiy 里「不可磨砂」与「诅咒」成员相同（都是 rarities:[诅咒]），保留以照搬其组数
        Group("no_grindstone", "不可磨砂类附魔", rarities = setOf("curse")),
        Group(
            "tradeable", "可交易附魔",
            enchants = setOf(
                "aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection", "channeling",
                "depth_strider", "efficiency", "feather_falling", "fire_aspect", "fire_protection", "flame",
                "fortune", "frost_walker", "impaling", "infinity", "knockback", "looting", "loyalty",
                "luck_of_the_sea", "lure", "mending", "multishot", "piercing", "power", "projectile_protection",
                "protection", "punch", "quick_charge", "respiration", "riptide", "sharpness", "silk_touch",
                "smite", "sweeping", "sweeping_edge", "thorns", "unbreaking", "vanishing_curse", "density", "breach"
            ),
            rarities = setOf("common", "uncommon", "rare")
        )
    )

    /** 某个附魔属于哪些组（供三态筛选的 accepts 使用） */
    fun idsContaining(enchant: EcoEnchantLike): List<String> =
        all.filter { it.matches(enchant) }.map { it.id }

    private val byId = all.associateBy { it.id }
    operator fun get(id: String): Group? = byId[id]
}
