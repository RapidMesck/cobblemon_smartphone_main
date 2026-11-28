package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.calendar.CalendarCategory
import com.nbp.cobblemon_smartphone.calendar.CalendarClockMode
import com.nbp.cobblemon_smartphone.calendar.CalendarEvent
import com.nbp.cobblemon_smartphone.calendar.CalendarTimeUtils
import com.nbp.cobblemon_smartphone.calendar.WorldTimePeriod
import com.nbp.cobblemon_smartphone.client.CalendarClientState
import com.nbp.cobblemon_smartphone.client.ui.hd.ButtonNode
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBackground
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/**
 * HD Calendar app built on [HdPhoneScreen] supporting:
 * - Real-time events stored in UTC and rendered automatically in the player's local timezone.
 * - Minecraft world-time events organized in an 8-column lunar-cycle day grid.
 * - Personal events (player NBT) and global Server events (world SavedData, OP level 2).
 * - Reminders, badges, and optional Social announcement for server events.
 */
class CalendarScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    scaleOverride: Float? = null
) : HdPhoneScreen(
    color,
    smartphoneStack,
    Component.translatable("cobblemon_smartphone.screen.calendar"),
    scaleOverride
) {
    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelHdBadge: Boolean = true

    private enum class ViewTab { GRID, UPCOMING }
    private enum class SourceFilter { ALL, SERVER, PERSONAL }
    private enum class ScreenMode { BROWSE, DETAIL, EDITOR }

    private var clockMode: CalendarClockMode = CalendarClockMode.REAL_TIME
    private var viewTab: ViewTab = ViewTab.GRID
    private var sourceFilter: SourceFilter = SourceFilter.ALL
    private var screenMode: ScreenMode = ScreenMode.BROWSE

    // Real-time browse state
    private var displayedMonth: YearMonth = YearMonth.now(ZoneId.systemDefault())
    private var selectedRealDate: LocalDate = LocalDate.now(ZoneId.systemDefault())

    // World-time browse state (0-based world day)
    private var selectedWorldDay: Long = currentWorldDay()
    private var worldGridStartDay: Long = (currentWorldDay() / WORLD_PAGE_DAYS) * WORLD_PAGE_DAYS

    // Detail state
    private var detailEventId: String? = null
    private var detailIsServer: Boolean = false

    // Editor state
    private var editingEventId: String? = null
    private var editingIsNew: Boolean = true
    private var editingClockMode: CalendarClockMode = CalendarClockMode.REAL_TIME
    private var editingCategory: CalendarCategory = CalendarCategory.GENERAL
    private var editingIsServer: Boolean = false
    private var editingAnnounceSocial: Boolean = false
    private var editingRemind: Boolean = true

    // Real-time editor fields (in viewer's local timezone)
    private var editYear: Int = LocalDate.now().year
    private var editMonth: Int = LocalDate.now().monthValue
    private var editDay: Int = LocalDate.now().dayOfMonth
    private var editHour: Int = 18
    private var editMinute: Int = 0

    // World-time editor fields
    private var editWorldDay: Long = currentWorldDay()
    private var editWorldPeriod: WorldTimePeriod = WorldTimePeriod.ALL_DAY

    // Delete confirmation modal
    private var confirmDeleteEvent: CalendarEvent? = null

    init {
        CalendarClientState.markAllServerEventsSeen()
    }

    private fun currentWorldDay(): Long {
        val level = Minecraft.getInstance().level ?: return 0L
        return CalendarTimeUtils.worldDayFromDayTime(level.dayTime)
    }

    private fun currentDayTime(): Long = Minecraft.getInstance().level?.dayTime ?: 0L

    override fun onBackPressed() {
        if (confirmDeleteEvent != null) {
            confirmDeleteEvent = null
            return
        }
        when (screenMode) {
            ScreenMode.EDITOR -> {
                screenMode = if (detailEventId != null && !editingIsNew) ScreenMode.DETAIL else ScreenMode.BROWSE
                return
            }
            ScreenMode.DETAIL -> {
                detailEventId = null
                screenMode = ScreenMode.BROWSE
                return
            }
            ScreenMode.BROWSE -> super.onBackPressed()
        }
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        when (screenMode) {
            ScreenMode.BROWSE -> buildBrowseView(theme, builder)
            ScreenMode.DETAIL -> buildDetailView(theme, builder)
            ScreenMode.EDITOR -> buildEditorView(theme, builder)
        }
    }

    // ==================== BROWSE VIEW ====================

    private fun buildBrowseView(theme: SmartphoneTheme, builder: HdBuilder) {
        val use24h = CalendarClientState.isUse24Hour()
        builder.appHeader(
            title = tr("cobblemon_smartphone.screen.calendar"),
            subtitle = if (clockMode == CalendarClockMode.REAL_TIME) {
                CalendarTimeUtils.formatZoneLabel()
            } else {
                val day = currentWorldDay()
                val clock = CalendarTimeUtils.formatWorldClock(currentDayTime())
                val moon = moonName(CalendarTimeUtils.moonPhaseIndex(day))
                "${tr("cobblemon_smartphone.calendar.day_short", day + 1)} • $clock • $moon"
            },
            icon = HdIconAtlas.Icon.BELL,
            iconColor = HdColorSpec.Token("primary")
        ) { hb ->
            if (clockMode == CalendarClockMode.REAL_TIME) {
                hb.button(
                    label = if (use24h) "24h" else "12h",
                    variant = ButtonNode.Variant.SURFACE,
                    fontSize = 7.8f,
                    radius = 7f,
                    onClick = {
                        playClickSound()
                        CalendarClientState.toggleUse24Hour()
                    }
                ) {
                    height = HdSizeSpec.Fixed(20f)
                    padding = HdPadding.symmetric(horizontal = 6f, vertical = 0f)
                    tooltip = tr("cobblemon_smartphone.calendar.toggle_clock_format")
                }
            }
            hb.button(
                label = tr("cobblemon_smartphone.calendar.new"),
                icon = HdIconAtlas.Icon.PLUS,
                variant = ButtonNode.Variant.PRIMARY,
                fontSize = 8.4f,
                radius = 7f,
                onClick = {
                    playClickSound()
                    openNewEvent()
                }
            ) {
                height = HdSizeSpec.Fixed(20f)
                padding = HdPadding.symmetric(horizontal = 7f, vertical = 0f)
                iconSizeDp = 9.5f
            }
        }

        // Mode & filter controls
        builder.column(
            gap = 3.5f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 3f),
            width = HdSizeSpec.Fill
        ) { bar ->
            bar.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row ->
                row.pill(
                    label = tr("cobblemon_smartphone.calendar.mode_real"),
                    active = clockMode == CalendarClockMode.REAL_TIME,
                    onClick = {
                        playClickSound()
                        clockMode = CalendarClockMode.REAL_TIME
                    }
                ) {
                    fontSizeDp = 7.8f
                    padding = HdPadding.symmetric(horizontal = 7f, vertical = 2.5f)
                }
                row.pill(
                    label = tr("cobblemon_smartphone.calendar.mode_world"),
                    active = clockMode == CalendarClockMode.WORLD_TIME,
                    onClick = {
                        playClickSound()
                        clockMode = CalendarClockMode.WORLD_TIME
                        val cur = currentWorldDay()
                        selectedWorldDay = cur
                        worldGridStartDay = (cur / WORLD_PAGE_DAYS) * WORLD_PAGE_DAYS
                    }
                ) {
                    fontSizeDp = 7.8f
                    padding = HdPadding.symmetric(horizontal = 7f, vertical = 2.5f)
                }

                row.spacer(weight = 1f)

                row.pill(
                    label = tr("cobblemon_smartphone.calendar.tab_grid"),
                    active = viewTab == ViewTab.GRID,
                    onClick = {
                        playClickSound()
                        viewTab = ViewTab.GRID
                    }
                ) {
                    fontSizeDp = 7.6f
                    padding = HdPadding.symmetric(horizontal = 6f, vertical = 2.5f)
                }
                row.pill(
                    label = tr("cobblemon_smartphone.calendar.tab_upcoming"),
                    active = viewTab == ViewTab.UPCOMING,
                    onClick = {
                        playClickSound()
                        viewTab = ViewTab.UPCOMING
                    }
                ) {
                    fontSizeDp = 7.6f
                    padding = HdPadding.symmetric(horizontal = 6f, vertical = 2.5f)
                }
            }

            // Source filter row
            bar.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { fRow ->
                fRow.pill(
                    label = tr("cobblemon_smartphone.calendar.filter_all"),
                    active = sourceFilter == SourceFilter.ALL,
                    onClick = { playClickSound(); sourceFilter = SourceFilter.ALL }
                ) {
                    fontSizeDp = 7.4f
                    padding = HdPadding.symmetric(horizontal = 6f, vertical = 2f)
                }
                fRow.pill(
                    label = tr("cobblemon_smartphone.calendar.filter_server"),
                    active = sourceFilter == SourceFilter.SERVER,
                    onClick = { playClickSound(); sourceFilter = SourceFilter.SERVER }
                ) {
                    fontSizeDp = 7.4f
                    padding = HdPadding.symmetric(horizontal = 6f, vertical = 2f)
                }
                fRow.pill(
                    label = tr("cobblemon_smartphone.calendar.filter_personal"),
                    active = sourceFilter == SourceFilter.PERSONAL,
                    onClick = { playClickSound(); sourceFilter = SourceFilter.PERSONAL }
                ) {
                    fontSizeDp = 7.4f
                    padding = HdPadding.symmetric(horizontal = 6f, vertical = 2f)
                }
            }
        }

        val filteredEvents = filteredEventsForActiveMode()

        if (viewTab == ViewTab.UPCOMING) {
            buildUpcomingList(theme, builder, filteredEvents)
        } else if (clockMode == CalendarClockMode.REAL_TIME) {
            buildRealGridAndDayEvents(theme, builder, filteredEvents)
        } else {
            buildWorldLunarGridAndDayEvents(theme, builder, filteredEvents)
        }
    }

    private fun filteredEventsForActiveMode(): List<CalendarEvent> {
        val base = when (sourceFilter) {
            SourceFilter.ALL -> CalendarClientState.getAllEvents()
            SourceFilter.SERVER -> CalendarClientState.getServerEvents()
            SourceFilter.PERSONAL -> CalendarClientState.getPersonalEvents()
        }
        return base.filter { it.clockMode == clockMode }
    }

    // ==================== REAL-TIME MONTHLY GRID ====================

    private fun buildRealGridAndDayEvents(
        theme: SmartphoneTheme,
        builder: HdBuilder,
        events: List<CalendarEvent>
    ) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val byDate = CalendarTimeUtils.groupRealEventsByLocalDate(events, zone)

        // Month navigation bar
        builder.row(
            gap = 4f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 2f),
            width = HdSizeSpec.Fill
        ) { nav ->
            nav.button(
                label = "<",
                variant = ButtonNode.Variant.SURFACE,
                fontSize = 8.4f,
                radius = 6f,
                onClick = {
                    playClickSound()
                    displayedMonth = displayedMonth.minusMonths(1)
                }
            ) {
                width = HdSizeSpec.Fixed(20f)
                height = HdSizeSpec.Fixed(18f)
                padding = HdPadding.ZERO
            }

            val monthName = displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                .replaceFirstChar { it.uppercase() }
            nav.text(
                text = "$monthName ${displayedMonth.year}",
                size = 9.0f,
                weight = HdTextLayout.Weight.BOLD,
                color = HdColorSpec.Token("text_dark"),
                align = HdAlign.CENTER
            ) {
                width = HdSizeSpec.Weight(1f)
            }

            nav.button(
                label = tr("cobblemon_smartphone.calendar.today"),
                variant = ButtonNode.Variant.SURFACE,
                fontSize = 7.4f,
                radius = 6f,
                onClick = {
                    playClickSound()
                    displayedMonth = YearMonth.from(today)
                    selectedRealDate = today
                }
            ) {
                height = HdSizeSpec.Fixed(18f)
                padding = HdPadding.symmetric(horizontal = 6f, vertical = 0f)
            }

            nav.button(
                label = ">",
                variant = ButtonNode.Variant.SURFACE,
                fontSize = 8.4f,
                radius = 6f,
                onClick = {
                    playClickSound()
                    displayedMonth = displayedMonth.plusMonths(1)
                }
            ) {
                width = HdSizeSpec.Fixed(20f)
                height = HdSizeSpec.Fixed(18f)
                padding = HdPadding.ZERO
            }
        }

        // Calendar grid card
        builder.column(
            gap = 2f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 2f),
            width = HdSizeSpec.Fill
        ) { wrap ->
            wrap.card(
                gap = 2f,
                padding = HdPadding.all(4f),
                radius = 8f,
                shadowBlur = 3f
            ) { grid ->
                // Weekday header (Sun..Sat)
                val weekHeaders = listOf("S", "M", "T", "W", "T", "F", "S")
                grid.row(gap = 2f, width = HdSizeSpec.Fill) { hdr ->
                    for (label in weekHeaders) {
                        hdr.text(
                            text = label,
                            size = 7.2f,
                            weight = HdTextLayout.Weight.BOLD,
                            color = HdColorSpec.Token("text_dark_muted"),
                            align = HdAlign.CENTER
                        ) {
                            width = HdSizeSpec.Weight(1f)
                        }
                    }
                }

                val firstOfMonth = displayedMonth.atDay(1)
                // Sunday = 0 .. Saturday = 6
                val startOffset = firstOfMonth.dayOfWeek.value % 7
                val daysInMonth = displayedMonth.lengthOfMonth()
                val totalCells = ((startOffset + daysInMonth + 6) / 7) * 7

                var cellIdx = 0
                while (cellIdx < totalCells) {
                    val rowStart = cellIdx
                    grid.row(gap = 2f, width = HdSizeSpec.Fill) { weekRow ->
                        for (col in 0 until 7) {
                            val dayNum = (rowStart + col) - startOffset + 1
                            if (dayNum < 1 || dayNum > daysInMonth) {
                                weekRow.spacer(h = 20f, weight = 1f)
                            } else {
                                val cellDate = displayedMonth.atDay(dayNum)
                                val dayEvents = byDate[cellDate].orEmpty()
                                val isToday = cellDate == today
                                val isSelected = cellDate == selectedRealDate

                                weekRow.column(
                                    gap = 1f,
                                    crossAlign = HdAlign.CENTER,
                                    padding = HdPadding.symmetric(horizontal = 1f, vertical = 2f),
                                    width = HdSizeSpec.Weight(1f),
                                    height = HdSizeSpec.Fixed(21f),
                                    background = HdBackground(
                                        color = when {
                                            isSelected -> HdColorSpec.Token("primary", 0.24f)
                                            isToday -> HdColorSpec.Token("surface_alt")
                                            else -> HdColorSpec.Fixed(0x00000000)
                                        },
                                        radius = 5f,
                                        borderWidth = if (isSelected) 1.2f else if (isToday) 0.8f else 0f,
                                        borderColor = if (isSelected) HdColorSpec.Token("primary") else HdColorSpec.Token("primary_border")
                                    )
                                ) { cell ->
                                    onClick = {
                                        playClickSound()
                                        selectedRealDate = cellDate
                                    }
                                    cell.text(
                                        text = dayNum.toString(),
                                        size = 7.8f,
                                        weight = if (isToday || isSelected) HdTextLayout.Weight.BOLD else HdTextLayout.Weight.REGULAR,
                                        color = if (isSelected || isToday) HdColorSpec.Token("primary") else HdColorSpec.Token("text_dark"),
                                        align = HdAlign.CENTER
                                    )
                                    if (dayEvents.isNotEmpty()) {
                                        cell.row(gap = 1.5f, mainAlign = HdAlign.CENTER, crossAlign = HdAlign.CENTER) { dots ->
                                            val sortedDots = dayEvents.sortedByDescending { it.isServerEvent }.take(3)
                                            for (ev in sortedDots) {
                                                dots.stack(
                                                    width = HdSizeSpec.Fixed(3.6f),
                                                    height = HdSizeSpec.Fixed(3.6f)
                                                ) {
                                                    background = HdBackground(
                                                        color = HdColorSpec.Fixed(ev.category.colorArgb),
                                                        radius = 1.8f
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    cellIdx += 7
                }
            }
        }

        // Selected day events list
        val dayEvents = byDate[selectedRealDate].orEmpty()
        builder.row(
            gap = 4f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(horizontal = 9f, vertical = 2f),
            width = HdSizeSpec.Fill
        ) { sec ->
            sec.text(
                text = "${selectedRealDate} (${dayEvents.size})",
                size = 8.0f,
                weight = HdTextLayout.Weight.BOLD,
                color = HdColorSpec.Token("text_dark")
            ) {
                width = HdSizeSpec.Weight(1f)
            }
        }

        builder.scroll(
            scrollId = DAY_LIST_SCROLL_ID,
            gap = 5f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 2f)
        ) { list ->
            if (dayEvents.isEmpty()) {
                list.emptyState(
                    title = tr("cobblemon_smartphone.calendar.no_events_day"),
                    description = null,
                    icon = HdIconAtlas.Icon.BELL,
                    actionLabel = tr("cobblemon_smartphone.calendar.new"),
                    actionIcon = HdIconAtlas.Icon.PLUS,
                    onAction = {
                        playClickSound()
                        openNewEvent()
                    }
                )
            } else {
                for (ev in dayEvents) {
                    buildEventCard(theme, list, ev, showDatePrefix = false)
                }
            }
        }
    }

    // ==================== WORLD-TIME LUNAR GRID (8 COLUMNS) ====================

    private fun buildWorldLunarGridAndDayEvents(
        theme: SmartphoneTheme,
        builder: HdBuilder,
        events: List<CalendarEvent>
    ) {
        val curDay = currentWorldDay()
        val byDay = CalendarTimeUtils.groupWorldEventsByDay(events)

        // Page navigation (< Days 1-32 >)
        builder.row(
            gap = 4f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 2f),
            width = HdSizeSpec.Fill
        ) { nav ->
            nav.button(
                label = "<",
                variant = ButtonNode.Variant.SURFACE,
                fontSize = 8.4f,
                radius = 6f,
                enabled = worldGridStartDay >= WORLD_PAGE_DAYS,
                onClick = {
                    playClickSound()
                    worldGridStartDay = (worldGridStartDay - WORLD_PAGE_DAYS).coerceAtLeast(0L)
                }
            ) {
                width = HdSizeSpec.Fixed(20f)
                height = HdSizeSpec.Fixed(18f)
                padding = HdPadding.ZERO
            }

            val endDisplay = worldGridStartDay + WORLD_PAGE_DAYS
            nav.text(
                text = tr("cobblemon_smartphone.calendar.world_range", worldGridStartDay + 1L, endDisplay),
                size = 8.8f,
                weight = HdTextLayout.Weight.BOLD,
                color = HdColorSpec.Token("text_dark"),
                align = HdAlign.CENTER
            ) {
                width = HdSizeSpec.Weight(1f)
            }

            nav.button(
                label = tr("cobblemon_smartphone.calendar.today"),
                variant = ButtonNode.Variant.SURFACE,
                fontSize = 7.4f,
                radius = 6f,
                onClick = {
                    playClickSound()
                    selectedWorldDay = curDay
                    worldGridStartDay = (curDay / WORLD_PAGE_DAYS) * WORLD_PAGE_DAYS
                }
            ) {
                height = HdSizeSpec.Fixed(18f)
                padding = HdPadding.symmetric(horizontal = 6f, vertical = 0f)
            }

            nav.button(
                label = ">",
                variant = ButtonNode.Variant.SURFACE,
                fontSize = 8.4f,
                radius = 6f,
                onClick = {
                    playClickSound()
                    worldGridStartDay += WORLD_PAGE_DAYS
                }
            ) {
                width = HdSizeSpec.Fixed(20f)
                height = HdSizeSpec.Fixed(18f)
                padding = HdPadding.ZERO
            }
        }

        // 8-column lunar grid card (each column = 1 moon phase!)
        builder.column(
            gap = 2f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 2f),
            width = HdSizeSpec.Fill
        ) { wrap ->
            wrap.card(
                gap = 2f,
                padding = HdPadding.all(4f),
                radius = 8f,
                shadowBlur = 3f
            ) { grid ->
                // Moon phase column headers
                grid.row(gap = 2f, width = HdSizeSpec.Fill) { hdr ->
                    for (phase in 0 until CalendarTimeUtils.MOON_CYCLE_DAYS) {
                        val isFull = phase == 0
                        val isNew = phase == 4
                        hdr.text(
                            text = MOON_SHORT_LABELS[phase],
                            size = 6.8f,
                            weight = if (isFull || isNew) HdTextLayout.Weight.BOLD else HdTextLayout.Weight.REGULAR,
                            color = if (isFull) HdColorSpec.Fixed(theme.colors.contentGold) else HdColorSpec.Token("text_dark_muted"),
                            align = HdAlign.CENTER
                        ) {
                            width = HdSizeSpec.Weight(1f)
                            tooltip = moonName(phase)
                        }
                    }
                }

                for (rowIdx in 0 until (WORLD_PAGE_DAYS / CalendarTimeUtils.MOON_CYCLE_DAYS)) {
                    grid.row(gap = 2f, width = HdSizeSpec.Fill) { dayRow ->
                        for (col in 0 until CalendarTimeUtils.MOON_CYCLE_DAYS) {
                            val dayZeroBased = worldGridStartDay + rowIdx * CalendarTimeUtils.MOON_CYCLE_DAYS + col
                            val dayEvents = byDay[dayZeroBased].orEmpty()
                            val isToday = dayZeroBased == curDay
                            val isSelected = dayZeroBased == selectedWorldDay
                            val isFullMoon = col == 0

                            dayRow.column(
                                gap = 1f,
                                crossAlign = HdAlign.CENTER,
                                padding = HdPadding.symmetric(horizontal = 1f, vertical = 2f),
                                width = HdSizeSpec.Weight(1f),
                                height = HdSizeSpec.Fixed(22f),
                                background = HdBackground(
                                    color = when {
                                        isSelected -> HdColorSpec.Token("primary", 0.24f)
                                        isToday -> HdColorSpec.Token("surface_alt")
                                        isFullMoon -> HdColorSpec.Fixed(0x14F59E0B)
                                        else -> HdColorSpec.Fixed(0x00000000)
                                    },
                                    radius = 5f,
                                    borderWidth = if (isSelected) 1.2f else if (isToday) 0.8f else 0f,
                                    borderColor = if (isSelected) HdColorSpec.Token("primary") else HdColorSpec.Token("primary_border")
                                )
                            ) { cell ->
                                tooltip = "${tr("cobblemon_smartphone.calendar.day_short", dayZeroBased + 1L)} • ${moonName(col)}"
                                onClick = {
                                    playClickSound()
                                    selectedWorldDay = dayZeroBased
                                }
                                cell.text(
                                    text = (dayZeroBased + 1L).toString(),
                                    size = 7.6f,
                                    weight = if (isToday || isSelected) HdTextLayout.Weight.BOLD else HdTextLayout.Weight.REGULAR,
                                    color = if (isSelected || isToday) HdColorSpec.Token("primary") else HdColorSpec.Token("text_dark"),
                                    align = HdAlign.CENTER
                                )
                                if (dayEvents.isNotEmpty()) {
                                    cell.row(gap = 1.5f, mainAlign = HdAlign.CENTER, crossAlign = HdAlign.CENTER) { dots ->
                                        for (ev in dayEvents.sortedByDescending { it.isServerEvent }.take(3)) {
                                            dots.stack(
                                                width = HdSizeSpec.Fixed(3.6f),
                                                height = HdSizeSpec.Fixed(3.6f)
                                            ) {
                                                background = HdBackground(
                                                    color = HdColorSpec.Fixed(ev.category.colorArgb),
                                                    radius = 1.8f
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected world day events
        val dayEvents = byDay[selectedWorldDay].orEmpty()
        val selMoon = moonName(CalendarTimeUtils.moonPhaseIndex(selectedWorldDay))
        builder.row(
            gap = 4f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(horizontal = 9f, vertical = 2f),
            width = HdSizeSpec.Fill
        ) { sec ->
            sec.text(
                text = "${tr("cobblemon_smartphone.calendar.day_short", selectedWorldDay + 1L)} • $selMoon (${dayEvents.size})",
                size = 8.0f,
                weight = HdTextLayout.Weight.BOLD,
                color = HdColorSpec.Token("text_dark")
            ) {
                width = HdSizeSpec.Weight(1f)
            }
        }

        builder.scroll(
            scrollId = DAY_LIST_SCROLL_ID,
            gap = 5f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 2f)
        ) { list ->
            if (dayEvents.isEmpty()) {
                list.emptyState(
                    title = tr("cobblemon_smartphone.calendar.no_events_day"),
                    description = null,
                    icon = HdIconAtlas.Icon.BELL,
                    actionLabel = tr("cobblemon_smartphone.calendar.new"),
                    actionIcon = HdIconAtlas.Icon.PLUS,
                    onAction = {
                        playClickSound()
                        openNewEvent()
                    }
                )
            } else {
                for (ev in dayEvents) {
                    buildEventCard(theme, list, ev, showDatePrefix = false)
                }
            }
        }
    }

    // ==================== UPCOMING LIST ====================

    private fun buildUpcomingList(
        theme: SmartphoneTheme,
        builder: HdBuilder,
        events: List<CalendarEvent>
    ) {
        val nowMs = System.currentTimeMillis()
        val curDay = currentWorldDay()
        val upcoming = if (clockMode == CalendarClockMode.REAL_TIME) {
            events.filter { it.startEpochMillis >= nowMs - 3_600_000L }
        } else {
            events.filter { it.worldDay >= curDay }
        }

        builder.scroll(
            scrollId = UPCOMING_SCROLL_ID,
            gap = 6f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 4f)
        ) { list ->
            if (upcoming.isEmpty()) {
                list.emptyState(
                    title = tr("cobblemon_smartphone.calendar.no_upcoming"),
                    description = tr("cobblemon_smartphone.calendar.no_upcoming_desc"),
                    icon = HdIconAtlas.Icon.BELL,
                    actionLabel = tr("cobblemon_smartphone.calendar.new"),
                    actionIcon = HdIconAtlas.Icon.PLUS,
                    onAction = {
                        playClickSound()
                        openNewEvent()
                    }
                )
            } else {
                for (ev in upcoming) {
                    buildEventCard(theme, list, ev, showDatePrefix = true)
                }
            }
        }
    }

    // ==================== EVENT CARD ====================

    private fun buildEventCard(
        theme: SmartphoneTheme,
        list: HdBuilder,
        ev: CalendarEvent,
        showDatePrefix: Boolean
    ) {
        val remindOn = CalendarClientState.isReminderEnabled(ev)
        val use24h = CalendarClientState.isUse24Hour()
        val nowMs = System.currentTimeMillis()

        val timeBadge = if (ev.clockMode == CalendarClockMode.REAL_TIME) {
            val localTime = CalendarTimeUtils.formatLocalTime(ev.startEpochMillis, use24h)
            val rel = CalendarTimeUtils.formatRelativeRealTime(ev.startEpochMillis, nowMs)
            if (showDatePrefix) {
                "${CalendarTimeUtils.formatLocalDate(ev.startEpochMillis)} $localTime • $rel"
            } else {
                "$localTime • $rel"
            }
        } else {
            val periodLabel = periodName(ev.worldPeriod)
            if (showDatePrefix) {
                val moon = moonName(CalendarTimeUtils.moonPhaseIndex(ev.worldDay))
                "${tr("cobblemon_smartphone.calendar.day_short", ev.worldDay + 1L)} • $periodLabel • $moon"
            } else {
                periodLabel
            }
        }

        list.card(
            gap = 3f,
            padding = HdPadding.symmetric(horizontal = 9f, vertical = 6.5f),
            radius = 9f,
            shadowBlur = 4f,
            hoverHighlight = true,
            onClick = {
                playClickSound()
                detailEventId = ev.id
                detailIsServer = ev.isServerEvent
                screenMode = ScreenMode.DETAIL
            }
        ) { card ->
            card.row(gap = 5f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { top ->
                top.stack(
                    width = HdSizeSpec.Fixed(7f),
                    height = HdSizeSpec.Fixed(7f)
                ) {
                    background = HdBackground(color = HdColorSpec.Fixed(ev.category.colorArgb), radius = 3.5f)
                }

                if (ev.isServerEvent) {
                    top.icon(
                        icon = HdIconAtlas.Icon.GLOBE,
                        size = 9.5f,
                        color = HdColorSpec.Fixed(theme.colors.contentGold)
                    ) {
                        tooltip = tr("cobblemon_smartphone.calendar.server_event")
                    }
                }

                top.text(
                    text = ev.title,
                    size = 9.2f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = HdColorSpec.Token("text_dark")
                ) {
                    width = HdSizeSpec.Weight(1f)
                }

                // Reminder bell toggle
                top.stack(
                    hAlign = HdAlign.CENTER,
                    vAlign = HdAlign.CENTER,
                    width = HdSizeSpec.Fixed(20f),
                    height = HdSizeSpec.Fixed(20f)
                ) { bellBtn ->
                    background = HdBackground(
                        color = if (remindOn) HdColorSpec.Token("primary", 0.18f) else HdColorSpec.Token("surface_alt"),
                        radius = 6f,
                        borderWidth = 0.6f,
                        borderColor = if (remindOn) HdColorSpec.Token("primary") else HdColorSpec.Token("primary_border", 0.55f)
                    )
                    tooltip = tr(if (remindOn) "cobblemon_smartphone.calendar.reminder_on" else "cobblemon_smartphone.calendar.reminder_off")
                    onClick = {
                        playClickSound()
                        CalendarClientState.toggleReminder(ev)
                    }
                    bellBtn.icon(
                        icon = HdIconAtlas.Icon.BELL,
                        size = 10f,
                        color = if (remindOn) HdColorSpec.Token("primary") else HdColorSpec.Token("text_dark_muted")
                    )
                }
            }

            card.row(gap = 6f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { sub ->
                sub.text(
                    text = timeBadge,
                    size = 7.8f,
                    color = HdColorSpec.Token("text_dark_muted")
                ) {
                    width = HdSizeSpec.Weight(1f)
                }
                sub.text(
                    text = categoryName(ev.category),
                    size = 7.2f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = HdColorSpec.Fixed(ev.category.colorArgb)
                )
            }
        }
    }

    // ==================== DETAIL VIEW ====================

    private fun buildDetailView(theme: SmartphoneTheme, builder: HdBuilder) {
        val ev = findDetailEvent()
        if (ev == null) {
            screenMode = ScreenMode.BROWSE
            buildBrowseView(theme, builder)
            return
        }

        val canEdit = !ev.isServerEvent || CalendarClientState.canManageServerEvents()
        val remindOn = CalendarClientState.isReminderEnabled(ev)

        builder.appHeader(
            title = ev.title,
            subtitle = if (ev.isServerEvent) {
                tr("cobblemon_smartphone.calendar.server_event") +
                    if (ev.authorName.isNotBlank()) " • ${ev.authorName}" else ""
            } else {
                tr("cobblemon_smartphone.calendar.personal_event")
            },
            icon = if (ev.isServerEvent) HdIconAtlas.Icon.GLOBE else HdIconAtlas.Icon.BELL,
            iconColor = HdColorSpec.Fixed(ev.category.colorArgb)
        ) { hb ->
            hb.button(
                icon = HdIconAtlas.Icon.BELL,
                variant = if (remindOn) ButtonNode.Variant.PRIMARY else ButtonNode.Variant.SURFACE,
                radius = 7f,
                onClick = {
                    playClickSound()
                    CalendarClientState.toggleReminder(ev)
                }
            ) {
                width = HdSizeSpec.Fixed(22f)
                height = HdSizeSpec.Fixed(22f)
                padding = HdPadding.ZERO
                iconSizeDp = 10.5f
                tooltip = tr(if (remindOn) "cobblemon_smartphone.calendar.reminder_on" else "cobblemon_smartphone.calendar.reminder_off")
            }

            if (canEdit) {
                hb.button(
                    icon = HdIconAtlas.Icon.EDIT,
                    variant = ButtonNode.Variant.SURFACE,
                    radius = 7f,
                    onClick = {
                        playClickSound()
                        openEditEvent(ev)
                    }
                ) {
                    width = HdSizeSpec.Fixed(22f)
                    height = HdSizeSpec.Fixed(22f)
                    padding = HdPadding.ZERO
                    iconSizeDp = 10.5f
                    tooltip = tr("cobblemon_smartphone.calendar.edit")
                }

                hb.button(
                    icon = HdIconAtlas.Icon.TRASH,
                    variant = ButtonNode.Variant.DANGER,
                    radius = 7f,
                    onClick = {
                        playClickSound()
                        confirmDeleteEvent = ev
                    }
                ) {
                    width = HdSizeSpec.Fixed(22f)
                    height = HdSizeSpec.Fixed(22f)
                    padding = HdPadding.ZERO
                    iconSizeDp = 10.5f
                    tooltip = tr("cobblemon_smartphone.calendar.delete")
                }
            }
        }

        builder.scroll(
            scrollId = DETAIL_SCROLL_ID,
            gap = 7f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f)
        ) { body ->
            body.card(gap = 4f, padding = HdPadding.all(9f), radius = 9f, shadowBlur = 4f) { info ->
                info.row(gap = 5f, crossAlign = HdAlign.CENTER) { catRow ->
                    catRow.stack(width = HdSizeSpec.Fixed(8f), height = HdSizeSpec.Fixed(8f)) {
                        background = HdBackground(color = HdColorSpec.Fixed(ev.category.colorArgb), radius = 4f)
                    }
                    catRow.text(
                        text = categoryName(ev.category),
                        size = 8.4f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Fixed(ev.category.colorArgb)
                    )
                }

                if (ev.clockMode == CalendarClockMode.REAL_TIME) {
                    val use24h = CalendarClientState.isUse24Hour()
                    val localDate = CalendarTimeUtils.formatLocalDate(ev.startEpochMillis)
                    val localTime = CalendarTimeUtils.formatLocalTime(ev.startEpochMillis, use24h)
                    val rel = CalendarTimeUtils.formatRelativeRealTime(ev.startEpochMillis, System.currentTimeMillis())
                    info.text(
                        text = "$localDate • $localTime ($rel)",
                        size = 9.4f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("text_dark")
                    )
                    info.text(
                        text = "${CalendarTimeUtils.formatZoneLabel()}  |  ${CalendarTimeUtils.formatUtcDateTime(ev.startEpochMillis)}",
                        size = 7.4f,
                        color = HdColorSpec.Token("text_dark_muted")
                    )
                } else {
                    val moon = moonName(CalendarTimeUtils.moonPhaseIndex(ev.worldDay))
                    info.text(
                        text = "${tr("cobblemon_smartphone.calendar.day_short", ev.worldDay + 1L)} • ${periodName(ev.worldPeriod)}",
                        size = 9.4f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("text_dark")
                    )
                    info.text(
                        text = "${tr("cobblemon_smartphone.calendar.moon_phase_label")}: $moon",
                        size = 7.8f,
                        color = HdColorSpec.Token("text_dark_muted")
                    )
                }
            }

            if (ev.description.isNotBlank()) {
                body.card(gap = 4f, padding = HdPadding.all(9f), radius = 9f, shadowBlur = 4f) { descCard ->
                    descCard.text(
                        text = ev.description,
                        size = 8.8f,
                        color = HdColorSpec.Token("text_dark")
                    )
                }
            }
        }
    }

    private fun findDetailEvent(): CalendarEvent? {
        val id = detailEventId ?: return null
        return if (detailIsServer) {
            CalendarClientState.getServerEvents().firstOrNull { it.id == id }
        } else {
            CalendarClientState.getPersonalEvents().firstOrNull { it.id == id }
        }
    }

    // ==================== EDITOR VIEW ====================

    private fun openNewEvent() {
        editingEventId = UUID.randomUUID().toString()
        editingIsNew = true
        editingClockMode = clockMode
        editingCategory = CalendarCategory.GENERAL
        editingIsServer = false
        editingAnnounceSocial = false
        editingRemind = true

        editYear = selectedRealDate.year
        editMonth = selectedRealDate.monthValue
        editDay = selectedRealDate.dayOfMonth
        val nowLocal = ZonedDateTime.now(ZoneId.systemDefault()).plusHours(1)
        editHour = nowLocal.hour
        editMinute = (nowLocal.minute / 5) * 5

        editWorldDay = selectedWorldDay
        editWorldPeriod = WorldTimePeriod.ALL_DAY

        setInputContent(TITLE_INPUT_ID, "")
        setInputContent(DESC_INPUT_ID, "")
        stateStore.clearFocus()
        screenMode = ScreenMode.EDITOR
    }

    private fun openEditEvent(ev: CalendarEvent) {
        editingEventId = ev.id
        editingIsNew = false
        editingClockMode = ev.clockMode
        editingCategory = ev.category
        editingIsServer = ev.isServerEvent
        editingAnnounceSocial = false
        editingRemind = ev.remind

        if (ev.clockMode == CalendarClockMode.REAL_TIME) {
            val zdt = CalendarTimeUtils.toLocalDateTime(ev.startEpochMillis)
            editYear = zdt.year
            editMonth = zdt.monthValue
            editDay = zdt.dayOfMonth
            editHour = zdt.hour
            editMinute = zdt.minute
        } else {
            editWorldDay = ev.worldDay
            editWorldPeriod = ev.worldPeriod
        }

        setInputContent(TITLE_INPUT_ID, ev.title)
        setInputContent(DESC_INPUT_ID, ev.description)
        stateStore.clearFocus()
        screenMode = ScreenMode.EDITOR
    }

    private fun setInputContent(inputId: String, text: String) {
        val st = stateStore.input(inputId, text)
        st.text = text
        st.cursor = text.length
        st.selectionAnchor = null
        st.scrollOffsetX = 0f
        st.resetBlink()
    }

    private fun buildEditorView(theme: SmartphoneTheme, builder: HdBuilder) {
        builder.appHeader(
            title = tr(if (editingIsNew) "cobblemon_smartphone.calendar.new_event" else "cobblemon_smartphone.calendar.edit_event"),
            subtitle = if (editingClockMode == CalendarClockMode.REAL_TIME) {
                CalendarTimeUtils.formatZoneLabel()
            } else {
                "${tr("cobblemon_smartphone.calendar.day_short", editWorldDay + 1L)} • ${moonName(CalendarTimeUtils.moonPhaseIndex(editWorldDay))}"
            },
            icon = HdIconAtlas.Icon.EDIT,
            iconColor = HdColorSpec.Fixed(editingCategory.colorArgb)
        ) { hb ->
            hb.button(
                label = tr("cobblemon_smartphone.calendar.save"),
                icon = HdIconAtlas.Icon.CHECK,
                variant = ButtonNode.Variant.PRIMARY,
                fontSize = 8.2f,
                radius = 7f,
                onClick = {
                    playClickSound()
                    saveEditingEvent()
                }
            ) {
                height = HdSizeSpec.Fixed(21f)
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 0f)
                iconSizeDp = 9.5f
            }
        }

        builder.scroll(
            scrollId = EDITOR_SCROLL_ID,
            gap = 6f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 5f)
        ) { ed ->
            // Clock mode switch
            ed.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { mRow ->
                mRow.pill(
                    label = tr("cobblemon_smartphone.calendar.mode_real"),
                    active = editingClockMode == CalendarClockMode.REAL_TIME,
                    onClick = { playClickSound(); editingClockMode = CalendarClockMode.REAL_TIME }
                ) {
                    fontSizeDp = 7.8f
                    padding = HdPadding.symmetric(horizontal = 8f, vertical = 3f)
                }
                mRow.pill(
                    label = tr("cobblemon_smartphone.calendar.mode_world"),
                    active = editingClockMode == CalendarClockMode.WORLD_TIME,
                    onClick = { playClickSound(); editingClockMode = CalendarClockMode.WORLD_TIME }
                ) {
                    fontSizeDp = 7.8f
                    padding = HdPadding.symmetric(horizontal = 8f, vertical = 3f)
                }
            }

            // Title input
            ed.textInput(
                inputId = TITLE_INPUT_ID,
                placeholder = tr("cobblemon_smartphone.calendar.title_placeholder"),
                maxLength = CalendarEvent.MAX_TITLE_LENGTH
            ) {
                fontSizeDp = 9.2f
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 5.5f)
            }

            // Category picker
            ed.row(
                gap = 5f,
                crossAlign = HdAlign.CENTER,
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 4.5f),
                width = HdSizeSpec.Fill,
                background = HdBackground(
                    color = HdColorSpec.Token("surface"),
                    radius = 8f,
                    borderWidth = 0.7f,
                    borderColor = HdColorSpec.Token("primary_border")
                )
            ) { catRow ->
                catRow.text(
                    text = categoryName(editingCategory),
                    size = 7.8f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = HdColorSpec.Fixed(editingCategory.colorArgb)
                ) {
                    width = HdSizeSpec.Weight(1f)
                }
                for (cat in CalendarCategory.entries) {
                    val selected = editingCategory == cat
                    catRow.stack(
                        hAlign = HdAlign.CENTER,
                        vAlign = HdAlign.CENTER,
                        width = HdSizeSpec.Fixed(15f),
                        height = HdSizeSpec.Fixed(15f)
                    ) { dot ->
                        background = HdBackground(
                            color = HdColorSpec.Fixed(cat.colorArgb),
                            radius = 7.5f,
                            borderWidth = if (selected) 1.8f else 0.6f,
                            borderColor = if (selected) HdColorSpec.Token("text_dark") else HdColorSpec.Token("primary_border")
                        )
                        tooltip = categoryName(cat)
                        onClick = { playClickSound(); editingCategory = cat }
                        if (selected) {
                            dot.icon(HdIconAtlas.Icon.CHECK, size = 8f, color = HdColorSpec.Fixed(0xFFFFFFFF.toInt()))
                        }
                    }
                }
            }

            // Date / Time picker
            if (editingClockMode == CalendarClockMode.REAL_TIME) {
                buildRealTimePicker(ed)
            } else {
                buildWorldTimePicker(ed)
            }

            // Description input
            ed.textInput(
                inputId = DESC_INPUT_ID,
                placeholder = tr("cobblemon_smartphone.calendar.desc_placeholder"),
                maxLength = CalendarEvent.MAX_DESCRIPTION_LENGTH,
                multiline = true,
                showCounter = true
            ) {
                width = HdSizeSpec.Fill
                height = HdSizeSpec.Fixed(64f)
                fontSizeDp = 8.4f
                padding = HdPadding.all(7f)
            }

            // Server event toggle (OP level 2 only)
            if (CalendarClientState.canManageServerEvents() && editingIsNew) {
                ed.row(
                    gap = 6f,
                    crossAlign = HdAlign.CENTER,
                    padding = HdPadding.symmetric(horizontal = 8f, vertical = 5f),
                    width = HdSizeSpec.Fill,
                    background = HdBackground(
                        color = if (editingIsServer) HdColorSpec.Token("primary", 0.14f) else HdColorSpec.Token("surface"),
                        radius = 8f,
                        borderWidth = 0.7f,
                        borderColor = if (editingIsServer) HdColorSpec.Token("primary") else HdColorSpec.Token("primary_border")
                    )
                ) { srvRow ->
                    onClick = {
                        playClickSound()
                        editingIsServer = !editingIsServer
                        if (!editingIsServer) editingAnnounceSocial = false
                    }
                    srvRow.icon(
                        icon = HdIconAtlas.Icon.GLOBE,
                        size = 11f,
                        color = if (editingIsServer) HdColorSpec.Fixed(theme.colors.contentGold) else HdColorSpec.Token("text_dark_muted")
                    )
                    srvRow.text(
                        text = tr("cobblemon_smartphone.calendar.server_event_toggle"),
                        size = 8.2f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("text_dark")
                    ) {
                        width = HdSizeSpec.Weight(1f)
                    }
                    srvRow.text(
                        text = if (editingIsServer) "ON" else "OFF",
                        size = 7.6f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = if (editingIsServer) HdColorSpec.Token("primary") else HdColorSpec.Token("text_dark_muted")
                    )
                }

                // Optional "Announce in Social" toggle when creating a new server event
                if (editingIsServer && CobblemonSmartphone.config.features.enableSocial) {
                    ed.row(
                        gap = 6f,
                        crossAlign = HdAlign.CENTER,
                        padding = HdPadding.symmetric(horizontal = 8f, vertical = 5f),
                        width = HdSizeSpec.Fill,
                        background = HdBackground(
                            color = if (editingAnnounceSocial) HdColorSpec.Token("primary", 0.14f) else HdColorSpec.Token("surface"),
                            radius = 8f,
                            borderWidth = 0.7f,
                            borderColor = if (editingAnnounceSocial) HdColorSpec.Token("primary") else HdColorSpec.Token("primary_border")
                        )
                    ) { socRow ->
                        onClick = {
                            playClickSound()
                            editingAnnounceSocial = !editingAnnounceSocial
                        }
                        socRow.icon(
                            icon = HdIconAtlas.Icon.FEED,
                            size = 10.5f,
                            color = if (editingAnnounceSocial) HdColorSpec.Token("primary") else HdColorSpec.Token("text_dark_muted")
                        )
                        socRow.text(
                            text = tr("cobblemon_smartphone.calendar.announce_social"),
                            size = 8.0f,
                            color = HdColorSpec.Token("text_dark")
                        ) {
                            width = HdSizeSpec.Weight(1f)
                        }
                        socRow.text(
                            text = if (editingAnnounceSocial) "ON" else "OFF",
                            size = 7.6f,
                            weight = HdTextLayout.Weight.BOLD,
                            color = if (editingAnnounceSocial) HdColorSpec.Token("primary") else HdColorSpec.Token("text_dark_muted")
                        )
                    }
                }
            }
        }
    }

    private fun buildRealTimePicker(ed: HdBuilder) {
        val maxDay = LocalDate.of(editYear, editMonth, 1).lengthOfMonth()
        if (editDay > maxDay) editDay = maxDay

        ed.card(gap = 5f, padding = HdPadding.all(7f), radius = 8f, shadowBlur = 2f) { box ->
            // Date row: Year / Month / Day steppers
            box.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { dateRow ->
                buildStepper(dateRow, editYear.toString(),
                    onDec = { editYear = (editYear - 1).coerceAtLeast(2024) },
                    onInc = { editYear = (editYear + 1).coerceAtMost(2099) }
                )
                buildStepper(dateRow, String.format(Locale.ROOT, "%02d", editMonth),
                    onDec = {
                        if (editMonth == 1) { editMonth = 12; editYear = (editYear - 1).coerceAtLeast(2024) }
                        else editMonth--
                    },
                    onInc = {
                        if (editMonth == 12) { editMonth = 1; editYear = (editYear + 1).coerceAtMost(2099) }
                        else editMonth++
                    }
                )
                buildStepper(dateRow, String.format(Locale.ROOT, "%02d", editDay),
                    onDec = {
                        editDay = if (editDay <= 1) LocalDate.of(editYear, editMonth, 1).lengthOfMonth() else editDay - 1
                    },
                    onInc = {
                        val len = LocalDate.of(editYear, editMonth, 1).lengthOfMonth()
                        editDay = if (editDay >= len) 1 else editDay + 1
                    }
                )
            }

            // Time row: Hour / Minute steppers (5-minute steps)
            box.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { timeRow ->
                timeRow.text(
                    text = tr("cobblemon_smartphone.calendar.time_label"),
                    size = 7.8f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = HdColorSpec.Token("text_dark_muted")
                )
                buildStepper(timeRow, String.format(Locale.ROOT, "%02d", editHour),
                    onDec = { editHour = (editHour + 23) % 24 },
                    onInc = { editHour = (editHour + 1) % 24 }
                )
                timeRow.text(text = ":", size = 9f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
                buildStepper(timeRow, String.format(Locale.ROOT, "%02d", editMinute),
                    onDec = { editMinute = (editMinute + 55) % 60 },
                    onInc = { editMinute = (editMinute + 5) % 60 }
                )
            }
        }
    }

    private fun buildWorldTimePicker(ed: HdBuilder) {
        ed.card(gap = 5f, padding = HdPadding.all(7f), radius = 8f, shadowBlur = 2f) { box ->
            box.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { dayRow ->
                dayRow.button(label = "-10", variant = ButtonNode.Variant.SURFACE, fontSize = 7.4f, radius = 5f,
                    onClick = { playClickSound(); editWorldDay = (editWorldDay - 10L).coerceAtLeast(0L) }
                ) { height = HdSizeSpec.Fixed(18f); padding = HdPadding.symmetric(horizontal = 5f, vertical = 0f) }

                dayRow.button(label = "-1", variant = ButtonNode.Variant.SURFACE, fontSize = 7.4f, radius = 5f,
                    onClick = { playClickSound(); editWorldDay = (editWorldDay - 1L).coerceAtLeast(0L) }
                ) { height = HdSizeSpec.Fixed(18f); padding = HdPadding.symmetric(horizontal = 5f, vertical = 0f) }

                dayRow.text(
                    text = "${tr("cobblemon_smartphone.calendar.day_short", editWorldDay + 1L)} • ${moonName(CalendarTimeUtils.moonPhaseIndex(editWorldDay))}",
                    size = 8.0f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = HdColorSpec.Token("text_dark"),
                    align = HdAlign.CENTER
                ) {
                    width = HdSizeSpec.Weight(1f)
                }

                dayRow.button(label = "+1", variant = ButtonNode.Variant.SURFACE, fontSize = 7.4f, radius = 5f,
                    onClick = { playClickSound(); editWorldDay += 1L }
                ) { height = HdSizeSpec.Fixed(18f); padding = HdPadding.symmetric(horizontal = 5f, vertical = 0f) }

                dayRow.button(label = "+10", variant = ButtonNode.Variant.SURFACE, fontSize = 7.4f, radius = 5f,
                    onClick = { playClickSound(); editWorldDay += 10L }
                ) { height = HdSizeSpec.Fixed(18f); padding = HdPadding.symmetric(horizontal = 5f, vertical = 0f) }
            }

            // Period pills
            box.row(gap = 3f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { pRow ->
                for (period in WorldTimePeriod.entries) {
                    pRow.pill(
                        label = periodName(period),
                        active = editWorldPeriod == period,
                        onClick = { playClickSound(); editWorldPeriod = period }
                    ) {
                        fontSizeDp = 7.0f
                        padding = HdPadding.symmetric(horizontal = 4.5f, vertical = 2f)
                    }
                }
            }
        }
    }

    private fun buildStepper(
        row: HdBuilder,
        value: String,
        onDec: () -> Unit,
        onInc: () -> Unit
    ) {
        row.row(gap = 2f, crossAlign = HdAlign.CENTER) { s ->
            s.button(label = "-", variant = ButtonNode.Variant.SURFACE, fontSize = 8f, radius = 5f,
                onClick = { playClickSound(); onDec() }
            ) {
                width = HdSizeSpec.Fixed(16f)
                height = HdSizeSpec.Fixed(18f)
                padding = HdPadding.ZERO
            }
            s.text(
                text = value,
                size = 8.4f,
                weight = HdTextLayout.Weight.BOLD,
                color = HdColorSpec.Token("text_dark"),
                align = HdAlign.CENTER
            ) {
                width = HdSizeSpec.Fixed(26f)
            }
            s.button(label = "+", variant = ButtonNode.Variant.SURFACE, fontSize = 8f, radius = 5f,
                onClick = { playClickSound(); onInc() }
            ) {
                width = HdSizeSpec.Fixed(16f)
                height = HdSizeSpec.Fixed(18f)
                padding = HdPadding.ZERO
            }
        }
    }

    private fun saveEditingEvent() {
        val title = stateStore.getInputText(TITLE_INPUT_ID).trim()
        if (title.isBlank()) {
            showToast(tr("cobblemon_smartphone.calendar.title_required"))
            return
        }
        val desc = stateStore.getInputText(DESC_INPUT_ID).trim()
        val id = editingEventId ?: UUID.randomUUID().toString()

        val utcMillis = if (editingClockMode == CalendarClockMode.REAL_TIME) {
            CalendarTimeUtils.localDateTimeToUtcMillis(editYear, editMonth, editDay, editHour, editMinute)
        } else 0L

        val event = CalendarEvent(
            id = id,
            title = title,
            description = desc,
            clockMode = editingClockMode,
            startEpochMillis = utcMillis,
            worldDay = if (editingClockMode == CalendarClockMode.WORLD_TIME) editWorldDay else 0L,
            worldPeriod = editWorldPeriod,
            category = editingCategory,
            isServerEvent = editingIsServer,
            remind = editingRemind
        )

        if (editingIsServer) {
            CalendarClientState.upsertServer(event, announceInSocial = editingAnnounceSocial)
        } else {
            val ok = CalendarClientState.upsertPersonal(event)
            if (!ok) {
                showToast(tr("cobblemon_smartphone.calendar.limit_reached"))
                return
            }
        }

        // Navigate browse selection to the saved event's mode/day
        clockMode = editingClockMode
        if (editingClockMode == CalendarClockMode.REAL_TIME) {
            val d = CalendarTimeUtils.toLocalDate(utcMillis)
            selectedRealDate = d
            displayedMonth = YearMonth.from(d)
        } else {
            selectedWorldDay = editWorldDay
            worldGridStartDay = (editWorldDay / WORLD_PAGE_DAYS) * WORLD_PAGE_DAYS
        }

        showToast(tr("cobblemon_smartphone.calendar.saved"))
        screenMode = ScreenMode.BROWSE
    }

    // ==================== DELETE MODAL ====================

    override fun buildModal(theme: SmartphoneTheme): ModalSpec? {
        val target = confirmDeleteEvent ?: return null
        return ModalSpec(
            title = tr("cobblemon_smartphone.calendar.delete_confirm_title"),
            widthDp = 240f,
            onClose = { confirmDeleteEvent = null }
        ) { modal ->
            modal.text(
                text = tr("cobblemon_smartphone.calendar.delete_confirm_body", target.title),
                size = 8.6f,
                color = HdColorSpec.Token("text_dark_muted")
            )
            modal.row(gap = 6f, mainAlign = HdAlign.END, width = HdSizeSpec.Fill) { actions ->
                actions.button(
                    label = tr("cobblemon_smartphone.calendar.cancel"),
                    variant = ButtonNode.Variant.SURFACE,
                    fontSize = 8.4f,
                    radius = 7f,
                    onClick = {
                        playClickSound()
                        confirmDeleteEvent = null
                    }
                ) {
                    padding = HdPadding.symmetric(horizontal = 10f, vertical = 5f)
                }
                actions.button(
                    label = tr("cobblemon_smartphone.calendar.delete"),
                    variant = ButtonNode.Variant.DANGER,
                    fontSize = 8.4f,
                    radius = 7f,
                    onClick = {
                        playClickSound()
                        if (target.isServerEvent) {
                            CalendarClientState.deleteServer(target.id)
                        } else {
                            CalendarClientState.deletePersonal(target.id)
                        }
                        confirmDeleteEvent = null
                        detailEventId = null
                        screenMode = ScreenMode.BROWSE
                    }
                ) {
                    padding = HdPadding.symmetric(horizontal = 10f, vertical = 5f)
                }
            }
        }
    }

    // ==================== TRANSLATION HELPERS ====================

    private fun categoryName(cat: CalendarCategory): String =
        tr("cobblemon_smartphone.calendar.category.${cat.id}")

    private fun periodName(period: WorldTimePeriod): String =
        tr("cobblemon_smartphone.calendar.period.${period.id}")

    private fun moonName(phaseIndex: Int): String =
        tr("cobblemon_smartphone.calendar.moon.$phaseIndex")

    private fun tr(key: String, vararg args: Any): String =
        Component.translatable(key, *args).string

    companion object {
        private const val WORLD_PAGE_DAYS = 32L
        private const val DAY_LIST_SCROLL_ID = "calendar_day_list"
        private const val UPCOMING_SCROLL_ID = "calendar_upcoming_list"
        private const val DETAIL_SCROLL_ID = "calendar_detail"
        private const val EDITOR_SCROLL_ID = "calendar_editor"
        private const val TITLE_INPUT_ID = "calendar_title"
        private const val DESC_INPUT_ID = "calendar_desc"

        private val MOON_SHORT_LABELS = listOf("FM", "WG", "LQ", "WC", "NM", "XC", "FQ", "XG")
    }
}
