package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.SaveQuickActionsPacket
import com.nbp.cobblemon_smartphone.util.QuickActionBindingsStorage
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SaveQuickActionsHandler : ServerNetworkPacketHandler<SaveQuickActionsPacket> {
    override fun handle(packet: SaveQuickActionsPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            QuickActionBindingsStorage.write(player, packet.bindings)
        }
    }
}
