package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.SyncDatapackScreensPacket
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenClientState
import net.minecraft.client.Minecraft

object SyncDatapackScreensHandler : ClientNetworkPacketHandler<SyncDatapackScreensPacket> {
    override fun handle(packet: SyncDatapackScreensPacket, client: Minecraft) {
        DatapackScreenClientState.acceptScreens(packet.screens)
    }
}
