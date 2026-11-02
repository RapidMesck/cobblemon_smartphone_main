package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.nbp.cobblemon_smartphone.client.RotomRadarClientState
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.rotom.RotomRadarConfig
import com.nbp.cobblemon_smartphone.rotom.RotomTarget
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeHelper
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeRenderer
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

class RotomAppScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack? = null,
    initialTab: Tab = Tab.SETTINGS,
    initialAddingTarget: Boolean = false
) : Screen(Component.translatable("cobblemon_smartphone.screen.rotom")) {

    private val theme get() = SmartphoneThemeHelper.resolveCurrentTheme(smartphoneStack)
    private val SECTION_TITLE_BG get() = theme.colors.primary
    private val SECTION_CONTENT_BG get() = theme.colors.surface
    private val CARD_HOVER_BG get() = theme.colors.primaryHover
    private val CONTENT_TEXT get() = theme.colors.textDark
    private val CONTENT_DIM get() = theme.colors.textDarkMuted

    private var screenX = 0
    private var screenY = 0

    enum class Tab {
        SETTINGS,
        TARGETS
    }

    private var currentTab = initialTab
    private var isAddingTarget = initialAddingTarget

    // Target form inputs
    private lateinit var speciesBox: EditBox
    private var selectedNatureIndex = 0
    private var selectedMinIvPercent = 0
    private var selectedMinPerfectIvs = 0
    private var selectedShinyOnly = false

    // Autocomplete / Species insert recommendation state
    data class SpeciesSuggestion(
        val name: String,
        val path: String,
        val translatedName: String,
        val dexNumber: Int
    )

    private var cachedSpeciesList: List<SpeciesSuggestion>? = null
    private var lastSuggestionQuery: String? = null
    private var currentSuggestions: List<SpeciesSuggestion> = emptyList()
    private var selectedSuggestionIndex = -1

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
            if (others.isEmpty()) {
                return emptyList()
            }
        }

        val prefixMatches = all.filter {
            it.path.startsWith(q) || it.name.lowercase().startsWith(q) || it.translatedName.lowercase().startsWith(q)
        }

        val containsMatches = all.filter {
            !prefixMatches.contains(it) && (it.path.contains(q) || it.name.lowercase().contains(q) || it.translatedName.lowercase().contains(q))
        }

        return (prefixMatches + containsMatches).take(5)
    }

    private fun getActiveSuggestions(): List<SpeciesSuggestion> {
        val q = if (::speciesBox.isInitialized) speciesBox.value else ""
        if (q != lastSuggestionQuery) {
            lastSuggestionQuery = q
            currentSuggestions = filterSuggestions(q)
            selectedSuggestionIndex = -1
        }
        return currentSuggestions
    }

    private fun insertSuggestion(suggestion: SpeciesSuggestion) {
        speciesBox.value = suggestion.name
        speciesBox.setCursorPosition(speciesBox.value.length)
        speciesBox.setHighlightPos(speciesBox.value.length)
        selectedSuggestionIndex = -1
        lastSuggestionQuery = null
        getActiveSuggestions()
    }

    // Scroll state for targets list
    private var scrollY = 0
    private var maxScroll = 0
    private var draggingScrollbar = false
    private var dragStartMouseY = 0
    private var dragStartScrollY = 0

    // Scroll state for settings tab
    private var settingsScrollY = 0
    private var settingsMaxScroll = 0
    private var draggingSettingsScrollbar = false
    private var dragStartSettingsMouseY = 0
    private var dragStartSettingsScrollY = 0

    private val config: RotomRadarConfig
        get() = RotomRadarClientState.config

    companion object {
        private const val GUI_WIDTH = 211
        private const val GUI_HEIGHT = 207
        private const val BACK_X = 20
        private const val BACK_Y = 14
        private const val CONTENT_X = 20
        private const val CONTENT_WIDTH = 171
        private const val LIST_WIDTH = 171
        private const val SETTINGS_START_Y = 46
        private const val SETTINGS_END_Y = 195
        private const val SETTINGS_ROW_H = 22
        private const val SETTINGS_ROW_GAP = 2
        private const val LIST_START_Y = 70
        private const val LIST_END_Y = 195
        private const val CARD_HEIGHT = 34
        private const val CARD_GAP = 4
        private const val SCROLLBAR_WIDTH = 4
        private const val SCROLL_SPEED = 14

        private val NATURE_OPTIONS = listOf("any") + listOf(
            "adamant", "bashful", "bold", "brave", "calm", "careful", "docile",
            "gentle", "hardy", "hasty", "impish", "jolly", "lax", "lonely",
            "mild", "modest", "naive", "naughty", "quiet", "quirky", "rash",
            "relaxed", "sassy", "serious", "timid"
        )
    }

    private fun lang(key: String, vararg args: Any): String =
        Component.translatable("cobblemon_smartphone.rotom.$key", *args).string

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color

        val boxX = screenX + CONTENT_X
        val boxY = screenY + 56
        speciesBox = EditBox(
            font,
            boxX + 4,
            boxY + 3,
            LIST_WIDTH - 8,
            10,
            Component.translatable("cobblemon_smartphone.rotom.species_hint")
        )
        speciesBox.setMaxLength(32)
        speciesBox.setBordered(false)
        speciesBox.setHint(Component.translatable("cobblemon_smartphone.rotom.species_hint"))
        speciesBox.setTextColor(theme.colors.textDarkMuted)
        speciesBox.setResponder {
            getActiveSuggestions()
        }
        addWidget(speciesBox)
        speciesBox.visible = (currentTab == Tab.TARGETS && isAddingTarget)

        updateMaxScroll()
    }

    override fun removed() {
        RotomRadarClientState.saveConfig()
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    private fun updateMaxScroll() {
        val totalHeight = config.targets.size * (CARD_HEIGHT + CARD_GAP)
        val viewHeight = LIST_END_Y - LIST_START_Y
        maxScroll = maxOf(0, totalHeight - viewHeight)
        scrollY = scrollY.coerceIn(0, maxScroll)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = guiGraphics.pose()

        SmartphoneThemeRenderer.renderLargeTheme(guiGraphics, theme, screenX, screenY)
        blitk(
            matrixStack = matrices,
            texture = theme.getLargeFrameTexture(color.getLargeScreenTexture()),
            x = screenX,
            y = screenY,
            width = GUI_WIDTH,
            height = GUI_HEIGHT
        )

        // Header: Back button
        val backHover = isInBackButton(mouseX, mouseY)
        val backColor = if (backHover) theme.colors.gold else theme.colors.textPrimary
        guiGraphics.drawString(font, lang("back"), screenX + BACK_X, screenY + BACK_Y, backColor, false)

        SmartphoneBatteryRenderer.render(guiGraphics, screenX, screenY, isLargeScreen = true, smartphoneStack)

        // Tabs
        renderTabs(guiGraphics, mouseX, mouseY)

        // Content
        when (currentTab) {
            Tab.SETTINGS -> renderSettingsTab(guiGraphics, mouseX, mouseY)
            Tab.TARGETS -> {
                if (isAddingTarget) {
                    renderAddTargetForm(guiGraphics, mouseX, mouseY, delta)
                } else {
                    renderTargetsListTab(guiGraphics, mouseX, mouseY)
                }
            }
        }
    }

    private fun renderTabs(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val tabY = screenY + 28
        val tabH = 15
        val tabW = (LIST_WIDTH - 4) / 2

        val tab1X = screenX + CONTENT_X
        val tab2X = tab1X + tabW + 4

        val tab1Hover = mouseX in tab1X..(tab1X + tabW) && mouseY in tabY..(tabY + tabH)
        val tab2Hover = mouseX in tab2X..(tab2X + tabW) && mouseY in tabY..(tabY + tabH)

        val tab1Bg = if (currentTab == Tab.SETTINGS) SECTION_TITLE_BG else (if (tab1Hover) theme.colors.surfaceHover else SECTION_CONTENT_BG)
        val tab2Bg = if (currentTab == Tab.TARGETS) SECTION_TITLE_BG else (if (tab2Hover) theme.colors.surfaceHover else SECTION_CONTENT_BG)

        guiGraphics.fill(tab1X, tabY, tab1X + tabW, tabY + tabH, tab1Bg)
        guiGraphics.fill(tab2X, tabY, tab2X + tabW, tabY + tabH, tab2Bg)

        val text1 = lang("tab_settings")
        val text2 = lang("tab_targets", config.targets.size)

        val text1Color = if (currentTab == Tab.SETTINGS) theme.colors.textPrimary else CONTENT_TEXT
        val text2Color = if (currentTab == Tab.TARGETS) theme.colors.textPrimary else CONTENT_TEXT

        guiGraphics.drawString(font, text1, tab1X + (tabW - font.width(text1)) / 2, tabY + 4, text1Color, false)
        guiGraphics.drawString(font, text2, tab2X + (tabW - font.width(text2)) / 2, tabY + 4, text2Color, false)
    }

    private fun renderSettingsTab(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val totalRows = 8
        val totalH = totalRows * (SETTINGS_ROW_H + SETTINGS_ROW_GAP)
        val viewH = SETTINGS_END_Y - SETTINGS_START_Y
        settingsMaxScroll = (totalH - viewH).coerceAtLeast(0)
        settingsScrollY = settingsScrollY.coerceIn(0, settingsMaxScroll)

        guiGraphics.enableScissor(
            screenX + CONTENT_X, screenY + SETTINGS_START_Y,
            screenX + CONTENT_X + LIST_WIDTH, screenY + SETTINGS_END_Y
        )

        var cy = screenY + SETTINGS_START_Y - settingsScrollY

        // 1. Radar Geral
        renderSettingRow(
            guiGraphics, mouseX, mouseY, cy, SETTINGS_ROW_H,
            title = lang("setting_radar_title"),
            desc = lang("setting_radar_desc"),
            enabled = config.enabled
        )
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 2. Alerta Shiny
        renderSettingRow(
            guiGraphics, mouseX, mouseY, cy, SETTINGS_ROW_H,
            title = lang("setting_shiny_title"),
            desc = lang("setting_shiny_desc"),
            enabled = config.shinyAlert
        )
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 3. Alerta de Alvos
        renderSettingRow(
            guiGraphics, mouseX, mouseY, cy, SETTINGS_ROW_H,
            title = lang("setting_targets_title"),
            desc = lang("setting_targets_desc"),
            enabled = config.targetAlert
        )
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 4. Som de Notificação
        renderSettingRow(
            guiGraphics, mouseX, mouseY, cy, SETTINGS_ROW_H,
            title = lang("setting_sound_title"),
            desc = lang("setting_sound_desc"),
            enabled = config.soundAlert
        )
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 5. Auto Scanner Pokédex
        renderSettingRow(
            guiGraphics, mouseX, mouseY, cy, SETTINGS_ROW_H,
            title = lang("setting_autoscan_title"),
            desc = lang("setting_autoscan_desc"),
            enabled = config.autoScanPokedex
        )
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 6. Exibição na Tela (3 opções: Tudo / Mini / Off)
        renderTriStateRow(
            guiGraphics, mouseX, mouseY, cy, SETTINGS_ROW_H,
            title = lang("setting_curiosity_title"),
            desc = lang("setting_curiosity_desc"),
            currentMode = config.scanDisplayMode
        )
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 7. Idioma do Rotom / Rotom Language
        renderLanguageRow(
            guiGraphics, mouseX, mouseY, cy, SETTINGS_ROW_H,
            title = lang("setting_language_title"),
            desc = lang("setting_language_desc"),
            language = config.language
        )
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 8. Holograma de Rastreamento no HUD
        renderSettingRow(
            guiGraphics, mouseX, mouseY, cy, SETTINGS_ROW_H,
            title = lang("setting_hud_tracker_title"),
            desc = lang("setting_hud_tracker_desc"),
            enabled = config.hudTracker
        )

        guiGraphics.disableScissor()
        renderSettingsScrollbar(guiGraphics, mouseY)
    }

    private fun renderSettingsScrollbar(guiGraphics: GuiGraphics, mouseY: Int) {
        if (settingsMaxScroll <= 0) return
        val trackX = screenX + CONTENT_X + LIST_WIDTH + 1
        val trackY = screenY + SETTINGS_START_Y
        val trackH = SETTINGS_END_Y - SETTINGS_START_Y
        val totalH = 8 * (SETTINGS_ROW_H + SETTINGS_ROW_GAP)
        val handleH = maxOf(14, (trackH.toFloat() * trackH / totalH.toFloat()).toInt())
        val handleY = trackY + (settingsScrollY.toFloat() / settingsMaxScroll * (trackH - handleH)).toInt()

        if (draggingSettingsScrollbar) {
            val dy = mouseY - dragStartSettingsMouseY
            val scrollRange = trackH - handleH
            if (scrollRange > 0) {
                settingsScrollY = (dragStartSettingsScrollY + (dy.toFloat() / scrollRange * settingsMaxScroll).toInt()).coerceIn(0, settingsMaxScroll)
            }
        }

        guiGraphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackH, 0x20FFFFFF.toInt())
        val color = if (draggingSettingsScrollbar) 0x90FFFFFF.toInt() else 0x60FFFFFF.toInt()
        guiGraphics.fill(trackX, handleY, trackX + SCROLLBAR_WIDTH, handleY + handleH, color)
    }

    private fun renderToggleSwitch(
        guiGraphics: GuiGraphics,
        trackX: Int,
        trackY: Int,
        trackW: Int,
        trackH: Int,
        enabled: Boolean,
        hovered: Boolean
    ) {
        val trackBg = if (enabled) {
            if (hovered) theme.colors.primaryHover else theme.colors.primary
        } else {
            if (hovered) theme.colors.surfaceHover else theme.colors.surfaceAlt
        }
        val trackBorder = if (enabled) theme.colors.primaryBorder else ((theme.colors.primaryBorder and 0x00FFFFFF) or 0x66000000)

        // Pílula do trilho
        guiGraphics.fill(trackX + 2, trackY, trackX + trackW - 2, trackY + trackH, trackBg)
        guiGraphics.fill(trackX, trackY + 2, trackX + trackW, trackY + trackH - 2, trackBg)
        guiGraphics.renderOutline(trackX + 1, trackY + 1, trackW - 2, trackH - 2, trackBorder)

        // Botão deslizante (knob)
        val knobSize = 10
        val knobY = trackY + (trackH - knobSize) / 2
        val knobX = if (enabled) (trackX + trackW - knobSize - 2) else (trackX + 2)

        val knobColor = if (enabled) {
            theme.colors.textPrimary
        } else {
            if (hovered) theme.colors.textDarkMuted else ((theme.colors.textDarkMuted and 0x00FFFFFF) or 0x99000000.toInt())
        }

        guiGraphics.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, knobColor)
    }

    private fun renderSettingRow(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        y: Int,
        rowH: Int = SETTINGS_ROW_H,
        title: String,
        desc: String,
        enabled: Boolean
    ) {
        val x1 = screenX + CONTENT_X
        val x2 = x1 + LIST_WIDTH
        val y2 = y + rowH

        guiGraphics.fill(x1, y, x2, y2, SECTION_CONTENT_BG)

        // Toggle Switch à direita
        val switchW = 26
        val switchH = 14
        val switchX = x2 - switchW - 5
        val switchY = y + (rowH - switchH) / 2
        val rowHover = mouseX in x1..x2 && mouseY in y..y2

        renderToggleSwitch(guiGraphics, switchX, switchY, switchW, switchH, enabled, rowHover)

        // Text (prevent overflow behind the toggle switch)
        val maxTextW = switchX - (x1 + 6) - 4
        val displayDesc = if (font.width(desc) > maxTextW) {
            font.plainSubstrByWidth(desc, maxTextW)
        } else {
            desc
        }
        guiGraphics.drawString(font, title, x1 + 6, y + 2, CONTENT_TEXT, false)
        guiGraphics.drawString(font, displayDesc, x1 + 6, y + 12, CONTENT_DIM, false)
    }

    private fun renderTriStateRow(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        y: Int,
        rowH: Int = SETTINGS_ROW_H,
        title: String,
        desc: String,
        currentMode: Int
    ) {
        val x1 = screenX + CONTENT_X
        val x2 = x1 + LIST_WIDTH
        val y2 = y + rowH

        guiGraphics.fill(x1, y, x2, y2, SECTION_CONTENT_BG)

        val btnW = 72
        val btnH = 14
        val segW = btnW / 3 // 24px cada segmento
        val btnX = x2 - btnW - 5
        val btnY = y + (rowH - btnH) / 2

        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, theme.colors.surfaceAlt)
        guiGraphics.renderOutline(btnX, btnY, btnW, btnH, theme.colors.primaryBorder)

        val labels = listOf(lang("mode_full"), lang("mode_mini"), lang("mode_off"))

        for (i in 0..2) {
            val segX = btnX + i * segW
            val segHover = mouseX in segX..(segX + segW) && mouseY in btnY..(btnY + btnH)
            val isSelected = (currentMode == i)

            if (isSelected) {
                val segBg = if (segHover) theme.colors.primaryHover else theme.colors.primary
                guiGraphics.fill(segX, btnY, segX + segW, btnY + btnH, segBg)
            } else if (segHover) {
                guiGraphics.fill(segX, btnY, segX + segW, btnY + btnH, theme.colors.surfaceHover)
            }

            if (i > 0) {
                guiGraphics.fill(segX, btnY, segX + 1, btnY + btnH, theme.colors.primaryBorder)
            }

            val text = labels[i]
            val textColor = if (isSelected) theme.colors.textPrimary else theme.colors.textDarkMuted
            guiGraphics.drawString(font, text, segX + (segW - font.width(text)) / 2, btnY + 3, textColor, false)
        }

        val maxTextW = btnX - (x1 + 6) - 4
        val displayDesc = if (font.width(desc) > maxTextW) {
            font.plainSubstrByWidth(desc, maxTextW)
        } else {
            desc
        }
        guiGraphics.drawString(font, title, x1 + 6, y + 2, CONTENT_TEXT, false)
        guiGraphics.drawString(font, displayDesc, x1 + 6, y + 12, CONTENT_DIM, false)
    }

    private fun renderLanguageRow(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        y: Int,
        rowH: Int = SETTINGS_ROW_H,
        title: String,
        desc: String,
        language: String
    ) {
        val x1 = screenX + CONTENT_X
        val x2 = x1 + LIST_WIDTH
        val y2 = y + rowH

        guiGraphics.fill(x1, y, x2, y2, SECTION_CONTENT_BG)

        val btnW = 46
        val btnH = 14
        val segW = btnW / 2 // 23px cada
        val btnX = x2 - btnW - 5
        val btnY = y + (rowH - btnH) / 2

        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, theme.colors.surfaceAlt)
        guiGraphics.renderOutline(btnX, btnY, btnW, btnH, theme.colors.primaryBorder)

        val isEn = language.equals("en", ignoreCase = true)

        // EN Segment
        val enHover = mouseX in btnX..(btnX + segW) && mouseY in btnY..(btnY + btnH)
        val enBg = if (isEn) (if (enHover) theme.colors.primaryHover else theme.colors.primary)
                   else (if (enHover) theme.colors.surfaceHover else 0)
        if (enBg != 0) guiGraphics.fill(btnX, btnY, btnX + segW, btnY + btnH, enBg)
        val enColor = if (isEn) theme.colors.textPrimary else theme.colors.textDarkMuted
        guiGraphics.drawString(font, "EN", btnX + (segW - font.width("EN")) / 2, btnY + 3, enColor, false)

        // Divisória
        guiGraphics.fill(btnX + segW, btnY, btnX + segW + 1, btnY + btnH, theme.colors.primaryBorder)

        // PT Segment
        val ptX = btnX + segW
        val ptHover = mouseX in ptX..(ptX + segW) && mouseY in btnY..(btnY + btnH)
        val ptBg = if (!isEn) (if (ptHover) theme.colors.primaryHover else theme.colors.primary)
                   else (if (ptHover) theme.colors.surfaceHover else 0)
        if (ptBg != 0) guiGraphics.fill(ptX, btnY, ptX + segW, btnY + btnH, ptBg)
        val ptColor = if (!isEn) theme.colors.textPrimary else theme.colors.textDarkMuted
        guiGraphics.drawString(font, "PT", ptX + (segW - font.width("PT")) / 2, btnY + 3, ptColor, false)

        // Text
        val maxTextW = btnX - (x1 + 6) - 4
        val displayDesc = if (font.width(desc) > maxTextW) {
            font.plainSubstrByWidth(desc, maxTextW)
        } else {
            desc
        }
        guiGraphics.drawString(font, title, x1 + 6, y + 2, CONTENT_TEXT, false)
        guiGraphics.drawString(font, displayDesc, x1 + 6, y + 12, CONTENT_DIM, false)
    }

    private fun renderTargetsListTab(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        // "+ Adicionar Alvo" button at the top
        val addBtnX = screenX + CONTENT_X
        val addBtnY = screenY + 48
        val addBtnW = LIST_WIDTH
        val addBtnH = 16
        val addBtnHover = mouseX in addBtnX..(addBtnX + addBtnW) && mouseY in addBtnY..(addBtnY + addBtnH)
        val addBtnBg = if (addBtnHover) theme.colors.primaryHover else SECTION_TITLE_BG

        guiGraphics.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + addBtnH, addBtnBg)
        val addText = lang("btn_add_target")
        guiGraphics.drawString(font, addText, addBtnX + (addBtnW - font.width(addText)) / 2, addBtnY + 4, theme.colors.textPrimary, false)

        if (config.targets.isEmpty()) {
            val maxTextW = LIST_WIDTH - 16
            val emptyTitle = lang("targets_empty_1")
            val emptyDesc2 = lang("targets_empty_2")
            val emptyDesc3 = lang("targets_empty_3")
            val fullDesc = if (emptyDesc2.endsWith(" ") || emptyDesc3.startsWith(" ")) {
                "$emptyDesc2$emptyDesc3"
            } else {
                "$emptyDesc2 $emptyDesc3"
            }.trim()

            val titleLines = font.split(Component.literal(emptyTitle), maxTextW)
            val descLines = font.split(Component.literal(fullDesc), maxTextW)

            val lineHeight = font.lineHeight
            val descLineSpacing = lineHeight + 2
            val titleSpacing = lineHeight + 4
            val totalH = (titleLines.size * titleSpacing) + (descLines.size * descLineSpacing)

            val availableTop = screenY + 68
            val availableBottom = screenY + LIST_END_Y
            var cy = availableTop + (availableBottom - availableTop - totalH) / 2

            for (line in titleLines) {
                val lw = font.width(line)
                val lx = screenX + CONTENT_X + (LIST_WIDTH - lw) / 2
                guiGraphics.drawString(font, line, lx, cy, CONTENT_TEXT, false)
                cy += titleSpacing
            }

            for (line in descLines) {
                val lw = font.width(line)
                val lx = screenX + CONTENT_X + (LIST_WIDTH - lw) / 2
                guiGraphics.drawString(font, line, lx, cy, CONTENT_DIM, false)
                cy += descLineSpacing
            }
            return
        }

        guiGraphics.enableScissor(
            screenX + CONTENT_X, screenY + LIST_START_Y,
            screenX + CONTENT_X + LIST_WIDTH, screenY + LIST_END_Y
        )

        for (i in config.targets.indices) {
            val target = config.targets[i]
            val cy = screenY + LIST_START_Y + i * (CARD_HEIGHT + CARD_GAP) - scrollY
            if (cy + CARD_HEIGHT < screenY + LIST_START_Y || cy > screenY + LIST_END_Y) continue
            renderTargetCard(guiGraphics, mouseX, mouseY, cy, target)
        }

        guiGraphics.disableScissor()
        renderScrollbar(guiGraphics, mouseY)
    }

    private fun renderTargetCard(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        y: Int,
        target: RotomTarget
    ) {
        val x1 = screenX + CONTENT_X
        val x2 = x1 + LIST_WIDTH
        val y2 = y + CARD_HEIGHT
        val toggleBtnX = x2 - 42

        val hovered = mouseX in x1..x2 && mouseY in y..y2
        val bg = if (hovered) theme.colors.surfaceHover else SECTION_CONTENT_BG
        guiGraphics.fill(x1, y, x2, y2, bg)

        // Border
        val borderCol = if (target.enabled) theme.colors.primary else theme.colors.textDarkMuted
        guiGraphics.fill(x1, y, x1 + 2, y2, borderCol)

        // Line 1: Species name (constrained so it never overlaps buttons)
        val maxCardTextW = toggleBtnX - 4 - (x1 + 6)
        val specName = if (target.species.isBlank() || target.species == "any") lang("species_any")
        else target.species.replaceFirstChar(Char::uppercase)
        val shinyBadge = if (target.shinyOnly) " ✨" else ""
        val titleColor = if (target.enabled) CONTENT_TEXT else CONTENT_DIM
        val fullTitle = "$specName$shinyBadge"
        val trimmedTitle = if (font.width(fullTitle) > maxCardTextW) font.plainSubstrByWidth(fullTitle, maxCardTextW) else fullTitle
        guiGraphics.drawString(font, trimmedTitle, x1 + 6, y + 4, titleColor, false)

        // Line 2: Filters summary
        val ivText = if (target.minIvPercent > 0) "IV ≥${target.minIvPercent}%" else ""
        val perfText = if (target.minPerfectIvs > 0) "${target.minPerfectIvs}x 31" else ""
        val natText = if (target.nature.isNotBlank() && target.nature != "any") target.nature.replaceFirstChar(Char::uppercase) else ""

        val summaryParts = mutableListOf<String>()
        if (ivText.isNotEmpty()) summaryParts.add(ivText)
        if (perfText.isNotEmpty()) summaryParts.add(perfText)
        if (natText.isNotEmpty()) summaryParts.add(natText)
        val summary = if (summaryParts.isEmpty()) lang("filters_none") else summaryParts.joinToString(" • ")
        val trimmedSummary = if (font.width(summary) > maxCardTextW) font.plainSubstrByWidth(summary, maxCardTextW) else summary

        guiGraphics.drawString(font, trimmedSummary, x1 + 6, y + 17, CONTENT_DIM, false)

        // Action buttons: Toggle [✓/X] and Delete [🗑]
        val toggleBtnY = y + (CARD_HEIGHT - 16) / 2
        val toggleBtnW = 16
        val toggleBtnH = 16
        val toggleHover = mouseX in toggleBtnX..(toggleBtnX + toggleBtnW) && mouseY in toggleBtnY..(toggleBtnY + toggleBtnH)
        val toggleBg = if (target.enabled) (if (toggleHover) theme.colors.primaryHover else theme.colors.success)
        else (if (toggleHover) theme.colors.surfaceHover else theme.colors.surfaceAlt)
        guiGraphics.fill(toggleBtnX, toggleBtnY, toggleBtnX + toggleBtnW, toggleBtnY + toggleBtnH, toggleBg)
        val toggleText = if (target.enabled) "✓" else "✕"
        guiGraphics.drawString(font, toggleText, toggleBtnX + 5, toggleBtnY + 4, theme.colors.textPrimary, false)

        val delBtnX = x2 - 22
        val delBtnY = y + (CARD_HEIGHT - 16) / 2
        val delBtnW = 16
        val delBtnH = 16
        val delHover = mouseX in delBtnX..(delBtnX + delBtnW) && mouseY in delBtnY..(delBtnY + delBtnH)
        val delBg = if (delHover) theme.colors.danger else ((theme.colors.danger and 0x00FFFFFF) or 0xDD000000.toInt())
        guiGraphics.fill(delBtnX, delBtnY, delBtnX + delBtnW, delBtnY + delBtnH, delBg)
        guiGraphics.drawString(font, "✕", delBtnX + 5, delBtnY + 4, theme.colors.textPrimary, false)
    }

    private fun renderAddTargetForm(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        // Field 1: Species
        guiGraphics.drawString(font, lang("form_species"), screenX + CONTENT_X, screenY + 46, CONTENT_TEXT, false)

        val boxX = screenX + CONTENT_X
        val boxY = screenY + 56
        val boxH = 14
        guiGraphics.fill(boxX, boxY, boxX + LIST_WIDTH, boxY + boxH, SECTION_CONTENT_BG)
        guiGraphics.fill(boxX, boxY, boxX + LIST_WIDTH, boxY + 1, SECTION_TITLE_BG)
        guiGraphics.fill(boxX, boxY + boxH - 1, boxX + LIST_WIDTH, boxY + boxH, SECTION_TITLE_BG)
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, SECTION_TITLE_BG)
        guiGraphics.fill(boxX + LIST_WIDTH - 1, boxY, boxX + LIST_WIDTH, boxY + boxH, SECTION_TITLE_BG)
        speciesBox.setX(boxX + 4)
        speciesBox.setY(boxY + 3)
        speciesBox.renderWidget(guiGraphics, mouseX, mouseY, delta)

        // Ghost text for instant inline completion hint
        val suggestions = getActiveSuggestions()
        if (speciesBox.isFocused && speciesBox.value.isNotEmpty()) {
            val top = suggestions.firstOrNull()
            if (top != null && top.name.lowercase().startsWith(speciesBox.value.lowercase())) {
                val suffix = top.name.substring(speciesBox.value.length)
                val typedW = font.width(speciesBox.value)
                val ghostX = speciesBox.x + typedW
                if (ghostX + font.width(suffix) <= boxX + LIST_WIDTH - 28) {
                    guiGraphics.drawString(font, suffix, ghostX, speciesBox.y, 0x88777777.toInt(), false)
                }
                val tabHint = "⇥ Tab"
                guiGraphics.drawString(font, tabHint, boxX + LIST_WIDTH - font.width(tabHint) - 3, boxY + 3, 0xFF6D879B.toInt(), false)
            }
        }

        // Field 2: Nature
        guiGraphics.drawString(font, lang("form_nature"), screenX + CONTENT_X, screenY + 73, CONTENT_TEXT, false)
        renderCycleSelector(
            guiGraphics, mouseX, mouseY, screenY + 83,
            label = if (selectedNatureIndex == 0) lang("nature_any") else NATURE_OPTIONS[selectedNatureIndex].replaceFirstChar(Char::uppercase)
        )

        // Field 3: Min IV %
        guiGraphics.drawString(font, lang("form_min_iv"), screenX + CONTENT_X, screenY + 100, CONTENT_TEXT, false)
        renderCycleSelector(
            guiGraphics, mouseX, mouseY, screenY + 110,
            label = if (selectedMinIvPercent == 0) lang("iv_any") else "$selectedMinIvPercent%"
        )

        // Field 4: Min 31s
        guiGraphics.drawString(font, lang("form_min_31s"), screenX + CONTENT_X, screenY + 127, CONTENT_TEXT, false)
        renderCycleSelector(
            guiGraphics, mouseX, mouseY, screenY + 137,
            label = if (selectedMinPerfectIvs == 0) lang("iv_any") else "$selectedMinPerfectIvs x 31"
        )

        // Field 5: Shiny only toggle (inline row)
        val shinyY = screenY + 154
        guiGraphics.drawString(font, lang("form_shiny_only"), screenX + CONTENT_X, shinyY + 3, CONTENT_TEXT, false)
        val shinyBtnX = screenX + CONTENT_X + LIST_WIDTH - 44
        val shinyBtnW = 44
        val shinyBtnH = 14
        val shinyHover = mouseX in shinyBtnX..(shinyBtnX + shinyBtnW) && mouseY in shinyY..(shinyY + shinyBtnH)
        val shinyBg = if (selectedShinyOnly) (if (shinyHover) theme.colors.primaryHover else theme.colors.success)
        else (if (shinyHover) theme.colors.surfaceHover else theme.colors.surfaceAlt)
        guiGraphics.fill(shinyBtnX, shinyY, shinyBtnX + shinyBtnW, shinyY + shinyBtnH, shinyBg)
        val shinyText = if (selectedShinyOnly) lang("status_yes") else lang("status_no")
        guiGraphics.drawString(font, shinyText, shinyBtnX + (shinyBtnW - font.width(shinyText)) / 2, shinyY + 3, theme.colors.textPrimary, false)

        // Action buttons: [ Save ] and [ Cancel ]
        val btnY = screenY + 172
        val btnH = 16
        val btnW = (LIST_WIDTH - 6) / 2
        val saveBtnX = screenX + CONTENT_X
        val saveBtnHover = mouseX in saveBtnX..(saveBtnX + btnW) && mouseY in btnY..(btnY + btnH)
        val saveBg = if (saveBtnHover) theme.colors.primaryHover else theme.colors.success
        guiGraphics.fill(saveBtnX, btnY, saveBtnX + btnW, btnY + btnH, saveBg)
        val saveText = lang("btn_save_target")
        guiGraphics.drawString(font, saveText, saveBtnX + (btnW - font.width(saveText)) / 2, btnY + 4, theme.colors.textPrimary, false)

        val cancelBtnX = saveBtnX + btnW + 6
        val cancelBtnHover = mouseX in cancelBtnX..(cancelBtnX + btnW) && mouseY in btnY..(btnY + btnH)
        val cancelBg = if (cancelBtnHover) theme.colors.surfaceHover else theme.colors.surfaceAlt
        guiGraphics.fill(cancelBtnX, btnY, cancelBtnX + btnW, btnY + btnH, cancelBg)
        val cancelText = lang("btn_cancel")
        val cancelTextColor = if (cancelBtnHover) theme.colors.textDark else theme.colors.textDarkMuted
        guiGraphics.drawString(font, cancelText, cancelBtnX + (btnW - font.width(cancelText)) / 2, btnY + 4, cancelTextColor, false)

        // Floating suggestion dropdown rendered above other form rows
        renderSuggestionDropdown(guiGraphics, mouseX, mouseY)
    }

    private fun renderSuggestionDropdown(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!speciesBox.isFocused) return
        val suggestions = getActiveSuggestions()
        if (suggestions.isEmpty()) return

        val popupX = screenX + CONTENT_X
        val popupY = screenY + 56 + 14 + 1
        val popupW = LIST_WIDTH
        val rowH = 15
        val popupH = suggestions.size * rowH + 2

        val matrices = guiGraphics.pose()
        matrices.pushPose()
        matrices.translate(0f, 0f, 100f)

        // Sombra suave para destacar o dropdown flutuante
        guiGraphics.fill(popupX + 2, popupY + 2, popupX + popupW + 2, popupY + popupH + 2, 0x55000000)

        // Borda externa
        guiGraphics.fill(popupX, popupY, popupX + popupW, popupY + popupH, SECTION_TITLE_BG)

        // Fundo interno
        guiGraphics.fill(popupX + 1, popupY + 1, popupX + popupW - 1, popupY + popupH - 1, theme.colors.headerNavy)

        for (i in suggestions.indices) {
            val suggestion = suggestions[i]
            val rowY = popupY + 1 + i * rowH
            val isHovered = mouseX in popupX..(popupX + popupW) && mouseY in rowY..(rowY + rowH)
            val isSelected = i == selectedSuggestionIndex

            val rowBg = when {
                isSelected -> theme.colors.primaryHover
                isHovered -> theme.colors.headerNavyLight
                else -> if (i % 2 == 0) theme.colors.headerNavy else theme.colors.headerNavyLight
            }
            guiGraphics.fill(popupX + 1, rowY, popupX + popupW - 1, rowY + rowH, rowBg)

            // Marcador
            val bulletColor = if (isSelected || isHovered) theme.colors.gold else theme.colors.textMuted
            guiGraphics.drawString(font, "▸", popupX + 4, rowY + 3, bulletColor, false)

            // Nome da espécie
            val nameColor = if (isSelected || isHovered) theme.colors.textPrimary else theme.colors.textSecondary
            guiGraphics.drawString(font, suggestion.name, popupX + 14, rowY + 3, nameColor, false)

            // Indicador de Inserir ou Número da Pokédex
            if (isHovered || isSelected) {
                val insertBadge = lang("badge_insert")
                val badgeColor = if (isSelected) theme.colors.textPrimary else theme.colors.gold
                guiGraphics.drawString(font, insertBadge, popupX + popupW - font.width(insertBadge) - 4, rowY + 3, badgeColor, false)
            } else {
                val dexText = if (suggestion.dexNumber > 0) "#%03d".format(suggestion.dexNumber) else ""
                if (dexText.isNotEmpty()) {
                    guiGraphics.drawString(font, dexText, popupX + popupW - font.width(dexText) - 5, rowY + 3, theme.colors.textMuted, false)
                }
            }
        }

        matrices.popPose()
    }

    private fun renderCycleSelector(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        y: Int,
        label: String
    ) {
        val x1 = screenX + CONTENT_X
        val w = LIST_WIDTH
        val h = 14
        guiGraphics.fill(x1, y, x1 + w, y + h, SECTION_CONTENT_BG)

        // [<] button
        val leftBtnHover = mouseX in x1..(x1 + 16) && mouseY in y..(y + h)
        val leftBtnBg = if (leftBtnHover) theme.colors.primaryHover else theme.colors.primary
        guiGraphics.fill(x1, y, x1 + 16, y + h, leftBtnBg)
        guiGraphics.drawString(font, "<", x1 + 6, y + 3, theme.colors.textPrimary, false)

        // Label
        guiGraphics.drawString(font, label, x1 + (w - font.width(label)) / 2, y + 3, CONTENT_TEXT, false)

        // [>] button
        val rightX = x1 + w - 16
        val rightBtnHover = mouseX in rightX..(rightX + 16) && mouseY in y..(y + h)
        val rightBtnBg = if (rightBtnHover) theme.colors.primaryHover else theme.colors.primary
        guiGraphics.fill(rightX, y, rightX + 16, y + h, rightBtnBg)
        guiGraphics.drawString(font, ">", rightX + 6, y + 3, theme.colors.textPrimary, false)
    }

    private fun renderNumberSelector(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        y: Int,
        label: String
    ) {
        renderCycleSelector(guiGraphics, mouseX, mouseY, y, label)
    }

    private fun renderCountSelector(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        y: Int,
        label: String
    ) {
        renderCycleSelector(guiGraphics, mouseX, mouseY, y, label)
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics, mouseY: Int) {
        if (maxScroll <= 0) return
        val trackX = screenX + CONTENT_X + LIST_WIDTH + 1
        val trackY = screenY + LIST_START_Y
        val trackH = LIST_END_Y - LIST_START_Y
        val totalH = config.targets.size * (CARD_HEIGHT + CARD_GAP)
        val handleH = maxOf(12, (trackH.toFloat() * trackH / totalH.toFloat()).toInt())
        val handleY = trackY + (scrollY.toFloat() / maxScroll * (trackH - handleH)).toInt()

        if (draggingScrollbar) {
            val dy = mouseY - dragStartMouseY
            val scrollRange = trackH - handleH
            if (scrollRange > 0) {
                scrollY = (dragStartScrollY + (dy.toFloat() / scrollRange * maxScroll).toInt()).coerceIn(0, maxScroll)
            }
        }

        guiGraphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackH, 0x20FFFFFF.toInt())
        val color = if (draggingScrollbar) 0x90FFFFFF.toInt() else 0x60FFFFFF.toInt()
        guiGraphics.fill(trackX, handleY, trackX + SCROLLBAR_WIDTH, handleY + handleH, color)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        // Back button
        if (isInBackButton(mx, my)) {
            playClickSound()
            Minecraft.getInstance().setScreen(SmartphoneScreen(color, smartphoneStack))
            return true
        }

        // Tabs
        val tabY = screenY + 28
        val tabH = 15
        val tabW = (LIST_WIDTH - 4) / 2
        val tab1X = screenX + CONTENT_X
        val tab2X = tab1X + tabW + 4

        if (my in tabY..(tabY + tabH)) {
            if (mx in tab1X..(tab1X + tabW)) {
                if (currentTab != Tab.SETTINGS) {
                    playClickSound()
                    currentTab = Tab.SETTINGS
                    isAddingTarget = false
                    speciesBox.visible = false
                }
                return true
            }
            if (mx in tab2X..(tab2X + tabW)) {
                if (currentTab != Tab.TARGETS) {
                    playClickSound()
                    currentTab = Tab.TARGETS
                    isAddingTarget = false
                    speciesBox.visible = false
                    updateMaxScroll()
                }
                return true
            }
        }

        if (currentTab == Tab.SETTINGS) {
            return handleSettingsClick(mx, my)
        } else {
            return handleTargetsClick(mx, my)
        }
    }

    private fun handleSettingsClick(mx: Int, my: Int): Boolean {
        // Scrollbar dragging start
        if (settingsMaxScroll > 0) {
            val trackX = screenX + CONTENT_X + LIST_WIDTH + 1
            val trackY = screenY + SETTINGS_START_Y
            val trackH = SETTINGS_END_Y - SETTINGS_START_Y
            val totalH = 8 * (SETTINGS_ROW_H + SETTINGS_ROW_GAP)
            val handleH = maxOf(14, (trackH.toFloat() * trackH / totalH.toFloat()).toInt())
            val handleY = trackY + (settingsScrollY.toFloat() / settingsMaxScroll * (trackH - handleH)).toInt()
            if (mx in trackX..(trackX + SCROLLBAR_WIDTH) && my in handleY..(handleY + handleH)) {
                draggingSettingsScrollbar = true
                dragStartSettingsMouseY = my
                dragStartSettingsScrollY = settingsScrollY
                return true
            }
        }

        if (my !in (screenY + SETTINGS_START_Y)..(screenY + SETTINGS_END_Y)) return false
        val x1 = screenX + CONTENT_X
        val x2 = x1 + LIST_WIDTH
        if (mx !in x1..x2) return false

        var cy = screenY + SETTINGS_START_Y - settingsScrollY

        fun isRowClicked(y: Int): Boolean = my in y..(y + SETTINGS_ROW_H)

        // 1. Radar Geral
        if (isRowClicked(cy)) {
            playClickSound()
            config.enabled = !config.enabled
            RotomRadarClientState.saveConfig()
            return true
        }
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 2. Alerta Shiny
        if (isRowClicked(cy)) {
            playClickSound()
            config.shinyAlert = !config.shinyAlert
            RotomRadarClientState.saveConfig()
            return true
        }
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 3. Alerta de Alvos
        if (isRowClicked(cy)) {
            playClickSound()
            config.targetAlert = !config.targetAlert
            RotomRadarClientState.saveConfig()
            return true
        }
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 4. Som de Notificação
        if (isRowClicked(cy)) {
            playClickSound()
            config.soundAlert = !config.soundAlert
            RotomRadarClientState.saveConfig()
            return true
        }
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 5. Auto Scanner Pokédex
        if (isRowClicked(cy)) {
            playClickSound()
            config.autoScanPokedex = !config.autoScanPokedex
            RotomRadarClientState.saveConfig()
            return true
        }
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 6. Exibição na Tela (3 opções: Tudo / Mini / Off)
        if (isRowClicked(cy)) {
            val btnW = 72
            val segW = btnW / 3
            val btnX = x2 - btnW - 5
            playClickSound()
            if (mx in btnX..(btnX + btnW)) {
                val segIdx = ((mx - btnX) / segW).coerceIn(0, 2)
                config.scanDisplayMode = segIdx
            } else {
                config.scanDisplayMode = (config.scanDisplayMode + 1) % 3
            }
            RotomRadarClientState.saveConfig()
            return true
        }
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 7. Idioma do Rotom / Rotom Language
        if (isRowClicked(cy)) {
            val btnW = 46
            val segW = btnW / 2
            val btnX = x2 - btnW - 5
            playClickSound()
            if (mx in btnX..(btnX + btnW)) {
                config.language = if (mx < btnX + segW) "en" else "pt"
            } else {
                config.language = if (config.language.equals("en", ignoreCase = true)) "pt" else "en"
            }
            com.nbp.cobblemon_smartphone.client.RotomScannerClientState.clientConfig.curiosityLanguage = config.language
            RotomRadarClientState.saveConfig()
            return true
        }
        cy += SETTINGS_ROW_H + SETTINGS_ROW_GAP

        // 8. Holograma de Rastreamento no HUD
        if (isRowClicked(cy)) {
            playClickSound()
            config.hudTracker = !config.hudTracker
            RotomRadarClientState.saveConfig()
            return true
        }

        return false
    }

    private fun handleTargetsClick(mx: Int, my: Int): Boolean {
        if (isAddingTarget) {
            if (speciesBox.isFocused) {
                val suggestions = getActiveSuggestions()
                if (suggestions.isNotEmpty()) {
                    val popupX = screenX + CONTENT_X
                    val popupY = screenY + 56 + 14 + 1
                    val popupW = LIST_WIDTH
                    val rowH = 15
                    val popupH = suggestions.size * rowH + 2

                    if (mx in popupX..(popupX + popupW) && my in popupY..(popupY + popupH)) {
                        val clickedIdx = (my - (popupY + 1)) / rowH
                        if (clickedIdx in suggestions.indices) {
                            playClickSound()
                            insertSuggestion(suggestions[clickedIdx])
                            return true
                        }
                    }
                }
            }

            val boxClicked = speciesBox.mouseClicked(mx.toDouble(), my.toDouble(), 0)
            speciesBox.isFocused = boxClicked
            if (boxClicked) return true
            return handleAddFormClick(mx, my)
        }

        // "+ Adicionar Alvo" button
        val addBtnX = screenX + CONTENT_X
        val addBtnY = screenY + 48
        val addBtnW = LIST_WIDTH
        val addBtnH = 16
        if (mx in addBtnX..(addBtnX + addBtnW) && my in addBtnY..(addBtnY + addBtnH)) {
            playClickSound()
            isAddingTarget = true
            speciesBox.value = ""
            speciesBox.visible = true
            speciesBox.isFocused = true
            selectedSuggestionIndex = -1
            lastSuggestionQuery = null
            getActiveSuggestions()
            selectedNatureIndex = 0
            selectedMinIvPercent = 0
            selectedMinPerfectIvs = 0
            selectedShinyOnly = false
            return true
        }

        // Targets list cards click
        val x2 = screenX + CONTENT_X + LIST_WIDTH
        for (i in config.targets.indices) {
            val target = config.targets[i]
            val cy = screenY + LIST_START_Y + i * (CARD_HEIGHT + CARD_GAP) - scrollY
            if (cy + CARD_HEIGHT < screenY + LIST_START_Y || cy > screenY + LIST_END_Y) continue

            // Toggle button
            val toggleBtnX = x2 - 42
            val toggleBtnY = cy + 6
            if (mx in toggleBtnX..(toggleBtnX + 16) && my in toggleBtnY..(toggleBtnY + 16)) {
                playClickSound()
                target.enabled = !target.enabled
                RotomRadarClientState.saveConfig()
                return true
            }

            // Delete button
            val delBtnX = x2 - 22
            val delBtnY = cy + 6
            if (mx in delBtnX..(delBtnX + 16) && my in delBtnY..(delBtnY + 16)) {
                playClickSound()
                config.targets.removeAt(i)
                updateMaxScroll()
                RotomRadarClientState.saveConfig()
                return true
            }
        }

        // Scrollbar dragging start
        if (maxScroll > 0) {
            val trackX = screenX + CONTENT_X + LIST_WIDTH + 1
            val trackY = screenY + LIST_START_Y
            val trackH = LIST_END_Y - LIST_START_Y
            val totalH = config.targets.size * (CARD_HEIGHT + CARD_GAP)
            val handleH = maxOf(12, (trackH.toFloat() * trackH / totalH.toFloat()).toInt())
            val handleY = trackY + (scrollY.toFloat() / maxScroll * (trackH - handleH)).toInt()
            if (mx in trackX..(trackX + SCROLLBAR_WIDTH) && my in handleY..(handleY + handleH)) {
                draggingScrollbar = true
                dragStartMouseY = my
                dragStartScrollY = scrollY
                return true
            }
        }

        return false
    }

    private fun handleAddFormClick(mx: Int, my: Int): Boolean {
        // Nature cycle selector: screenY + 83 .. screenY + 97
        val x1 = screenX + CONTENT_X
        val w = LIST_WIDTH
        val natureY = screenY + 83
        if (my in natureY..(natureY + 14)) {
            if (mx in x1..(x1 + 16)) {
                playClickSound()
                selectedNatureIndex = (selectedNatureIndex - 1 + NATURE_OPTIONS.size) % NATURE_OPTIONS.size
                return true
            }
            if (mx in (x1 + w - 16)..(x1 + w)) {
                playClickSound()
                selectedNatureIndex = (selectedNatureIndex + 1) % NATURE_OPTIONS.size
                return true
            }
        }

        // Min IV % selector: screenY + 110 .. screenY + 124
        val ivSteps = listOf(0, 50, 60, 70, 80, 85, 90, 95, 100)
        val ivY = screenY + 110
        if (my in ivY..(ivY + 14)) {
            val curIdx = ivSteps.indexOf(selectedMinIvPercent).coerceAtLeast(0)
            if (mx in x1..(x1 + 16)) {
                playClickSound()
                val nextIdx = (curIdx - 1 + ivSteps.size) % ivSteps.size
                selectedMinIvPercent = ivSteps[nextIdx]
                return true
            }
            if (mx in (x1 + w - 16)..(x1 + w)) {
                playClickSound()
                val nextIdx = (curIdx + 1) % ivSteps.size
                selectedMinIvPercent = ivSteps[nextIdx]
                return true
            }
        }

        // Min 31s count: screenY + 137 .. screenY + 151
        val iv31Y = screenY + 137
        if (my in iv31Y..(iv31Y + 14)) {
            if (mx in x1..(x1 + 16)) {
                playClickSound()
                selectedMinPerfectIvs = (selectedMinPerfectIvs - 1 + 7) % 7
                return true
            }
            if (mx in (x1 + w - 16)..(x1 + w)) {
                playClickSound()
                selectedMinPerfectIvs = (selectedMinPerfectIvs + 1) % 7
                return true
            }
        }

        // Shiny only toggle: screenY + 154 .. screenY + 168
        val shinyY = screenY + 154
        val shinyBtnX = screenX + CONTENT_X + LIST_WIDTH - 44
        if (mx in shinyBtnX..(shinyBtnX + 44) && my in shinyY..(shinyY + 14)) {
            playClickSound()
            selectedShinyOnly = !selectedShinyOnly
            return true
        }

        // Action buttons: [ Save ] and [ Cancel ] at screenY + 172 .. screenY + 188
        val btnY = screenY + 172
        val btnW = (LIST_WIDTH - 6) / 2
        val saveBtnX = screenX + CONTENT_X
        if (mx in saveBtnX..(saveBtnX + btnW) && my in btnY..(btnY + 16)) {
            playClickSound()
            val rawSpecies = speciesBox.value.trim().lowercase()
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
            speciesBox.visible = false
            updateMaxScroll()
            return true
        }

        val cancelBtnX = saveBtnX + btnW + 6
        if (mx in cancelBtnX..(cancelBtnX + btnW) && my in btnY..(btnY + 16)) {
            playClickSound()
            isAddingTarget = false
            speciesBox.visible = false
            return true
        }

        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingScrollbar = false
        draggingSettingsScrollbar = false
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (currentTab == Tab.SETTINGS && settingsMaxScroll > 0 && verticalAmount != 0.0) {
            settingsScrollY = (settingsScrollY - verticalAmount.toInt() * SCROLL_SPEED).coerceIn(0, settingsMaxScroll)
            return true
        }
        if (currentTab == Tab.TARGETS && !isAddingTarget && maxScroll > 0 && verticalAmount != 0.0) {
            scrollY = (scrollY - verticalAmount.toInt() * SCROLL_SPEED).coerceIn(0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (isAddingTarget) {
            selectedSuggestionIndex = -1
            if (speciesBox.charTyped(codePoint, modifiers)) {
                getActiveSuggestions()
                return true
            }
        }
        return super.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (isAddingTarget) {
            val suggestions = getActiveSuggestions()
            val hasSuggestions = speciesBox.isFocused && suggestions.isNotEmpty()

            if (hasSuggestions) {
                // Seta para baixo: navegar pelas sugestões
                if (keyCode == 264) {
                    selectedSuggestionIndex = (selectedSuggestionIndex + 1).coerceAtMost(suggestions.size - 1)
                    return true
                }
                // Seta para cima
                if (keyCode == 265) {
                    selectedSuggestionIndex = (selectedSuggestionIndex - 1).coerceAtLeast(-1)
                    return true
                }
                // Tab: inserir sugestão selecionada ou do topo
                if (keyCode == 258) {
                    val chosen = if (selectedSuggestionIndex in suggestions.indices) {
                        suggestions[selectedSuggestionIndex]
                    } else {
                        suggestions.firstOrNull()
                    }
                    if (chosen != null) {
                        playClickSound()
                        insertSuggestion(chosen)
                        return true
                    }
                }
                // Enter: inserir sugestão selecionada
                if ((keyCode == 257 || keyCode == 335) && selectedSuggestionIndex in suggestions.indices) {
                    playClickSound()
                    insertSuggestion(suggestions[selectedSuggestionIndex])
                    return true
                }
            }

            if (keyCode == 256) { // Escape
                if (hasSuggestions && selectedSuggestionIndex != -1) {
                    selectedSuggestionIndex = -1
                    return true
                }
                isAddingTarget = false
                speciesBox.visible = false
                selectedSuggestionIndex = -1
                return true
            }
            if (speciesBox.isFocused && speciesBox.keyPressed(keyCode, scanCode, modifiers)) return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun isInBackButton(mouseX: Int, mouseY: Int): Boolean {
        return mouseX in (screenX + BACK_X)..(screenX + BACK_X + 35) &&
                mouseY in (screenY + BACK_Y - 2)..(screenY + BACK_Y + 10)
    }

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.GUI_CLICK, 0.5f, 1f)
    }
}
