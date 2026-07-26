package com.nbp.cobblemon_smartphone.social

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import java.util.UUID

/**
 * A 1:1 conversation.
 *
 * Read state is stored per participant as the id of the last message they have seen, so unread
 * counts survive relogs and are computed rather than incremented (no drift).
 *
 * [displayNames] caches each participant's username so the thread list can render a counterpart
 * who is currently offline.
 */
class DmThread(val key: ThreadKey) {

    val messages = mutableListOf<DmMessage>()
    private val lastReadIds = mutableMapOf<UUID, Long>()
    private val displayNames = mutableMapOf<UUID, String>()

    fun lastMessage(): DmMessage? = messages.lastOrNull()

    fun displayNameOf(uuid: UUID): String = displayNames[uuid] ?: "?"

    fun rememberName(uuid: UUID, name: String) {
        displayNames[uuid] = name
    }

    /** Messages the viewer has not seen. Own messages never count as unread. */
    fun unreadCountFor(viewer: UUID): Int {
        val lastRead = lastReadIds[viewer] ?: 0L
        return messages.count { it.id > lastRead && it.senderUuid != viewer }
    }

    /** Returns true when the read marker actually moved. */
    fun markRead(viewer: UUID): Boolean {
        val newest = messages.lastOrNull()?.id ?: return false
        if (lastReadIds[viewer] == newest) return false
        lastReadIds[viewer] = newest
        return true
    }

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putString(KEY_KEY, key.serialize())

        val messageList = ListTag()
        messages.forEach { messageList.add(it.toNbt()) }
        tag.put(MESSAGES_KEY, messageList)

        val readTag = CompoundTag()
        lastReadIds.forEach { (uuid, id) -> readTag.putLong(uuid.toString(), id) }
        tag.put(LAST_READ_KEY, readTag)

        val namesTag = CompoundTag()
        displayNames.forEach { (uuid, name) -> namesTag.putString(uuid.toString(), name) }
        tag.put(NAMES_KEY, namesTag)
        return tag
    }

    companion object {
        private const val KEY_KEY = "key"
        private const val MESSAGES_KEY = "messages"
        private const val LAST_READ_KEY = "last_read"
        private const val NAMES_KEY = "names"

        fun fromNbt(tag: CompoundTag): DmThread? {
            val key = ThreadKey.deserialize(tag.getString(KEY_KEY)) ?: return null
            val thread = DmThread(key)

            tag.getList(MESSAGES_KEY, Tag.TAG_COMPOUND.toInt()).forEach { element ->
                DmMessage.fromNbt(element as CompoundTag)?.let { thread.messages.add(it) }
            }

            val readTag = tag.getCompound(LAST_READ_KEY)
            readTag.allKeys.forEach { raw ->
                runCatching { UUID.fromString(raw) }.getOrNull()?.let { uuid ->
                    thread.lastReadIds[uuid] = readTag.getLong(raw)
                }
            }

            val namesTag = tag.getCompound(NAMES_KEY)
            namesTag.allKeys.forEach { raw ->
                runCatching { UUID.fromString(raw) }.getOrNull()?.let { uuid ->
                    thread.displayNames[uuid] = namesTag.getString(raw)
                }
            }
            return thread
        }
    }
}
