package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.compat.tomsstorage.TomsStorageLink
import com.nbp.cobblemon_smartphone.compat.tomsstorage.TomsStorageRemoteSession
import com.nbp.cobblemon_smartphone.compat.tomsstorage.getTomsStorageLink
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenTomsStorageTerminalPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

object OpenTomsStorageTerminalHandler : ServerNetworkPacketHandler<OpenTomsStorageTerminalPacket> {
    private const val ACTION_ID = "cobblemon_smartphone:toms_storage_terminal"
    private val buttonCooldowns = mutableMapOf<UUID, Long>()

    override fun handle(packet: OpenTomsStorageTerminalPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute { execute(server, player, isNativeAction = true) }
    }

    fun execute(server: MinecraftServer, player: ServerPlayer, isNativeAction: Boolean) {
        if (isNativeAction && !CobblemonSmartphone.config.features.enableTomsStorage) {
            player.displayClientMessage(
                Component.translatable("message.nbp.toms_storage.disabled").withColor(0xfd0100),
                true
            )
            return
        }

        if (!isModLoaded(TomsStorageLink.MOD_ID)) {
            player.displayClientMessage(
                Component.translatable("message.nbp.toms_storage.unavailable").withColor(0xfd0100),
                true
            )
            return
        }

        if (isNativeAction) {
            val cooldown = CobblemonSmartphone.config.cooldowns.tomsStorageButton
            val currentTime = System.currentTimeMillis()
            val lastUse = buttonCooldowns[player.uuid] ?: 0
            val elapsedSeconds = (currentTime - lastUse) / 1000

            if (elapsedSeconds < cooldown) {
                val remainingSeconds = (cooldown - elapsedSeconds).toInt() + 1
                player.displayClientMessage(
                    Component.translatable("message.nbp.toms_storage.cooldown", remainingSeconds).withColor(0xfd0100),
                    true
                )
                return
            }
        }

        val smartphone = SmartphoneHelper.findSmartphoneWithUpgradeAndLink(
            player,
            TomsStorageLink.UPGRADE_NBT_KEY,
            { it.getTomsStorageLink() != null },
            ACTION_ID
        )
        if (smartphone == null) {
            player.displayClientMessage(
                Component.translatable("message.nbp.toms_storage.not_linked").withColor(0xfd0100),
                true
            )
            return
        }

        if (isNativeAction) {
            buttonCooldowns[player.uuid] = System.currentTimeMillis()
        }

        val (dimension, pos) = smartphone.getTomsStorageLink()!!
        val targetLevel = server.getLevel(dimension)
        if (targetLevel == null || !targetLevel.isLoaded(pos)) {
            player.displayClientMessage(
                Component.translatable("message.nbp.toms_storage.not_loaded").withColor(0xfd0100),
                true
            )
            return
        }

        val state = targetLevel.getBlockState(pos)
        if (!state.`is`(TomsStorageLink.REMOTE_ACTIVATE_TAG)) {
            player.displayClientMessage(
                Component.translatable("message.nbp.toms_storage.invalid_block").withColor(0xfd0100),
                true
            )
            return
        }

        val hitResult = BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, true)
        val previousMenu = player.containerMenu

        TomsStorageRemoteSession.begin(player)
        state.useWithoutItem(targetLevel, player, hitResult)

        // Opening failed (menu unchanged) - nothing left to keep the session open for.
        if (player.containerMenu === previousMenu) {
            TomsStorageRemoteSession.end(player)
        }
    }
}
