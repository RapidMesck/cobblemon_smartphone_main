package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.GpsClientState
import com.nbp.cobblemon_smartphone.network.packet.GpsSearchResultPacket
import net.minecraft.client.Minecraft

object GpsSearchResultHandler : ClientNetworkPacketHandler<GpsSearchResultPacket> {
    override fun handle(packet: GpsSearchResultPacket, client: Minecraft) {
        GpsClientState.acceptSearchResult(packet)
    }
}
