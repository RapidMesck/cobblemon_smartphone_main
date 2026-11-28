package com.nbp.cobblemon_smartphone.command

import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.nbp.cobblemon_smartphone.calendar.CalendarCategory
import com.nbp.cobblemon_smartphone.calendar.CalendarClockMode
import com.nbp.cobblemon_smartphone.calendar.CalendarEvent
import com.nbp.cobblemon_smartphone.calendar.CalendarServerData
import com.nbp.cobblemon_smartphone.calendar.CalendarTimeUtils
import com.nbp.cobblemon_smartphone.calendar.WorldTimePeriod
import com.nbp.cobblemon_smartphone.network.packet.SyncServerCalendarPacket
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Server commands for managing global calendar events:
 * - `/smartphone calendar list`
 * - `/smartphone calendar add real <yyyy-MM-dd> <HH:mm> <category> <title>` (UTC)
 * - `/smartphone calendar add world <day1Based> <period> <category> <title>`
 * - `/smartphone calendar remove <id>`
 */
object CalendarCommands {
    fun buildSubcommand(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("calendar")
            .then(
                Commands.literal("list")
                    .executes { ctx ->
                        val server = ctx.source.server
                        val events = CalendarServerData.get(server).listEvents()
                        if (events.isEmpty()) {
                            ctx.source.sendSuccess({ Component.literal("No server calendar events scheduled.") }, false)
                        } else {
                            ctx.source.sendSuccess({ Component.literal("Server calendar events (${events.size}):") }, false)
                            for (ev in events) {
                                val whenStr = if (ev.clockMode == CalendarClockMode.REAL_TIME) {
                                    CalendarTimeUtils.formatUtcDateTime(ev.startEpochMillis)
                                } else {
                                    "World Day ${ev.worldDay + 1} (${ev.worldPeriod.id})"
                                }
                                ctx.source.sendSuccess(
                                    { Component.literal(" - [${ev.id.take(8)}] ${ev.title} | $whenStr | ${ev.category.id}") },
                                    false
                                )
                            }
                        }
                        1
                    }
            )
            .then(
                Commands.literal("add")
                    .requires { it.hasPermission(CalendarServerData.OP_PERMISSION_LEVEL) }
                    .then(
                        Commands.literal("real")
                            .then(
                                Commands.argument("date", StringArgumentType.word())
                                    .then(
                                        Commands.argument("time", StringArgumentType.word())
                                            .then(
                                                Commands.argument("category", StringArgumentType.word())
                                                    .then(
                                                        Commands.argument("title", StringArgumentType.greedyString())
                                                            .executes { ctx ->
                                                                if (!com.nbp.cobblemon_smartphone.CobblemonSmartphone.config.features.enableCalendar) {
                                                                    ctx.source.sendFailure(Component.literal("Calendar feature is disabled in config."))
                                                                    return@executes 0
                                                                }
                                                                val dateStr = StringArgumentType.getString(ctx, "date")
                                                                val timeStr = StringArgumentType.getString(ctx, "time")
                                                                val catStr = StringArgumentType.getString(ctx, "category")
                                                                val title = StringArgumentType.getString(ctx, "title").trim()

                                                                val date = runCatching { LocalDate.parse(dateStr) }.getOrNull()
                                                                val time = runCatching { LocalTime.parse(timeStr) }.getOrNull()
                                                                if (date == null || time == null || date.year !in 1970..2099) {
                                                                    ctx.source.sendFailure(Component.literal("Invalid date/time. Expected: yyyy-MM-dd HH:mm (UTC, years 1970-2099)"))
                                                                    return@executes 0
                                                                }

                                                                val epochMs = date.atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli()
                                                                val event = CalendarEvent(
                                                                    id = UUID.randomUUID().toString(),
                                                                    title = title,
                                                                    clockMode = CalendarClockMode.REAL_TIME,
                                                                    startEpochMillis = epochMs,
                                                                    category = CalendarCategory.fromId(catStr.lowercase()),
                                                                    isServerEvent = true,
                                                                    authorName = ctx.source.textName
                                                                )
                                                                val server = ctx.source.server
                                                                val data = CalendarServerData.get(server)
                                                                val saved = data.upsertEvent(event, ctx.source.textName)
                                                                if (saved == null) {
                                                                    ctx.source.sendFailure(Component.literal("Could not create server event (limit reached or empty title)."))
                                                                    return@executes 0
                                                                }
                                                                val pkt = SyncServerCalendarPacket(data.listEvents())
                                                                server.playerList.players.forEach { pkt.sendToPlayer(it) }
                                                                ctx.source.sendSuccess(
                                                                    { Component.literal("Created server event [${saved.id.take(8)}]: ${saved.title} (${CalendarTimeUtils.formatUtcDateTime(saved.startEpochMillis)})") },
                                                                    true
                                                                )
                                                                1
                                                            }
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        Commands.literal("world")
                            .then(
                                Commands.argument("day", LongArgumentType.longArg(1))
                                    .then(
                                        Commands.argument("period", StringArgumentType.word())
                                            .then(
                                                Commands.argument("category", StringArgumentType.word())
                                                    .then(
                                                        Commands.argument("title", StringArgumentType.greedyString())
                                                            .executes { ctx ->
                                                                if (!com.nbp.cobblemon_smartphone.CobblemonSmartphone.config.features.enableCalendar) {
                                                                    ctx.source.sendFailure(Component.literal("Calendar feature is disabled in config."))
                                                                    return@executes 0
                                                                }
                                                                val day1Based = LongArgumentType.getLong(ctx, "day")
                                                                val periodStr = StringArgumentType.getString(ctx, "period")
                                                                val catStr = StringArgumentType.getString(ctx, "category")
                                                                val title = StringArgumentType.getString(ctx, "title").trim()

                                                                val event = CalendarEvent(
                                                                    id = UUID.randomUUID().toString(),
                                                                    title = title,
                                                                    clockMode = CalendarClockMode.WORLD_TIME,
                                                                    worldDay = (day1Based - 1L).coerceAtLeast(0L),
                                                                    worldPeriod = WorldTimePeriod.fromId(periodStr.lowercase()),
                                                                    category = CalendarCategory.fromId(catStr.lowercase()),
                                                                    isServerEvent = true,
                                                                    authorName = ctx.source.textName
                                                                )
                                                                val server = ctx.source.server
                                                                val data = CalendarServerData.get(server)
                                                                val saved = data.upsertEvent(event, ctx.source.textName)
                                                                if (saved == null) {
                                                                    ctx.source.sendFailure(Component.literal("Could not create server event (limit reached or empty title)."))
                                                                    return@executes 0
                                                                }
                                                                val pkt = SyncServerCalendarPacket(data.listEvents())
                                                                server.playerList.players.forEach { pkt.sendToPlayer(it) }
                                                                ctx.source.sendSuccess(
                                                                    { Component.literal("Created world event [${saved.id.take(8)}]: ${saved.title} (Day ${saved.worldDay + 1}, ${saved.worldPeriod.id})") },
                                                                    true
                                                                )
                                                                1
                                                            }
                                                    )
                                            )
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("remove")
                    .requires { it.hasPermission(CalendarServerData.OP_PERMISSION_LEVEL) }
                    .then(
                        Commands.argument("idPrefix", StringArgumentType.word())
                            .executes { ctx ->
                                val prefix = StringArgumentType.getString(ctx, "idPrefix").trim()
                                val server = ctx.source.server
                                val data = CalendarServerData.get(server)
                                val match = data.listEvents().firstOrNull { it.id == prefix || it.id.startsWith(prefix) }
                                if (match == null) {
                                    ctx.source.sendFailure(Component.literal("No server calendar event found matching: $prefix"))
                                    return@executes 0
                                }
                                data.removeEvent(match.id)
                                val pkt = SyncServerCalendarPacket(data.listEvents())
                                server.playerList.players.forEach { pkt.sendToPlayer(it) }
                                ctx.source.sendSuccess({ Component.literal("Removed server event: ${match.title}") }, true)
                                1
                            }
                    )
            )
    }
}
