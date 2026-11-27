package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.nbp.cobblemon_smartphone.client.RotomDroneClientManager
import com.nbp.cobblemon_smartphone.client.RotomRadarClientState
import com.nbp.cobblemon_smartphone.client.RotomScannerClientState
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
import com.nbp.cobblemon_smartphone.client.ui.hd.TabsNode
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.DefuseRotomPacket
import com.nbp.cobblemon_smartphone.rotom.RotomRadarConfig
import com.nbp.cobblemon_smartphone.rotom.RotomTarget
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Rotom Radar configuration and targets management screen built on the HD [HdPhoneScreen] UI engine.
 */
class RotomAppScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    initialTab: Tab = Tab.SETTINGS,
    initialAddingTarget: Boolean = false,
    scaleOverride: Float? = null
) : HdPhoneScreen(
    color,
    smartphoneStack,
    Component.translatable("cobblemon_smartphone.screen.rotom"),
    scaleOverride
) {

    enum class Tab {
        SETTINGS,
        TARGETS
    }

    data class SpeciesSuggestion(
        val name: String,
        val path: String,
        val translatedName: String,
        val dexNumber: Int
    )

    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelHdBadge: Boolean = true

    private var currentTab = initialTab
    private var isAddingTarget = initialAddingTarget
    private var showDefuseConfirmation = false

    private var selectedNatureIndex = 0
    private var selectedMinIvPercent = 0
    private var selectedMinPerfectIvs = 0
    private var selectedShinyOnly = false

    private var cachedSpeciesList: List<SpeciesSuggestion>? = null
    private var lastSuggestionQuery: String? = null
    private var currentSuggestions: List<SpeciesSuggestion> = emptyList()
    private var selectedSuggestionIndex = -1

    private val config: RotomRadarConfig
        get() = RotomRadarClientState.config

    override fun buildModal(theme: SmartphoneTheme): ModalSpec? {
        if (!showDefuseConfirmation) return null
        return ModalSpec(
            title = lang("dialog_defuse_title"),
            widthDp = 248f,
            onClose = { showDefuseConfirmation = false }
        ) { body ->
            body.text(
                text = lang("dialog_defuse_desc_1"),
                size = 9.6f,
                color = HdColorSpec.Token("text_dark"),
                wrap = true
            ) {
                width = HdSizeSpec.Fill
            }
            body.text(
                text = lang("dialog_defuse_desc_2"),
                size = 9.4f,
                color = HdColorSpec.Token("text_dark_muted"),
                wrap = true
            ) {
                width = HdSizeSpec.Fill
            }
            body.spacer(h = 4f)
            body.row(gap = 8f, width = HdSizeSpec.Fill) { actions ->
                actions.button(
                    label = lang("btn_cancel"),
                    variant = ButtonNode.Variant.SECONDARY,
                    fontSize = 9.4f,
                    onClick = {
                        playClickSound()
                        showDefuseConfirmation = false
                    }
                ) {
                    width = HdSizeSpec.Weight(1f)
                }
                actions.button(
                    label = lang("btn_confirm"),
                    variant = ButtonNode.Variant.DANGER,
                    fontSize = 9.4f,
                    onClick = {
                        playClickSound()
                        DefuseRotomPacket().sendToServer()
                        Minecraft.getInstance().setScreen(null)
                    }
                ) {
                    width = HdSizeSpec.Weight(1f)
                }
            }
        }
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        buildHeader(builder)
        when (currentTab) {
            Tab.SETTINGS -> buildSettingsTab(builder)
            Tab.TARGETS -> if (isAddingTarget) buildAddTargetForm(builder) else buildTargetsTab(builder)
        }
    }

    private fun buildHeader(builder: HdBuilder) {
        builder.appHeader(
            title = lang("title"),
            icon = HdIconAtlas.Icon.SPARKLES,
            iconColor = HdColorSpec.Token("primary"),
            bottomContent = { hdr ->
                hdr.segmentedControl(
                    options = listOf(
                        com.nbp.cobblemon_smartphone.client.ui.hd.SegmentedControlNode.Option(
                            Tab.SETTINGS.name,
                            lang("tab_settings"),
                            HdIconAtlas.Icon.SETTINGS
                        ),
                        com.nbp.cobblemon_smartphone.client.ui.hd.SegmentedControlNode.Option(
                            Tab.TARGETS.name,
                            lang("tab_targets", config.targets.size),
                            HdIconAtlas.Icon.FILTER
                        )
                    ),
                    selectedId = currentTab.name,
                    onSelect = { id ->
                        val next = Tab.valueOf(id)
                        if (currentTab != next) {
                            playClickSound()
                            currentTab = next
                            isAddingTarget = false
                            stateStore.clearFocus()
                        }
                    }
                )
            }
        )
    }

    private fun buildSettingsTab(builder: HdBuilder) {
        builder.scroll(
            scrollId = "rotom_settings_scroll",
            gap = 5f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f)
        ) { list ->
            if (RotomScannerClientState.clientConfig.enableDrone) {
                buildDroneLaunchRow(list)
            }
            buildToggleRow(
                list,
                lang("setting_radar_title"),
                lang("setting_radar_desc"),
                config.enabled
            ) {
                config.enabled = !config.enabled
                RotomRadarClientState.saveConfig()
            }
            buildToggleRow(
                list,
                lang("setting_shiny_title"),
                lang("setting_shiny_desc"),
                config.shinyAlert
            ) {
                config.shinyAlert = !config.shinyAlert
                RotomRadarClientState.saveConfig()
            }
            buildToggleRow(
                list,
                lang("setting_targets_title"),
                lang("setting_targets_desc"),
                config.targetAlert
            ) {
                config.targetAlert = !config.targetAlert
                RotomRadarClientState.saveConfig()
            }
            buildToggleRow(
                list,
                lang("setting_sound_title"),
                lang("setting_sound_desc"),
                config.soundAlert
            ) {
                config.soundAlert = !config.soundAlert
                RotomRadarClientState.saveConfig()
            }
            buildToggleRow(
                list,
                lang("setting_autoscan_title"),
                lang("setting_autoscan_desc"),
                config.autoScanPokedex
            ) {
                config.autoScanPokedex = !config.autoScanPokedex
                RotomRadarClientState.saveConfig()
            }
            buildSegmentedRow(
                builder = list,
                title = lang("setting_curiosity_title"),
                desc = lang("setting_curiosity_desc"),
                options = listOf(
                    0 to lang("mode_full"),
                    1 to lang("mode_mini"),
                    2 to lang("mode_off")
                ),
                selectedValue = config.scanDisplayMode
            ) { mode ->
                config.scanDisplayMode = mode
                RotomRadarClientState.saveConfig()
            }
            buildSegmentedRow(
                builder = list,
                title = lang("setting_language_title"),
                desc = lang("setting_language_desc"),
                options = listOf("en" to "EN", "pt" to "PT"),
                selectedValue = if (config.language.equals("pt", ignoreCase = true)) "pt" else "en"
            ) { langCode ->
                config.language = langCode
                RotomScannerClientState.clientConfig.curiosityLanguage = langCode
                RotomRadarClientState.saveConfig()
            }
            buildToggleRow(
                list,
                lang("setting_hud_tracker_title"),
                lang("setting_hud_tracker_desc"),
                config.hudTracker
            ) {
                config.hudTracker = !config.hudTracker
                RotomRadarClientState.saveConfig()
            }
            buildToggleRow(
                list,
                lang("setting_invert_direction_title"),
                lang("setting_invert_direction_desc"),
                config.invertCameraDirection
            ) {
                config.invertCameraDirection = !config.invertCameraDirection
                RotomRadarClientState.saveConfig()
            }
            buildToggleRow(
                list,
                lang("setting_auto_direction_title"),
                lang("setting_auto_direction_desc"),
                config.autoAdjustDirection
            ) {
                config.autoAdjustDirection = !config.autoAdjustDirection
                RotomRadarClientState.saveConfig()
            }
            buildDefuseRow(list)
        }
    }

    private fun buildDroneLaunchRow(builder: HdBuilder) {
        val maxRange = RotomScannerClientState.clientConfig.droneMaxRange.toInt()
        builder.card(
            gap = 4f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 7f),
            radius = 8f,
            shadowBlur = 3f,
            hoverHighlight = true
        ) { card ->
            card.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row ->
                row.column(gap = 1.5f, width = HdSizeSpec.Weight(1f)) { col ->
                    col.text(
                        lang("setting_drone_title"),
                        size = 9.6f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("text_dark")
                    )
                    col.text(
                        lang("setting_drone_desc", maxRange),
                        size = 8.2f,
                        color = HdColorSpec.Token("text_dark_muted"),
                        wrap = true
                    ) {
                        width = HdSizeSpec.Fill
                    }
                }
                row.button(
                    label = lang("btn_launch_drone"),
                    variant = ButtonNode.Variant.PRIMARY,
                    fontSize = 8.6f,
                    onClick = {
                        playClickSound()
                        RotomDroneClientManager.activate(color, smartphoneStack)
                    }
                )
            }
        }
    }

    private fun buildToggleRow(
        builder: HdBuilder,
        title: String,
        desc: String,
        enabled: Boolean,
        onToggle: () -> Unit
    ) {
        builder.card(
            gap = 4f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f),
            radius = 8f,
            shadowBlur = 3f,
            hoverHighlight = true,
            onClick = {
                playClickSound()
                onToggle()
            }
        ) { card ->
            card.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row ->
                row.column(gap = 1.5f, width = HdSizeSpec.Weight(1f)) { col ->
                    col.text(title, size = 9.6f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
                    col.text(desc, size = 8.2f, color = HdColorSpec.Token("text_dark_muted"), wrap = true) {
                        width = HdSizeSpec.Fill
                    }
                }
                row.switch(
                    checked = enabled,
                    onToggle = {
                        playClickSound()
                        onToggle()
                    }
                )
            }
        }
    }

    private fun <T> buildSegmentedRow(
        builder: HdBuilder,
        title: String,
        desc: String,
        options: List<Pair<T, String>>,
        selectedValue: T,
        onSelect: (T) -> Unit
    ) {
        builder.card(
            gap = 4f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f),
            radius = 8f,
            shadowBlur = 3f
        ) { card ->
            card.row(gap = 6f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row ->
                row.column(gap = 1.5f, width = HdSizeSpec.Weight(1f)) { col ->
                    col.text(title, size = 9.6f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
                    col.text(desc, size = 8.2f, color = HdColorSpec.Token("text_dark_muted"), wrap = true) {
                        width = HdSizeSpec.Fill
                    }
                }
                row.row(
                    gap = 2f,
                    padding = HdPadding.all(2f),
                    background = HdBackground(
                        color = HdColorSpec.Token("surface_alt"),
                        radius = 7f,
                        borderWidth = 0.6f,
                        borderColor = HdColorSpec.Token("primary_border")
                    )
                ) { segGroup ->
                    for ((value, label) in options) {
                        val active = value == selectedValue
                        segGroup.row(
                            padding = HdPadding.symmetric(horizontal = 6f, vertical = 3f),
                            background = HdBackground(
                                color = if (active) HdColorSpec.Token("primary") else HdColorSpec.TRANSPARENT,
                                radius = 5f
                            )
                        ) { seg ->
                            hoverBackground = HdBackground(
                                color = if (active) HdColorSpec.Token("primary_hover") else HdColorSpec.Token("surface_hover"),
                                radius = 5f
                            )
                            onClick = {
                                playClickSound()
                                onSelect(value)
                            }
                            seg.text(
                                text = label,
                                size = 8.0f,
                                weight = if (active) HdTextLayout.Weight.BOLD else HdTextLayout.Weight.REGULAR,
                                color = if (active) HdColorSpec.Token("text_primary") else HdColorSpec.Token("text_dark_muted")
                            )
                        }
                    }
                }
            }
        }
    }

    private fun buildDefuseRow(builder: HdBuilder) {
        builder.card(
            gap = 4f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f),
            radius = 8f,
            shadowBlur = 3f,
            hoverHighlight = true,
            onClick = {
                playClickSound()
                showDefuseConfirmation = true
            }
        ) { card ->
            card.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row ->
                row.column(gap = 1.5f, width = HdSizeSpec.Weight(1f)) { col ->
                    col.text(
                        text = lang("setting_defuse_title"),
                        size = 9.6f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("danger")
                    )
                    col.text(
                        text = lang("setting_defuse_desc"),
                        size = 8.2f,
                        color = HdColorSpec.Token("text_dark_muted"),
                        wrap = true
                    ) {
                        width = HdSizeSpec.Fill
                    }
                }
                row.button(
                    label = lang("btn_defuse"),
                    variant = ButtonNode.Variant.DANGER,
                    fontSize = 8.4f,
                    radius = 7f,
                    onClick = {
                        playClickSound()
                        showDefuseConfirmation = true
                    }
                ) {
                    padding = HdPadding.symmetric(horizontal = 8f, vertical = 4f)
                }
            }
        }
    }

    private fun buildTargetsTab(builder: HdBuilder) {
        builder.column(
            gap = 6f,
            padding = HdPadding.only(left = 8f, right = 8f, top = 6f, bottom = 0f),
            width = HdSizeSpec.Fill
        ) { top ->
            top.button(
                label = lang("btn_add_target"),
                variant = ButtonNode.Variant.PRIMARY,
                fontSize = 9.4f,
                radius = 8f,
                onClick = {
                    playClickSound()
                    openAddTargetForm()
                }
            ) {
                width = HdSizeSpec.Fill
            }
        }

        builder.scroll(
            scrollId = "rotom_targets_scroll",
            gap = 6f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f)
        ) { list ->
            if (config.targets.isEmpty()) {
                val emptyDesc = "${lang("targets_empty_2")} ${lang("targets_empty_3")}".trim()
                list.column(
                    gap = 5f,
                    crossAlign = HdAlign.CENTER,
                    padding = HdPadding.symmetric(horizontal = 16f, vertical = 26f),
                    width = HdSizeSpec.Fill
                ) { empty ->
                    empty.icon(HdIconAtlas.Icon.SPARKLES, size = 22f, color = HdColorSpec.Token("primary"))
                    empty.text(
                        text = lang("targets_empty_1"),
                        size = 10.4f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("text_primary"),
                        align = HdAlign.CENTER,
                        wrap = true
                    ) {
                        width = HdSizeSpec.Fill
                    }
                    empty.text(
                        text = emptyDesc,
                        size = 8.8f,
                        color = HdColorSpec.Token("text_secondary"),
                        align = HdAlign.CENTER,
                        wrap = true
                    ) {
                        width = HdSizeSpec.Fill
                    }
                }
            } else {
                config.targets.forEachIndexed { index, target ->
                    buildTargetCard(list, index, target)
                }
            }
        }
    }

    private fun buildTargetCard(builder: HdBuilder, index: Int, target: RotomTarget) {
        val specName = if (target.species.isBlank() || target.species == "any") {
            lang("species_any")
        } else {
            target.species.replaceFirstChar(Char::uppercase)
        }
        val title = if (target.shinyOnly) "$specName ✨" else specName

        val summaryParts = buildList {
            if (target.minIvPercent > 0) add("IV ≥${target.minIvPercent}%")
            if (target.minPerfectIvs > 0) add("${target.minPerfectIvs}x 31")
            if (target.nature.isNotBlank() && target.nature != "any") {
                add(target.nature.replaceFirstChar(Char::uppercase))
            }
        }
        val summary = if (summaryParts.isEmpty()) lang("filters_none") else summaryParts.joinToString(" • ")

        builder.row(
            gap = 6f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.all(7f),
            width = HdSizeSpec.Fill,
            background = HdBackground(
                color = HdColorSpec.Token("surface"),
                radius = 8f,
                borderWidth = 0.7f,
                borderColor = if (target.enabled) HdColorSpec.Token("primary_border") else HdColorSpec.Token("surface_alt"),
                shadowBlur = 4f,
                shadowOffsetY = 1.5f,
                shadowColor = HdColorSpec.Fixed(0x28000000)
            )
        ) { card ->
            card.divider(
                thickness = 3f,
                vertical = true,
                color = if (target.enabled) HdColorSpec.Token("primary") else HdColorSpec.Token("text_dark_muted")
            ).apply {
                height = HdSizeSpec.Fixed(26f)
            }
            card.column(gap = 2f, width = HdSizeSpec.Weight(1f)) { info ->
                info.text(
                    text = title,
                    size = 10f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = if (target.enabled) HdColorSpec.Token("text_dark") else HdColorSpec.Token("text_dark_muted"),
                    wrap = true
                ) {
                    width = HdSizeSpec.Fill
                }
                info.text(
                    text = summary,
                    size = 8.4f,
                    color = HdColorSpec.Token("text_dark_muted"),
                    wrap = true
                ) {
                    width = HdSizeSpec.Fill
                }
            }
            card.button(
                label = "",
                icon = if (target.enabled) HdIconAtlas.Icon.CHECK else HdIconAtlas.Icon.CLOSE,
                variant = if (target.enabled) ButtonNode.Variant.PRIMARY else ButtonNode.Variant.SECONDARY,
                fontSize = 9.0f,
                radius = 6f,
                onClick = {
                    playClickSound()
                    target.enabled = !target.enabled
                    RotomRadarClientState.saveConfig()
                }
            ) {
                padding = HdPadding.symmetric(horizontal = 6f, vertical = 4f)
            }
            card.button(
                label = "",
                icon = HdIconAtlas.Icon.TRASH,
                variant = ButtonNode.Variant.DANGER,
                fontSize = 9f,
                radius = 6f,
                onClick = {
                    playClickSound()
                    if (index in config.targets.indices) {
                        config.targets.removeAt(index)
                        RotomRadarClientState.saveConfig()
                    }
                }
            ) {
                padding = HdPadding.symmetric(horizontal = 6f, vertical = 4f)
            }
        }
    }

    private fun buildAddTargetForm(builder: HdBuilder) {
        builder.scroll(
            scrollId = "rotom_add_target_scroll",
            gap = 7f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f)
        ) { outer ->
            outer.column(
                gap = 7f,
                padding = HdPadding.all(8f),
                width = HdSizeSpec.Fill,
                background = HdBackground(
                    color = HdColorSpec.Fixed(0x880D1520.toInt()),
                    radius = 9f,
                    borderWidth = 0.7f,
                    borderColor = HdColorSpec.Token("primary_border", 0.75f)
                )
            ) { form ->
            // Species input
            form.column(gap = 3f, width = HdSizeSpec.Fill) { sec ->
                sec.text(lang("form_species"), size = 9.2f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_primary"))
                sec.textInput(
                    inputId = SPECIES_INPUT_ID,
                    placeholder = lang("species_hint"),
                    maxLength = 32,
                    multiline = false,
                    onChange = { getActiveSuggestions() }
                ) {
                    width = HdSizeSpec.Fill
                }

                val suggestions = getActiveSuggestions()
                if (stateStore.focusedId == SPECIES_INPUT_ID && suggestions.isNotEmpty()) {
                    sec.column(
                        gap = 2f,
                        padding = HdPadding.all(4f),
                        width = HdSizeSpec.Fill,
                        background = HdBackground(
                            color = HdColorSpec.Token("surface"),
                            radius = 7f,
                            borderWidth = 0.7f,
                            borderColor = HdColorSpec.Token("primary_border")
                        )
                    ) { sugBox ->
                        suggestions.forEachIndexed { idx, sug ->
                            val selected = idx == selectedSuggestionIndex
                            sugBox.row(
                                gap = 4f,
                                crossAlign = HdAlign.CENTER,
                                padding = HdPadding.symmetric(horizontal = 6f, vertical = 3.5f),
                                width = HdSizeSpec.Fill,
                                background = HdBackground(
                                    color = if (selected) HdColorSpec.Token("primary") else HdColorSpec.TRANSPARENT,
                                    radius = 5f
                                )
                            ) { item ->
                                hoverBackground = HdBackground(
                                    color = HdColorSpec.Token("surface_hover"),
                                    radius = 5f
                                )
                                onClick = {
                                    playClickSound()
                                    insertSuggestion(sug)
                                }
                                val dexText = if (sug.dexNumber > 0) "#${sug.dexNumber} " else ""
                                item.text(
                                    text = "$dexText${sug.translatedName}",
                                    size = 8.8f,
                                    weight = HdTextLayout.Weight.BOLD,
                                    color = if (selected) HdColorSpec.Token("text_primary") else HdColorSpec.Token("text_dark")
                                ) {
                                    width = HdSizeSpec.Weight(1f)
                                }
                                item.text(
                                    text = lang("badge_insert"),
                                    size = 7.8f,
                                    color = if (selected) HdColorSpec.Token("text_primary") else HdColorSpec.Token("text_dark_muted")
                                )
                            }
                        }
                    }
                }
            }

            // Nature selector
            val natureLabel = if (selectedNatureIndex == 0) {
                lang("nature_any")
            } else {
                NATURE_OPTIONS[selectedNatureIndex].replaceFirstChar(Char::uppercase)
            }
            buildCycleSelector(
                builder = form,
                title = lang("form_nature"),
                valueLabel = natureLabel,
                onPrev = { selectedNatureIndex = (selectedNatureIndex - 1 + NATURE_OPTIONS.size) % NATURE_OPTIONS.size },
                onNext = { selectedNatureIndex = (selectedNatureIndex + 1) % NATURE_OPTIONS.size }
            )

            // Min IV %
            val ivLabel = if (selectedMinIvPercent == 0) lang("iv_any") else "$selectedMinIvPercent%"
            buildCycleSelector(
                builder = form,
                title = lang("form_min_iv"),
                valueLabel = ivLabel,
                onPrev = {
                    val idx = IV_STEPS.indexOf(selectedMinIvPercent).coerceAtLeast(0)
                    selectedMinIvPercent = IV_STEPS[(idx - 1 + IV_STEPS.size) % IV_STEPS.size]
                },
                onNext = {
                    val idx = IV_STEPS.indexOf(selectedMinIvPercent).coerceAtLeast(0)
                    selectedMinIvPercent = IV_STEPS[(idx + 1) % IV_STEPS.size]
                }
            )

            // Min Perfect 31s
            val iv31Label = if (selectedMinPerfectIvs == 0) lang("iv_any") else "$selectedMinPerfectIvs x 31"
            buildCycleSelector(
                builder = form,
                title = lang("form_min_31s"),
                valueLabel = iv31Label,
                onPrev = { selectedMinPerfectIvs = (selectedMinPerfectIvs - 1 + 7) % 7 },
                onNext = { selectedMinPerfectIvs = (selectedMinPerfectIvs + 1) % 7 }
            )

            // Shiny only row
            form.card(
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 5f),
                radius = 8f,
                hoverHighlight = true,
                onClick = {
                    playClickSound()
                    selectedShinyOnly = !selectedShinyOnly
                }
            ) { row ->
                row.row(gap = 6f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { r ->
                    r.text(
                        text = lang("form_shiny_only"),
                        size = 9.4f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("text_dark")
                    ) {
                        width = HdSizeSpec.Weight(1f)
                    }
                    r.row(
                        padding = HdPadding.symmetric(horizontal = 10f, vertical = 3.5f),
                        background = HdBackground(
                            color = if (selectedShinyOnly) HdColorSpec.Token("success") else HdColorSpec.Token("surface_alt"),
                            radius = 10f,
                            borderWidth = 0.6f,
                            borderColor = if (selectedShinyOnly) HdColorSpec.Token("success") else HdColorSpec.Token("primary_border")
                        )
                    ) { badge ->
                        badge.text(
                            text = if (selectedShinyOnly) lang("status_yes") else lang("status_no"),
                            size = 8.4f,
                            weight = HdTextLayout.Weight.BOLD,
                            color = if (selectedShinyOnly) HdColorSpec.Token("text_primary") else HdColorSpec.Token("text_dark_muted")
                        )
                    }
                }
            }

            // Save / Cancel
            form.row(gap = 8f, width = HdSizeSpec.Fill) { actions ->
                actions.button(
                    label = lang("btn_save_target"),
                    variant = ButtonNode.Variant.PRIMARY,
                    fontSize = 9.4f,
                    radius = 8f,
                    onClick = {
                        playClickSound()
                        saveNewTarget()
                    }
                ) {
                    width = HdSizeSpec.Weight(1f)
                }
                actions.button(
                    label = lang("btn_cancel"),
                    variant = ButtonNode.Variant.SECONDARY,
                    fontSize = 9.4f,
                    radius = 8f,
                    onClick = {
                        playClickSound()
                        isAddingTarget = false
                        stateStore.clearFocus()
                    }
                ) {
                    width = HdSizeSpec.Weight(1f)
                }
            }
            }
        }
    }

    private fun buildCycleSelector(
        builder: HdBuilder,
        title: String,
        valueLabel: String,
        onPrev: () -> Unit,
        onNext: () -> Unit
    ) {
        builder.column(gap = 2.5f, width = HdSizeSpec.Fill) { col ->
            col.text(title, size = 9.0f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_primary"))
            col.row(
                gap = 4f,
                crossAlign = HdAlign.CENTER,
                padding = HdPadding.all(2f),
                width = HdSizeSpec.Fill,
                background = HdBackground(
                    color = HdColorSpec.Token("surface"),
                    radius = 7f,
                    borderWidth = 0.7f,
                    borderColor = HdColorSpec.Token("primary_border")
                )
            ) { bar ->
                bar.button(
                    label = "<",
                    variant = ButtonNode.Variant.SECONDARY,
                    fontSize = 9.6f,
                    radius = 5f,
                    onClick = {
                        playClickSound()
                        onPrev()
                    }
                ) {
                    padding = HdPadding.symmetric(horizontal = 8f, vertical = 2.5f)
                }
                bar.text(
                    text = valueLabel,
                    size = 9.2f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = HdColorSpec.Token("text_dark"),
                    align = HdAlign.CENTER
                ) {
                    width = HdSizeSpec.Weight(1f)
                }
                bar.button(
                    label = ">",
                    variant = ButtonNode.Variant.SECONDARY,
                    fontSize = 9.6f,
                    radius = 5f,
                    onClick = {
                        playClickSound()
                        onNext()
                    }
                ) {
                    padding = HdPadding.symmetric(horizontal = 8f, vertical = 2.5f)
                }
            }
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (currentTab == Tab.TARGETS && isAddingTarget && stateStore.focusedId == SPECIES_INPUT_ID) {
            val suggestions = getActiveSuggestions()
            if (suggestions.isNotEmpty()) {
                if (keyCode == 264) { // Down
                    selectedSuggestionIndex = (selectedSuggestionIndex + 1).coerceAtMost(suggestions.size - 1)
                    return true
                }
                if (keyCode == 265) { // Up
                    selectedSuggestionIndex = (selectedSuggestionIndex - 1).coerceAtLeast(-1)
                    return true
                }
                if (keyCode == 258) { // Tab
                    val chosen = suggestions.getOrNull(selectedSuggestionIndex) ?: suggestions.firstOrNull()
                    if (chosen != null) {
                        playClickSound()
                        insertSuggestion(chosen)
                        return true
                    }
                }
                if ((keyCode == 257 || keyCode == 335) && selectedSuggestionIndex in suggestions.indices) {
                    playClickSound()
                    insertSuggestion(suggestions[selectedSuggestionIndex])
                    return true
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun openAddTargetForm() {
        isAddingTarget = true
        val input = stateStore.input(SPECIES_INPUT_ID)
        input.text = ""
        input.cursor = 0
        input.selectionAnchor = null
        stateStore.focusedId = SPECIES_INPUT_ID
        selectedSuggestionIndex = -1
        lastSuggestionQuery = null
        getActiveSuggestions()
        selectedNatureIndex = 0
        selectedMinIvPercent = 0
        selectedMinPerfectIvs = 0
        selectedShinyOnly = false
    }

    private fun saveNewTarget() {
        val rawSpecies = stateStore.getInputText(SPECIES_INPUT_ID).trim().lowercase()
        val nat = if (selectedNatureIndex == 0) "" else NATURE_OPTIONS[selectedNatureIndex]
        val newTarget = RotomTarget(
            species = rawSpecies,
            minIvPercent = selectedMinIvPercent,
            minPerfectIvs = selectedMinPerfectIvs,
            nature = nat,
            shinyOnly = selectedShinyOnly,
            enabled = true
        )
        config.targets.add(newTarget)
        RotomRadarClientState.saveConfig()
        isAddingTarget = false
        stateStore.clearFocus()
    }

    private fun getSpeciesList(): List<SpeciesSuggestion> {
        if (cachedSpeciesList == null) {
            val src = PokemonSpecies.implemented.ifEmpty { PokemonSpecies.species.toList() }
            cachedSpeciesList = src.map { sp ->
                SpeciesSuggestion(
                    name = sp.name,
                    path = sp.resourceIdentifier.path,
                    translatedName = sp.translatedName.string,
                    dexNumber = sp.nationalPokedexNumber
                )
            }.sortedWith(
                compareBy<SpeciesSuggestion> {
                    if (it.dexNumber > 0) it.dexNumber else Int.MAX_VALUE
                }.thenBy {
                    it.name.lowercase()
                }
            )
        }
        return cachedSpeciesList ?: emptyList()
    }

    private fun filterSuggestions(query: String): List<SpeciesSuggestion> {
        val all = getSpeciesList()
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            val popular = listOf("ditto", "pikachu", "eevee", "charizard", "gengar", "lucario", "mewtwo")
            return popular.mapNotNull { pop -> all.firstOrNull { it.path == pop || it.name.lowercase() == pop } }
        }
        val exactMatch = all.firstOrNull { it.path == q || it.name.lowercase() == q }
        if (exactMatch != null) {
            val others = all.filter {
                (it.path != exactMatch.path && it.name != exactMatch.name) &&
                    (it.path.startsWith(q) || it.name.lowercase().startsWith(q))
            }
            if (others.isEmpty()) return emptyList()
        }
        val prefixMatches = all.filter {
            it.path.startsWith(q) || it.name.lowercase().startsWith(q) || it.translatedName.lowercase().startsWith(q)
        }
        val containsMatches = all.filter {
            !prefixMatches.contains(it) &&
                (it.path.contains(q) || it.name.lowercase().contains(q) || it.translatedName.lowercase().contains(q))
        }
        return (prefixMatches + containsMatches).take(5)
    }

    private fun getActiveSuggestions(): List<SpeciesSuggestion> {
        val q = stateStore.getInputText(SPECIES_INPUT_ID)
        if (q != lastSuggestionQuery) {
            lastSuggestionQuery = q
            currentSuggestions = filterSuggestions(q)
            selectedSuggestionIndex = -1
        }
        return currentSuggestions
    }

    private fun insertSuggestion(suggestion: SpeciesSuggestion) {
        val st = stateStore.input(SPECIES_INPUT_ID)
        st.text = suggestion.name
        st.cursor = suggestion.name.length
        st.selectionAnchor = null
        st.resetBlink()
        selectedSuggestionIndex = -1
        lastSuggestionQuery = null
        getActiveSuggestions()
    }

    private fun lang(key: String, vararg args: Any): String =
        Component.translatable("cobblemon_smartphone.rotom.$key", *args).string

    companion object {
        private const val SPECIES_INPUT_ID = "rotom_species_input"
        private val IV_STEPS = listOf(0, 50, 60, 70, 80, 85, 90, 95, 100)
        private val NATURE_OPTIONS = listOf("any") + listOf(
            "adamant", "bashful", "bold", "brave", "calm", "careful", "docile",
            "gentle", "hardy", "hasty", "impish", "jolly", "lax", "lonely",
            "mild", "modest", "naive", "naughty", "quiet", "quirky", "rash",
            "relaxed", "sassy", "serious", "timid"
        )
    }
}
