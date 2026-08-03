package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.actions.OpenRefinedStorageAction
import com.nbp.cobblemon_smartphone.compat.refinedstorage.RefinedStorageAccessHolder
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenRefinedStorageGridPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object OpenRefinedStorageGridHandler : ServerNetworkPacketHandler<OpenRefinedStorageGridPacket> {
    private const val ACTION_ID = "cobblemon_smartphone:refinedstorage_grid"
    private val buttonCooldowns = mutableMapOf<UUID, Long>()

    override fun handle(packet: OpenRefinedStorageGridPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute { execute(player, isNativeAction = true) }
    }

    fun execute(player: ServerPlayer, isNativeAction: Boolean) {
        if (isNativeAction && !CobblemonSmartphone.config.features.enableRefinedStorage) {
            player.displayClientMessage(
                Component.translatable("message.nbp.refinedstorage.disabled").withColor(0xfd0100),
                true
            )
            return
        }

        val access = RefinedStorageAccessHolder.instance
        if (access == null || !isModLoaded(OpenRefinedStorageAction.MOD_ID)) {
            player.displayClientMessage(
                Component.translatable("message.nbp.refinedstorage.unavailable").withColor(0xfd0100),
                true
            )
            return
        }

        if (isNativeAction) {
            val cooldown = CobblemonSmartphone.config.cooldowns.refinedStorageButton
            val currentTime = System.currentTimeMillis()
            val lastUse = buttonCooldowns[player.uuid] ?: 0
            val elapsedSeconds = (currentTime - lastUse) / 1000

            if (elapsedSeconds < cooldown) {
                val remainingSeconds = (cooldown - elapsedSeconds).toInt() + 1
                player.displayClientMessage(
                    Component.translatable("message.nbp.refinedstorage.cooldown", remainingSeconds).withColor(0xfd0100),
                    true
                )
                return
            }
        }

        val smartphone = SmartphoneHelper.findSmartphoneWithUpgradeAndLink(
            player,
            OpenRefinedStorageAction.UPGRADE_NBT_KEY,
            { access.isBound(it) },
            ACTION_ID
        )
        if (smartphone == null) {
            player.displayClientMessage(
                Component.translatable("message.nbp.refinedstorage.not_linked").withColor(0xfd0100),
                true
            )
            return
        }

        if (!access.isReachable(player, smartphone)) {
            player.displayClientMessage(
                Component.translatable("message.nbp.refinedstorage.not_reachable").withColor(0xfd0100),
                true
            )
            return
        }

        if (isNativeAction) {
            buttonCooldowns[player.uuid] = System.currentTimeMillis()
        }

        access.openGrid(player, smartphone)
    }
}
