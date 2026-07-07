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
        "textures/gui/smartphone_${color.modelName}.png"
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

        val contentHeight = calculateContentHeight()
        maxScroll = maxOf(0, contentHeight - (CONTENT_END_Y - CONTENT_START_Y))
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
            screenX + CONTENT_X,
            screenY + CONTENT_START_Y,
            screenX + CONTENT_X + CONTENT_WIDTH,
            screenY + CONTENT_END_Y
        )

        var y = CONTENT_START_Y - scrollY
        y = renderTypesSection(guiGraphics, y)
        y = renderPokemonModel(guiGraphics, mouseX, mouseY, y)
        y += 2
        y = renderBaseStatsSection(guiGraphics, y)
        y += 2
        y = renderAbilitiesSection(guiGraphics, y)
        if (detail.preEvolution != null || detail.evolutions.isNotEmpty()) {
            y += 2
            y = renderEvolutionSection(guiGraphics, y)
        }
        y += 2
        y = renderMovesSection(guiGraphics, y)
        y += 2
        y = renderTrainingSection(guiGraphics, y)
        y += 2
        y = renderTypeDefensesSection(guiGraphics, y)
        y += 2
        y = renderBreedingSection(guiGraphics, y)

        guiGraphics.disableScissor()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (isInBackButton(mx, my)) {
            playClickSound()
            Minecraft.getInstance().setScreen(PokeInfoScreen(color, smartphoneStack))
            return true
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        if (verticalAmount == 0.0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)

        scrollY = (scrollY - verticalAmount.toInt() * SCROLL_SPEED).coerceIn(0, maxScroll)
        return true
    }

    private fun renderHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val mx = mouseX
        val my = mouseY
        val hovered = isInBackButton(mx, my)
        val color = if (hovered) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt()

        draw(guiGraphics, "\u00AB Back", screenX + HEADER_BACK_X, screenY + HEADER_Y, color)

        val title = "#${String.format("%03d", detail.dexNumber)} ${detail.name}"
        val titleWidth = textWidth(title)
        draw(guiGraphics, title, screenX + (GUI_WIDTH - titleWidth) / 2, screenY + HEADER_Y, 0xFFFFFFFF.toInt())
    }

    private fun renderTypesSection(guiGraphics: GuiGraphics, y: Int): Int {
        val types = listOfNotNull(detail.primaryType, detail.secondaryType)
        val text = types.joinToString(" / ") { it.replaceFirstChar { c -> c.uppercase() } }
        val textWidth = textWidth(text)
        draw(guiGraphics, text, screenX + CONTENT_X + (CONTENT_WIDTH - textWidth) / 2, screenY + y + 2, 0xFFFFFFFF.toInt())
        return y + 14
    }

    private fun renderPokemonModel(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, y: Int): Int {
        val modelH = 48
        val modelX = screenX + CONTENT_X + (CONTENT_WIDTH - MODEL_W) / 2
        val modelY = screenY + y

        guiGraphics.fill(modelX, modelY, modelX + MODEL_W, modelY + modelH, 0x20FFFFFF.toInt())

        val pokemon = modelPokemon
        if (pokemon != null) {
            val matrices = guiGraphics.pose()
            matrices.pushPose()
            matrices.translate(modelX + MODEL_W / 2f, modelY + modelH - 4f, 0f)

            drawProfilePokemon(
                renderablePokemon = pokemon,
                matrixStack = matrices,
                rotation = Quaternionf().rotateY(Math.toRadians(30.0).toFloat()),
                poseType = PoseType.PROFILE,
                state = posableState,
                partialTicks = 0f,
                scale = 14f
            )

            matrices.popPose()
        } else {
            val placeholder = "3D Preview"
            val pw = textWidth(placeholder)
            draw(guiGraphics, placeholder, modelX + (MODEL_W - pw) / 2, modelY + (modelH - 8) / 2, 0x80FFFFFF.toInt())
        }

        return y + modelH + 2
    }

    private fun renderBaseStatsSection(guiGraphics: GuiGraphics, y: Int): Int {
        var cy = y
        draw(guiGraphics, "Base Stats", screenX + CONTENT_X, screenY + cy, SECTION_TITLE_COLOR)
        cy += 10

        val stats = listOf(
            "HP" to detail.baseStats.hp,
            "ATK" to detail.baseStats.attack,
            "DEF" to detail.baseStats.defence,
            "SpA" to detail.baseStats.specialAttack,
            "SpD" to detail.baseStats.specialDefence,
            "SPD" to detail.baseStats.speed
        )

        stats.forEach { (label, value) ->
            val barWidth = maxOf(0, minOf(value * STAT_BAR_MAX / 255, STAT_BAR_MAX))
            val barColor = statBarColor(value)
            val barX = screenX + CONTENT_X + STAT_LABEL_W
            val barY = screenY + cy + 1

            draw(guiGraphics, label, screenX + CONTENT_X, screenY + cy, 0xFFFFD700.toInt())
            guiGraphics.fill(barX, barY, barX + barWidth, barY + STAT_BAR_H, barColor)
            draw(guiGraphics, value.toString(), barX + barWidth + 2, screenY + cy, 0xFFFFFFFF.toInt())
            cy += ROW_H
        }

        draw(guiGraphics, "Total: ${detail.baseStats.total}", screenX + CONTENT_X, screenY + cy + 2, 0x80FFFFFF.toInt())
        cy += ROW_H + 2

        return cy
    }

    private fun renderAbilitiesSection(guiGraphics: GuiGraphics, y: Int): Int {
        var cy = y
        draw(guiGraphics, "Abilities", screenX + CONTENT_X, screenY + cy, SECTION_TITLE_COLOR)
        cy += 10

        detail.abilities.forEach { ability ->
            val prefix = if (ability.isHidden) "(H)" else "${detail.abilities.indexOf(ability) + 1}."
            val color = if (ability.isHidden) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt()
            draw(guiGraphics, "$prefix ${ability.name.replaceFirstChar { it.uppercase() }}", screenX + CONTENT_X, screenY + cy, color)
            cy += 10
        }

        return cy
    }

    private fun renderEvolutionSection(guiGraphics: GuiGraphics, y: Int): Int {
        var cy = y
        draw(guiGraphics, "Evolution", screenX + CONTENT_X, screenY + cy, SECTION_TITLE_COLOR)
        cy += 10

        if (detail.preEvolution != null) {
            draw(guiGraphics, "\u2191 ${detail.preEvolution!!.split(":").last().replaceFirstChar { it.uppercase() }}",
                screenX + CONTENT_X, screenY + cy, 0x80FFFFFF.toInt())
            cy += 10
        }

        detail.evolutions.forEach { evo ->
            val name = evo.targetName.replaceFirstChar { it.uppercase() }
            draw(guiGraphics, "\u2193 $name  (${evo.method})", screenX + CONTENT_X, screenY + cy, 0xFFFFFFFF.toInt())
            cy += 10
        }

        return cy
    }

    private fun renderMovesSection(guiGraphics: GuiGraphics, y: Int): Int {
        var cy = y
        draw(guiGraphics, "Moves", screenX + CONTENT_X, screenY + cy, SECTION_TITLE_COLOR)
        cy += 10

        val levelUp = detail.moves.filter { it.method == "level" }.sortedBy { it.level }
        val others = detail.moves.filter { it.method != "level" }

        levelUp.take(20).forEach { move ->
            val pw = if (move.power == 0) "\u2014" else move.power.toString()
            val ac = if (move.accuracy == 0) "\u221E" else move.accuracy.toString()
            val cat = when (move.category) {
                "physical" -> "Ph"
                "special" -> "Sp"
                else -> "St"
            }
            val typeAbbr = TypeDefenseChart.typeAbbreviation(move.type)
            val line = "Lv${move.level} ${move.name.replaceFirstChar { it.uppercase() }}  $typeAbbr $cat $pw $ac"
            val truncated = if (textWidth(line) > CONTENT_WIDTH) line.take(26) else line
            draw(guiGraphics, truncated, screenX + CONTENT_X, screenY + cy, 0xFFFFFFFF.toInt())
            cy += 9
        }

        if (levelUp.size > 20) {
            draw(guiGraphics, "... ${levelUp.size - 20} more", screenX + CONTENT_X, screenY + cy, 0x80FFFFFF.toInt())
            cy += 10
        }

        if (others.isNotEmpty()) {
            val groups = others.groupBy { it.method }
            groups.forEach { (method, methodMoves) ->
                val label = when (method) {
                    "tm" -> "TM"
                    "egg" -> "Egg"
                    "tutor" -> "Tutor"
                    else -> method
                }
                val names = methodMoves.joinToString(", ") { it.name.replaceFirstChar { c -> c.uppercase() } }
                val wrapped = wrapText("$label: $names", CONTENT_WIDTH - 4)
                wrapped.forEach { line ->
                    draw(guiGraphics, line, screenX + CONTENT_X + 4, screenY + cy, 0xA0FFFFFF.toInt())
                    cy += 9
                }
            }
        }

        return cy
    }

    private fun renderTrainingSection(guiGraphics: GuiGraphics, y: Int): Int {
        var cy = y
        draw(guiGraphics, "Training", screenX + CONTENT_X, screenY + cy, SECTION_TITLE_COLOR)
        cy += 10

        val evText = buildEvYieldText()
        draw(guiGraphics, "EV: $evText", screenX + CONTENT_X, screenY + cy, 0xFFFFFFFF.toInt())
        cy += 10

        val catchPct = detail.catchRate.toDouble() / 255.0 * 100.0
        draw(guiGraphics, "Catch: ${detail.catchRate} (${"%.1f".format(catchPct)}%)", screenX + CONTENT_X, screenY + cy, 0xFFFFFFFF.toInt())
        cy += 10

        val growth = detail.growthRate.replace("_", " ").replaceFirstChar { it.uppercase() }
        draw(guiGraphics, "Growth: $growth", screenX + CONTENT_X, screenY + cy, 0xFFFFFFFF.toInt())
        cy += 10

        draw(guiGraphics, "Base EXP: ${detail.baseExp}", screenX + CONTENT_X, screenY + cy, 0xFFFFFFFF.toInt())
        cy += 10

        return cy
    }

    private fun renderTypeDefensesSection(guiGraphics: GuiGraphics, y: Int): Int {
        var cy = y
        draw(guiGraphics, "Type Defenses", screenX + CONTENT_X, screenY + cy, SECTION_TITLE_COLOR)
        cy += 10

        val types = listOfNotNull(detail.primaryType, detail.secondaryType)
        val effectiveness = TypeDefenseChart.getEffectiveness(types)

        val weak = effectiveness.filter { it.value > 1.0 }.keys
        val resist = effectiveness.filter { it.value in 0.01..0.99 }.keys
        val immune = effectiveness.filter { it.value == 0.0 }.keys

        val weakText = weak.takeIf { it.isNotEmpty() }?.joinToString(" ") {
            TypeDefenseChart.multiplierText(effectiveness[it]!!) + TypeDefenseChart.typeAbbreviation(it)
        } ?: "None"
        val resistText = resist.takeIf { it.isNotEmpty() }?.joinToString(" ") {
            TypeDefenseChart.multiplierText(effectiveness[it]!!) + TypeDefenseChart.typeAbbreviation(it)
        } ?: "None"
        val immuneText = immune.takeIf { it.isNotEmpty() }?.joinToString(" ") {
            TypeDefenseChart.typeAbbreviation(it)
        } ?: "None"

        wrapText("Weak: $weakText", CONTENT_WIDTH).forEach {
            draw(guiGraphics, it, screenX + CONTENT_X, screenY + cy, 0xFFAA4444.toInt())
            cy += 9
        }
        wrapText("Resist: $resistText", CONTENT_WIDTH).forEach {
            draw(guiGraphics, it, screenX + CONTENT_X, screenY + cy, 0xFF44AA44.toInt())
            cy += 9
        }
        wrapText("Immune: $immuneText", CONTENT_WIDTH).forEach {
            draw(guiGraphics, it, screenX + CONTENT_X, screenY + cy, 0xFFAAAAFF.toInt())
            cy += 9
        }

        return cy
    }

    private fun renderBreedingSection(guiGraphics: GuiGraphics, y: Int): Int {
        var cy = y
        draw(guiGraphics, "Breeding", screenX + CONTENT_X, screenY + cy, SECTION_TITLE_COLOR)
        cy += 10

        val eggText = detail.eggGroups.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        draw(guiGraphics, "Egg: $eggText", screenX + CONTENT_X, screenY + cy, 0xFFFFFFFF.toInt())
        cy += 10

        val malePct = "%.0f".format(detail.maleRatio * 100)
        val femalePct = "%.0f".format((1 - detail.maleRatio) * 100)
        draw(guiGraphics, "Gender: $malePct%\u2642 $femalePct%\u2640", screenX + CONTENT_X, screenY + cy, 0xFFFFFFFF.toInt())
        cy += 10

        val steps = detail.eggCycles * 255
        draw(guiGraphics, "Cycles: ${detail.eggCycles} ($steps steps)", screenX + CONTENT_X, screenY + cy, 0xFFFFFFFF.toInt())
        cy += 10

        return cy
    }

    private fun draw(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int) {
        guiGraphics.drawString(font, text, x, y, color, false)
    }

    private fun textWidth(text: String): Int = font.width(text)

    private fun buildEvYieldText(): String {
        val yields = listOf(
            "HP" to detail.evYield.hp,
            "ATK" to detail.evYield.attack,
            "DEF" to detail.evYield.defence,
            "SpA" to detail.evYield.specialAttack,
            "SpD" to detail.evYield.specialDefence,
            "SPD" to detail.evYield.speed
        ).filter { it.second > 0 }

        if (yields.isEmpty()) return "None"
        return yields.joinToString(" ") { "${it.second} ${it.first}" }
    }

    private fun calculateContentHeight(): Int {
        var h = 14 + 50 + 2
        h += 10 + 6 * ROW_H + ROW_H + 2 + 2
        h += 10 + detail.abilities.size * 10 + 2
        if (detail.preEvolution != null || detail.evolutions.isNotEmpty()) {
            h += 10 + 10 + detail.evolutions.size * 10 + 2
            if (detail.preEvolution != null) h += 10
        }
        h += 10 + minOf(detail.moves.count { it.method == "level" }, 20) * 10 + 2
        h += 10 + 4 * 10 + 2
        h += 10 + 3 * 9 + 2
        h += 10 + 3 * 10 + 2
        return h
    }

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

    private fun isInBackButton(mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= screenX + HEADER_BACK_X && mouseX <= screenX + HEADER_BACK_X + 30 &&
                mouseY >= screenY + HEADER_Y - 2 && mouseY <= screenY + HEADER_Y + 10
    }

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    companion object {
        private const val GUI_WIDTH = 131
        private const val GUI_HEIGHT = 207

        private const val HEADER_BACK_X = 8
        private const val HEADER_Y = 8

        private const val CONTENT_X = 8
        private const val CONTENT_WIDTH = 115
        private const val CONTENT_START_Y = 24
        private const val CONTENT_END_Y = 200

        private const val SCROLL_SPEED = 10

        private const val MODEL_W = 34
        private const val ROW_H = 10
        private const val STAT_LABEL_W = 24
        private const val STAT_BAR_MAX = 55
        private const val STAT_BAR_H = 6

        private const val SECTION_TITLE_COLOR = 0xFFFFD700.toInt()

        private val HOME_SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/home_screen.png"
        )
    }
}
