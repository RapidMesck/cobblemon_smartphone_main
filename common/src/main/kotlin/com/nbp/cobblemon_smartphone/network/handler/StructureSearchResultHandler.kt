package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.StructureCompassClientState
import com.nbp.cobblemon_smartphone.network.packet.StructureSearchResultPacket
import net.minecraft.client.Minecraft

object StructureSearchResultHandler : ClientNetworkPacketHandler<StructureSearchResultPacket> {
    override fun handle(packet: StructureSearchResultPacket, client: Minecraft) {
        StructureCompassClientState.acceptSearchResult(packet)
    }
}
