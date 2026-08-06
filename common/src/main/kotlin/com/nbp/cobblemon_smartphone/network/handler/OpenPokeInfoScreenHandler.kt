package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.gui.PokeInfoScreen
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.OpenPokeInfoScreenPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft

object OpenPokeInfoScreenHandler : ClientNetworkPacketHandler<OpenPokeInfoScreenPacket> {
    override fun handle(packet: OpenPokeInfoScreenPacket, client: Minecraft) {
        val color = SmartphoneHelper.contextColor ?: SmartphoneColor.BLACK
        Minecraft.getInstance().setScreen(PokeInfoScreen(color, SmartphoneHelper.contextSmartphone))
    }
}
