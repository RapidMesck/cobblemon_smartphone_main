package com.nbp.cobblemon_smartphone.calendar

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

enum class CalendarClockMode(val id: String) {
    REAL_TIME("real"),
    WORLD_TIME("world");

    companion object {
        fun fromId(id: String): CalendarClockMode =
            entries.firstOrNull { it.id == id } ?: REAL_TIME
    }
}

/**
 * In-game day periods aligned with Minecraft's 24,000-tick day cycle
 * (0 = 06:00 sunrise, 6000 = 12:00 noon, 12000 = 18:00 sunset, 13500 = 19:30 night).
 */
enum class WorldTimePeriod(val id: String, val startTick: Int) {
    ALL_DAY("all_day", 0),
    MORNING("morning", 0),
    NOON("noon", 6000),
    EVENING("evening", 12000),
    NIGHT("night", 13500);

    companion object {
        fun fromId(id: String): WorldTimePeriod =
            entries.firstOrNull { it.id == id } ?: ALL_DAY
    }
}

enum class CalendarCategory(val id: String, val colorArgb: Int) {
    GENERAL("general", 0xFF3B82F6.toInt()),
    TOURNAMENT("tournament", 0xFFF59E0B.toInt()),
    EVENT("event", 0xFFA855F7.toInt()),
    REMINDER("reminder", 0xFF10B981.toInt()),
    BATTLE("battle", 0xFFEF4444.toInt()),
    HUNT("hunt", 0xFF06B6D4.toInt());

    companion object {
        fun fromId(id: String): CalendarCategory =
            entries.firstOrNull { it.id == id } ?: GENERAL
    }
}

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String = "",
    val clockMode: CalendarClockMode = CalendarClockMode.REAL_TIME,
    /** UTC epoch milliseconds (used when [clockMode] == [CalendarClockMode.REAL_TIME]). */
    val startEpochMillis: Long = 0L,
    /** 0-based Minecraft world day (`dayTime / 24000L`, used when [clockMode] == [CalendarClockMode.WORLD_TIME]). */
    val worldDay: Long = 0L,
    val worldPeriod: WorldTimePeriod = WorldTimePeriod.ALL_DAY,
    val category: CalendarCategory = CalendarCategory.GENERAL,
    val isServerEvent: Boolean = false,
    val authorName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Personal reminder toggle (for server events, per-player reminders are tracked in player prefs). */
    val remind: Boolean = true
) {
    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putString(KEY_ID, id)
        tag.putString(KEY_TITLE, title)
        tag.putString(KEY_DESCRIPTION, description)
        tag.putString(KEY_CLOCK_MODE, clockMode.id)
        tag.putLong(KEY_START_EPOCH, startEpochMillis)
        tag.putLong(KEY_WORLD_DAY, worldDay)
        tag.putString(KEY_WORLD_PERIOD, worldPeriod.id)
        tag.putString(KEY_CATEGORY, category.id)
        tag.putBoolean(KEY_IS_SERVER, isServerEvent)
        tag.putString(KEY_AUTHOR, authorName)
        tag.putLong(KEY_CREATED_AT, createdAt)
        tag.putBoolean(KEY_REMIND, remind)
        return tag
    }

    fun writeToBuf(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(id, MAX_ID_LENGTH)
        buf.writeUtf(title, MAX_TITLE_LENGTH)
        buf.writeUtf(description, MAX_DESCRIPTION_LENGTH)
        buf.writeUtf(clockMode.id, 16)
        buf.writeLong(startEpochMillis)
        buf.writeLong(worldDay)
        buf.writeUtf(worldPeriod.id, 16)
        buf.writeUtf(category.id, 24)
        buf.writeBoolean(isServerEvent)
        buf.writeUtf(authorName, MAX_AUTHOR_LENGTH)
        buf.writeLong(createdAt)
        buf.writeBoolean(remind)
    }

    companion object {
        const val MAX_ID_LENGTH = 64
        const val MAX_TITLE_LENGTH = 64
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_AUTHOR_LENGTH = 48
        /** 2100-01-01T00:00:00Z — keeps all Instant/LocalDate conversions safe from DateTimeException. */
        const val MAX_SAFE_EPOCH_MILLIS = 4_102_444_800_000L
        /** Prevents Long overflow when formatting 1-based world days (`worldDay + 1L`). */
        const val MAX_SAFE_WORLD_DAY = 1_000_000_000L

        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_CLOCK_MODE = "clock_mode"
        private const val KEY_START_EPOCH = "start_epoch"
        private const val KEY_WORLD_DAY = "world_day"
        private const val KEY_WORLD_PERIOD = "world_period"
        private const val KEY_CATEGORY = "category"
        private const val KEY_IS_SERVER = "is_server"
        private const val KEY_AUTHOR = "author"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_REMIND = "remind"

        fun fromNbt(tag: CompoundTag): CalendarEvent? {
            val title = tag.getString(KEY_TITLE).trim()
            if (title.isBlank()) return null
            val id = tag.getString(KEY_ID).trim().ifBlank { UUID.randomUUID().toString() }
            return CalendarEvent(
                id = id.take(MAX_ID_LENGTH),
                title = title.take(MAX_TITLE_LENGTH),
                description = tag.getString(KEY_DESCRIPTION).take(MAX_DESCRIPTION_LENGTH),
                clockMode = CalendarClockMode.fromId(tag.getString(KEY_CLOCK_MODE)),
                startEpochMillis = tag.getLong(KEY_START_EPOCH).coerceIn(0L, MAX_SAFE_EPOCH_MILLIS),
                worldDay = tag.getLong(KEY_WORLD_DAY).coerceIn(0L, MAX_SAFE_WORLD_DAY),
                worldPeriod = WorldTimePeriod.fromId(tag.getString(KEY_WORLD_PERIOD)),
                category = CalendarCategory.fromId(tag.getString(KEY_CATEGORY)),
                isServerEvent = tag.getBoolean(KEY_IS_SERVER),
                authorName = tag.getString(KEY_AUTHOR).take(MAX_AUTHOR_LENGTH),
                createdAt = tag.getLong(KEY_CREATED_AT).coerceAtLeast(0L),
                remind = if (tag.contains(KEY_REMIND)) tag.getBoolean(KEY_REMIND) else true
            )
        }

        fun readFromBuf(buf: RegistryFriendlyByteBuf): CalendarEvent {
            return CalendarEvent(
                id = buf.readUtf(MAX_ID_LENGTH),
                title = buf.readUtf(MAX_TITLE_LENGTH),
                description = buf.readUtf(MAX_DESCRIPTION_LENGTH),
                clockMode = CalendarClockMode.fromId(buf.readUtf(16)),
                startEpochMillis = buf.readLong().coerceIn(0L, MAX_SAFE_EPOCH_MILLIS),
                worldDay = buf.readLong().coerceIn(0L, MAX_SAFE_WORLD_DAY),
                worldPeriod = WorldTimePeriod.fromId(buf.readUtf(16)),
                category = CalendarCategory.fromId(buf.readUtf(24)),
                isServerEvent = buf.readBoolean(),
                authorName = buf.readUtf(MAX_AUTHOR_LENGTH),
                createdAt = buf.readLong().coerceAtLeast(0L),
                remind = buf.readBoolean()
            )
        }

        fun sanitize(event: CalendarEvent, forceServerFlag: Boolean? = null): CalendarEvent? {
            val title = event.title.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').trim().take(MAX_TITLE_LENGTH)
            if (title.isBlank()) return null
            val desc = event.description.replace("\r\n", "\n").replace('\r', '\n').trim().take(MAX_DESCRIPTION_LENGTH)
            val id = event.id.trim().take(MAX_ID_LENGTH).ifBlank { UUID.randomUUID().toString() }
            return event.copy(
                id = id,
                title = title,
                description = desc,
                startEpochMillis = event.startEpochMillis.coerceIn(0L, MAX_SAFE_EPOCH_MILLIS),
                worldDay = event.worldDay.coerceIn(0L, MAX_SAFE_WORLD_DAY),
                isServerEvent = forceServerFlag ?: event.isServerEvent,
                authorName = event.authorName.trim().take(MAX_AUTHOR_LENGTH),
                createdAt = event.createdAt.coerceAtLeast(0L)
            )
        }

        fun sanitizeList(
            events: List<CalendarEvent>,
            maxCount: Int,
            forceServerFlag: Boolean? = null
        ): List<CalendarEvent> {
            val seen = HashSet<String>()
            val out = ArrayList<CalendarEvent>(minOf(events.size, maxCount))
            for (raw in events) {
                val clean = sanitize(raw, forceServerFlag) ?: continue
                val unique = if (seen.add(clean.id)) clean else clean.copy(id = UUID.randomUUID().toString()).also { seen.add(it.id) }
                out.add(unique)
                if (out.size >= maxCount) break
            }
            return sortEvents(out)
        }

        fun sortEvents(events: List<CalendarEvent>): List<CalendarEvent> =
            events.sortedWith(
                compareBy<CalendarEvent> { it.clockMode.ordinal }
                    .thenBy { if (it.clockMode == CalendarClockMode.REAL_TIME) it.startEpochMillis else it.worldDay }
                    .thenBy { if (it.clockMode == CalendarClockMode.WORLD_TIME) it.worldPeriod.startTick else 0 }
                    .thenByDescending { it.isServerEvent }
                    .thenBy { it.title.lowercase() }
            )
    }
}
