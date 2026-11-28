package com.nbp.cobblemon_smartphone.client

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.calendar.CalendarClockMode
import com.nbp.cobblemon_smartphone.calendar.CalendarEvent
import com.nbp.cobblemon_smartphone.calendar.CalendarPersonalStorage
import com.nbp.cobblemon_smartphone.calendar.CalendarTimeUtils
import com.nbp.cobblemon_smartphone.calendar.PersonalCalendarData
import com.nbp.cobblemon_smartphone.network.packet.DeleteServerCalendarEventPacket
import com.nbp.cobblemon_smartphone.network.packet.SavePersonalCalendarPacket
import com.nbp.cobblemon_smartphone.network.packet.UpsertServerCalendarEventPacket
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.time.LocalDate
import java.time.ZoneId

/**
 * Client-side cache & reminder engine for the Calendar app.
 *
 * Combines the player's personal events with synced global server events, computes
 * notification badges, and fires chat + sound reminders on client tick.
 */
object CalendarClientState {
    private var personalEvents: List<CalendarEvent> = emptyList()
    private var serverEvents: List<CalendarEvent> = emptyList()
    private var serverReminderIds: Set<String> = emptySet()
    private var seenServerEventIds: Set<String> = emptySet()
    private var use24Hour: Boolean = true
    private var canManageServerEvents: Boolean = false

    private val firedReminders = HashSet<String>()
    private var tickCounter = 0

    // Cached badge count updated every ~1s so badgeCount() is O(1) on render thread
    private var cachedBadgeCount = 0

    fun getPersonalEvents(): List<CalendarEvent> = personalEvents
    fun getServerEvents(): List<CalendarEvent> = serverEvents
    fun getAllEvents(): List<CalendarEvent> = CalendarEvent.sortEvents(personalEvents + serverEvents)
    fun isUse24Hour(): Boolean = use24Hour
    fun canManageServerEvents(): Boolean {
        val liveOp = runCatching {
            Minecraft.getInstance().player?.hasPermissions(
                com.nbp.cobblemon_smartphone.calendar.CalendarServerData.OP_PERMISSION_LEVEL
            )
        }.getOrNull()
        return liveOp ?: canManageServerEvents
    }

    fun isReminderEnabled(event: CalendarEvent): Boolean =
        if (event.isServerEvent) serverReminderIds.contains(event.id) else event.remind

    fun isServerEventUnseen(eventId: String): Boolean =
        !seenServerEventIds.contains(eventId)

    fun setPersonalFromServer(data: PersonalCalendarData, canManage: Boolean) {
        personalEvents = data.events
        serverReminderIds = data.serverReminderIds
        seenServerEventIds = data.seenServerEventIds
        use24Hour = data.use24Hour
        canManageServerEvents = canManage
        recomputeBadgeCount()
    }

    fun setServerEventsFromServer(events: List<CalendarEvent>) {
        serverEvents = CalendarEvent.sortEvents(events)
        val liveIds = serverEvents.mapTo(HashSet()) { it.id }
        val prunedRem = serverReminderIds.filterTo(LinkedHashSet()) { it in liveIds }
        val prunedSeen = seenServerEventIds.filterTo(LinkedHashSet()) { it in liveIds }
        if (prunedRem.size != serverReminderIds.size || prunedSeen.size != seenServerEventIds.size) {
            serverReminderIds = prunedRem
            seenServerEventIds = prunedSeen
        }
        recomputeBadgeCount()
    }

    fun upsertPersonal(event: CalendarEvent): Boolean {
        val maxEvents = runCatching { CobblemonSmartphone.config.calendar.maxPersonalEvents.coerceIn(1, 200) }
            .getOrDefault(CalendarPersonalStorage.DEFAULT_MAX_PERSONAL_EVENTS)
        val withoutOld = personalEvents.filterNot { it.id == event.id }
        if (withoutOld.size >= maxEvents && personalEvents.none { it.id == event.id }) {
            return false
        }
        val clean = CalendarEvent.sanitize(event, forceServerFlag = false) ?: return false
        personalEvents = CalendarEvent.sortEvents(withoutOld + clean)
        // Clear fired reminder keys if rescheduled
        firedReminders.removeIf { it.startsWith("${clean.id}:") }
        persistPersonal()
        recomputeBadgeCount()
        return true
    }

    fun deletePersonal(eventId: String) {
        personalEvents = personalEvents.filterNot { it.id == eventId }
        firedReminders.removeIf { it.startsWith("$eventId:") }
        persistPersonal()
        recomputeBadgeCount()
    }

    fun upsertServer(event: CalendarEvent, announceInSocial: Boolean = false) {
        if (!canManageServerEvents) return
        val clean = CalendarEvent.sanitize(event, forceServerFlag = true) ?: return
        // Optimistically mark seen for the creator
        seenServerEventIds = seenServerEventIds + clean.id
        firedReminders.removeIf { it.startsWith("${clean.id}:") }
        UpsertServerCalendarEventPacket(clean, announceInSocial).sendToServer()
        persistPersonal()
    }

    fun deleteServer(eventId: String) {
        if (!canManageServerEvents) return
        firedReminders.removeIf { it.startsWith("$eventId:") }
        DeleteServerCalendarEventPacket(eventId).sendToServer()
    }

    fun toggleReminder(event: CalendarEvent) {
        if (event.isServerEvent) {
            serverReminderIds = if (serverReminderIds.contains(event.id)) {
                serverReminderIds - event.id
            } else {
                serverReminderIds + event.id
            }
            persistPersonal()
        } else {
            personalEvents = personalEvents.map {
                if (it.id == event.id) it.copy(remind = !it.remind) else it
            }
            persistPersonal()
        }
        recomputeBadgeCount()
    }

    fun toggleUse24Hour() {
        use24Hour = !use24Hour
        persistPersonal()
    }

    fun markAllServerEventsSeen() {
        val currentServerIds = serverEvents.map { it.id }.toSet()
        if (currentServerIds.isEmpty()) return
        val merged = seenServerEventIds + currentServerIds
        if (merged != seenServerEventIds) {
            seenServerEventIds = merged
            persistPersonal()
            recomputeBadgeCount()
        }
    }

    fun badgeCount(): Int = cachedBadgeCount

    fun tick() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (!CobblemonSmartphone.config.features.enableCalendar) return

        tickCounter++
        if (tickCounter % 20 != 0) return

        val nowMs = System.currentTimeMillis()
        val leadMins = runCatching { CobblemonSmartphone.config.calendar.reminderLeadMinutes.coerceIn(0, 120) }
            .getOrDefault(15)
        val dayTime = level.dayTime

        val all = personalEvents + serverEvents
        for (ev in all) {
            if (!isReminderEnabled(ev)) continue
            when (ev.clockMode) {
                CalendarClockMode.REAL_TIME -> {
                    val trigger = CalendarTimeUtils.checkRealTimeReminderTrigger(ev.startEpochMillis, nowMs, leadMins)
                    if (trigger != null) {
                        val key = "${ev.id}:real:$trigger"
                        if (firedReminders.add(key)) {
                            val msg = if (trigger == "lead") {
                                Component.translatable(
                                    "message.cobblemon_smartphone.calendar.reminder_soon",
                                    ev.title,
                                    leadMins
                                )
                            } else {
                                Component.translatable(
                                    "message.cobblemon_smartphone.calendar.reminder_now",
                                    ev.title
                                )
                            }
                            player.displayClientMessage(msg, false)
                            player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.7f, 1.2f)
                        }
                    }
                }
                CalendarClockMode.WORLD_TIME -> {
                    if (CalendarTimeUtils.checkWorldTimeReminderTrigger(ev.worldDay, ev.worldPeriod, dayTime)) {
                        val key = "${ev.id}:world:${ev.worldDay}:${ev.worldPeriod.id}"
                        if (firedReminders.add(key)) {
                            val msg = Component.translatable(
                                "message.cobblemon_smartphone.calendar.reminder_world",
                                ev.worldDay + 1L,
                                ev.title
                            )
                            player.displayClientMessage(msg, false)
                            player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.7f, 1.2f)
                        }
                    }
                }
            }
        }

        recomputeBadgeCount()
    }

    private fun recomputeBadgeCount() {
        val level = runCatching { Minecraft.getInstance().level }.getOrNull()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val nowMs = System.currentTimeMillis()
        val currentWorldDay = level?.let { CalendarTimeUtils.worldDayFromDayTime(it.dayTime) }

        var count = 0
        // 1. Unseen server events
        for (ev in serverEvents) {
            if (!seenServerEventIds.contains(ev.id)) {
                count++
            }
        }
        // 2. Upcoming events happening today with reminder enabled
        for (ev in personalEvents + serverEvents) {
            if (!isReminderEnabled(ev)) continue
            when (ev.clockMode) {
                CalendarClockMode.REAL_TIME -> {
                    val evDate = CalendarTimeUtils.toLocalDate(ev.startEpochMillis, zone)
                    if (evDate == today && ev.startEpochMillis >= nowMs - 60_000L) {
                        count++
                    }
                }
                CalendarClockMode.WORLD_TIME -> {
                    if (currentWorldDay != null && ev.worldDay == currentWorldDay) {
                        count++
                    }
                }
            }
        }
        cachedBadgeCount = count
    }

    private fun persistPersonal() {
        // Prune tracked IDs for deleted server events if server events are loaded
        val data = PersonalCalendarData(
            events = personalEvents,
            serverReminderIds = serverReminderIds,
            seenServerEventIds = seenServerEventIds,
            use24Hour = use24Hour
        )
        SavePersonalCalendarPacket(data).sendToServer()
    }

    fun clear() {
        personalEvents = emptyList()
        serverEvents = emptyList()
        serverReminderIds = emptySet()
        seenServerEventIds = emptySet()
        use24Hour = true
        canManageServerEvents = false
        firedReminders.clear()
        tickCounter = 0
        cachedBadgeCount = 0
    }
}
