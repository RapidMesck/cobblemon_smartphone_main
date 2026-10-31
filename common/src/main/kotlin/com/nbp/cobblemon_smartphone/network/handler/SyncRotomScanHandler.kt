package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.RotomScannerClientState
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomScanPacket
import net.minecraft.client.Minecraft

object SyncRotomScanHandler : ClientNetworkPacketHandler<SyncRotomScanPacket> {
    override fun handle(packet: SyncRotomScanPacket, client: Minecraft) {
        client.execute {
            RotomScannerClientState.updateScanState(packet)
        }
    }
}
