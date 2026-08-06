package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.util.party
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
import com.nbp.cobblemon_smartphone.social.PokemonAttachment
import com.nbp.cobblemon_smartphone.social.SocialPhotoManager
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.SocialMutationResultPacket
import com.nbp.cobblemon_smartphone.network.packet.ThreadSummaryUpdatePacket
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
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.THREAD_LIST)) return@execute
            val pageSize = CobblemonSmartphone.config.social.threadPageSize.coerceIn(1, 100)
            val all = SocialData.get(server).threadSummaries(player.uuid)
            val candidates = if (packet.beforeTimestamp <= 0L) all else {
                all.filter { it.lastTimestamp < packet.beforeTimestamp }
            }
            val page = candidates.take(pageSize)
            ThreadListPacket(
                threads = page,
                hasMore = candidates.size > page.size,
                append = packet.beforeTimestamp > 0L
            ).sendToPlayer(player)
        }
    }
}

object RequestThreadPageHandler : ServerNetworkPacketHandler<RequestThreadPagePacket> {
    override fun handle(packet: RequestThreadPagePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.THREAD_PAGE)) {
                sendPage(server, player, packet.otherUuid, packet.beforeId)
            }
        }
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
                result(player, packet, SocialMutationResultPacket.Status.DISABLED)
                return@execute
            }

            val data = SocialData.get(server)
            if (data.isDmBanned(player.uuid)) {
                player.socialError("message.nbp.social.dm_banned")
                result(player, packet, SocialMutationResultPacket.Status.BANNED)
                return@execute
            }
            if (packet.targetUuid == player.uuid) {
                result(player, packet, SocialMutationResultPacket.Status.INVALID_TARGET)
                return@execute
            }

            val cooldown = CobblemonSmartphone.config.cooldowns.socialMessage
            if (cooldown > 0) {
                val now = System.currentTimeMillis() / 1000
                if (now - (lastMessage[player.uuid] ?: 0) < cooldown) {
                    result(player, packet, SocialMutationResultPacket.Status.RATE_LIMITED)
                    return@execute
                }
            }

            val text = packet.text.trim().take(CobblemonSmartphone.config.social.maxMessageLength)
            val target = server.playerList.getPlayer(packet.targetUuid)
            val targetName = target?.gameProfile?.name
                ?: server.profileCache?.get(packet.targetUuid)?.orElse(null)?.name
                ?: run {
                    result(player, packet, SocialMutationResultPacket.Status.INVALID_TARGET)
                    return@execute
                }

            val attachment = resolveAttachment(player, packet)
            val photo = SocialPhotoManager.claim(player, packet.photoId)
            if (text.isEmpty() && attachment == null && photo == null) {
                result(player, packet, SocialMutationResultPacket.Status.EMPTY)
                return@execute
            }

            val message = data.addMessage(player, packet.targetUuid, targetName, text, attachment, photo)
            lastMessage[player.uuid] = System.currentTimeMillis() / 1000

            // Echo to the sender so their thread updates without a refetch...
            NewDmPacket(packet.targetUuid, targetName, message).sendToPlayer(player)
            // ...and push to the recipient if they are online. Offline players simply pick it up
            // from the store on their next login, since it is already persisted.
            target?.let {
                NewDmPacket(player.uuid, player.gameProfile.name, message).sendToPlayer(it)
                syncUnread(server, it)
            }
            data.threadSummaries(player.uuid).firstOrNull { it.otherUuid == packet.targetUuid }?.let {
                ThreadSummaryUpdatePacket(it).sendToPlayer(player)
            }
            target?.let { recipient ->
                data.threadSummaries(recipient.uuid).firstOrNull { it.otherUuid == player.uuid }?.let {
                    ThreadSummaryUpdatePacket(it).sendToPlayer(recipient)
                }
            }
            result(player, packet, SocialMutationResultPacket.Status.SUCCESS)
        }
    }

    private fun result(player: ServerPlayer, packet: SendDmPacket, status: SocialMutationResultPacket.Status) {
        SocialMutationResultPacket(packet.requestId, status).sendToPlayer(player)
    }

    private fun resolveAttachment(player: ServerPlayer, packet: SendDmPacket): PokemonAttachment? {
        if (packet.attachSlot < 0) return null
        val pokemon = player.party().get(packet.attachSlot) ?: return null
        val showDetails = packet.showDetails
        return PokemonAttachment(
            species = pokemon.species.resourceIdentifier.toString(),
            aspects = pokemon.aspects,
            level = pokemon.level,
            nickname = pokemon.nickname?.string,
            ivs = if (showDetails && packet.showIvs) ATTACHMENT_STATS.map { pokemon.ivs[it] ?: 0 } else emptyList(),
            evs = if (showDetails && packet.showEvs) ATTACHMENT_STATS.map { pokemon.evs[it] ?: 0 } else emptyList(),
            gender = pokemon.gender.name,
            ability = if (showDetails && packet.showAbility) pokemon.ability.displayName else null,
            nature = if (showDetails && packet.showNature) pokemon.effectiveNature.displayName else null,
            types = if (showDetails) listOfNotNull(pokemon.form.primaryType.name, pokemon.form.secondaryType?.name) else emptyList()
        )
    }

    private val ATTACHMENT_STATS = listOf(
        Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    )
}

object MarkThreadReadHandler : ServerNetworkPacketHandler<MarkThreadReadPacket> {
    override fun handle(packet: MarkThreadReadPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.MARK_READ)) return@execute
            if (SocialData.get(server).markThreadRead(player.uuid, packet.otherUuid)) {
                syncUnread(server, player)
            }
        }
    }
}
