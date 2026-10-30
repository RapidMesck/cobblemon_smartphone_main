package com.nbp.cobblemon_smartphone.server

import com.nbp.cobblemon_smartphone.network.packet.SyncSmartphoneOpenStatePacket
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RotomServerManager {
    private val openPlayers = ConcurrentHashMap.newKeySet<UUID>()

    fun onPlayerSetOpen(player: ServerPlayer, isOpen: Boolean) {
        if (isOpen) {
            openPlayers.add(player.uuid)
        } else {
            openPlayers.remove(player.uuid)
        }

        // Broadcast para todos os outros jogadores sincronizarem a animação
        val syncPacket = SyncSmartphoneOpenStatePacket(player.uuid, isOpen)
        for (other in player.server.playerList.players) {
            if (other.uuid != player.uuid) {
                syncPacket.sendToPlayer(other)
            }
        }
    }

    fun onPlayerJoin(player: ServerPlayer) {
        // Envia para o jogador que acabou de entrar quais jogadores estão com o smartphone aberto
        for (openUuid in openPlayers) {
            SyncSmartphoneOpenStatePacket(openUuid, true).sendToPlayer(player)
        }
    }

    fun onPlayerDisconnect(player: ServerPlayer) {
        if (openPlayers.remove(player.uuid)) {
            val syncPacket = SyncSmartphoneOpenStatePacket(player.uuid, false)
            for (other in player.server.playerList.players) {
                if (other.uuid != player.uuid) {
                    syncPacket.sendToPlayer(other)
                }
            }
        }
    }
}
