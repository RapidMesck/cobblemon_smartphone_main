package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.StructureCompassClientState
import com.nbp.cobblemon_smartphone.network.packet.StructureListResponsePacket
import net.minecraft.client.Minecraft

object StructureListResponseHandler : ClientNetworkPacketHandler<StructureListResponsePacket> {
    override fun handle(packet: StructureListResponsePacket, client: Minecraft) {
        StructureCompassClientState.accept(packet)
    }
}
