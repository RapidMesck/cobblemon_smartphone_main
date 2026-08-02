package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.compat.rct.RctTrainerCardBridge
import com.nbp.cobblemon_smartphone.network.packet.OpenRctTrainerCardScreenPacket
import net.minecraft.client.Minecraft

object OpenRctTrainerCardScreenHandler : ClientNetworkPacketHandler<OpenRctTrainerCardScreenPacket> {
    override fun handle(packet: OpenRctTrainerCardScreenPacket, client: Minecraft) {
        RctTrainerCardBridge.openTrainerCardScreen()
    }
}
