package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.compat.rct.RctTrainerCardBridge
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenRctTrainerCardPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenRctTrainerCardScreenPacket
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object OpenRctTrainerCardHandler : ServerNetworkPacketHandler<OpenRctTrainerCardPacket> {
    override fun handle(packet: OpenRctTrainerCardPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            execute(player)
        }
    }

    fun execute(player: ServerPlayer) {
        if (!CobblemonSmartphone.config.features.enableRctTrainerCard) {
            player.displayClientMessage(
                Component.translatable("message.nbp.rct.disabled").withColor(0xfd0100),
                true
            )
            return
        }

        if (!isModLoaded(RctTrainerCardBridge.RCT_MOD_ID)) {
            player.displayClientMessage(
                Component.translatable("message.nbp.rct.unavailable").withColor(0xfd0100),
                true
            )
            return
        }

        OpenRctTrainerCardScreenPacket().sendToPlayer(player)
    }
}
