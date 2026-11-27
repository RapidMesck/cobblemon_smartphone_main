package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.NotesStorage
import com.nbp.cobblemon_smartphone.util.SmartphoneNote
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SyncNotesPacket(val notes: List<SmartphoneNote>) : CobblemonSmartphoneNetworkPacket<SyncNotesPacket> {
    companion object {
        val ID = smartphoneResource("sync_notes")

        fun decode(buffer: RegistryFriendlyByteBuf): SyncNotesPacket {
            val count = buffer.readVarInt().coerceIn(0, NotesStorage.MAX_NOTES)
            val list = (0 until count).map {
                SmartphoneNote(
                    id = buffer.readUtf(NotesStorage.MAX_ID_LENGTH),
                    title = buffer.readUtf(NotesStorage.MAX_TITLE_LENGTH),
                    content = buffer.readUtf(NotesStorage.MAX_CONTENT_LENGTH),
                    pinned = buffer.readBoolean(),
                    colorTag = buffer.readVarInt().coerceIn(0, NotesStorage.COLOR_TAG_COUNT - 1),
                    updatedAt = buffer.readLong()
                )
            }
            return SyncNotesPacket(list)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        val sanitized = NotesStorage.sanitize(notes)
        buffer.writeVarInt(sanitized.size)
        for (note in sanitized) {
            buffer.writeUtf(note.id, NotesStorage.MAX_ID_LENGTH)
            buffer.writeUtf(note.title, NotesStorage.MAX_TITLE_LENGTH)
            buffer.writeUtf(note.content, NotesStorage.MAX_CONTENT_LENGTH)
            buffer.writeBoolean(note.pinned)
            buffer.writeVarInt(note.colorTag)
            buffer.writeLong(note.updatedAt)
        }
    }
}
