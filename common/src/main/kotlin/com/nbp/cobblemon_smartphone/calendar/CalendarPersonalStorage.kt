package com.nbp.cobblemon_smartphone.calendar

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.util.PreferencesSaver
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerPlayer

data class PersonalCalendarData(
    val events: List<CalendarEvent> = emptyList(),
    val serverReminderIds: Set<String> = emptySet(),
    val seenServerEventIds: Set<String> = emptySet(),
    val use24Hour: Boolean = true
)

/**
 * Persists a player's personal calendar events and calendar preferences inside
 * [PreferencesSaver] NBT (`calendar` compound).
 */
object CalendarPersonalStorage {
    const val DEFAULT_MAX_PERSONAL_EVENTS = 50
    const val MAX_TRACKED_IDS = 200

    private const val KEY_EVENTS = "events"
    private const val KEY_SERVER_REMINDERS = "server_reminders"
    private const val KEY_SEEN_SERVER_EVENTS = "seen_server_events"
    private const val KEY_USE_24H = "use_24h"

    private fun maxEvents(): Int =
        runCatching { CobblemonSmartphone.config.calendar.maxPersonalEvents.coerceIn(1, 200) }
            .getOrDefault(DEFAULT_MAX_PERSONAL_EVENTS)

    fun read(player: ServerPlayer): PersonalCalendarData {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        if (!prefs.contains(PreferencesSaver.CALENDAR_KEY, Tag.TAG_COMPOUND.toInt())) {
            return PersonalCalendarData()
        }
        return decode(prefs.getCompound(PreferencesSaver.CALENDAR_KEY), maxEvents())
    }

    fun write(player: ServerPlayer, data: PersonalCalendarData) {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        prefs.put(PreferencesSaver.CALENDAR_KEY, encode(data, maxEvents()))
    }

    fun encode(data: PersonalCalendarData, maxEvents: Int = DEFAULT_MAX_PERSONAL_EVENTS): CompoundTag {
        val root = CompoundTag()
        val eventsList = ListTag()
        for (ev in CalendarEvent.sanitizeList(data.events, maxEvents, forceServerFlag = false)) {
            eventsList.add(ev.toNbt())
        }
        root.put(KEY_EVENTS, eventsList)

        val remList = ListTag()
        data.serverReminderIds.take(MAX_TRACKED_IDS).forEach { id ->
            val trimmed = id.trim().take(CalendarEvent.MAX_ID_LENGTH)
            if (trimmed.isNotEmpty()) remList.add(StringTag.valueOf(trimmed))
        }
        root.put(KEY_SERVER_REMINDERS, remList)

        val seenList = ListTag()
        data.seenServerEventIds.take(MAX_TRACKED_IDS).forEach { id ->
            val trimmed = id.trim().take(CalendarEvent.MAX_ID_LENGTH)
            if (trimmed.isNotEmpty()) seenList.add(StringTag.valueOf(trimmed))
        }
        root.put(KEY_SEEN_SERVER_EVENTS, seenList)

        root.putBoolean(KEY_USE_24H, data.use24Hour)
        return root
    }

    fun decode(root: CompoundTag, maxEvents: Int = DEFAULT_MAX_PERSONAL_EVENTS): PersonalCalendarData {
        val parsedEvents = mutableListOf<CalendarEvent>()
        val eventsTag = root.getList(KEY_EVENTS, Tag.TAG_COMPOUND.toInt())
        for (i in 0 until eventsTag.size) {
            CalendarEvent.fromNbt(eventsTag.getCompound(i))?.let { parsedEvents.add(it) }
        }

        val reminders = LinkedHashSet<String>()
        val remTag = root.getList(KEY_SERVER_REMINDERS, Tag.TAG_STRING.toInt())
        for (i in 0 until remTag.size) {
            val s = remTag.getString(i).trim().take(CalendarEvent.MAX_ID_LENGTH)
            if (s.isNotEmpty() && reminders.size < MAX_TRACKED_IDS) reminders.add(s)
        }

        val seen = LinkedHashSet<String>()
        val seenTag = root.getList(KEY_SEEN_SERVER_EVENTS, Tag.TAG_STRING.toInt())
        for (i in 0 until seenTag.size) {
            val s = seenTag.getString(i).trim().take(CalendarEvent.MAX_ID_LENGTH)
            if (s.isNotEmpty() && seen.size < MAX_TRACKED_IDS) seen.add(s)
        }

        val use24h = if (root.contains(KEY_USE_24H)) root.getBoolean(KEY_USE_24H) else true

        return PersonalCalendarData(
            events = CalendarEvent.sanitizeList(parsedEvents, maxEvents, forceServerFlag = false),
            serverReminderIds = reminders,
            seenServerEventIds = seen,
            use24Hour = use24h
        )
    }
}
