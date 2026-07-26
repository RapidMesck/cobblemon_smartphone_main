package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.DeletePostPacket
import com.nbp.cobblemon_smartphone.social.SocialData
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object DeletePostHandler : ServerNetworkPacketHandler<DeletePostPacket> {
    private const val OP_PERMISSION_LEVEL = 2

    override fun handle(packet: DeletePostPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute

            val data = SocialData.get(server)
            val post = data.postById(packet.postId) ?: return@execute

            // Authorised here, never on the client: author deletes own, operators delete any.
            val allowed = post.authorUuid == player.uuid || player.hasPermissions(OP_PERMISSION_LEVEL)
            if (!allowed) {
                player.displayClientMessage(
                    Component.translatable("message.nbp.social.delete_denied").withColor(0xfd0100),
                    true
                )
                return@execute
            }

            data.removePost(packet.postId)
            RequestFeedPageHandler.sendPage(server, player, 0L)
        }
    }
}
