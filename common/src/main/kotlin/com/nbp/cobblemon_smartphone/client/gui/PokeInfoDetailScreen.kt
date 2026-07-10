package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.TypeDefenseChart
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf

class PokeInfoDetailScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack?,
    private val dexNumber: Int
) : Screen(Component.literal("PokeInfo Detail")) {

    private val frameTexture = ResourceLocation.fromNamespaceAndPath(
        "cobblemon_smartphone",
        "textures/gui/large_smartphone_red.png"
    )
    private var screenX = 0
    private var screenY = 0
    private var scrollY = 0
    private var maxScroll = 0
    private var modelPokemon: RenderablePokemon? = null
    private var posableState = FloatingState()
    private lateinit var detail: PokeInfoDataProvider.SpeciesDetail

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color

        detail = PokeInfoDataProvider.getDetail(dexNumber) ?: run {
            Minecraft.getInstance().setScreen(SmartphoneScreen(color, smartphoneStack))
            return
        }

        val species = PokemonSpecies.getByPokedexNumber(dexNumber)
        if (species != null) {
            modelPokemon = RenderablePokemon(species, emptySet())
        }

        maxScroll = maxOf(0, calculateContentHeight() - (CONTENT_END_Y - CONTENT_START_Y))
        scrollY = 0
    }

    override fun removed() {
        modelPokemon = null
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = guiGraphics.pose()

        blitk(
            matrixStack = matrices,
            texture = frameTexture,
            x = screenX,
            y = screenY,
            width = GUI_WIDTH,
            height = GUI_HEIGHT
        )
        blitk(
            matrixStack = matrices,
            texture = HOME_SCREEN_TEXTURE,
            x = screenX,
            y = screenY,
            width = GUI_WIDTH,
            height = GUI_HEIGHT
        )

        renderHeader(guiGraphics, mouseX, mouseY)

        guiGraphics.enableScissor(
            screenX + CONTENT_X, screenY + CONTENT_START_Y,
            screenX + CONTENT_X + CONTENT_WIDTH, screenY + CONTENT_END_Y
        )

        var cy = CONTENT_START_Y - scrollY

        cy = renderTopSection(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        cy = renderBaseStatsSection(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        cy = renderAbilitiesSection(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        cy = renderEvolutionSection(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        cy = renderTypeDefensesSection(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        cy = renderLevelMovesTable(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        cy = renderLearnableMoves(guiGraphics, cy)

        guiGraphics.disableScissor()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isInBackButton(mouseX.toInt(), mouseY.toInt())) {
            playClickSound()
            Minecraft.getInstance().setScreen(PokeInfoScreen(color, smartphoneStack))
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        if (v == 0.0) return super.mouseScrolled(mx, my, h, v)
        scrollY = (scrollY - v.toInt() * SCROLL_SPEED).coerceIn(0, maxScroll)
        return true
    }

    private fun renderHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val hovered = isInBackButton(mouseX, mouseY)
        val color = if (hovered) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt()
        draw(guiGraphics, "\u00AB Back", screenX + HEADER_BACK_X, screenY + HEADER_Y, color)
        val title = "#${String.format("%03d", detail.dexNumber)} ${detail.name}"
        draw(guiGraphics, title, screenX + (GUI_WIDTH - textWidth(title)) / 2, screenY + HEADER_Y, 0xFFFFFFFF.toInt())
    }

    private fun renderTopSection(guiGraphics: GuiGraphics, sy: Int): Int {
        val topPad = 8
        val leftW = 66
        val rightX = CONTENT_X + leftW + 4 + SECTION_PAD
        val totalH = SECTION_PAD_TOP + topPad + 56 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP + topPad
        renderModel(guiGraphics, screenX + CONTENT_X, screenY + y, leftW)

        val dexAndName = "#${String.format("%03d", detail.dexNumber)} ${detail.name}"
        draw(guiGraphics, dexAndName, screenX + rightX, screenY + y, CONTENT_TEXT)

        val types = listOfNotNull(detail.primaryType, detail.secondaryType)
        val typeText = types.joinToString(" / ") { it.replaceFirstChar { c -> c.uppercase() } }
        draw(guiGraphics, typeText, screenX + rightX, screenY + y + 10, CONTENT_GOLD)

        draw(
            guiGraphics,
            "H:${detail.height / 10f}m  W:${detail.weight / 10f}kg",
            screenX + rightX,
            screenY + y + 20,
            CONTENT_DIM
        )

        return sy + totalH
    }

    private fun renderModel(guiGraphics: GuiGraphics, mx: Int, my: Int, w: Int) {
        val modelH = 56
        val pokemon = modelPokemon
        if (pokemon != null) {
            val matrices = guiGraphics.pose()
            matrices.pushPose()
            matrices.translate(mx + w / 2f, my - 18f, 0f)
            drawProfilePokemon(
                pokemon, matrices, Quaternionf().rotateY(Math.toRadians(30.0).toFloat()),
                PoseType.PROFILE, posableState, 0f, 40f
            )
            matrices.popPose()
        } else {
            draw(guiGraphics, "3D", mx + w / 2 - 8, my + 10, 0x80FFFFFF.toInt())
        }
    }

    private fun renderBaseStatsSection(guiGraphics: GuiGraphics, sy: Int): Int {
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 6 * 9 + 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)
        var y = sy + SECTION_PAD_TOP
        draw(guiGraphics, "Base Stats", screenX + CONTENT_X + SECTION_PAD, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        val stats = listOf(
            "HP" to detail.baseStats.hp,
            "ATK" to detail.baseStats.attack,
            "DEF" to detail.baseStats.defence,
            "SpA" to detail.baseStats.specialAttack,
            "SpD" to detail.baseStats.specialDefence,
            "SPD" to detail.baseStats.speed
        )
        val labelW = 22
        val valW = 28
        val barMaxW = CONTENT_WIDTH - SECTION_PAD * 2 - labelW - valW
        val barX = screenX + CONTENT_X + SECTION_PAD + labelW
        stats.forEach { (label, value) ->
            draw(guiGraphics, label, screenX + CONTENT_X + SECTION_PAD, screenY + y, CONTENT_GOLD)
            val bw = (value * barMaxW / 255).coerceIn(1, barMaxW)
            guiGraphics.fill(
                barX,
                screenY + y + 1,
                barX + bw,
                screenY + y + 6,
                statBarColor(value)
            )
            draw(guiGraphics, "$value", barX + bw + 2, screenY + y, CONTENT_TEXT)
            y += 9
        }
        draw(
            guiGraphics,
            "Total: ${detail.baseStats.total}",
            screenX + CONTENT_X + SECTION_PAD + labelW - 8,
            screenY + y,
            CONTENT_DIM
        )

        return sy + totalH
    }

    private fun renderAbilitiesSection(guiGraphics: GuiGraphics, sy: Int): Int {
        if (detail.abilities.isEmpty()) return sy
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + detail.abilities.size * 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)
        var y = sy + SECTION_PAD_TOP
        draw(guiGraphics, "Abilities", screenX + CONTENT_X + SECTION_PAD, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP
        detail.abilities.forEach { a ->
            val prefix = if (a.isHidden) "(H) " else ""
            val name = prefix + a.name.replaceFirstChar { it.uppercase() }
            draw(guiGraphics, name, screenX + CONTENT_X + SECTION_PAD, screenY + y, CONTENT_TEXT)
            y += 9
        }
        return sy + totalH
    }

    private fun renderEvolutionSection(guiGraphics: GuiGraphics, sy: Int): Int {
        val hasPre = detail.preEvolution != null
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + (if (hasPre) 10 else 0) + detail.evolutions.size * 10 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)
        var y = sy + SECTION_PAD_TOP
        draw(guiGraphics, "Evolution", screenX + CONTENT_X + SECTION_PAD, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP
        if (hasPre) {
            draw(
                guiGraphics, "\u2191 ${detail.preEvolution!!.split(":").last().replaceFirstChar { it.uppercase() }}",
                screenX + CONTENT_X + SECTION_PAD, screenY + y, CONTENT_DIM
            )
            y += 10
        }
        detail.evolutions.forEach { evo ->
            val name = evo.targetName.replaceFirstChar { it.uppercase() }
            draw(guiGraphics, "\u2193 $name  (${evo.method})", screenX + CONTENT_X + SECTION_PAD, screenY + y, CONTENT_TEXT)
            y += 10
        }
        return sy + totalH
    }

    private fun renderTypeDefensesSection(guiGraphics: GuiGraphics, sy: Int): Int {
        val types = listOfNotNull(detail.primaryType, detail.secondaryType)
        val eff = TypeDefenseChart.getEffectiveness(types)
        val weak = eff.filter { it.value > 1.0 }.keys
        val resist = eff.filter { it.value in 0.01..0.99 }.keys
        val immune = eff.filter { it.value == 0.0 }.keys

        val weakText = weak.takeIf { it.isNotEmpty() }?.joinToString(" ") {
            TypeDefenseChart.multiplierText(eff[it]!!) + TypeDefenseChart.typeAbbreviation(it)
        } ?: "None"
        val resistText = resist.takeIf { it.isNotEmpty() }?.joinToString(" ") {
            TypeDefenseChart.multiplierText(eff[it]!!) + TypeDefenseChart.typeAbbreviation(it)
        } ?: "None"
        val immuneText =
            immune.takeIf { it.isNotEmpty() }?.joinToString(" ") { TypeDefenseChart.typeAbbreviation(it) } ?: "None"

        val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
        val weakLines = wrapText("Weak: $weakText", wrapW).size
        val resistLines = wrapText("Resist: $resistText", wrapW).size
        val immuneLines = wrapText("Immune: $immuneText", wrapW).size
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + (weakLines + resistLines + immuneLines) * 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        val tx = screenX + CONTENT_X + SECTION_PAD
        draw(guiGraphics, "Type Defenses", tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        wrapText("Weak: $weakText", wrapW).forEach {
            draw(guiGraphics, it, tx, screenY + y, CONTENT_WEAK); y += 9
        }
        wrapText("Resist: $resistText", wrapW).forEach {
            draw(guiGraphics, it, tx, screenY + y, CONTENT_RESIST); y += 9
        }
        wrapText("Immune: $immuneText", wrapW).forEach {
            draw(guiGraphics, it, tx, screenY + y, CONTENT_IMMUNE); y += 9
        }
        return sy + totalH
    }

    private fun renderLevelMovesTable(guiGraphics: GuiGraphics, sy: Int): Int {
        val levelUp = detail.moves.filter { it.method == "level" }.sortedBy { it.level }
        val moveCount = minOf(levelUp.size, 20)
        val scale = 0.8f
        val rowH = (9 * scale).toInt()
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + rowH + moveCount * rowH + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        draw(guiGraphics, "Level Moves", screenX + CONTENT_X + SECTION_PAD, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        val tableW = LV_W + MOVE_W + TYPE_W + CAT_W + PW_W + AC_W
        val availW = CONTENT_WIDTH - SECTION_PAD * 2
        val offsetX = (availW - tableW * scale) / 2f
        val baseX = (screenX + CONTENT_X + SECTION_PAD + offsetX) / scale
        val baseY = (screenY + y) / scale

        val matrices = guiGraphics.pose()
        matrices.pushPose()
        matrices.translate(baseX * scale, baseY * scale, 0f)
        matrices.scale(scale, scale, 1f)

        draw(guiGraphics, "Lvl", 0, 0, CONTENT_DIM)
        draw(guiGraphics, "Move", LV_W, 0, CONTENT_DIM)
        draw(guiGraphics, "Type", LV_W + MOVE_W, 0, CONTENT_DIM)
        draw(guiGraphics, "Ct", LV_W + MOVE_W + TYPE_W, 0, CONTENT_DIM)
        draw(guiGraphics, "Pw", LV_W + MOVE_W + TYPE_W + CAT_W, 0, CONTENT_DIM)
        draw(guiGraphics, "Ac", LV_W + MOVE_W + TYPE_W + CAT_W + PW_W, 0, CONTENT_DIM)
        var ry = 9

        levelUp.take(20).forEach { move ->
            val pw = if (move.power == 0) "\u2014" else move.power.toString()
            val ac = if (move.accuracy == 0) "\u221E" else move.accuracy.toString()
            val cat = when (move.category) {
                "physical" -> "Ph"; "special" -> "Sp"; else -> "St"
            }
            val typeAbbr = TypeDefenseChart.typeAbbreviation(move.type)
            val moveName = truncate(move.name.replaceFirstChar { it.uppercase() }, MOVE_W - 2)

            draw(guiGraphics, "${move.level}", 0, ry, CONTENT_TEXT)
            draw(guiGraphics, moveName, LV_W, ry, CONTENT_TEXT)
            draw(guiGraphics, typeAbbr, LV_W + MOVE_W, ry, CONTENT_TEXT)
            draw(guiGraphics, cat, LV_W + MOVE_W + TYPE_W, ry, CONTENT_TEXT)
            draw(guiGraphics, pw, LV_W + MOVE_W + TYPE_W + CAT_W, ry, CONTENT_TEXT)
            draw(guiGraphics, ac, LV_W + MOVE_W + TYPE_W + CAT_W + PW_W, ry, CONTENT_TEXT)
            ry += 9
        }

        matrices.popPose()
        return sy + totalH
    }

    private fun renderLearnableMoves(guiGraphics: GuiGraphics, sy: Int): Int {
        val others = detail.moves.filter { it.method != "level" }
        if (others.isEmpty()) return sy

        val groups = others.groupBy { it.method }
        val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
        val tx = screenX + CONTENT_X + SECTION_PAD

        var lineCount = 0
        groups.forEach { (method, methodMoves) ->
            val label = when (method) {
                "tm" -> "TM"; "egg" -> "Egg"; "tutor" -> "Tutor"; else -> method
            }
            val names = methodMoves.joinToString(", ") { it.name.replaceFirstChar { c -> c.uppercase() } }
            lineCount += wrapText("$label: $names", wrapW).size
        }
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + lineCount * 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        draw(guiGraphics, "Learnable Moves", tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        groups.forEach { (method, methodMoves) ->
            val label = when (method) {
                "tm" -> "TM"; "egg" -> "Egg"; "tutor" -> "Tutor"; else -> method
            }
            val names = methodMoves.joinToString(", ") { it.name.replaceFirstChar { c -> c.uppercase() } }
            wrapText("$label: $names", wrapW).forEach { line ->
                draw(guiGraphics, line, tx, screenY + y, CONTENT_DIM)
                y += 9
            }
        }
        return sy + totalH
    }

    private fun sectionBox(guiGraphics: GuiGraphics, y: Int, h: Int) {
        val x1 = screenX + CONTENT_X
        val y1 = screenY + y
        val x2 = screenX + CONTENT_X + CONTENT_WIDTH
        val titleH = SECTION_PAD_TOP + TITLE_GAP
        // Title bar bg
        guiGraphics.fill(x1, y1, x2, y1 + titleH, SECTION_TITLE_BG)
        // Content area bg
        guiGraphics.fill(x1, y1 + titleH, x2, y1 + h, SECTION_CONTENT_BG)
    }

    private fun drawSep(guiGraphics: GuiGraphics, sy: Int): Int {
    guiGraphics.fill(
        screenX + CONTENT_X + SECTION_PAD,
        screenY + sy,
        screenX + CONTENT_X + CONTENT_WIDTH - SECTION_PAD,
        screenY + sy + 1,
        0x30FFFFFF.toInt()
    )
    return sy + 4
}

private fun truncate(text: String, maxWidth: Int): String {
    if (textWidth(text) <= maxWidth) return text
    var result = text
    while (textWidth(result + "..") > maxWidth && result.length > 1) result = result.dropLast(1)
    return result + ".."
}

private fun draw(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int) {
    guiGraphics.drawString(font, text, x, y, color, false)
}

private fun textWidth(text: String): Int = font.width(text)

private fun wrapText(text: String, maxWidth: Int): List<String> {
    if (textWidth(text) <= maxWidth) return listOf(text)
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = StringBuilder()
    words.forEach { word ->
        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (textWidth(testLine) <= maxWidth) {
            if (currentLine.isNotEmpty()) currentLine.append(" ")
            currentLine.append(word)
        } else {
            if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
            currentLine = StringBuilder(word)
        }
    }
    if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
    return lines.ifEmpty { listOf(text) }
}

private fun statBarColor(value: Int): Int = when {
    value >= 120 -> 0xFF00C2B8.toInt()
    value >= 100 -> 0xFF23CD5E.toInt()
    value >= 80 -> 0xFFA0E515.toInt()
    value >= 60 -> 0xFFFFDD57.toInt()
    value >= 40 -> 0xFFFF7F0F.toInt()
    else -> 0xFFF34444.toInt()
}

private fun calculateContentHeight(): Int {
    val topH = SECTION_PAD_TOP + 8 + 56 + SECTION_PAD_BOTTOM
    val statsH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 6 * 9 + 9 + SECTION_PAD_BOTTOM
    val abiH =
        if (detail.abilities.isNotEmpty()) SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + detail.abilities.size * 9 + SECTION_PAD_BOTTOM else 0
    val evoH =
        SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + (if (detail.preEvolution != null) 10 else 0) + detail.evolutions.size * 10 + SECTION_PAD_BOTTOM
    val defH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 3 * 9 + SECTION_PAD_BOTTOM
    val movesH =
        SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 7 + minOf(detail.moves.count { it.method == "level" }, 20) * 7 + SECTION_PAD_BOTTOM
    val learnH =
        if (detail.moves.any { it.method != "level" }) SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 3 * 9 + SECTION_PAD_BOTTOM else 0
    val separators = 7 * 4
    return topH + statsH + abiH + evoH + defH + movesH + learnH + separators
}

private fun isInBackButton(mouseX: Int, mouseY: Int): Boolean {
    return mouseX >= screenX + HEADER_BACK_X && mouseX <= screenX + HEADER_BACK_X + 30 &&
            mouseY >= screenY + HEADER_Y - 2 && mouseY <= screenY + HEADER_Y + 10
}

private fun playClickSound() {
    Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
}

companion object {
    private const val GUI_WIDTH = 211
    private const val GUI_HEIGHT = 207

    private const val HEADER_BACK_X = 20
    private const val HEADER_Y = 8

    private const val CONTENT_X = 20
    private const val CONTENT_WIDTH = 171
    private const val CONTENT_START_Y = 28
    private const val CONTENT_END_Y = 192

    private const val SECTION_PAD = 4
    private const val SECTION_PAD_TOP = 4
    private const val SECTION_PAD_BOTTOM = 4
    private const val TITLE_GAP = 13
    private const val CONTENT_GAP = 3
    private const val SECTION_TITLE_BG = 0xFF3A96B6.toInt()
    private const val SECTION_CONTENT_BG = 0xFFEFFDFF.toInt()
    private const val CONTENT_TEXT = 0xFF1A1A2E.toInt()
    private const val CONTENT_DIM = 0xFF555555.toInt()
    private const val CONTENT_GOLD = 0xFFB8860B.toInt()
    private const val CONTENT_WEAK = 0xFFCC3333.toInt()
    private const val CONTENT_RESIST = 0xFF339933.toInt()
    private const val CONTENT_IMMUNE = 0xFF6666CC.toInt()

    private const val SCROLL_SPEED = 10

    private const val LV_W = 22
    private const val MOVE_W = 62
    private const val TYPE_W = 34
    private const val CAT_W = 20
    private const val PW_W = 22
    private const val AC_W = 22

    private const val SECTION_TITLE_COLOR = 0xFFFFFFFF.toInt()

    private val HOME_SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "cobblemon_smartphone", "textures/gui/large_screen.png"
    )
}
}
