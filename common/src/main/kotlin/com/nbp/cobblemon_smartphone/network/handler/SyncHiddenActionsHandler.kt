package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.api.SmartphoneHiddenActions
import com.nbp.cobblemon_smartphone.network.packet.SyncHiddenActionsPacket
import net.minecraft.client.Minecraft

object SyncHiddenActionsHandler : ClientNetworkPacketHandler<SyncHiddenActionsPacket> {
    override fun handle(packet: SyncHiddenActionsPacket, client: Minecraft) {
        SmartphoneHiddenActions.setHidden(packet.hidden)
    }
}
