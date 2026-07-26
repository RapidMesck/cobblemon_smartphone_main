package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * A page of feed posts, newest-first. [append] is false when this is a fresh first page (client
 * replaces its cache) and true when it is a continuation (client appends).
 */
class FeedPagePacket(
    val posts: List<SocialPostView>,
    val hasMore: Boolean,
    val append: Boolean
) : CobblemonSmartphoneNetworkPacket<FeedPagePacket> {
    companion object {
        val ID = smartphoneResource("feed_page")
        fun decode(buffer: RegistryFriendlyByteBuf): FeedPagePacket {
            val count = buffer.readVarInt()
            val posts = (0 until count).map { SocialPostView.decode(buffer) }
            return FeedPagePacket(posts, buffer.readBoolean(), buffer.readBoolean())
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(posts.size)
        posts.forEach { it.encode(buffer) }
        buffer.writeBoolean(hasMore)
        buffer.writeBoolean(append)
    }
}
