package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.social.DmMessage
import com.nbp.cobblemon_smartphone.social.DmThreadSummary
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/** [beforeTimestamp] of zero requests the newest conversation summaries. */
class RequestThreadListPacket(val beforeTimestamp: Long = 0L) : CobblemonSmartphoneNetworkPacket<RequestThreadListPacket> {
    companion object {
        val ID = smartphoneResource("request_thread_list")
        fun decode(buffer: RegistryFriendlyByteBuf) = RequestThreadListPacket(buffer.readLong())
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) { buffer.writeLong(beforeTimestamp) }
}

class ThreadListPacket(
    val threads: List<DmThreadSummary>,
    val hasMore: Boolean,
    val append: Boolean
) : CobblemonSmartphoneNetworkPacket<ThreadListPacket> {
    companion object {
        val ID = smartphoneResource("thread_list")
        fun decode(buffer: RegistryFriendlyByteBuf): ThreadListPacket {
            val count = buffer.readVarInt()
            return ThreadListPacket(
                (0 until count).map { DmThreadSummary.decode(buffer) },
                buffer.readBoolean(),
                buffer.readBoolean()
            )
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(threads.size)
        threads.forEach { it.encode(buffer) }
        buffer.writeBoolean(hasMore)
        buffer.writeBoolean(append)
    }
}

/** [beforeId] of 0 asks for the newest page; otherwise messages strictly older than it. */
class RequestThreadPagePacket(
    val otherUuid: UUID,
    val beforeId: Long
) : CobblemonSmartphoneNetworkPacket<RequestThreadPagePacket> {
    companion object {
        val ID = smartphoneResource("request_thread_page")
        fun decode(buffer: RegistryFriendlyByteBuf) =
            RequestThreadPagePacket(buffer.readUUID(), buffer.readVarLong())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(otherUuid)
        buffer.writeVarLong(beforeId)
    }
}

class ThreadPagePacket(
    val otherUuid: UUID,
    val otherName: String,
    val messages: List<DmMessage>,
    val hasMore: Boolean,
    val append: Boolean
) : CobblemonSmartphoneNetworkPacket<ThreadPagePacket> {
    companion object {
        val ID = smartphoneResource("thread_page")
        fun decode(buffer: RegistryFriendlyByteBuf): ThreadPagePacket {
            val otherUuid = buffer.readUUID()
            val otherName = buffer.readUtf()
            val count = buffer.readVarInt()
            val messages = (0 until count).map { DmMessage.decode(buffer) }
            return ThreadPagePacket(otherUuid, otherName, messages, buffer.readBoolean(), buffer.readBoolean())
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(otherUuid)
        buffer.writeUtf(otherName)
        buffer.writeVarInt(messages.size)
        messages.forEach { it.encode(buffer) }
        buffer.writeBoolean(hasMore)
        buffer.writeBoolean(append)
    }
}

class SendDmPacket(
    val requestId: Long,
    val targetUuid: UUID,
    val text: String,
    val attachSlot: Int = -1,
    val showDetails: Boolean = false,
    val showIvs: Boolean = false,
    val showEvs: Boolean = false,
    val showAbility: Boolean = false,
    val showNature: Boolean = false,
    val photoId: UUID? = null
) : CobblemonSmartphoneNetworkPacket<SendDmPacket> {
    companion object {
        val ID = smartphoneResource("send_dm")
        fun decode(buffer: RegistryFriendlyByteBuf) = SendDmPacket(
            buffer.readVarLong(), buffer.readUUID(), buffer.readUtf(4_096), buffer.readVarInt(),
            buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
            if (buffer.readBoolean()) buffer.readUUID() else null
        )
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
        buffer.writeUUID(targetUuid)
        buffer.writeUtf(text, 4_096)
        buffer.writeVarInt(attachSlot)
        buffer.writeBoolean(showDetails)
        buffer.writeBoolean(showIvs)
        buffer.writeBoolean(showEvs)
        buffer.writeBoolean(showAbility)
        buffer.writeBoolean(showNature)
        buffer.writeBoolean(photoId != null)
        photoId?.let(buffer::writeUUID)
    }
}

/**
 * Pushed to both participants when a DM is sent. [otherUuid]/[otherName] are the *counterpart from
 * the receiving client's perspective*, so the client can route it to the right thread without
 * knowing who it is.
 */
class NewDmPacket(
    val otherUuid: UUID,
    val otherName: String,
    val message: DmMessage
) : CobblemonSmartphoneNetworkPacket<NewDmPacket> {
    companion object {
        val ID = smartphoneResource("new_dm")
        fun decode(buffer: RegistryFriendlyByteBuf) =
            NewDmPacket(buffer.readUUID(), buffer.readUtf(), DmMessage.decode(buffer))
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(otherUuid)
        buffer.writeUtf(otherName)
        message.encode(buffer)
    }
}

class MarkThreadReadPacket(val otherUuid: UUID) : CobblemonSmartphoneNetworkPacket<MarkThreadReadPacket> {
    companion object {
        val ID = smartphoneResource("mark_thread_read")
        fun decode(buffer: RegistryFriendlyByteBuf) = MarkThreadReadPacket(buffer.readUUID())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(otherUuid)
    }
}

/** Total unread across all threads, for the home-screen badge. */
class SyncUnreadPacket(val total: Int) : CobblemonSmartphoneNetworkPacket<SyncUnreadPacket> {
    companion object {
        val ID = smartphoneResource("sync_unread")
        fun decode(buffer: RegistryFriendlyByteBuf) = SyncUnreadPacket(buffer.readVarInt())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(total)
    }
}
