package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.SaveActionOrderPacket
import com.nbp.cobblemon_smartphone.util.ActionOrderStorage
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SaveActionOrderHandler : ServerNetworkPacketHandler<SaveActionOrderPacket> {
    override fun handle(packet: SaveActionOrderPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            ActionOrderStorage.write(player, packet.order)
        }
    }
}
