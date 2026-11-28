package com.nbp.cobblemon_smartphone.calendar

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData

/**
 * World-level store for global server calendar events, persisted in `data/cobblemon_smartphone_calendar.dat`.
 */
class CalendarServerData : SavedData() {

    private val events = mutableListOf<CalendarEvent>()

    fun listEvents(): List<CalendarEvent> = CalendarEvent.sortEvents(events)

    fun getById(id: String): CalendarEvent? = events.firstOrNull { it.id == id }

    fun upsertEvent(raw: CalendarEvent, authorName: String): CalendarEvent? {
        val maxEvents = runCatching { CobblemonSmartphone.config.calendar.maxServerEvents.coerceIn(1, 500) }
            .getOrDefault(DEFAULT_MAX_SERVER_EVENTS)
        val clean = CalendarEvent.sanitize(
            raw.copy(authorName = authorName.ifBlank { raw.authorName }),
            forceServerFlag = true
        ) ?: return null

        val existingIdx = events.indexOfFirst { it.id == clean.id }
        if (existingIdx >= 0) {
            events[existingIdx] = clean
        } else {
            if (events.size >= maxEvents) return null
            events.add(clean)
        }
        setDirty()
        return clean
    }

    fun removeEvent(id: String): Boolean {
        val removed = events.removeIf { it.id == id }
        if (removed) setDirty()
        return removed
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        for (ev in events) {
            list.add(ev.toNbt())
        }
        tag.put(EVENTS_KEY, list)
        return tag
    }

    companion object {
        const val NAME = "cobblemon_smartphone_calendar"
        const val OP_PERMISSION_LEVEL = 2
        const val DEFAULT_MAX_SERVER_EVENTS = 100
        private const val EVENTS_KEY = "events"

        fun load(tag: CompoundTag): CalendarServerData {
            val data = CalendarServerData()
            val list = tag.getList(EVENTS_KEY, Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                CalendarEvent.fromNbt(list.getCompound(i))?.let { ev ->
                    data.events.add(ev.copy(isServerEvent = true))
                }
            }
            return data
        }

        private val factory: Factory<CalendarServerData>
            get() = Factory({ CalendarServerData() }, { tag, _ -> load(tag) }, null)

        fun get(server: MinecraftServer): CalendarServerData =
            server.overworld().dataStorage.computeIfAbsent(factory, NAME)
    }
}
