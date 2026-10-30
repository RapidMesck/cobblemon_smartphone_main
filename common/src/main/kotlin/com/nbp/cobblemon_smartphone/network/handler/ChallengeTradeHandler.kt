package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.ChallengeTradePacket
import com.nbp.cobblemon_smartphone.social.SmartphoneInteractionManager
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object ChallengeTradeHandler : ServerNetworkPacketHandler<ChallengeTradePacket> {
    private const val ERROR_COLOR = 0xfd0100

    override fun handle(packet: ChallengeTradePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) {
                player.displayClientMessage(Component.translatable("message.nbp.social.disabled").withColor(ERROR_COLOR), false)
                return@execute
            }

            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.FEED_PAGE)) return@execute

            SmartphoneInteractionManager.sendRequest(player, packet.targetUuid, SmartphoneInteractionManager.InteractionType.TRADE)
        }
    }
}
