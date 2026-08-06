package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.gui.StructureCompassScreen
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.OpenStructureCompassScreenPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft

object OpenStructureCompassScreenHandler : ClientNetworkPacketHandler<OpenStructureCompassScreenPacket> {
    override fun handle(packet: OpenStructureCompassScreenPacket, client: Minecraft) {
        val color = SmartphoneHelper.contextColor ?: SmartphoneColor.BLACK
        Minecraft.getInstance().setScreen(StructureCompassScreen(color, SmartphoneHelper.contextSmartphone))
    }
}
