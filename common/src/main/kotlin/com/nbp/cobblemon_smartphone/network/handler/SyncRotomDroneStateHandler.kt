package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.RotomDroneClientManager
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomDroneStatePacket
import net.minecraft.client.Minecraft

object SyncRotomDroneStateHandler : ClientNetworkPacketHandler<SyncRotomDroneStatePacket> {
    override fun handle(packet: SyncRotomDroneStatePacket, client: Minecraft) {
        client.execute {
            RotomDroneClientManager.handleServerSync(packet)
        }
    }
}
