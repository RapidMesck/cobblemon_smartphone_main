package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.SaveHiddenActionsPacket
import com.nbp.cobblemon_smartphone.util.HiddenActionsStorage
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SaveHiddenActionsHandler : ServerNetworkPacketHandler<SaveHiddenActionsPacket> {
    override fun handle(packet: SaveHiddenActionsPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            HiddenActionsStorage.write(player, packet.hidden)
        }
    }
}
