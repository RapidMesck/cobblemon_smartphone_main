package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.client.pokedex.PokedexType
import com.cobblemon.mod.common.net.messages.client.ui.PokedexUIPacket
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.OpenPokedexPacket
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object OpenPokedexHandler : ServerNetworkPacketHandler<OpenPokedexPacket> {
    private val buttonCooldowns = mutableMapOf<UUID, Long>()

    override fun handle(packet: OpenPokedexPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            execute(player, packet.type, useNativeCooldown = true)
        }
    }

    fun execute(player: ServerPlayer, type: PokedexType, useNativeCooldown: Boolean) {
        if (!CobblemonSmartphone.config.features.enablePokedex) {
            player.displayClientMessage(
                Component.translatable("message.nbp.pokedex.disabled").withColor(0xfd0100),
                true
            )
            return
        }

        if (useNativeCooldown) {
            val cooldown = CobblemonSmartphone.config.cooldowns.pokedexButton
            val now = System.currentTimeMillis()
            val lastClick = buttonCooldowns[player.uuid] ?: 0L

            if (now - lastClick < cooldown * 1000L) {
                val remaining = ((cooldown * 1000L - (now - lastClick)) / 1000L).toInt() + 1
                player.displayClientMessage(
                    Component.translatable("message.nbp.pokedex.cooldown", remaining).withColor(0xfd0100),
                    true
                )
                return
            }

            buttonCooldowns[player.uuid] = now
        }

        PokedexUIPacket(type = type).sendToPlayer(player)
    }
}
