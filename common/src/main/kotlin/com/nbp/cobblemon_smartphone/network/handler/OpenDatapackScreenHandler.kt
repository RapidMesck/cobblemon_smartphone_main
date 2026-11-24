package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.gui.DatapackScreen
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.network.packet.OpenDatapackScreenPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft

object OpenDatapackScreenHandler : ClientNetworkPacketHandler<OpenDatapackScreenPacket> {
    override fun handle(packet: OpenDatapackScreenPacket, client: Minecraft) {
        client.execute {
            val stack = SmartphoneHelper.contextSmartphone ?: client.player?.let { SmartphoneHelper.getSmartphone(it) }
            val color = SmartphoneHelper.contextColor
                ?: (stack?.item as? SmartphoneItem)?.getColor()
                ?: SmartphoneColor.RED
            DatapackScreen.openTargetScreen(color, stack, packet.screenId)
        }
    }
}
