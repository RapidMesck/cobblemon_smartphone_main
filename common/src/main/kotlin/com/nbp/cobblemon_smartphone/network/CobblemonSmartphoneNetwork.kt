package com.nbp.cobblemon_smartphone.network

import com.cobblemon.mod.common.net.PacketRegisterInfo
import com.nbp.cobblemon_smartphone.network.handler.HealPokemonHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenPCHandler
import com.nbp.cobblemon_smartphone.network.handler.server.OpenEnderChestHandler
import com.nbp.cobblemon_smartphone.network.packet.HealPokemonPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenEnderChestPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenPCPacket

object CobblemonSmartphoneNetwork {
    val s2cPayloads = generateS2CPacketInfoList()
    val c2sPayloads = generateC2SPacketInfoList()

    private fun generateS2CPacketInfoList(): List<PacketRegisterInfo<*>> {
        val list = mutableListOf<PacketRegisterInfo<*>>()

        return list
    }

    private fun generateC2SPacketInfoList(): List<PacketRegisterInfo<*>> {
        val list = mutableListOf<PacketRegisterInfo<*>>()

        list.add(PacketRegisterInfo(HealPokemonPacket.ID, HealPokemonPacket::decode, HealPokemonHandler))
        list.add(PacketRegisterInfo(OpenPCPacket.ID, OpenPCPacket::decode, OpenPCHandler))
        list.add(PacketRegisterInfo(OpenEnderChestPacket.ID, OpenEnderChestPacket::decode, OpenEnderChestHandler))

        return list
    }
}