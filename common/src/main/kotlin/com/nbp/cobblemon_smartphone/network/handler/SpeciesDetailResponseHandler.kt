package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.SpeciesDetailResponsePacket
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import net.minecraft.client.Minecraft

object SpeciesDetailResponseHandler : ClientNetworkPacketHandler<SpeciesDetailResponsePacket> {
    override fun handle(packet: SpeciesDetailResponsePacket, client: Minecraft) {
        PokeInfoDataProvider.pendingDetail = packet.detail
    }
}
