package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.render.RotomClientManager
import com.nbp.cobblemon_smartphone.network.packet.SetSmartphoneOpenStatePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncSmartphoneOpenStatePacket
import com.nbp.cobblemon_smartphone.server.RotomServerManager
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SetSmartphoneOpenStateHandler : ServerNetworkPacketHandler<SetSmartphoneOpenStatePacket> {
    override fun handle(packet: SetSmartphoneOpenStatePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            RotomServerManager.onPlayerSetOpen(player, packet.isOpen)
        }
    }
}

object SyncSmartphoneOpenStateHandler : ClientNetworkPacketHandler<SyncSmartphoneOpenStatePacket> {
    override fun handle(packet: SyncSmartphoneOpenStatePacket, client: Minecraft) {
        client.execute {
            RotomClientManager.setRemotePlayerOpen(packet.playerUuid, packet.isOpen)
        }
    }
}
