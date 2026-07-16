package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.party
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.HealPokemonPacket
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object HealPokemonHandler : ServerNetworkPacketHandler<HealPokemonPacket> {
    override fun handle(packet: HealPokemonPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            execute(player, isNativeAction = true)
        }
    }

    fun execute(player: ServerPlayer, isNativeAction: Boolean) {
        // Feature toggle and cooldown only apply to the native smartphone action;
        // datapack actions are independent and manage their own cooldown/requirements.
        if (isNativeAction && !CobblemonSmartphone.config.features.enableHeal) {
            player.displayClientMessage(
                Component.translatable("message.nbp.heal.disabled").withColor(0xfd0100),
                true
            )
            return
        }

        if (isNativeAction) {
            val currentTime = System.currentTimeMillis() / 1000
            val lastUse = HealPokemonCooldowns.lastHealUse[player.uuid] ?: 0
            val cooldown = CobblemonSmartphone.config.cooldowns.healButton
            val timeElapsed = currentTime - lastUse

            if (timeElapsed < cooldown) {
                val remainingSeconds = (cooldown - timeElapsed).toInt()
                player.displayClientMessage(
                    Component.translatable("message.nbp.heal.cooldown", remainingSeconds).withColor(0xfd0100),
                    true
                )
                return
            }
        }

        if (player.isInBattle()) {
            player.displayClientMessage(
                Component.translatable("message.nbp.heal.battle_error").withColor(0xfd0100),
                true
            )
            return
        }

        if (isNativeAction) {
            HealPokemonCooldowns.lastHealUse[player.uuid] = System.currentTimeMillis() / 1000
        }

        player.party().heal()
        player.displayClientMessage(
            Component.translatable("message.nbp.heal.success").withColor(0x00ff00),
            true
        )
    }
}

object HealPokemonCooldowns {
    val lastHealUse = mutableMapOf<UUID, Long>()
}
