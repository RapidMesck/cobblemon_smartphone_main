package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.RotomRadarClientState
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomRadarPacket
import net.minecraft.client.Minecraft

object SyncRotomRadarHandler : ClientNetworkPacketHandler<SyncRotomRadarPacket> {
    override fun handle(packet: SyncRotomRadarPacket, client: Minecraft) {
        client.execute {
            RotomRadarClientState.updateConfig(packet.config)
        }
    }
}
