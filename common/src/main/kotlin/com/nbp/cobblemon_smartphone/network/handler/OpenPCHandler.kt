package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.pc
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.OpenPCPacket
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object OpenPCHandler : ServerNetworkPacketHandler<OpenPCPacket> {
    override fun handle(packet: OpenPCPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            execute(player, isNativeAction = true)
        }
    }

    fun execute(player: ServerPlayer, isNativeAction: Boolean) {
        // Feature toggle and cooldown only apply to the native smartphone action;
        // datapack actions are independent and manage their own cooldown/requirements.
        if (isNativeAction && !CobblemonSmartphone.config.features.enablePC) {
            player.displayClientMessage(
                Component.translatable("message.nbp.pc.disabled").withColor(0xfd0100),
                true
            )
            return
        }

        if (isNativeAction) {
            val currentTime = System.currentTimeMillis() / 1000
            val lastUse = PCCooldowns.lastPcUse[player.uuid] ?: 0
            val cooldown = CobblemonSmartphone.config.cooldowns.pcButton
            val timeElapsed = currentTime - lastUse

            if (timeElapsed < cooldown) {
                val remainingSeconds = (cooldown - timeElapsed).toInt()
                player.displayClientMessage(
                    Component.translatable("message.nbp.pc.cooldown", remainingSeconds).withColor(0xfd0100),
                    true
                )
                return
            }
        }

        if (player.isInBattle()) {
            player.displayClientMessage(
                Component.translatable("message.nbp.pc.battle_error").withColor(0xfd0100),
                true
            )
            return
        }

        if (isNativeAction) {
            PCCooldowns.lastPcUse[player.uuid] = System.currentTimeMillis() / 1000
        }

        val pc = player.pc()
        PCLinkManager.addLink(player.uuid, pc)
        com.cobblemon.mod.common.net.messages.client.storage.pc.OpenPCPacket(pc, box = 0).sendToPlayer(player)
    }
}

object PCCooldowns {
    val lastPcUse = mutableMapOf<UUID, Long>()
}
