package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.util.isInBattle
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.HealPokemonPacket
import net.minecraft.network.chat.Component
import java.util.UUID

object HealPokemonHandler : ServerNetworkPacketHandler<HealPokemonPacket> {
    override fun handle(packet: HealPokemonPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            val isEnabled = CobblemonSmartphone.config.features.enableHeal

            if (!isEnabled) {
                player.displayClientMessage(Component.translatable("message.nbp.heal.disabled").withColor(0xfd0100), true);
                return@execute
            }

            // Verifica cooldown
            val currentTime = System.currentTimeMillis() / 1000 // tempo em segundos
            val lastUse = HealPokemonCooldowns.lastHealUse[player.uuid] ?: 0
            val cooldown = CobblemonSmartphone.config.cooldowns.healButton
            val timeElapsed = currentTime - lastUse

            if (timeElapsed < cooldown) {
                val remainingSeconds = (cooldown - timeElapsed).toInt()
                player.displayClientMessage(Component.translatable("message.nbp.heal.cooldown", remainingSeconds).withColor(0xfd0100), true);
                return@execute
            }

            if (player.isInBattle()) {
                player.displayClientMessage(Component.translatable("message.nbp.heal.battle_error").withColor(0xfd0100), true);
                return@execute
            }

            // Atualiza o cooldown
            HealPokemonCooldowns.lastHealUse[player.uuid] = currentTime

            // Cura todos os Pokémon da party do jogador
            val party = player.party()
            party.heal()

            player.displayClientMessage(Component.translatable("message.nbp.heal.success").withColor(0x00ff00), true);
        }
    }
}

// Objeto auxiliar para armazenar o cooldown de cada jogador
object HealPokemonCooldowns {
    val lastHealUse = mutableMapOf<UUID, Long>()
}
