package com.nbp.cobblemon_smartphone.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Pure time/timezone/moon-phase helpers for the Calendar app.
 *
 * All real-time events are stored in UTC epoch millis and converted to the viewer's [ZoneId]
 * (defaulting to [ZoneId.systemDefault]) at render time — matching Discord's dynamic timestamps.
 */
object CalendarTimeUtils {
    const val TICKS_PER_DAY = 24000L
    const val MOON_CYCLE_DAYS = 8

    private val TIME_24H: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val TIME_12H: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val DATE_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val DATE_TIME_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    fun toLocalDateTime(utcEpochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        Instant.ofEpochMilli(utcEpochMillis).atZone(zone)

    fun toLocalDate(utcEpochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        toLocalDateTime(utcEpochMillis, zone).toLocalDate()

    fun localDateTimeToUtcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val safeDay = day.coerceIn(1, LocalDate.of(year, month, 1).lengthOfMonth())
        val ldt = LocalDateTime.of(year, month, safeDay, hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        return ldt.atZone(zone).toInstant().toEpochMilli()
    }

    fun formatLocalTime(
        utcEpochMillis: Long,
        use24Hour: Boolean,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val zdt = toLocalDateTime(utcEpochMillis, zone)
        return zdt.format(if (use24Hour) TIME_24H else TIME_12H)
    }

    fun formatLocalDate(
        utcEpochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): String = toLocalDate(utcEpochMillis, zone).format(DATE_SHORT)

    fun formatUtcDateTime(utcEpochMillis: Long): String =
        Instant.ofEpochMilli(utcEpochMillis).atOffset(ZoneOffset.UTC).format(DATE_TIME_UTC) + " UTC"

    fun formatZoneLabel(zone: ZoneId = ZoneId.systemDefault(), nowMillis: Long = System.currentTimeMillis()): String {
        val offset = zone.rules.getOffset(Instant.ofEpochMilli(nowMillis))
        val offsetStr = if (offset == ZoneOffset.UTC) "UTC+0" else "UTC${offset.id.removeSuffix(":00")}"
        return "$offsetStr • ${zone.id}"
    }

    /**
     * Returns a compact Discord-like relative time string (e.g. "in 2h 15m", "now", "3d ago").
     */
    fun formatRelativeRealTime(targetUtcMillis: Long, nowUtcMillis: Long): String {
        val diffMillis = targetUtcMillis - nowUtcMillis
        val diffMinutes = diffMillis / 60_000L
        if (abs(diffMinutes) < 1L) return "now"

        val future = diffMinutes > 0
        val totalMins = abs(diffMinutes)
        val days = totalMins / (60 * 24)
        val hours = (totalMins % (60 * 24)) / 60
        val mins = totalMins % 60

        val body = when {
            days >= 1L && hours > 0L -> "${days}d ${hours}h"
            days >= 1L -> "${days}d"
            hours >= 1L && mins > 0L -> "${hours}h ${mins}m"
            hours >= 1L -> "${hours}h"
            else -> "${mins}m"
        }
        return if (future) "in $body" else "$body ago"
    }

    // --- Minecraft World Time helpers ---

    /** 0-based world day from `level.dayTime`. */
    fun worldDayFromDayTime(dayTime: Long): Long =
        (dayTime.coerceAtLeast(0L)) / TICKS_PER_DAY

    /** Tick of day in `0..23999`. */
    fun tickOfDay(dayTime: Long): Int =
        ((dayTime % TICKS_PER_DAY) + TICKS_PER_DAY).toInt() % TICKS_PER_DAY.toInt()

    /**
     * Vanilla Minecraft moon phase index `0..7` (`0 = Full Moon`, `4 = New Moon`).
     * Matches `DimensionType.moonPhase(dayTime)`.
     */
    fun moonPhaseIndex(worldDay: Long): Int =
        (((worldDay % MOON_CYCLE_DAYS) + MOON_CYCLE_DAYS) % MOON_CYCLE_DAYS).toInt()

    fun formatWorldClock(dayTime: Long): String {
        val shifted = (tickOfDay(dayTime) + 6000) % 24000
        val hours = shifted / 1000
        val minutes = ((shifted % 1000) * 60) / 1000
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes)
    }

    /**
     * Returns whether a real-time event should fire its lead reminder (`leadMinutes` before)
     * or its start reminder (`0` minutes before).
     * Returns `"lead"`, `"start"`, or `null`.
     */
    fun checkRealTimeReminderTrigger(
        eventUtcMillis: Long,
        nowUtcMillis: Long,
        leadMinutes: Int
    ): String? {
        val diffMs = eventUtcMillis - nowUtcMillis
        // Start trigger: within [-60s, +30s] of event start
        if (diffMs in -60_000L..30_000L) return "start"
        // Lead trigger: within [leadMs - 60s, leadMs + 30s]
        if (leadMinutes > 0) {
            val leadMs = leadMinutes * 60_000L
            if (diffMs in (leadMs - 60_000L)..(leadMs + 30_000L)) return "lead"
        }
        return null
    }

    /**
     * Returns whether a world-time event should trigger right now given the current `dayTime`.
     */
    fun checkWorldTimeReminderTrigger(
        eventDay: Long,
        eventPeriod: WorldTimePeriod,
        currentDayTime: Long
    ): Boolean {
        val currentDay = worldDayFromDayTime(currentDayTime)
        if (currentDay != eventDay) return false
        val tick = tickOfDay(currentDayTime)
        return tick in eventPeriod.startTick..(eventPeriod.startTick + 600)
    }

    /**
     * Group real-time events by their local date in [zone].
     */
    fun groupRealEventsByLocalDate(
        events: List<CalendarEvent>,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, List<CalendarEvent>> =
        events
            .filter { it.clockMode == CalendarClockMode.REAL_TIME }
            .groupBy { toLocalDate(it.startEpochMillis, zone) }

    fun groupWorldEventsByDay(events: List<CalendarEvent>): Map<Long, List<CalendarEvent>> =
        events
            .filter { it.clockMode == CalendarClockMode.WORLD_TIME }
            .groupBy { it.worldDay }
}
