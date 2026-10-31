package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.RotomAlertPacket
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object RotomAlertHandler : ClientNetworkPacketHandler<RotomAlertPacket> {
    override fun handle(packet: RotomAlertPacket, client: Minecraft) {
        client.execute {
            val player = client.player ?: return@execute

            if (packet.soundAlert) {
                if (packet.isShiny) {
                    player.playSound(CobblemonSounds.POKE_BALL_SHINY_SEND_OUT, 1.0f, 1.0f)
                } else {
                    player.playSound(CobblemonSounds.FISHING_NOTIFICATION, 0.8f, 1.2f)
                }
            }

            val prefix = Component.literal("[Rotom] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            val message = if (packet.isShiny) {
                Component.empty()
                    .append(prefix)
                    .append(Component.translatable("cobblemon_smartphone.rotom.alert_shiny", packet.speciesName, packet.distance).withStyle(ChatFormatting.YELLOW))
            } else {
                val nat = packet.nature.replaceFirstChar(Char::uppercase)
                Component.empty()
                    .append(prefix)
                    .append(Component.translatable("cobblemon_smartphone.rotom.alert_target", packet.speciesName, packet.ivPercent, nat, packet.distance).withStyle(ChatFormatting.AQUA))
            }

            // Display in action bar for immediate visual feedback and in chat so the coordinates/distance can be referenced
            player.displayClientMessage(message, true)
            player.displayClientMessage(message, false)
        }
    }
}
