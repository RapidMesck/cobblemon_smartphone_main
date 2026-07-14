package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.OpenCraftingTablePacket
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.CraftingMenu

object OpenCraftingTableHandler : ServerNetworkPacketHandler<OpenCraftingTablePacket> {
    override fun handle(packet: OpenCraftingTablePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableCrafting) {
                player.displayClientMessage(
                    Component.translatable("message.nbp.crafting.disabled").withColor(0xfd0100),
                    true
                )
                return@execute
            }

            player.openMenu(
                SimpleMenuProvider(
                    { containerId, inventory, _ ->
                        CraftingMenu(containerId, inventory)
                    },
                    Component.translatable("container.crafting")
                )
            )
        }
    }
}
