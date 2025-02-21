package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager
import com.cobblemon.mod.common.util.pc
import com.cobblemon.mod.common.util.isInBattle
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.OpenPCPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import java.util.UUID

object OpenPCHandler : ServerNetworkPacketHandler<OpenPCPacket> {
    override fun handle(packet: OpenPCPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            val isEnabled = CobblemonSmartphone.config.features.enablePC

            if (!isEnabled) {
                player.displayClientMessage(Component.translatable("message.nbp.pc.disabled").withColor(0xfd0100), true)
                return@execute
            }

            // Verifica cooldown
            val currentTime = System.currentTimeMillis() / 1000 // tempo em segundos
            val lastUse = PCCooldowns.lastPcUse[player.uuid] ?: 0
            val cooldown = CobblemonSmartphone.config.cooldowns.pcButton
            val timeElapsed = currentTime - lastUse

            if (timeElapsed < cooldown) {
                val remainingSeconds = (cooldown - timeElapsed).toInt()
                player.displayClientMessage(Component.translatable("message.nbp.pc.cooldown", remainingSeconds).withColor(0xfd0100), true)
                return@execute
            }

            if (player.isInBattle()) {
                player.displayClientMessage(Component.translatable("message.nbp.pc.battle_error").withColor(0xfd0100), true)
                return@execute
            }

            // Atualiza o cooldown
            PCCooldowns.lastPcUse[player.uuid] = currentTime

            // Recupera o PC do jogador e cria o link
            val pc = player.pc()
            PCLinkManager.addLink(player.uuid, pc)

            // Envia o packet para abrir o PC no cliente
            com.cobblemon.mod.common.net.messages.client.storage.pc.OpenPCPacket(pc.uuid, 0).sendToPlayer(player)
        }
    }
}

// Objeto auxiliar para armazenar o cooldown de cada jogador
object PCCooldowns {
    val lastPcUse = mutableMapOf<UUID, Long>()
}
