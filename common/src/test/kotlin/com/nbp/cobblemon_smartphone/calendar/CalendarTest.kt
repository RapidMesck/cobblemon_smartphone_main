package com.nbp.cobblemon_smartphone.calendar

import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class CalendarTest {

    @Test
    fun `real time event converts across timezones including day rollover`() {
        val saoPaulo = ZoneId.of("America/Sao_Paulo") // UTC-3
        val london = ZoneId.of("UTC")
        val tokyo = ZoneId.of("Asia/Tokyo") // UTC+9

        // Admin in Sao Paulo schedules a tournament on 2026-12-15 at 23:00 local time
        val utcMillis = CalendarTimeUtils.localDateTimeToUtcMillis(2026, 12, 15, 23, 0, saoPaulo)

        assertEquals(LocalDate.of(2026, 12, 15), CalendarTimeUtils.toLocalDate(utcMillis, saoPaulo))
        assertEquals("23:00", CalendarTimeUtils.formatLocalTime(utcMillis, use24Hour = true, zone = saoPaulo))
        assertEquals("11:00 PM", CalendarTimeUtils.formatLocalTime(utcMillis, use24Hour = false, zone = saoPaulo))

        // Player in UTC sees it on the NEXT day at 02:00
        assertEquals(LocalDate.of(2026, 12, 16), CalendarTimeUtils.toLocalDate(utcMillis, london))
        assertEquals("02:00", CalendarTimeUtils.formatLocalTime(utcMillis, use24Hour = true, zone = london))

        // Player in Tokyo sees it on 2026-12-16 at 11:00
        assertEquals(LocalDate.of(2026, 12, 16), CalendarTimeUtils.toLocalDate(utcMillis, tokyo))
        assertEquals("11:00", CalendarTimeUtils.formatLocalTime(utcMillis, use24Hour = true, zone = tokyo))

        // Grouping by local date places the event on the viewer's local day
        val ev = CalendarEvent(
            id = "tourney-1",
            title = "Weekend Cup",
            clockMode = CalendarClockMode.REAL_TIME,
            startEpochMillis = utcMillis,
            isServerEvent = true
        )
        assertTrue(CalendarTimeUtils.groupRealEventsByLocalDate(listOf(ev), saoPaulo).containsKey(LocalDate.of(2026, 12, 15)))
        assertTrue(CalendarTimeUtils.groupRealEventsByLocalDate(listOf(ev), london).containsKey(LocalDate.of(2026, 12, 16)))
    }

    @Test
    fun `relative time formats Discord-like strings`() {
        val base = 1_700_000_000_000L
        assertEquals("now", CalendarTimeUtils.formatRelativeRealTime(base + 20_000L, base))
        assertEquals("in 45m", CalendarTimeUtils.formatRelativeRealTime(base + 45 * 60_000L, base))
        assertEquals("in 2h 15m", CalendarTimeUtils.formatRelativeRealTime(base + (2 * 60 + 15) * 60_000L, base))
        assertEquals("in 3d 2h", CalendarTimeUtils.formatRelativeRealTime(base + (3 * 24 + 2) * 3_600_000L, base))
        assertEquals("30m ago", CalendarTimeUtils.formatRelativeRealTime(base - 30 * 60_000L, base))
    }

    @Test
    fun `world time day, clock, and 8-day lunar cycle calculations`() {
        assertEquals(0L, CalendarTimeUtils.worldDayFromDayTime(0L))
        assertEquals(0L, CalendarTimeUtils.worldDayFromDayTime(23999L))
        assertEquals(1L, CalendarTimeUtils.worldDayFromDayTime(24000L))
        assertEquals("06:00", CalendarTimeUtils.formatWorldClock(0L))
        assertEquals("12:00", CalendarTimeUtils.formatWorldClock(6000L))
        assertEquals("18:00", CalendarTimeUtils.formatWorldClock(12000L))
        assertEquals("00:00", CalendarTimeUtils.formatWorldClock(18000L))

        // 8-day lunar cycle: 0 = Full Moon, 4 = New Moon, wraps at 8
        assertEquals(0, CalendarTimeUtils.moonPhaseIndex(0L))
        assertEquals(4, CalendarTimeUtils.moonPhaseIndex(4L))
        assertEquals(0, CalendarTimeUtils.moonPhaseIndex(8L))
        assertEquals(3, CalendarTimeUtils.moonPhaseIndex(11L))
    }

    @Test
    fun `reminder trigger checks for real time and world time`() {
        val eventTime = 1_700_000_000_000L
        assertEquals("lead", CalendarTimeUtils.checkRealTimeReminderTrigger(eventTime, eventTime - 15 * 60_000L, 15))
        assertEquals("start", CalendarTimeUtils.checkRealTimeReminderTrigger(eventTime, eventTime, 15))
        assertNull(CalendarTimeUtils.checkRealTimeReminderTrigger(eventTime, eventTime - 60 * 60_000L, 15))

        // World time trigger on day 5, NIGHT (tick 13500)
        assertTrue(CalendarTimeUtils.checkWorldTimeReminderTrigger(5L, WorldTimePeriod.NIGHT, 5L * 24000L + 13550L))
        assertFalse(CalendarTimeUtils.checkWorldTimeReminderTrigger(5L, WorldTimePeriod.NIGHT, 5L * 24000L + 6000L))
        assertFalse(CalendarTimeUtils.checkWorldTimeReminderTrigger(5L, WorldTimePeriod.NIGHT, 4L * 24000L + 13550L))
    }

    @Test
    fun `personal calendar storage roundtrips via NBT and enforces personal flag`() {
        val original = PersonalCalendarData(
            events = listOf(
                CalendarEvent(
                    id = "p1",
                    title = "Breed Eevee",
                    description = "Use Destiny Knot",
                    clockMode = CalendarClockMode.WORLD_TIME,
                    worldDay = 12L,
                    worldPeriod = WorldTimePeriod.NIGHT,
                    category = CalendarCategory.HUNT,
                    isServerEvent = true, // must be forced to false for personal storage
                    remind = true
                )
            ),
            serverReminderIds = setOf("srv-1", "srv-2"),
            seenServerEventIds = setOf("srv-1"),
            use24Hour = false
        )

        val tag: CompoundTag = CalendarPersonalStorage.encode(original)
        val decoded = CalendarPersonalStorage.decode(tag)

        assertEquals(1, decoded.events.size)
        assertEquals("Breed Eevee", decoded.events[0].title)
        assertEquals(12L, decoded.events[0].worldDay)
        assertEquals(WorldTimePeriod.NIGHT, decoded.events[0].worldPeriod)
        assertEquals(CalendarCategory.HUNT, decoded.events[0].category)
        assertFalse(decoded.events[0].isServerEvent)
        assertEquals(setOf("srv-1", "srv-2"), decoded.serverReminderIds)
        assertEquals(setOf("srv-1"), decoded.seenServerEventIds)
        assertFalse(decoded.use24Hour)
    }

    @Test
    fun `server calendar data upsert, remove, and NBT load`() {
        val data = CalendarServerData()
        val saved = data.upsertEvent(
            CalendarEvent(
                id = "srv-tourney",
                title = "Spring Tournament",
                clockMode = CalendarClockMode.REAL_TIME,
                startEpochMillis = 1_800_000_000_000L,
                category = CalendarCategory.TOURNAMENT,
                isServerEvent = false // must be forced to true
            ),
            authorName = "Admin"
        )
        assertNotNull(saved)
        assertTrue(saved!!.isServerEvent)
        assertEquals("Admin", saved.authorName)

        // Update existing event
        data.upsertEvent(saved.copy(title = "Spring Cup Finals"), authorName = "Admin")
        assertEquals(1, data.listEvents().size)
        assertEquals("Spring Cup Finals", data.getById("srv-tourney")?.title)

        // Remove event
        assertTrue(data.removeEvent("srv-tourney"))
        assertTrue(data.listEvents().isEmpty())
    }

    @Test
    fun `extreme timestamps and world days are safely clamped`() {
        val extreme = CalendarEvent.sanitize(
            CalendarEvent(
                id = "ext",
                title = "Far Future",
                clockMode = CalendarClockMode.REAL_TIME,
                startEpochMillis = Long.MAX_VALUE,
                worldDay = Long.MAX_VALUE
            )
        )
        assertNotNull(extreme)
        assertEquals(CalendarEvent.MAX_SAFE_EPOCH_MILLIS, extreme!!.startEpochMillis)
        assertEquals(CalendarEvent.MAX_SAFE_WORLD_DAY, extreme.worldDay)
        // Must format without throwing DateTimeException
        assertNotNull(CalendarTimeUtils.toLocalDate(extreme.startEpochMillis))
        assertNotNull(CalendarTimeUtils.formatUtcDateTime(extreme.startEpochMillis))
    }
}
