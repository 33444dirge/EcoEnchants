package com.willfp.ecoenchants.enchant.impl.hardcoded

import com.willfp.eco.util.DurabilityUtils
import com.willfp.ecoenchants.EcoEnchantsScheduler
import com.willfp.ecoenchants.enchant.impl.HardcodedEcoEnchant
import com.willfp.ecoenchants.target.EnchantFinder.getItemsWithEnchantActive
import com.willfp.ecoenchants.target.EnchantFinder.hasEnchantActive
import com.willfp.libreforge.slot.impl.SlotTypeArmor
import com.willfp.libreforge.slot.impl.SlotTypeHands
import org.bukkit.Bukkit

object EnchantmentRepairing : HardcodedEcoEnchant(
    "repairing"
) {
    override fun onRegister() {
        val frequency = config.getInt("frequency").toLong()

        plugin.scheduler.runTimer(frequency, frequency) {
            handleRepairing()
        }
    }

    private fun handleRepairing() {
        val notWhileHolding = config.getBool("not-while-holding")

        for (player in Bukkit.getOnlinePlayers()) {
            // 计时器跑在全局线程；Folia 下背包的读写必须回到该玩家所属的区域线程，
            // 否则玩家跨区时会与旧区域的处理并发改同一份物品栏（耐久覆盖写 / CME）
            EcoEnchantsScheduler.runAtEntity(player) {
                if (!player.hasEnchantActive(this)) {
                    return@runAtEntity
                }

                val repairPerLevel = config.getIntFromExpression("repair-per-level", player)

                for ((item, level) in player.getItemsWithEnchantActive(this)) {
                    if (notWhileHolding) {
                        val isHolding = item in SlotTypeHands.getItems(player)
                        val isEquipped = item in SlotTypeArmor.getItems(player)

                        if (isHolding || isEquipped) {
                            continue
                        }
                    }

                    DurabilityUtils.repairItem(item, level * repairPerLevel)
                }
            }
        }
    }
}
