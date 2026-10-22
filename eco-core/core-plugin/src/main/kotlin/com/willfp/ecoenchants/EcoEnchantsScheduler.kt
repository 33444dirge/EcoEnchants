package com.willfp.ecoenchants

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

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

    /**
     * 在实体自己的区域线程上执行。
     *
     * 发布版 eco 的 Scheduler 还没有 runAtEntity（源码里有，制品里没有），
     * 需要按实体调度的地方走这里。
     */
    fun runAtEntity(entity: Entity, task: () -> Unit) {
        if (isFolia) {
            entity.scheduler.run(plugin, { task() }, null)
        } else {
            Bukkit.getScheduler().runTask(plugin, Runnable { task() })
        }
    }

    fun runAtPlayer(player: Player, task: () -> Unit) {
        runAtPlayerLater(player, 1, task)
    }

    fun runAtPlayerLater(player: Player, delay: Long, task: () -> Unit) {
        val ticksDelay = maxOf(1L, delay)
        if (isFolia) {
            player.scheduler.runDelayed(plugin, { task() }, null, ticksDelay)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable { task() }, ticksDelay)
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
