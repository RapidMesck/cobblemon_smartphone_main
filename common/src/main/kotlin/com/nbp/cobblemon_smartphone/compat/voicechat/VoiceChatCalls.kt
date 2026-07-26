package com.nbp.cobblemon_smartphone.compat.voicechat

import de.maxhenkel.voicechat.api.Group
import de.maxhenkel.voicechat.api.VoicechatServerApi
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * The only class besides the plugin that touches Simple Voice Chat types directly. Every entry
 * point assumes [VoiceChatBridge.isAvailable] is already true — callers must gate on it, so this
 * class is never classloaded when SVC is absent.
 *
 * A "call" is a temporary, hidden, non-persistent group of [Group.Type.NORMAL] — participants hear
 * each other at any distance (like a phone), while still hearing nearby players normally.
 */
object VoiceChatCalls {
    private val api: VoicechatServerApi
        get() = VoiceChatBridge.serverApi as VoicechatServerApi

    /** A live call's voice group plus the group each participant was in beforehand, for restoring. */
    class ActiveGroup(
        val groupId: UUID,
        val previous: Map<UUID, Group?>
    )

    /**
     * A player must be connected to voice chat (mic session established) to be put in a group.
     * Returns false when either side is not connected — the call cannot work in that case.
     */
    // Uses the UUID overload deliberately: getConnectionOf's other overload takes SVC's own
    // ServerPlayer wrapper, not Minecraft's.
    fun bothConnected(a: ServerPlayer, b: ServerPlayer): Boolean =
        api.getConnectionOf(a.uuid) != null && api.getConnectionOf(b.uuid) != null

    /**
     * Puts both players into a fresh call group, remembering the group each was in so it can be
     * restored on hang-up (SVC groups are exclusive — joining a call would otherwise silently drop
     * someone from their guild/party group). Returns null if either side isn't connected.
     */
    fun startGroup(a: ServerPlayer, b: ServerPlayer): ActiveGroup? {
        val connA = api.getConnectionOf(a.uuid) ?: return null
        val connB = api.getConnectionOf(b.uuid) ?: return null

        val group = api.groupBuilder()
            .setName(GROUP_NAME)
            .setType(Group.Type.NORMAL)
            .setHidden(true)
            .setPersistent(false)
            .build()

        val previous = mapOf(a.uuid to connA.group, b.uuid to connB.group)
        connA.group = group
        connB.group = group
        return ActiveGroup(group.id, previous)
    }

    /** Restores each still-connected participant to their prior group and deletes the call group. */
    fun endGroup(active: ActiveGroup, participants: List<ServerPlayer>) {
        participants.forEach { player ->
            val connection = api.getConnectionOf(player.uuid) ?: return@forEach
            connection.group = active.previous[player.uuid]
        }
        api.removeGroup(active.groupId)
    }

    private const val GROUP_NAME = "Call"
}
