package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.RequestSpeciesDetailPacket
import com.nbp.cobblemon_smartphone.network.packet.SpeciesDetailResponsePacket
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestSpeciesDetailHandler : ServerNetworkPacketHandler<RequestSpeciesDetailPacket> {
    override fun handle(packet: RequestSpeciesDetailPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            val detail = PokeInfoDataProvider.getDetail(packet.dexNumber, packet.formName)
            SpeciesDetailResponsePacket(detail).sendToPlayer(player)
        }
    }
}
