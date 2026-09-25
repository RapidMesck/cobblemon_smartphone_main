package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.compat.patchouli.PatchouliCompat
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenPatchouliBookPacket
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object OpenPatchouliBookHandler : ServerNetworkPacketHandler<OpenPatchouliBookPacket> {

    override fun handle(packet: OpenPatchouliBookPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            execute(player, packet.bookId)
        }
    }

    fun execute(player: ServerPlayer, bookId: String?) {
        if (!isModLoaded(PatchouliCompat.MOD_ID)) {
            player.displayClientMessage(
                Component.translatable("message.nbp.patchouli.unavailable").withColor(0xfd0100),
                true
            )
            return
        }

        if (bookId.isNullOrBlank()) {
            player.displayClientMessage(
                Component.translatable("message.nbp.patchouli.not_configured").withColor(0xfd0100),
                true
            )
            return
        }

        val bookLocation = ResourceLocation.tryParse(bookId)
        if (bookLocation == null) {
            player.displayClientMessage(
                Component.translatable("message.nbp.patchouli.invalid_book", bookId).withColor(0xfd0100),
                true
            )
            return
        }

        val success = PatchouliCompat.openBook(player, bookLocation)
        if (!success) {
            player.displayClientMessage(
                Component.translatable("message.nbp.patchouli.open_failed").withColor(0xfd0100),
                true
            )
        }
    }
}
