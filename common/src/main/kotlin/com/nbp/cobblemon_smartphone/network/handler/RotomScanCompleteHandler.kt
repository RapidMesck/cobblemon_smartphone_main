package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.RotomScannerClientState
import com.nbp.cobblemon_smartphone.network.packet.RotomScanCompletePacket
import net.minecraft.client.Minecraft

object RotomScanCompleteHandler : ClientNetworkPacketHandler<RotomScanCompletePacket> {
    override fun handle(packet: RotomScanCompletePacket, client: Minecraft) {
        client.execute {
            RotomScannerClientState.onScanComplete(packet)
        }
    }
}
