package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.MarkThreadReadPacket
import com.nbp.cobblemon_smartphone.network.packet.NewDmPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestThreadListPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestThreadPagePacket
import com.nbp.cobblemon_smartphone.network.packet.SendDmPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncUnreadPacket
import com.nbp.cobblemon_smartphone.network.packet.ThreadListPacket
import com.nbp.cobblemon_smartphone.network.packet.ThreadPagePacket
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.social.ThreadKey
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

private const val ERROR_COLOR = 0xfd0100

internal fun ServerPlayer.socialError(key: String) {
    displayClientMessage(Component.translatable(key).withColor(ERROR_COLOR), true)
}

/** Pushes the player's current unread total so the home-screen badge stays accurate. */
internal fun syncUnread(server: MinecraftServer, player: ServerPlayer) {
    SyncUnreadPacket(SocialData.get(server).totalUnreadFor(player.uuid)).sendToPlayer(player)
}

object RequestThreadListHandler : ServerNetworkPacketHandler<RequestThreadListPacket> {
    override fun handle(packet: RequestThreadListPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            ThreadListPacket(SocialData.get(server).threadSummaries(player.uuid)).sendToPlayer(player)
        }
    }
}

object RequestThreadPageHandler : ServerNetworkPacketHandler<RequestThreadPagePacket> {
    override fun handle(packet: RequestThreadPagePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute { sendPage(server, player, packet.otherUuid, packet.beforeId) }
    }

    /** Must already be on the server thread. */
    fun sendPage(server: MinecraftServer, player: ServerPlayer, otherUuid: UUID, beforeId: Long) {
        if (!CobblemonSmartphone.config.features.enableSocial) return

        val data = SocialData.get(server)
        val key = ThreadKey.of(player.uuid, otherUuid)
        val thread = data.thread(key)
        val pageSize = CobblemonSmartphone.config.social.messagePageSize.coerceIn(1, 100)

        // Oldest-first on the wire; the newest page is the tail of the list.
        val all = thread?.messages ?: emptyList()
        val candidates = if (beforeId <= 0L) all else all.filter { it.id < beforeId }
        val page = candidates.takeLast(pageSize)

        ThreadPagePacket(
            otherUuid = otherUuid,
            otherName = thread?.displayNameOf(otherUuid) ?: resolveName(server, otherUuid),
            messages = page,
            hasMore = candidates.size > page.size,
            append = beforeId > 0L
        ).sendToPlayer(player)
    }

    private fun resolveName(server: MinecraftServer, uuid: UUID): String =
        server.playerList.getPlayer(uuid)?.gameProfile?.name
            ?: server.profileCache?.get(uuid)?.orElse(null)?.name
            ?: uuid.toString().take(8)
}

object SendDmHandler : ServerNetworkPacketHandler<SendDmPacket> {
    private val lastMessage = mutableMapOf<UUID, Long>()

    override fun handle(packet: SendDmPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) {
                player.socialError("message.nbp.social.disabled")
                return@execute
            }

            val data = SocialData.get(server)
            if (data.isDmBanned(player.uuid)) {
                player.socialError("message.nbp.social.dm_banned")
                return@execute
            }
            if (packet.targetUuid == player.uuid) return@execute

            val cooldown = CobblemonSmartphone.config.cooldowns.socialMessage
            if (cooldown > 0) {
                val now = System.currentTimeMillis() / 1000
                if (now - (lastMessage[player.uuid] ?: 0) < cooldown) return@execute
            }

            val text = packet.text.trim().take(CobblemonSmartphone.config.social.maxMessageLength)
            if (text.isEmpty()) return@execute

            val target = server.playerList.getPlayer(packet.targetUuid)
            val targetName = target?.gameProfile?.name
                ?: server.profileCache?.get(packet.targetUuid)?.orElse(null)?.name
                ?: return@execute

            val message = data.addMessage(player, packet.targetUuid, targetName, text)
            lastMessage[player.uuid] = System.currentTimeMillis() / 1000

            // Echo to the sender so their thread updates without a refetch...
            NewDmPacket(packet.targetUuid, targetName, message).sendToPlayer(player)
            // ...and push to the recipient if they are online. Offline players simply pick it up
            // from the store on their next login, since it is already persisted.
            target?.let {
                NewDmPacket(player.uuid, player.gameProfile.name, message).sendToPlayer(it)
                syncUnread(server, it)
            }
        }
    }
}

object MarkThreadReadHandler : ServerNetworkPacketHandler<MarkThreadReadPacket> {
    override fun handle(packet: MarkThreadReadPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (SocialData.get(server).markThreadRead(player.uuid, packet.otherUuid)) {
                syncUnread(server, player)
            }
        }
    }
}
