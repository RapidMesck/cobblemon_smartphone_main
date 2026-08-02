package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.MutedPlayers
import com.nbp.cobblemon_smartphone.client.social.SocialDmCache
import com.nbp.cobblemon_smartphone.client.social.SocialMute
import com.nbp.cobblemon_smartphone.network.packet.NewDmPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncUnreadPacket
import com.nbp.cobblemon_smartphone.network.packet.ThreadListPacket
import com.nbp.cobblemon_smartphone.network.packet.ThreadPagePacket
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object ThreadListHandler : ClientNetworkPacketHandler<ThreadListPacket> {
    override fun handle(packet: ThreadListPacket, client: Minecraft) {
        SocialDmCache.acceptThreadList(packet.threads, packet.hasMore, packet.append)
    }
}

object ThreadPageHandler : ClientNetworkPacketHandler<ThreadPagePacket> {
    override fun handle(packet: ThreadPagePacket, client: Minecraft) {
        SocialDmCache.acceptThreadPage(
            packet.otherUuid,
            packet.otherName,
            packet.messages,
            packet.hasMore,
            packet.append
        )
    }
}

object SyncUnreadHandler : ClientNetworkPacketHandler<SyncUnreadPacket> {
    override fun handle(packet: SyncUnreadPacket, client: Minecraft) {
        SocialDmCache.setUnreadTotal(packet.total)
    }
}

/**
 * Decides, purely on the client, whether an incoming DM lands in the open conversation or becomes
 * a notification — which is what lets the server push unconditionally without tracking sessions.
 */
object NewDmHandler : ClientNetworkPacketHandler<NewDmPacket> {
    override fun handle(packet: NewDmPacket, client: Minecraft) {
        val player = client.player ?: return
        val landedInOpenThread = SocialDmCache.acceptIncoming(packet.otherUuid, packet.message)

        // Our own echo needs no notification, and neither does a thread we're already reading.
        if (landedInOpenThread || packet.message.senderUuid == player.uuid) return

        // The badge updates regardless — muting only suppresses the sound + action bar, so the
        // message is never lost, just not alerted. Global Do Not Disturb or having muted this
        // specific sender both silence the alert.
        SocialDmCache.setUnreadTotal(SocialDmCache.unreadTotal + 1)
        if (SocialMute.muted || MutedPlayers.contains(packet.message.senderUuid)) return

        player.playSound(CobblemonSounds.POKEDEX_OPEN, 0.4f, 1.4f)
        player.displayClientMessage(
            Component.translatable("message.nbp.social.new_dm", packet.otherName),
            true
        )
    }
}
