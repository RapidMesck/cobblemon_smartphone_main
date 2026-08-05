package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.GpsClientState
import com.nbp.cobblemon_smartphone.network.packet.GpsSearchProgressPacket
import net.minecraft.client.Minecraft

object GpsSearchProgressHandler : ClientNetworkPacketHandler<GpsSearchProgressPacket> {
    override fun handle(packet: GpsSearchProgressPacket, client: Minecraft) {
        GpsClientState.acceptSearchProgress(packet)
    }
}
