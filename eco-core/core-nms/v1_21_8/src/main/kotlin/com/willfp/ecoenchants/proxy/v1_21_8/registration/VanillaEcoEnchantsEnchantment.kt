package com.willfp.ecoenchants.proxy.v1_21_8.registration

import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.display.getFormattedName
import net.minecraft.core.HolderSet
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.enchantment.Enchantment
import org.bukkit.craftbukkit.util.CraftChatMessage

fun vanillaEcoEnchantsEnchantment(enchant: EcoEnchant): Enchantment {
    val enchantment = Enchantment.enchantment(
        Enchantment.definition(
            HolderSet.empty(),
            1, // 1.21 hardcodes a minimum weight of 1
            enchant.maximumLevel,
            Enchantment.constantCost(1),
            Enchantment.constantCost(1),
            0
        )
    )

    val built = enchantment.build(ResourceLocation.withDefaultNamespace(enchant.id))

    // Creative tabs render the NMS description component, not Bukkit's displayName.
    return Enchantment(
        CraftChatMessage.fromStringOrNull(enchant.getFormattedName(0), true),
        built.definition(),
        built.exclusiveSet(),
        built.effects()
    )
}
