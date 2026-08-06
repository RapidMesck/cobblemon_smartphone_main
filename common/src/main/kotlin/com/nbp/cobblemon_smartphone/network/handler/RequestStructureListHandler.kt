package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.PokeInfoRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.RequestStructureListPacket
import com.nbp.cobblemon_smartphone.network.packet.StructureListResponsePacket
import com.nbp.cobblemon_smartphone.util.StructureDataProvider
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestStructureListHandler : ServerNetworkPacketHandler<RequestStructureListPacket> {
    private val limiter = PokeInfoRequestLimiter(500L)

    override fun handle(packet: RequestStructureListPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableStructureCompass) return@execute

            if (!limiter.tryAcquire(player.uuid)) {
                StructureListResponsePacket(packet.requestId, StructureListResponsePacket.Status.RATE_LIMITED, emptyList())
                    .sendToPlayer(player)
                return@execute
            }
            val response = runCatching { StructureDataProvider.all(server) }
            StructureListResponsePacket(
                packet.requestId,
                if (response.isSuccess) StructureListResponsePacket.Status.SUCCESS else StructureListResponsePacket.Status.ERROR,
                response.getOrDefault(emptyList())
            ).sendToPlayer(player)
        }
    }
}
