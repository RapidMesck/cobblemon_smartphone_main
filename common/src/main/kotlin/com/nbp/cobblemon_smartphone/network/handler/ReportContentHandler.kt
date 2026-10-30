package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.ReportContentPacket
import com.nbp.cobblemon_smartphone.social.ReportTargetType
import com.nbp.cobblemon_smartphone.social.SocialData
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object ReportContentHandler : ServerNetworkPacketHandler<ReportContentPacket> {
    private const val OP_PERMISSION_LEVEL = 2
    private const val SUCCESS_COLOR = 0x55ff55
    private const val MOD_COLOR = 0xffaa00

    override fun handle(packet: ReportContentPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.DELETE)) return@execute

            val data = SocialData.get(server)
            val authorUuid = when (packet.targetType) {
                ReportTargetType.POST -> data.postById(packet.targetId)?.authorUuid
                ReportTargetType.COMMENT -> data.commentById(packet.targetId)?.authorUuid
            } ?: return@execute

            data.addReport(packet.targetType, packet.targetId, player.uuid, player.gameProfile.name, authorUuid, packet.reason)

            player.displayClientMessage(
                Component.translatable("message.nbp.social.report_sent").withColor(SUCCESS_COLOR),
                true
            )

            // Notify online operators
            val opMsg = Component.translatable(
                "message.nbp.social.mod_alert",
                player.gameProfile.name,
                packet.targetType.name.lowercase(),
                packet.targetId,
                packet.reason
            ).withColor(MOD_COLOR)

            server.playerList.players.forEach { op ->
                if (op.hasPermissions(OP_PERMISSION_LEVEL)) {
                    op.sendSystemMessage(opMsg)
                }
            }
        }
    }
}
