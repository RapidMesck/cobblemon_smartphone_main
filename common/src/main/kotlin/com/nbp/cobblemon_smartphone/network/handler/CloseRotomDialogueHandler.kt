package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.CloseRotomDialoguePacket
import com.nbp.cobblemon_smartphone.server.RotomAutoScannerManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object CloseRotomDialogueHandler : ServerNetworkPacketHandler<CloseRotomDialoguePacket> {
    override fun handle(packet: CloseRotomDialoguePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            RotomAutoScannerManager.onDialogueClosed(player)
        }
    }
}
