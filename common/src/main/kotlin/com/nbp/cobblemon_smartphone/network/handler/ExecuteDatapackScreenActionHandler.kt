package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.ExecuteDatapackScreenActionPacket
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenExecutor
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object ExecuteDatapackScreenActionHandler : ServerNetworkPacketHandler<ExecuteDatapackScreenActionPacket> {
    override fun handle(
        packet: ExecuteDatapackScreenActionPacket,
        server: MinecraftServer,
        player: ServerPlayer
    ) {
        server.execute {
            DatapackScreenExecutor.executeAction(
                server = server,
                player = player,
                screenId = packet.screenId,
                actionId = packet.actionId,
                clientInputs = packet.inputs,
                itemContext = packet.itemContext
            )
        }
    }
}
