package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.calendar.CalendarClockMode
import com.nbp.cobblemon_smartphone.calendar.CalendarServerData
import com.nbp.cobblemon_smartphone.calendar.CalendarTimeUtils
import com.nbp.cobblemon_smartphone.network.packet.SyncServerCalendarPacket
import com.nbp.cobblemon_smartphone.network.packet.UpsertServerCalendarEventPacket
import com.nbp.cobblemon_smartphone.social.SocialData
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object UpsertServerCalendarEventHandler : ServerNetworkPacketHandler<UpsertServerCalendarEventPacket> {
    override fun handle(packet: UpsertServerCalendarEventPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableCalendar) return@execute
            if (!player.hasPermissions(CalendarServerData.OP_PERMISSION_LEVEL)) return@execute

            val data = CalendarServerData.get(server)
            val existedBefore = data.getById(packet.event.id) != null
            val saved = data.upsertEvent(packet.event, player.gameProfile.name) ?: return@execute

            val syncPacket = SyncServerCalendarPacket(data.listEvents())
            server.playerList.players.forEach { target ->
                syncPacket.sendToPlayer(target)
            }

            if (packet.announceInSocial && !existedBefore && CobblemonSmartphone.config.features.enableSocial) {
                val maxLen = CobblemonSmartphone.config.social.maxPostLength.coerceAtLeast(64)
                val timeLine = if (saved.clockMode == CalendarClockMode.REAL_TIME) {
                    "${CalendarTimeUtils.formatUtcDateTime(saved.startEpochMillis)} (check Calendar for your local time)"
                } else {
                    "World Day ${saved.worldDay + 1} (${saved.worldPeriod.id.replace('_', ' ')})"
                }
                val descPart = if (saved.description.isNotBlank()) "\n${saved.description}" else ""
                val postText = "[Calendar] ${saved.title}\n$timeLine$descPart".take(maxLen)
                SocialData.get(server).addPost(
                    authorUuid = player.uuid,
                    authorName = player.gameProfile.name,
                    text = postText,
                    attachment = null
                )
            }
        }
    }
}
