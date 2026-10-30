package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.trade.TradeManager
import com.cobblemon.mod.common.util.isInBattle
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.ChallengeTradePacket
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object ChallengeTradeHandler : ServerNetworkPacketHandler<ChallengeTradePacket> {
    private const val ERROR_COLOR = 0xfd0100
    private const val SUCCESS_COLOR = 0x55ff55

    override fun handle(packet: ChallengeTradePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) {
                player.displayClientMessage(Component.translatable("message.nbp.social.disabled").withColor(ERROR_COLOR), true)
                return@execute
            }

            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.FEED_PAGE)) return@execute

            if (player.uuid == packet.targetUuid) return@execute

            val target = server.playerList.getPlayer(packet.targetUuid)
            if (target == null) {
                player.displayClientMessage(Component.translatable("message.nbp.social.target_offline").withColor(ERROR_COLOR), true)
                return@execute
            }

            if (player.isInBattle() || target.isInBattle()) {
                player.displayClientMessage(Component.translatable("message.nbp.social.already_in_battle").withColor(ERROR_COLOR), true)
                return@execute
            }

            val sent = TradeManager.sendRequest(TradeManager.TradeRequest(player, target))
            if (sent) {
                player.displayClientMessage(
                    Component.translatable("message.nbp.social.trade_sent", target.gameProfile.name).withColor(SUCCESS_COLOR),
                    true
                )
            } else {
                player.displayClientMessage(
                    Component.translatable("message.nbp.social.trade_busy").withColor(ERROR_COLOR),
                    true
                )
            }
        }
    }
}
