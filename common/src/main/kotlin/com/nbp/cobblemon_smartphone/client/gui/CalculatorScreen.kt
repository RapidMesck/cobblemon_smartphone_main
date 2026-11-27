package com.nbp.cobblemon_smartphone.client.gui

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
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.util.CalculatorEngine
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Large HD smartphone calculator built on [HdPhoneScreen] with a side-by-side live history panel,
 * full mouse + keyboard support, and click-to-copy result display.
 */
class CalculatorScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    scaleOverride: Float? = null
) : HdPhoneScreen(
    color,
    smartphoneStack,
    Component.translatable("cobblemon_smartphone.screen.calculator"),
    scaleOverride
) {
    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelHdBadge: Boolean = true

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        builder.appHeader(
            title = tr("cobblemon_smartphone.screen.calculator"),
            icon = HdIconAtlas.Icon.PLUS,
            iconColor = HdColorSpec.Token("primary")
        )

        // Two-column body: Calculator on left, live History panel on right
        builder.row(
            gap = 7f,
            crossAlign = HdAlign.STRETCH,
            padding = HdPadding.only(left = 7f, right = 7f, top = 4f, bottom = 7f),
            width = HdSizeSpec.Fill,
            height = HdSizeSpec.Weight(1f)
        ) { body ->
            // Left column: Display + 4x5 Keypad
            body.column(
                gap = 6f,
                width = HdSizeSpec.Weight(1.65f),
                height = HdSizeSpec.Fill
            ) { left ->
                // Display card
                left.column(
                    gap = 2f,
                    crossAlign = HdAlign.STRETCH,
                    padding = HdPadding.symmetric(horizontal = 10f, vertical = 7f),
                    width = HdSizeSpec.Fill,
                    background = HdBackground(
                        color = HdColorSpec.Token("surface"),
                        radius = 11f,
                        borderWidth = 0.85f,
                        borderColor = HdColorSpec.Token("primary_border"),
                        shadowBlur = 5f,
                        shadowOffsetY = 1.5f,
                        shadowColor = HdColorSpec.Fixed(0x26000000)
                    )
                ) { disp ->
                    this.height = HdSizeSpec.Fixed(58f)
                    this.tooltip = tr("cobblemon_smartphone.calculator.copy_hint")
                    this.onClick = {
                        if (!engine.isError) {
                            playClickSound()
                            Minecraft.getInstance().keyboardHandler.clipboard = engine.displayText
                            showToast(
                                tr("cobblemon_smartphone.calculator.copied"),
                                HdStateStore.ToastKind.SUCCESS,
                                HdIconAtlas.Icon.CHECK
                            )
                        }
                    }

                    val expr = engine.expressionText.ifEmpty { " " }
                    disp.text(
                        text = expr,
                        size = 8.4f,
                        weight = HdTextLayout.Weight.MEDIUM,
                        color = HdColorSpec.Token("text_dark_muted"),
                        align = HdAlign.END
                    ) {
                        width = HdSizeSpec.Fill
                    }

                    val display = if (engine.isError) tr("cobblemon_smartphone.calculator.error") else engine.displayText
                    val displaySize = when {
                        display.length <= 10 -> 18.5f
                        display.length <= 14 -> 14.5f
                        else -> 11.5f
                    }
                    disp.text(
                        text = display,
                        size = displaySize,
                        weight = HdTextLayout.Weight.BOLD,
                        color = if (engine.isError) HdColorSpec.Token("danger") else HdColorSpec.Token("text_dark"),
                        align = HdAlign.END
                    ) {
                        width = HdSizeSpec.Fill
                    }
                }

                // 4x5 Keypad
                left.column(
                    width = HdSizeSpec.Fill,
                    height = HdSizeSpec.Weight(1f),
                    mainAlign = HdAlign.END
                ) { padWrap ->
                    padWrap.grid(columns = 4, colGap = 5f, rowGap = 5f, width = HdSizeSpec.Fill) { g ->
                        val clearLabel = if (engine.canClearEntryOnly) "C" else "AC"
                        calcKey(g, clearLabel, ButtonNode.Variant.DANGER) { engine.clearSmart() }
                        calcKey(g, "±", ButtonNode.Variant.SECONDARY) { engine.toggleSign() }
                        calcKey(g, "%", ButtonNode.Variant.SECONDARY) { engine.percent() }
                        opKey(g, CalculatorEngine.Operator.DIVIDE)

                        calcKey(g, "7", ButtonNode.Variant.SURFACE) { engine.inputDigit('7') }
                        calcKey(g, "8", ButtonNode.Variant.SURFACE) { engine.inputDigit('8') }
                        calcKey(g, "9", ButtonNode.Variant.SURFACE) { engine.inputDigit('9') }
                        opKey(g, CalculatorEngine.Operator.MULTIPLY)

                        calcKey(g, "4", ButtonNode.Variant.SURFACE) { engine.inputDigit('4') }
                        calcKey(g, "5", ButtonNode.Variant.SURFACE) { engine.inputDigit('5') }
                        calcKey(g, "6", ButtonNode.Variant.SURFACE) { engine.inputDigit('6') }
                        opKey(g, CalculatorEngine.Operator.SUBTRACT)

                        calcKey(g, "1", ButtonNode.Variant.SURFACE) { engine.inputDigit('1') }
                        calcKey(g, "2", ButtonNode.Variant.SURFACE) { engine.inputDigit('2') }
                        calcKey(g, "3", ButtonNode.Variant.SURFACE) { engine.inputDigit('3') }
                        opKey(g, CalculatorEngine.Operator.ADD)

                        calcKey(g, "⌫", ButtonNode.Variant.SECONDARY) { engine.backspace() }
                        calcKey(g, "0", ButtonNode.Variant.SURFACE) { engine.inputDigit('0') }
                        calcKey(g, ".", ButtonNode.Variant.SURFACE) { engine.inputDecimal() }
                        calcKey(g, "=", ButtonNode.Variant.PRIMARY) { engine.evaluate() }
                    }
                }
            }

            // Right column: Live History panel
            body.column(
                gap = 5f,
                padding = HdPadding.all(7f),
                width = HdSizeSpec.Weight(1f),
                height = HdSizeSpec.Fill,
                background = HdBackground(
                    color = HdColorSpec.Token("surface"),
                    radius = 11f,
                    borderWidth = 0.85f,
                    borderColor = HdColorSpec.Token("primary_border"),
                    shadowBlur = 5f,
                    shadowOffsetY = 1.5f,
                    shadowColor = HdColorSpec.Fixed(0x26000000)
                )
            ) { hist ->
                hist.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { hdr ->
                    hdr.icon(HdIconAtlas.Icon.FEED, size = 10.5f, color = HdColorSpec.Token("primary"))
                    hdr.text(
                        text = tr("cobblemon_smartphone.calculator.history"),
                        size = 8.8f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("text_dark")
                    ) {
                        width = HdSizeSpec.Weight(1f)
                    }
                    if (engine.history.isNotEmpty()) {
                        hdr.stack(
                            hAlign = HdAlign.CENTER,
                            vAlign = HdAlign.CENTER,
                            width = HdSizeSpec.Fixed(18f),
                            height = HdSizeSpec.Fixed(18f)
                        ) { delBtn ->
                            background = HdBackground(color = HdColorSpec.Token("surface_alt"), radius = 5f)
                            hoverBackground = HdBackground(color = HdColorSpec.Token("surface_hover"), radius = 5f)
                            tooltip = tr("cobblemon_smartphone.calculator.clear_history")
                            onClick = {
                                playClickSound()
                                engine.clearHistory()
                            }
                            delBtn.icon(HdIconAtlas.Icon.TRASH, size = 9.5f, color = HdColorSpec.Token("danger"))
                        }
                    }
                }

                hist.divider(thickness = 0.7f, color = HdColorSpec.Token("primary_border", 0.4f))

                val items = engine.history
                if (items.isEmpty()) {
                    hist.column(
                        mainAlign = HdAlign.CENTER,
                        crossAlign = HdAlign.CENTER,
                        width = HdSizeSpec.Fill,
                        height = HdSizeSpec.Weight(1f)
                    ) { empty ->
                        empty.text(
                            text = tr("cobblemon_smartphone.calculator.history_empty"),
                            size = 8.0f,
                            color = HdColorSpec.Token("text_dark_muted"),
                            align = HdAlign.CENTER,
                            wrap = true
                        ) {
                            width = HdSizeSpec.Fill
                        }
                    }
                } else {
                    hist.scroll(
                        scrollId = "calc_history_scroll",
                        gap = 4f
                    ) { list ->
                        for (entry in items) {
                            list.column(
                                gap = 1f,
                                padding = HdPadding.symmetric(horizontal = 6f, vertical = 4.5f),
                                width = HdSizeSpec.Fill,
                                background = HdBackground(
                                    color = HdColorSpec.Token("surface_alt"),
                                    radius = 7f,
                                    borderWidth = 0.6f,
                                    borderColor = HdColorSpec.Token("primary_border", 0.45f)
                                )
                            ) { itemCol ->
                                hoverBackground = HdBackground(
                                    color = HdColorSpec.Token("surface_hover"),
                                    radius = 7f,
                                    borderWidth = 0.7f,
                                    borderColor = HdColorSpec.Token("primary")
                                )
                                onClick = {
                                    playClickSound()
                                    engine.recallValue(entry.result)
                                }
                                itemCol.text(
                                    text = entry.expression,
                                    size = 7.2f,
                                    color = HdColorSpec.Token("text_dark_muted"),
                                    align = HdAlign.END
                                ) { width = HdSizeSpec.Fill }
                                itemCol.text(
                                    text = "= ${entry.result}",
                                    size = 9.4f,
                                    weight = HdTextLayout.Weight.BOLD,
                                    color = HdColorSpec.Token("text_dark"),
                                    align = HdAlign.END
                                ) { width = HdSizeSpec.Fill }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun opKey(g: HdBuilder, op: CalculatorEngine.Operator) {
        val isActive = engine.activeOperator == op
        val variant = if (isActive) ButtonNode.Variant.PRIMARY else ButtonNode.Variant.SECONDARY
        calcKey(g, op.symbol, variant) { engine.inputOperator(op) }
    }

    private fun calcKey(g: HdBuilder, label: String, variant: ButtonNode.Variant, action: () -> Unit) {
        g.button(
            label = label,
            variant = variant,
            fontSize = if (label.length > 1) 9.8f else 12.0f,
            radius = 9f,
            onClick = {
                playClickSound()
                action()
            }
        ) {
            width = HdSizeSpec.Fill
            height = HdSizeSpec.Fixed(39f)
            padding = HdPadding.ZERO
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        when (keyCode) {
            257, 335 -> { // Enter / Numpad Enter
                playClickSound()
                engine.evaluate()
                return true
            }
            259 -> { // Backspace
                playClickSound()
                engine.backspace()
                return true
            }
            261 -> { // Delete
                playClickSound()
                engine.clearAll()
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        when (codePoint) {
            in '0'..'9' -> {
                playClickSound()
                engine.inputDigit(codePoint)
                return true
            }
            '.', ',' -> {
                playClickSound()
                engine.inputDecimal()
                return true
            }
            '+' -> {
                playClickSound()
                engine.inputOperator(CalculatorEngine.Operator.ADD)
                return true
            }
            '-' -> {
                playClickSound()
                engine.inputOperator(CalculatorEngine.Operator.SUBTRACT)
                return true
            }
            '*', 'x', 'X' -> {
                playClickSound()
                engine.inputOperator(CalculatorEngine.Operator.MULTIPLY)
                return true
            }
            '/' -> {
                playClickSound()
                engine.inputOperator(CalculatorEngine.Operator.DIVIDE)
                return true
            }
            '%' -> {
                playClickSound()
                engine.percent()
                return true
            }
            '=' -> {
                playClickSound()
                engine.evaluate()
                return true
            }
            'c', 'C' -> {
                playClickSound()
                engine.clearSmart()
                return true
            }
        }
        return super.charTyped(codePoint, modifiers)
    }

    private fun tr(key: String): String = Component.translatable(key).string

    companion object {
        // Persist calculator state while playing in the current session so closing and
        // reopening the phone doesn't lose the player's current calculation or history.
        private val engine = CalculatorEngine()
    }
}
