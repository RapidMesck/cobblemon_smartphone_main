package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.DatapackActionLoader
import com.nbp.cobblemon_smartphone.network.packet.ExecuteDatapackActionPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object ExecuteDatapackActionHandler : ServerNetworkPacketHandler<ExecuteDatapackActionPacket> {
    private val lastUse = mutableMapOf<UUID, MutableMap<String, Long>>()

    override fun handle(packet: ExecuteDatapackActionPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            val actionId = packet.actionId
            val commands = DatapackActionLoader.getActionCommands(actionId)
            if (commands.isEmpty()) {
                CobblemonSmartphone.LOGGER.warn("Unknown datapack action requested: {}", actionId)
                return@execute
            }

            val requiredUpgrade = DatapackActionLoader.getActionRequiredUpgrade(actionId)
            if (requiredUpgrade != null && !SmartphoneHelper.hasUpgradeOnAnySmartphone(player, requiredUpgrade, actionId)) {
                CobblemonSmartphone.LOGGER.warn(
                    "Player {} tried to execute upgrade-locked datapack action '{}' without required upgrade '{}'",
                    player.gameProfile.name,
                    actionId,
                    requiredUpgrade
                )
                return@execute
            }

            val cooldownSeconds = DatapackActionLoader.getActionCooldown(actionId)
            if (cooldownSeconds > 0) {
                val currentTime = System.currentTimeMillis() / 1000
                val playerActions = lastUse.getOrPut(player.uuid) { mutableMapOf() }
                val lastActionUse = playerActions[actionId] ?: 0
                val elapsed = currentTime - lastActionUse
                if (elapsed < cooldownSeconds) {
                    val remaining = (cooldownSeconds - elapsed).toInt()
                    player.displayClientMessage(
                        Component.translatable("message.nbp.datapack_action.cooldown", remaining).withColor(0xfd0100),
                        true
                    )
                    return@execute
                }
                playerActions[actionId] = currentTime
            }

            val source = player.createCommandSourceStack()
            for (command in commands) {
                server.commands.performPrefixedCommand(source, command)
            }
        }
    }
}
