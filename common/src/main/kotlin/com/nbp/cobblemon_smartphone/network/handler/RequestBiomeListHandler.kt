package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.PokeInfoRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.BiomeListResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.RequestBiomeListPacket
import com.nbp.cobblemon_smartphone.util.GpsDataProvider
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestBiomeListHandler : ServerNetworkPacketHandler<RequestBiomeListPacket> {
    private val limiter = PokeInfoRequestLimiter(500L)

    override fun handle(packet: RequestBiomeListPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableGps) return@execute

            if (!limiter.tryAcquire(player.uuid)) {
                BiomeListResponsePacket(packet.requestId, BiomeListResponsePacket.Status.RATE_LIMITED, emptyList())
                    .sendToPlayer(player)
                return@execute
            }
            val response = runCatching { GpsDataProvider.all(server) }
            BiomeListResponsePacket(
                packet.requestId,
                if (response.isSuccess) BiomeListResponsePacket.Status.SUCCESS else BiomeListResponsePacket.Status.ERROR,
                response.getOrDefault(emptyList())
            ).sendToPlayer(player)
        }
    }
}
