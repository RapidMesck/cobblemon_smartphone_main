package com.nbp.cobblemon_smartphone.client.dev

import com.nbp.cobblemon_smartphone.client.ui.hd.BadgeNode
import com.nbp.cobblemon_smartphone.client.ui.hd.ButtonNode
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBackground
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdStateStore
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle
import com.nbp.cobblemon_smartphone.client.ui.hd.SegmentedControlNode
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import net.minecraft.network.chat.Component

/**
 * Dev/QA showroom screen showcasing all HD UI components and vector icons in both
 * [HdUiStyle.HD] and [HdUiStyle.PIXEL] rendering modes.
 */
class HdShowroomScreen(
    color: SmartphoneColor = SmartphoneColor.RED,
    initialPage: Page = Page.CONTROLS,
    styleOverride: HdUiStyle = HdUiStyle.HD,
    scaleOverride: Float? = 1.0f,
    private val showSampleToast: Boolean = false
) : HdPhoneScreen(
    color = color,
    smartphoneStack = null,
    title = Component.literal("UI Showroom"),
    scaleOverride = scaleOverride,
    styleOverride = styleOverride
) {
    enum class Page { CONTROLS, FEEDBACK_ICONS }

    private var activePage: Page = initialPage
    private var autoScan: Boolean = true
    private var silentMode: Boolean = false
    private var scanRange: Float = 75f
    private var maxTargets: Float = 6f
    private var accordionExpanded: Boolean = true
    private var checkboxChecked: Boolean = true

    override val showBezelHdBadge: Boolean = true

    override fun init() {
        super.init()
        if (showSampleToast) {
            stateStore.showToast(
                message = "Showroom ready (${currentStyle().name})",
                kind = HdStateStore.ToastKind.SUCCESS,
                durationMs = 10_000L,
                nowMs = System.currentTimeMillis() - 400L
            )
        }
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        val style = currentStyle()

        // Top app header
        builder.row(
            gap = 6f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 5.5f),
            width = HdSizeSpec.Fill,
            background = HdBackground(color = HdColorSpec.Token("header"))
        ) { hb ->
            hb.icon(HdIconAtlas.Icon.SPARKLES, size = 13f, color = HdColorSpec.Token("gold"))
            hb.text(
                text = "UI Showroom",
                size = 11.0f,
                weight = HdTextLayout.Weight.BOLD,
                color = HdColorSpec.Token("text_primary")
            ) {
                width = HdSizeSpec.Weight(1f)
            }
            hb.badge(
                label = style.name,
                icon = if (style == HdUiStyle.HD) HdIconAtlas.Icon.STAR_FILLED else HdIconAtlas.Icon.SETTINGS,
                variant = if (style == HdUiStyle.HD) BadgeNode.Variant.GOLD else BadgeNode.Variant.PRIMARY
            )
        }

        // Page switcher (SegmentedControlNode)
        builder.column(
            padding = HdPadding.only(left = 8f, right = 8f, top = 6f, bottom = 3f),
            width = HdSizeSpec.Fill
        ) { sb ->
            sb.segmentedControl(
                options = listOf(
                    SegmentedControlNode.Option("controls", "Controls & Layout", HdIconAtlas.Icon.SETTINGS),
                    SegmentedControlNode.Option("feedback", "Feedback & Icons", HdIconAtlas.Icon.STAR)
                ),
                selectedId = if (activePage == Page.CONTROLS) "controls" else "feedback",
                onSelect = { id ->
                    playClickSound()
                    activePage = if (id == "controls") Page.CONTROLS else Page.FEEDBACK_ICONS
                }
            )
        }

        builder.scroll("showroom_${activePage.name.lowercase()}", gap = 6f, padding = HdPadding.symmetric(horizontal = 8f, vertical = 4f)) { body ->
            when (activePage) {
                Page.CONTROLS -> buildControlsPage(body)
                Page.FEEDBACK_ICONS -> buildFeedbackAndIconsPage(body)
            }
        }
    }

    private fun buildControlsPage(body: HdBuilder) {
        // Form controls card
        body.card(gap = 4.5f, padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f)) { c ->
            c.row(gap = 10f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { r ->
                r.switch(
                    label = "Auto Scan",
                    checked = autoScan,
                    onToggle = {
                        playClickSound()
                        autoScan = it
                    }
                ) { width = HdSizeSpec.Weight(1f) }

                r.switch(
                    label = "Silent",
                    checked = silentMode,
                    onToggle = {
                        playClickSound()
                        silentMode = it
                    }
                ) { width = HdSizeSpec.Weight(1f) }
            }

            c.divider()

            c.slider(
                value = scanRange,
                min = 10f,
                max = 100f,
                step = 5f,
                label = "Scan Range",
                valueFormat = { "${it.toInt()}m" },
                onChange = { scanRange = it }
            )

            c.numberStepper(
                value = maxTargets,
                min = 1f,
                max = 12f,
                step = 1f,
                label = "Max Targets",
                onChange = {
                    playClickSound()
                    maxTargets = it
                }
            ) { width = HdSizeSpec.Fill }
        }

        // Accordion with FlowRow + Badges + StatBars
        body.accordion(
            title = "Stats & Tags",
            subtitle = "FlowRow + Badge + StatBar",
            icon = HdIconAtlas.Icon.FILTER,
            expanded = accordionExpanded,
            gap = 4f,
            onToggle = {
                playClickSound()
                accordionExpanded = it
            }
        ) { acc ->
            headerPadding = HdPadding.symmetric(horizontal = 8f, vertical = 5f)
            bodyPadding = HdPadding.only(left = 8f, right = 8f, top = 4f, bottom = 6f)
            acc.flowRow(hGap = 4f, vGap = 4f) { fr ->
                fr.badge("Primary", icon = HdIconAtlas.Icon.SPARKLES, variant = BadgeNode.Variant.PRIMARY)
                fr.badge("Active", icon = HdIconAtlas.Icon.CHECK, variant = BadgeNode.Variant.SUCCESS)
                fr.badge("Alert", icon = HdIconAtlas.Icon.WARNING, variant = BadgeNode.Variant.WARNING)
                fr.badge("Locked", icon = HdIconAtlas.Icon.LOCK, variant = BadgeNode.Variant.DANGER)
                fr.badge("Shiny", icon = HdIconAtlas.Icon.STAR_FILLED, variant = BadgeNode.Variant.GOLD)
                fr.badge("Neutral", variant = BadgeNode.Variant.NEUTRAL)
            }
            acc.statBar("HP", 108f, 255f, fillColor = HdColorSpec.Fixed(0xFF2EAD5B.toInt()))
            acc.statBar("SPE", 142f, 255f, fillColor = HdColorSpec.Fixed(0xFF3F7FE5.toInt()))
        }

        // ListTile
        body.listTile(
            title = "Appearance & Theme",
            subtitle = "Cyan Modern • ${currentStyle().name}",
            leadingIcon = HdIconAtlas.Icon.COMPASS,
            trailingText = "Edit",
            showChevron = true,
            onClick = {
                playClickSound()
                showToast("ListTile clicked!", HdStateStore.ToastKind.INFO)
            }
        ) {
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 5f)
        }
    }

    private fun buildFeedbackAndIconsPage(body: HdBuilder) {
        // EmptyState inside a card
        body.card(gap = 0f, padding = HdPadding.all(1f)) { c ->
            c.emptyState(
                title = "No Targets Tracked",
                description = "Add a species to start scanning nearby biomes.",
                icon = HdIconAtlas.Icon.COMPASS,
                actionLabel = "Add Target",
                actionIcon = HdIconAtlas.Icon.PLUS,
                onAction = {
                    playClickSound()
                    showToast("Action triggered!", HdStateStore.ToastKind.SUCCESS)
                }
            ) {
                padding = HdPadding.symmetric(horizontal = 10f, vertical = 6f)
            }
        }

        // New vector icons showcase
        body.card(gap = 4f, padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f)) { c ->
            c.text("New Vector Icons (14)", size = 9.0f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
            val newIcons = listOf(
                HdIconAtlas.Icon.CHEVRON_DOWN,
                HdIconAtlas.Icon.CHEVRON_UP,
                HdIconAtlas.Icon.CHEVRON_RIGHT,
                HdIconAtlas.Icon.MINUS,
                HdIconAtlas.Icon.SETTINGS,
                HdIconAtlas.Icon.FILTER,
                HdIconAtlas.Icon.REFRESH,
                HdIconAtlas.Icon.STAR,
                HdIconAtlas.Icon.STAR_FILLED,
                HdIconAtlas.Icon.INFO,
                HdIconAtlas.Icon.WARNING,
                HdIconAtlas.Icon.LOCK,
                HdIconAtlas.Icon.EDIT,
                HdIconAtlas.Icon.COMPASS
            )
            c.flowRow(hGap = 4f, vGap = 4f) { fr ->
                for (ic in newIcons) {
                    fr.row(
                        gap = 3f,
                        crossAlign = HdAlign.CENTER,
                        padding = HdPadding.symmetric(horizontal = 4.5f, vertical = 2.5f),
                        background = HdBackground(
                            color = HdColorSpec.Token("surface_alt"),
                            radius = 6f,
                            borderWidth = 0.7f,
                            borderColor = HdColorSpec.Token("primary_border")
                        )
                    ) { chip ->
                        chip.icon(ic, size = 11f, color = HdColorSpec.Token("primary"))
                        chip.text(ic.name.lowercase().replace('_', ' '), size = 7.0f, weight = HdTextLayout.Weight.MEDIUM, color = HdColorSpec.Token("text_dark"))
                    }
                }
            }
        }

        // Classic widgets comparison row
        body.card(gap = 4f, padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f)) { c ->
            c.text("Buttons & Inputs", size = 9.0f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
            c.flowRow(hGap = 5f, vGap = 4f) { fr ->
                fr.button("Primary", icon = HdIconAtlas.Icon.CHECK, variant = ButtonNode.Variant.PRIMARY, fontSize = 8.2f)
                fr.button("Surface", icon = HdIconAtlas.Icon.EDIT, variant = ButtonNode.Variant.SURFACE, fontSize = 8.2f)
                fr.button("Danger", icon = HdIconAtlas.Icon.TRASH, variant = ButtonNode.Variant.DANGER, fontSize = 8.2f)
            }
            c.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { r ->
                r.checkbox("Enabled", checked = checkboxChecked, onToggle = { checkboxChecked = it })
                r.textInput("showroom_search", placeholder = "Search...", initialText = "Pikachu") {
                    width = HdSizeSpec.Weight(1f)
                }
            }
        }
    }
}
