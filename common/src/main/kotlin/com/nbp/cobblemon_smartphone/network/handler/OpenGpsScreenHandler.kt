package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.gui.GpsScreen
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.OpenGpsScreenPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft

object OpenGpsScreenHandler : ClientNetworkPacketHandler<OpenGpsScreenPacket> {
    override fun handle(packet: OpenGpsScreenPacket, client: Minecraft) {
        val color = SmartphoneHelper.contextColor ?: SmartphoneColor.BLACK
        Minecraft.getInstance().setScreen(GpsScreen(color, SmartphoneHelper.contextSmartphone))
    }
}
