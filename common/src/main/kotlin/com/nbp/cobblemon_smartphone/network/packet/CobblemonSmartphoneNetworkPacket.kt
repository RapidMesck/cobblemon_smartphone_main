package com.nbp.cobblemon_smartphone.network.packet

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import net.minecraft.server.level.ServerPlayer

interface CobblemonSmartphoneNetworkPacket<T : NetworkPacket<T>> : NetworkPacket<T> {
    override fun sendToServer() {
        CobblemonSmartphone.implementation.networkManager.sendToServer(this)
    }

    override fun sendToPlayer(player: ServerPlayer) = CobblemonSmartphone.implementation.networkManager.sendPacketToPlayer(player, this)
}