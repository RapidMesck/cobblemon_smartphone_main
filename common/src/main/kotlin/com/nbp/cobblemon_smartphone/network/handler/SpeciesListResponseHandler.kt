package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.PokeInfoClientState
import com.nbp.cobblemon_smartphone.network.packet.SpeciesListResponsePacket
import net.minecraft.client.Minecraft

object SpeciesListResponseHandler : ClientNetworkPacketHandler<SpeciesListResponsePacket> {
    override fun handle(packet: SpeciesListResponsePacket, client: Minecraft) {
        PokeInfoClientState.accept(packet)
    }
}
