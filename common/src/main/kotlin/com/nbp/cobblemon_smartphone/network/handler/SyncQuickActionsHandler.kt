package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.api.QuickActionBindings
import com.nbp.cobblemon_smartphone.network.packet.SyncQuickActionsPacket
import net.minecraft.client.Minecraft

object SyncQuickActionsHandler : ClientNetworkPacketHandler<SyncQuickActionsPacket> {
    override fun handle(packet: SyncQuickActionsPacket, client: Minecraft) {
        QuickActionBindings.setBindings(packet.bindings)
    }
}
