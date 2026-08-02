package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.social.DmThreadSummary
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SocialMutationResultPacket(
    val requestId: Long,
    val status: Status
) : CobblemonSmartphoneNetworkPacket<SocialMutationResultPacket> {
    enum class Status { SUCCESS, DISABLED, BANNED, RATE_LIMITED, INVALID_TARGET, EMPTY, ERROR;
        companion object { fun byId(id: Int) = entries.getOrElse(id) { ERROR } }
    }
    companion object {
        val ID = smartphoneResource("social_mutation_result")
        fun decode(buffer: RegistryFriendlyByteBuf) =
            SocialMutationResultPacket(buffer.readVarLong(), Status.byId(buffer.readVarInt()))
    }
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
        buffer.writeVarInt(status.ordinal)
    }
}

class FeedPostUpdatePacket(
    val deletedId: Long,
    val post: SocialPostView?
) : CobblemonSmartphoneNetworkPacket<FeedPostUpdatePacket> {
    companion object {
        val ID = smartphoneResource("feed_post_update")
        fun decode(buffer: RegistryFriendlyByteBuf): FeedPostUpdatePacket {
            val deletedId = buffer.readVarLong()
            return FeedPostUpdatePacket(deletedId, if (buffer.readBoolean()) SocialPostView.decode(buffer) else null)
        }
    }
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(deletedId)
        buffer.writeBoolean(post != null)
        post?.encode(buffer)
    }
}

class ThreadSummaryUpdatePacket(
    val summary: DmThreadSummary
) : CobblemonSmartphoneNetworkPacket<ThreadSummaryUpdatePacket> {
    companion object {
        val ID = smartphoneResource("thread_summary_update")
        fun decode(buffer: RegistryFriendlyByteBuf) = ThreadSummaryUpdatePacket(DmThreadSummary.decode(buffer))
    }
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) = summary.encode(buffer)
}
