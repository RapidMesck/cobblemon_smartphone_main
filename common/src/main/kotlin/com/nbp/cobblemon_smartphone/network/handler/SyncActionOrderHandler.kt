package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.api.SmartphoneActionOrder
import com.nbp.cobblemon_smartphone.network.packet.SyncActionOrderPacket
import net.minecraft.client.Minecraft

object SyncActionOrderHandler : ClientNetworkPacketHandler<SyncActionOrderPacket> {
    override fun handle(packet: SyncActionOrderPacket, client: Minecraft) {
        SmartphoneActionOrder.setOrder(packet.order)
    }
}
