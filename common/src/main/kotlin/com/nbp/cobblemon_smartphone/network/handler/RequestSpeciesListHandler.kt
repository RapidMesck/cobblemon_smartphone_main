package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.PokeInfoRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.RequestSpeciesListPacket
import com.nbp.cobblemon_smartphone.network.packet.SpeciesListResponsePacket
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestSpeciesListHandler : ServerNetworkPacketHandler<RequestSpeciesListPacket> {
    private val limiter = PokeInfoRequestLimiter(500L)

    override fun handle(packet: RequestSpeciesListPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!limiter.tryAcquire(player.uuid)) {
                SpeciesListResponsePacket(packet.requestId, SpeciesListResponsePacket.Status.RATE_LIMITED, emptyList())
                    .sendToPlayer(player)
                return@execute
            }
            val response = runCatching { PokeInfoDataProvider.all() }
            SpeciesListResponsePacket(
                packet.requestId,
                if (response.isSuccess) SpeciesListResponsePacket.Status.SUCCESS else SpeciesListResponsePacket.Status.ERROR,
                response.getOrDefault(emptyList())
            ).sendToPlayer(player)
        }
    }
}
