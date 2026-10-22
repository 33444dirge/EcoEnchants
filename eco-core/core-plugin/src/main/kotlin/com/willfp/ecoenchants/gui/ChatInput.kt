package com.willfp.ecoenchants.gui

import com.willfp.eco.util.formatEco
import com.willfp.ecoenchants.EcoEnchantsScheduler
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 让玩家在聊天栏填一个值，然后继续之前的操作。
 *
 * 附魔大全的「按名字/描述筛选」需要自由文本，而 eco 的菜单没有输入框，
 * 所以走聊天输入这条最省事的路，不额外引入依赖。
 */
object ChatInput : Listener {
    private val waiting = ConcurrentHashMap<UUID, (String) -> Unit>()

    /** 关掉菜单并等待玩家的下一句话 */
    fun await(player: Player, prompt: String, callback: (String) -> Unit) {
        player.closeInventory()
        player.sendMessage(prompt.formatEco())
        waiting[player.uniqueId] = callback
    }

    // 用的是老的 Bukkit 聊天事件而不是 Paper 的 AsyncChatEvent：
    // 后者给的是 Component，而把它转成纯文本要 adventure 的 plain 序列化器，
    // 那个 artifact 不在 paper-api 的依赖里。
    @Suppress("DEPRECATION")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        val callback = waiting.remove(event.player.uniqueId) ?: return

        event.isCancelled = true

        val message = event.message.trim()
        val player = event.player

        // 聊天事件在异步线程上，回调里要开菜单、读玩家数据，必须回到玩家自己的线程
        EcoEnchantsScheduler.runAtEntity(player) {
            if (message.equals("cancel", ignoreCase = true) || message.isEmpty()) {
                player.sendMessage("&c已取消".formatEco())
                return@runAtEntity
            }

            callback(message)
        }
    }

    /** 顺带清掉这名玩家在各菜单里留下的筛选条件与列表缓存 */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId

        waiting.remove(uuid)
        SearchFilters.forget(uuid)
        ResultCache.forgetAll(uuid)
        EnchantInfoGUI.forget(uuid)
    }
}
