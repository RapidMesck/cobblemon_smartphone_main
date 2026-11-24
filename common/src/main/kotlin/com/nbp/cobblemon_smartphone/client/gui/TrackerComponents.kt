package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.client.ui.hd.ButtonNode
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBackground
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Shared HD UI builders for tracker directory screens ([GpsScreen] and [StructureCompassScreen]).
 */
internal object TrackerComponents {
    fun buildHeader(
        builder: HdBuilder,
        icon: HdIconAtlas.Icon,
        title: String,
        countText: String?,
        tracking: Boolean,
        stopLabel: String,
        stopTooltip: String,
        onStopTracking: () -> Unit
    ) {
        builder.appHeader(
            title = title,
            subtitle = countText,
            icon = icon,
            iconColor = HdColorSpec.Token("primary")
        ) { hb ->
            if (tracking) {
                hb.button(
                    label = stopLabel,
                    variant = ButtonNode.Variant.DANGER,
                    fontSize = 8.4f,
                    radius = 8f,
                    onClick = {
                        playClickSound()
                        onStopTracking()
                    }
                ) {
                    padding = HdPadding.symmetric(horizontal = 7f, vertical = 3.5f)
                    tooltip = stopTooltip
                }
            }
        }
    }

    fun buildSearchBar(
        builder: HdBuilder,
        inputId: String,
        placeholder: String,
        onChange: (String) -> Unit
    ) {
        builder.row(
            gap = 6f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.only(left = 7f, right = 7f, top = 4f, bottom = 3f),
            width = HdSizeSpec.Fill
        ) { sb ->
            sb.textInput(
                inputId = inputId,
                placeholder = placeholder,
                maxLength = 40,
                multiline = false,
                onChange = onChange
            ) {
                width = HdSizeSpec.Weight(1f)
            }
        }
    }

    fun buildEmptyState(builder: HdBuilder, message: String) {
        builder.emptyState(
            title = message,
            icon = HdIconAtlas.Icon.COMPASS,
            iconColor = HdColorSpec.Token("primary")
        )
    }

    fun buildTrackerCard(
        builder: HdBuilder,
        theme: SmartphoneTheme,
        title: String,
        idLabel: String,
        idValue: String,
        dimsLabel: String,
        dimsValue: String,
        modLabel: String,
        modValue: String,
        trackable: Boolean,
        tooltipText: String,
        onSelect: () -> Unit
    ) {
        val c = theme.colors
        val cardBg = if (trackable) HdColorSpec.Token("surface") else HdColorSpec.Fixed(c.cardDisabledContentBg)
        val borderColor = if (trackable) HdColorSpec.Token("primary_border") else HdColorSpec.Fixed(c.cardDisabledHeaderBg)
        val headerBg = if (trackable) HdColorSpec.Token("primary") else HdColorSpec.Fixed(c.cardDisabledHeaderBg)

        builder.column(
            gap = 4f,
            padding = HdPadding.all(4f),
            width = HdSizeSpec.Fill,
            background = HdBackground(
                color = cardBg,
                radius = 9f,
                borderWidth = 0.7f,
                borderColor = borderColor,
                shadowBlur = 4f,
                shadowOffsetY = 1.5f,
                shadowColor = HdColorSpec.Fixed(0x28000000)
            )
        ) { card ->
            if (trackable) {
                hoverBackground = HdBackground(
                    color = HdColorSpec.Token("surface_hover"),
                    radius = 9f,
                    borderWidth = 0.8f,
                    borderColor = HdColorSpec.Token("primary_hover"),
                    shadowBlur = 5f,
                    shadowOffsetY = 1.5f,
                    shadowColor = HdColorSpec.Fixed(0x32000000)
                )
                onClick = {
                    playClickSound()
                    onSelect()
                }
            }
            tooltip = tooltipText

            card.row(
                gap = 4f,
                crossAlign = HdAlign.CENTER,
                padding = HdPadding.symmetric(horizontal = 7f, vertical = 4f),
                width = HdSizeSpec.Fill,
                background = HdBackground(color = headerBg, radius = 6f)
            ) { hdr ->
                hdr.text(
                    text = title,
                    size = 9.8f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = if (trackable) HdColorSpec.Token("text_primary") else HdColorSpec.Token("text_muted"),
                    wrap = true
                ) {
                    width = HdSizeSpec.Weight(1f)
                }
            }

            val labelColor = if (trackable) HdColorSpec.Token("text_dark_muted") else HdColorSpec.Fixed(c.cardDisabledText)
            val valueColor = if (trackable) HdColorSpec.Token("text_dark") else HdColorSpec.Fixed(c.cardDisabledText)
            val modColor = if (trackable) HdColorSpec.Token("content_gold") else HdColorSpec.Fixed(c.cardDisabledText)

            card.column(
                gap = 2f,
                padding = HdPadding.symmetric(horizontal = 4f, vertical = 1f),
                width = HdSizeSpec.Fill
            ) { body ->
                buildMetaRow(body, "$idLabel:", idValue, labelColor, valueColor)
                buildMetaRow(body, "$dimsLabel:", dimsValue, labelColor, valueColor)
                buildMetaRow(body, "$modLabel:", modValue, labelColor, modColor)
            }
        }
    }

    private fun buildMetaRow(
        builder: HdBuilder,
        label: String,
        value: String,
        labelColor: HdColorSpec,
        valueColor: HdColorSpec
    ) {
        builder.row(gap = 4f, crossAlign = HdAlign.START, width = HdSizeSpec.Fill) { r ->
            r.text(label, size = 8.4f, color = labelColor)
            r.text(value, size = 8.4f, color = valueColor, wrap = true) {
                width = HdSizeSpec.Weight(1f)
            }
        }
    }

    fun formatDimensions(dimensions: List<String>, anyDimensionLabel: String): String =
        if (dimensions.isEmpty()) anyDimensionLabel else dimensions.joinToString(", ")

    fun isInCurrentDimension(dimensions: List<String>): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val dimension = level.dimension().location().toString()
        return dimensions.isEmpty() || dimensions.contains(dimension)
    }

    fun tr(key: String): String = Component.translatable(key).string

    fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }
}
