package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.SyncDatapackScreenDataPacket
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenClientState
import net.minecraft.client.Minecraft

object SyncDatapackScreenDataHandler : ClientNetworkPacketHandler<SyncDatapackScreenDataPacket> {
    override fun handle(packet: SyncDatapackScreenDataPacket, client: Minecraft) {
        DatapackScreenClientState.acceptData(packet.screenId, packet.placeholders, packet.lists)
    }
}
