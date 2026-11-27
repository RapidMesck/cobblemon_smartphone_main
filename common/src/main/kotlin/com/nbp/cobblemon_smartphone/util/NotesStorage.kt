package com.nbp.cobblemon_smartphone.util

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

data class SmartphoneNote(
    val id: String,
    val title: String,
    val content: String,
    val pinned: Boolean = false,
    val colorTag: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Persists a player's smartphone notes inside the existing [PreferencesSaver] NBT compound
 * as a [ListTag] of [CompoundTag] entries, avoiding NBT string length limits while surviving
 * across sessions without a separate SavedData store.
 */
object NotesStorage {
    const val MAX_NOTES = 50
    const val MAX_ID_LENGTH = 64
    const val MAX_TITLE_LENGTH = 64
    const val MAX_CONTENT_LENGTH = 2000
    const val COLOR_TAG_COUNT = 6

    private const val KEY_ID = "id"
    private const val KEY_TITLE = "title"
    private const val KEY_CONTENT = "content"
    private const val KEY_PINNED = "pinned"
    private const val KEY_COLOR_TAG = "color_tag"
    private const val KEY_UPDATED_AT = "updated_at"

    fun read(player: ServerPlayer): List<SmartphoneNote> {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        if (!prefs.contains(PreferencesSaver.NOTES_KEY, Tag.TAG_LIST.toInt())) {
            return emptyList()
        }
        val listTag = prefs.getList(PreferencesSaver.NOTES_KEY, Tag.TAG_COMPOUND.toInt())
        return decodeFromListTag(listTag)
    }

    fun write(player: ServerPlayer, notes: List<SmartphoneNote>) {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        prefs.put(PreferencesSaver.NOTES_KEY, encodeToListTag(notes))
    }

    fun encodeToListTag(notes: List<SmartphoneNote>): ListTag {
        val sanitized = sanitize(notes)
        val listTag = ListTag()
        for (note in sanitized) {
            val tag = CompoundTag()
            tag.putString(KEY_ID, note.id)
            tag.putString(KEY_TITLE, note.title)
            tag.putString(KEY_CONTENT, note.content)
            tag.putBoolean(KEY_PINNED, note.pinned)
            tag.putInt(KEY_COLOR_TAG, note.colorTag)
            tag.putLong(KEY_UPDATED_AT, note.updatedAt)
            listTag.add(tag)
        }
        return listTag
    }

    fun decodeFromListTag(listTag: ListTag): List<SmartphoneNote> {
        val parsed = mutableListOf<SmartphoneNote>()
        for (i in 0 until listTag.size) {
            val tag = listTag.getCompound(i)
            val id = tag.getString(KEY_ID)
            val title = tag.getString(KEY_TITLE)
            val content = tag.getString(KEY_CONTENT)
            val pinned = tag.getBoolean(KEY_PINNED)
            val colorTag = tag.getInt(KEY_COLOR_TAG)
            val updatedAt = tag.getLong(KEY_UPDATED_AT)
            parsed.add(
                SmartphoneNote(
                    id = id,
                    title = title,
                    content = content,
                    pinned = pinned,
                    colorTag = colorTag,
                    updatedAt = updatedAt
                )
            )
        }
        return sanitize(parsed)
    }

    fun sanitize(notes: List<SmartphoneNote>): List<SmartphoneNote> {
        val seenIds = HashSet<String>()
        val result = ArrayList<SmartphoneNote>(minOf(notes.size, MAX_NOTES))
        for (raw in notes) {
            val title = raw.title.replace("\r\n", "\n").replace('\r', '\n').trim().take(MAX_TITLE_LENGTH)
            val content = raw.content.replace("\r\n", "\n").replace('\r', '\n').take(MAX_CONTENT_LENGTH)
            if (title.isBlank() && content.isBlank()) continue

            var id = raw.id.trim().take(MAX_ID_LENGTH)
            if (id.isBlank() || !seenIds.add(id)) {
                id = UUID.randomUUID().toString()
                seenIds.add(id)
            }
            result.add(
                SmartphoneNote(
                    id = id,
                    title = title,
                    content = content,
                    pinned = raw.pinned,
                    colorTag = raw.colorTag.coerceIn(0, COLOR_TAG_COUNT - 1),
                    updatedAt = raw.updatedAt.coerceAtLeast(0L)
                )
            )
        }
        return sortNotes(result).take(MAX_NOTES)
    }

    fun sortNotes(notes: List<SmartphoneNote>): List<SmartphoneNote> =
        notes.sortedWith(
            compareByDescending<SmartphoneNote> { it.pinned }
                .thenByDescending { it.updatedAt }
                .thenBy { it.id }
        )

    fun filterNotes(notes: List<SmartphoneNote>, query: String): List<SmartphoneNote> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return sortNotes(notes)
        return sortNotes(notes).filter { note ->
            note.title.lowercase().contains(q) || note.content.lowercase().contains(q)
        }
    }
}
