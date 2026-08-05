package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.actions.OpenAE2CraftingAction
import com.nbp.cobblemon_smartphone.compat.ae2.AE2AccessHolder
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenAE2CraftingTerminalPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object OpenAE2CraftingTerminalHandler : ServerNetworkPacketHandler<OpenAE2CraftingTerminalPacket> {
    private const val ACTION_ID = "cobblemon_smartphone:ae2_crafting_terminal"
    private val buttonCooldowns = mutableMapOf<UUID, Long>()

    override fun handle(packet: OpenAE2CraftingTerminalPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute { execute(player, isNativeAction = true) }
    }

    fun execute(player: ServerPlayer, isNativeAction: Boolean) {
        if (isNativeAction && !CobblemonSmartphone.config.features.enableAE2) {
            player.displayClientMessage(Component.translatable("message.nbp.ae2.disabled").withColor(0xfd0100), true)
            return
        }

        val access = AE2AccessHolder.instance
        if (access == null || !isModLoaded(OpenAE2CraftingAction.MOD_ID)) {
            player.displayClientMessage(Component.translatable("message.nbp.ae2.unavailable").withColor(0xfd0100), true)
            return
        }

        if (isNativeAction) {
            val cooldown = CobblemonSmartphone.config.cooldowns.ae2Button.coerceAtMost(3)
            val elapsedSeconds = (System.currentTimeMillis() - (buttonCooldowns[player.uuid] ?: 0)) / 1000
            if (elapsedSeconds < cooldown) {
                val remainingSeconds = (cooldown - elapsedSeconds).toInt() + 1
                player.displayClientMessage(Component.translatable("message.nbp.ae2.cooldown", remainingSeconds).withColor(0xfd0100), true)
                return
            }
        }

        val smartphone = SmartphoneHelper.findSmartphoneWithUpgradeAndLink(
            player,
            OpenAE2CraftingAction.UPGRADE_NBT_KEY,
            { access.isBound(it) },
            ACTION_ID
        )
        if (smartphone == null) {
            player.displayClientMessage(Component.translatable("message.nbp.ae2.not_linked").withColor(0xfd0100), true)
            return
        }
        if (!access.isReachable(player, smartphone)) {
            player.displayClientMessage(Component.translatable("message.nbp.ae2.not_reachable").withColor(0xfd0100), true)
            return
        }

        if (isNativeAction) buttonCooldowns[player.uuid] = System.currentTimeMillis()
        access.openCraftingTerminal(player, smartphone)
    }
}
