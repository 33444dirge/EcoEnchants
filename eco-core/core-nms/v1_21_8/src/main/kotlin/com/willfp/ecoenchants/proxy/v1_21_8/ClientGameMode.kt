package com.willfp.ecoenchants.proxy.v1_21_8

import com.willfp.ecoenchants.mechanics.ClientGameModeProxy
import net.minecraft.network.protocol.game.ClientboundGameEventPacket
import org.bukkit.GameMode
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player

class ClientGameMode : ClientGameModeProxy {
    @Suppress("DEPRECATION")
    override fun sendClientGameMode(player: Player, gameMode: GameMode) {
        val packet = ClientboundGameEventPacket(
            ClientboundGameEventPacket.CHANGE_GAME_MODE,
            gameMode.value.toFloat()
        )
        (player as CraftPlayer).handle.connection.send(packet)
    }
}
