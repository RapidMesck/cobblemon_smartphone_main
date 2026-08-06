package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.RequestSpeciesDetailPacket
import com.nbp.cobblemon_smartphone.network.packet.SpeciesDetailResponsePacket
import com.nbp.cobblemon_smartphone.network.PokeInfoRequestLimiter
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestSpeciesDetailHandler : ServerNetworkPacketHandler<RequestSpeciesDetailPacket> {
    private val limiter = PokeInfoRequestLimiter(150L)

    override fun handle(packet: RequestSpeciesDetailPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enablePokeInfo) return@execute

            if (!limiter.tryAcquire(player.uuid)) {
                SpeciesDetailResponsePacket(
                    packet.requestId, packet.dexNumber,
                    SpeciesDetailResponsePacket.Status.RATE_LIMITED, emptyList()
                ).sendToPlayer(player)
                return@execute
            }

            try {
                val details = PokeInfoDataProvider.getAllDetails(packet.dexNumber)
                val status = if (details.isEmpty()) SpeciesDetailResponsePacket.Status.NOT_FOUND
                else SpeciesDetailResponsePacket.Status.SUCCESS
                SpeciesDetailResponsePacket(
                    packet.requestId, packet.dexNumber, status, details
                ).sendToPlayer(player)
            } catch (_: Exception) {
                SpeciesDetailResponsePacket(
                    packet.requestId, packet.dexNumber,
                    SpeciesDetailResponsePacket.Status.ERROR, emptyList()
                ).sendToPlayer(player)
            }
        }
    }
}
