package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.SyncSmartphoneSkinsPacket
import com.nbp.cobblemon_smartphone.skin.SmartphoneSkinRegistry
import net.minecraft.client.Minecraft

object SyncSmartphoneSkinsHandler : ClientNetworkPacketHandler<SyncSmartphoneSkinsPacket> {
    override fun handle(packet: SyncSmartphoneSkinsPacket, client: Minecraft) {
        client.execute {
            SmartphoneSkinRegistry.updateDatapackSkins(packet.skins)
        }
    }
}
