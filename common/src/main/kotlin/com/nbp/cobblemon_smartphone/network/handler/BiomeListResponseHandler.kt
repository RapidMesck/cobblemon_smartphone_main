package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.GpsClientState
import com.nbp.cobblemon_smartphone.network.packet.BiomeListResponsePacket
import net.minecraft.client.Minecraft

object BiomeListResponseHandler : ClientNetworkPacketHandler<BiomeListResponsePacket> {
    override fun handle(packet: BiomeListResponsePacket, client: Minecraft) {
        GpsClientState.accept(packet)
    }
}
