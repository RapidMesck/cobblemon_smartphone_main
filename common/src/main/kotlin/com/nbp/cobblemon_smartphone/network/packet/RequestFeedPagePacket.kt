package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Asks the server for a page of the feed. [beforeId] is the id of the oldest post the client
 * already has (0 = give me the newest page); the server returns posts strictly older than it.
 * [followingOnly] filters posts only from users the requesting player is following (plus own posts).
 * Page size is decided by the server config, never by the client.
 */
class RequestFeedPagePacket(
    val beforeId: Long,
    val followingOnly: Boolean = false
) : CobblemonSmartphoneNetworkPacket<RequestFeedPagePacket> {
    companion object {
        val ID = smartphoneResource("request_feed_page")
        fun decode(buffer: RegistryFriendlyByteBuf): RequestFeedPagePacket {
            val beforeId = buffer.readVarLong()
            val followingOnly = if (buffer.isReadable) buffer.readBoolean() else false
            return RequestFeedPagePacket(beforeId, followingOnly)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(beforeId)
        buffer.writeBoolean(followingOnly)
    }
}
