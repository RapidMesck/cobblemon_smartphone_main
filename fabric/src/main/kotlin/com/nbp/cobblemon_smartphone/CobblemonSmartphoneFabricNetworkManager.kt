package com.nbp.cobblemon_smartphone

import com.cobblemon.mod.common.NetworkManager
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.fabric.net.FabricPacketInfo
import com.nbp.cobblemon_smartphone.network.CobblemonSmartphoneNetwork
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer

object CobblemonSmartphoneFabricNetworkManager : NetworkManager {
    fun registerMessages() {
        CobblemonSmartphoneNetwork.s2cPayloads.map { FabricPacketInfo(it) }.forEach { it.registerPacket(client = true) }
        CobblemonSmartphoneNetwork.c2sPayloads.map { FabricPacketInfo(it) }.forEach { it.registerPacket(client = false) }
    }

    fun registerClientHandlers() {
        CobblemonSmartphoneNetwork.s2cPayloads.map { FabricPacketInfo(it) }.forEach { it.registerClientHandler() }
    }

    fun registerServerHandlers() {
        CobblemonSmartphoneNetwork.c2sPayloads.map { FabricPacketInfo(it) }.forEach { it.registerServerHandler() }
    }

    override fun sendPacketToPlayer(player: ServerPlayer, packet: NetworkPacket<*>) {
        ServerPlayNetworking.send(player, packet)
    }

    override fun sendToServer(packet: NetworkPacket<*>) {
        ClientPlayNetworking.send(packet)
    }
}