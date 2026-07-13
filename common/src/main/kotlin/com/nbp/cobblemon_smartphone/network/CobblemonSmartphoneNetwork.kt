package com.nbp.cobblemon_smartphone.network

import com.cobblemon.mod.common.net.PacketRegisterInfo
import com.nbp.cobblemon_smartphone.network.handler.ExecuteDatapackActionHandler
import com.nbp.cobblemon_smartphone.network.handler.HealPokemonHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenCobblenavPokenavHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenCobbledollarsShopHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenPCHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenPokedexHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenWaystonesWarpStoneHandler
import com.nbp.cobblemon_smartphone.network.handler.RequestSpeciesDetailHandler
import com.nbp.cobblemon_smartphone.network.handler.SaveActionOrderHandler
import com.nbp.cobblemon_smartphone.network.handler.SpeciesDetailResponseHandler
import com.nbp.cobblemon_smartphone.network.handler.SyncActionOrderHandler
import com.nbp.cobblemon_smartphone.network.handler.SyncDatapackActionsHandler
import com.nbp.cobblemon_smartphone.network.handler.server.OpenEnderChestHandler
import com.nbp.cobblemon_smartphone.network.packet.ExecuteDatapackActionPacket
import com.nbp.cobblemon_smartphone.network.packet.HealPokemonPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenCobblenavPokenavPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenCobbledollarsShopPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenEnderChestPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenPCPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenPokedexPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenWaystonesWarpStonePacket
import com.nbp.cobblemon_smartphone.network.packet.RequestSpeciesDetailPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveActionOrderPacket
import com.nbp.cobblemon_smartphone.network.packet.SpeciesDetailResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncActionOrderPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncDatapackActionsPacket

object CobblemonSmartphoneNetwork {
    val s2cPayloads = generateS2CPacketInfoList()
    val c2sPayloads = generateC2SPacketInfoList()

    private fun generateS2CPacketInfoList(): List<PacketRegisterInfo<*>> {
        val list = mutableListOf<PacketRegisterInfo<*>>()

        list.add(
            PacketRegisterInfo(
                SyncDatapackActionsPacket.ID,
                SyncDatapackActionsPacket::decode,
                SyncDatapackActionsHandler
            )
        )
        list.add(PacketRegisterInfo(SyncActionOrderPacket.ID, SyncActionOrderPacket::decode, SyncActionOrderHandler))
        list.add(
            PacketRegisterInfo(
                SpeciesDetailResponsePacket.ID,
                SpeciesDetailResponsePacket::decode,
                SpeciesDetailResponseHandler
            )
        )

        return list
    }

    private fun generateC2SPacketInfoList(): List<PacketRegisterInfo<*>> {
        val list = mutableListOf<PacketRegisterInfo<*>>()

        list.add(PacketRegisterInfo(HealPokemonPacket.ID, HealPokemonPacket::decode, HealPokemonHandler))
        list.add(PacketRegisterInfo(OpenPCPacket.ID, OpenPCPacket::decode, OpenPCHandler))
        list.add(PacketRegisterInfo(OpenEnderChestPacket.ID, OpenEnderChestPacket::decode, OpenEnderChestHandler))
        list.add(
            PacketRegisterInfo(
                OpenCobblenavPokenavPacket.ID,
                OpenCobblenavPokenavPacket::decode,
                OpenCobblenavPokenavHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                OpenCobbledollarsShopPacket.ID,
                OpenCobbledollarsShopPacket::decode,
                OpenCobbledollarsShopHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                OpenWaystonesWarpStonePacket.ID,
                OpenWaystonesWarpStonePacket::decode,
                OpenWaystonesWarpStoneHandler
            )
        )
        list.add(PacketRegisterInfo(OpenPokedexPacket.ID, OpenPokedexPacket::decode, OpenPokedexHandler))
        list.add(
            PacketRegisterInfo(
                ExecuteDatapackActionPacket.ID,
                ExecuteDatapackActionPacket::decode,
                ExecuteDatapackActionHandler
            )
        )
        list.add(PacketRegisterInfo(SaveActionOrderPacket.ID, SaveActionOrderPacket::decode, SaveActionOrderHandler))
        list.add(
            PacketRegisterInfo(
                RequestSpeciesDetailPacket.ID,
                RequestSpeciesDetailPacket::decode,
                RequestSpeciesDetailHandler
            )
        )

        return list
    }
}
