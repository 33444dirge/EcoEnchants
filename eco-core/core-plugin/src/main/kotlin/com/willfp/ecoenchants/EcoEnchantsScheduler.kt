package com.willfp.ecoenchants

import org.bukkit.Bukkit
import org.bukkit.Location

object EcoEnchantsScheduler {
    private val isFolia = try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        true
    } catch (_: ClassNotFoundException) {
        false
    }

    fun runAtLocation(location: Location, task: () -> Unit) {
        if (isFolia) {
            Bukkit.getRegionScheduler().execute(plugin, location) { task() }
        } else {
            Bukkit.getScheduler().runTask(plugin, Runnable { task() })
        }
    }

    fun runAtLocationLater(location: Location, delay: Long, task: () -> Unit) {
        val ticksDelay = maxOf(1L, delay)
        if (isFolia) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, { task() }, ticksDelay)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable { task() }, ticksDelay)
        }
    }
}
