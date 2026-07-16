package com.nbp.cobblemon_smartphone.network.handler.server

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.OpenEnderChestPacket
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import java.util.UUID

object OpenEnderChestHandler : ServerNetworkPacketHandler<OpenEnderChestPacket> {
    override fun handle(packet: OpenEnderChestPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            execute(player, isNativeAction = true)
        }
    }

    fun execute(player: ServerPlayer, isNativeAction: Boolean) {
        // Feature toggle and cooldown only apply to the native smartphone action;
        // datapack actions are independent and manage their own cooldown/requirements.
        if (isNativeAction && !CobblemonSmartphone.config.features.enableCloud) {
            player.displayClientMessage(
                Component.translatable("message.nbp.cloud.disabled").withColor(0xfd0100),
                true
            )
            return
        }

        if (isNativeAction) {
            val currentTime = System.currentTimeMillis() / 1000
            val lastUse = EnderChestCooldowns.lastEnderChestUse[player.uuid] ?: 0
            val cooldown = CobblemonSmartphone.config.cooldowns.cloudButton
            val timeElapsed = currentTime - lastUse

            if (timeElapsed < cooldown) {
                val remainingSeconds = (cooldown - timeElapsed).toInt()
                player.displayClientMessage(
                    Component.translatable("message.nbp.cloud.cooldown", remainingSeconds).withColor(0xfd0100),
                    true
                )
                return
            }

            EnderChestCooldowns.lastEnderChestUse[player.uuid] = currentTime
        }

        openEnderChestForPlayer(player)
    }

    private fun openEnderChestForPlayer(player: ServerPlayer) {
        val enderChestInventory = player.enderChestInventory
        player.openMenu(object : MenuProvider {
            override fun getDisplayName() = Component.translatable("container.enderchest")

            override fun createMenu(
                containerId: Int,
                playerInventory: Inventory,
                playerEntity: Player
            ): AbstractContainerMenu {
                return ChestMenu.threeRows(containerId, playerInventory, enderChestInventory)
            }
        })
    }
}

object EnderChestCooldowns {
    val lastEnderChestUse = mutableMapOf<UUID, Long>()
}
