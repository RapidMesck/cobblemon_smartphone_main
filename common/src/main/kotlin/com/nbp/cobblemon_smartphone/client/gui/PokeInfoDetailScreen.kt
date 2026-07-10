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
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    private var currentFormIndex = 0

    private fun reloadDetail() {
        val formName = if (currentFormIndex == 0) null else detail.availableForms.getOrNull(currentFormIndex)?.name
        detail = PokeInfoDataProvider.getDetail(dexNumber, formName) ?: return
        val species = PokemonSpecies.getByPokedexNumber(dexNumber)
        if (species != null) {
            val form = if (formName != null) species.forms.firstOrNull { it.name == formName } else species.standardForm
            modelPokemon = RenderablePokemon(species, (form?.aspects ?: emptyList()).toSet())
        }
        maxScroll = maxOf(0, calculateContentHeight() - (CONTENT_END_Y - CONTENT_START_Y))
        scrollY = 0
    }

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color

        reloadDetail()
        if (!::detail.isInitialized) {
            Minecraft.getInstance().setScreen(SmartphoneScreen(color, smartphoneStack))
            return
        }
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

        cy = renderFormSection(guiGraphics, cy, mouseX, mouseY)
        cy = drawSep(guiGraphics, cy)
        cy = renderTopSection(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        cy = renderBaseStatsSection(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        cy = renderAbilitiesSection(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        cy = renderEvolutionSection(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        cy = renderTrainingSection(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        cy = renderSpawningSection(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        cy = renderBreedingSection(guiGraphics, cy)
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
        // Form navigation (inside scissor, scroll-relative)
        val forms = detail.availableForms
        if (forms.size > 1) {
            val navSy = CONTENT_START_Y - scrollY
            val navY = screenY + navSy + SECTION_PAD_TOP
            val navCenter = screenX + CONTENT_X + CONTENT_WIDTH / 2
            val mx = mouseX.toInt()
            val my = mouseY.toInt()
            if (currentFormIndex > 0 && mx >= navCenter - 64 && mx <= navCenter - 50 && my >= navY - 2 && my <= navY + 10) {
                currentFormIndex--
                reloadDetail()
                return true
            }
            if (currentFormIndex < forms.size - 1 && mx >= navCenter + 50 && mx <= navCenter + 60 && my >= navY - 2 && my <= navY + 10) {
                currentFormIndex++
                reloadDetail()
                return true
            }
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
        val backY = screenY + HEADER_Y + 6
        val color = if (hovered) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt()
        draw(guiGraphics, "\u00AB Back", screenX + HEADER_BACK_X, backY, color)
    }

    private fun renderFormSection(guiGraphics: GuiGraphics, sy: Int, mouseX: Int, mouseY: Int): Int {
        val forms = detail.availableForms
        if (forms.size <= 1) return sy

        val totalH = SECTION_PAD_TOP + TITLE_GAP
        sectionBox(guiGraphics, sy, totalH)

        val y = sy + SECTION_PAD_TOP
        val navCenter = screenX + CONTENT_X + CONTENT_WIDTH / 2
        val formName = forms[currentFormIndex].displayName

        if (currentFormIndex > 0) {
            val lx = navCenter - 60
            val leftHover = mouseX >= lx - 4 && mouseX <= lx + 10 && mouseY >= screenY + y - 2 && mouseY <= screenY + y + 10
            draw(guiGraphics, "\u003C", lx, screenY + y, if (leftHover) 0xFFFFD700.toInt() else SECTION_TITLE_COLOR)
        }
        draw(guiGraphics, formName, navCenter - textWidth(formName) / 2, screenY + y, 0xFFFFFFFF.toInt())
        if (currentFormIndex < forms.size - 1) {
            val rx = navCenter + 50
            val rightHover = mouseX >= rx - 4 && mouseX <= rx + 10 && mouseY >= screenY + y - 2 && mouseY <= screenY + y + 10
            draw(guiGraphics, "\u003E", rx, screenY + y, if (rightHover) 0xFFFFD700.toInt() else SECTION_TITLE_COLOR)
        }

        return sy + totalH
    }

    private fun renderTopSection(guiGraphics: GuiGraphics, sy: Int): Int {
        val topPad = 20
        val leftW = 66
        val rightX = CONTENT_X + leftW + 4 + SECTION_PAD
        val totalH = SECTION_PAD_TOP + topPad + 56 + SECTION_PAD_BOTTOM
        sectionBgLight(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP + topPad
        renderModel(guiGraphics, screenX + CONTENT_X, screenY + y, leftW)

        val dexAndName = "#${String.format("%03d", detail.dexNumber)} ${detail.name}"
        draw(guiGraphics, dexAndName, screenX + rightX, screenY + y, CONTENT_TEXT)

        val types = listOfNotNull(detail.primaryType, detail.secondaryType)
        var tx = screenX + rightX
        types.forEachIndexed { i, type ->
            val name = type.replaceFirstChar { c -> c.uppercase() }
            draw(guiGraphics, name, tx, screenY + y + 10, typeColor(type))
            tx += textWidth(name)
            if (i < types.size - 1) {
                draw(guiGraphics, " / ", tx, screenY + y + 10, CONTENT_DIM)
                tx += textWidth(" / ")
            }
        }

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
            matrices.translate(mx + w / 2f, my - 22f, 0f)
            drawProfilePokemon(
                pokemon, matrices, Quaternionf().rotateY(Math.toRadians(30.0).toFloat()),
                PoseType.PROFILE, posableState, 0f, 35f
            )
            matrices.popPose()
        } else {
            draw(guiGraphics, "3D", mx + w / 2 - 8, my + modelH / 2 - 4, 0x80FFFFFF.toInt())
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
            "Spe" to detail.baseStats.speed
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

        val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
        val descW = wrapW - 8 // indent for description

        var lineCount = 1 // title line
        detail.abilities.forEach { a ->
            lineCount += 1 // ability name
            if (a.description.isNotEmpty()) {
                lineCount += wrapText(a.description, descW).size
            }
        }
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + lineCount * 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        val tx = screenX + CONTENT_X + SECTION_PAD
        draw(guiGraphics, "Abilities", tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        detail.abilities.forEach { a ->
            val prefix = if (a.isHidden) "(H) " else ""
            draw(guiGraphics, prefix + a.name.replaceFirstChar { it.uppercase() }, tx, screenY + y, CONTENT_TEXT)
            y += 9
            if (a.description.isNotEmpty()) {
                wrapText(a.description, descW).forEach { line ->
                    draw(guiGraphics, line, tx + 8, screenY + y, CONTENT_DIM)
                    y += 9
                }
            }
        }
        return sy + totalH
    }

    private fun renderEvolutionSection(guiGraphics: GuiGraphics, sy: Int): Int {
        val hasPre = detail.preEvolution != null
        val hasEvos = detail.evolutions.isNotEmpty()
        if (!hasPre && !hasEvos) return sy

        val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
        val tx = screenX + CONTENT_X + SECTION_PAD

        // Pre-calculate wrapped lines
        var lineCount = 0
        if (hasPre) {
            val preName = detail.preEvolution!!.split(":").last().replaceFirstChar { it.uppercase() }
            lineCount += wrapText("\u2191 $preName", wrapW).size
        }
        detail.evolutions.forEach { evo ->
            val name = evo.targetName.replaceFirstChar { it.uppercase() }
            lineCount += wrapText("\u2193 $name (${evo.method})", wrapW).size
        }

        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + lineCount * 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)
        var y = sy + SECTION_PAD_TOP
        draw(guiGraphics, "Evolution", tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        if (hasPre) {
            val preName = detail.preEvolution!!.split(":").last().replaceFirstChar { it.uppercase() }
            wrapText("\u2191 $preName", wrapW).forEach {
                draw(guiGraphics, it, tx, screenY + y, CONTENT_DIM)
                y += 9
            }
        }
        detail.evolutions.forEach { evo ->
            val name = evo.targetName.replaceFirstChar { it.uppercase() }
            wrapText("\u2193 $name (${evo.method})", wrapW).forEach {
                draw(guiGraphics, it, tx, screenY + y, CONTENT_TEXT)
                y += 9
            }
        }
        return sy + totalH
    }

    private fun renderTrainingSection(guiGraphics: GuiGraphics, sy: Int): Int {
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 4 * 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        val tx = screenX + CONTENT_X + SECTION_PAD
        draw(guiGraphics, "Training", tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        // EV Yield - only show non-zero
        val evs = listOf(
            "HP" to detail.evYield.hp,
            "Atk" to detail.evYield.attack,
            "Def" to detail.evYield.defence,
            "SpA" to detail.evYield.specialAttack,
            "SpD" to detail.evYield.specialDefence,
            "Spe" to detail.evYield.speed
        ).filter { it.second > 0 }
        val evText = if (evs.isEmpty()) "None"
        else evs.joinToString(", ") { "${it.first} ${it.second}" }
        draw(guiGraphics, "EV: $evText", tx, screenY + y, CONTENT_TEXT); y += 9

        // Catch rate
        draw(guiGraphics, "Catch Rate: ${detail.catchRate} (${catchPercent(detail.catchRate)}%)", tx, screenY + y, CONTENT_TEXT); y += 9

        // Base Friendship
        draw(guiGraphics, "Friendship: ${detail.baseFriendship}", tx, screenY + y, CONTENT_TEXT); y += 9

        // Base Exp
        draw(guiGraphics, "Base Exp: ${detail.baseExp}", tx, screenY + y, CONTENT_TEXT); y += 9

        return sy + totalH
    }

    private fun renderSpawningSection(guiGraphics: GuiGraphics, sy: Int): Int {
        val biomes = detail.spawnBiomes
        if (biomes.isEmpty()) return sy

        val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
        val tx = screenX + CONTENT_X + SECTION_PAD
        val biomeText = biomes.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        val lines = wrapText("Biomes: $biomeText", wrapW)
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + lines.size * 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        draw(guiGraphics, "Spawning", tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        lines.forEach { line ->
            draw(guiGraphics, line, tx, screenY + y, CONTENT_TEXT)
            y += 9
        }
        return sy + totalH
    }

    private fun renderBreedingSection(guiGraphics: GuiGraphics, sy: Int): Int {
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 3 * 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        val tx = screenX + CONTENT_X + SECTION_PAD
        draw(guiGraphics, "Breeding", tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        // Egg Groups
        val groups = detail.eggGroups.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        draw(guiGraphics, "Egg Groups: $groups", tx, screenY + y, CONTENT_TEXT); y += 9

        // Gender
        val gender = when {
            detail.maleRatio < 0 -> "Genderless"
            detail.maleRatio == 0f -> "100% F"
            detail.maleRatio == 1f -> "100% M"
            else -> "${(detail.maleRatio * 100).toInt()}% M / ${((1 - detail.maleRatio) * 100).toInt()}% F"
        }
        draw(guiGraphics, "Gender: $gender", tx, screenY + y, CONTENT_TEXT); y += 9

        // Egg Cycles
        draw(guiGraphics, "Egg Cycles: ${detail.eggCycles}", tx, screenY + y, CONTENT_TEXT); y += 9

        return sy + totalH
    }

    private fun renderTypeDefensesSection(guiGraphics: GuiGraphics, sy: Int): Int {
        val types = listOfNotNull(detail.primaryType, detail.secondaryType)
        val eff = getEffectiveness(types)

        // Group by multiplier, exclude neutral (1.0)
        val groups = eff.entries
            .filter { it.value != 1.0 }
            .groupBy({ it.value }, { it.key })
            .mapKeys { (mult, _) ->
                when {
                    mult > 1.0 -> "Weak (x${multiplierText(mult)})"
                    mult == 0.0 -> "Immune (x0)"
                    else -> "Resist (x${multiplierText(mult)})"
                }
            }
            .mapValues { (_, typeNames) ->
                typeNames.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
            }

        val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
        var totalLines = 0
        if (groups.isEmpty()) {
            totalLines = 1 // "None"
        } else {
            groups.forEach { (label, text) ->
                totalLines += wrapText("$label: $text", wrapW).size
            }
        }
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + totalLines * 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        val tx = screenX + CONTENT_X + SECTION_PAD
        draw(guiGraphics, "Type Defenses", tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        if (groups.isEmpty()) {
            draw(guiGraphics, "None", tx, screenY + y, CONTENT_DIM)
        } else {
            groups.forEach { (label, text) ->
                val color = when {
                    label.startsWith("Weak") -> CONTENT_WEAK
                    label.startsWith("Immune") -> CONTENT_IMMUNE
                    else -> CONTENT_RESIST
                }
                wrapText("$label: $text", wrapW).forEach {
                    draw(guiGraphics, it, tx, screenY + y, color); y += 9
                }
            }
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
            val typeAbbr = typeAbbreviation(move.type)
            val moveName = truncate(move.name.replaceFirstChar { it.uppercase() }, MOVE_W - 2)

            draw(guiGraphics, "${move.level}", 0, ry, CONTENT_TEXT)
            draw(guiGraphics, moveName, LV_W, ry, CONTENT_TEXT)
            draw(guiGraphics, typeAbbr, LV_W + MOVE_W, ry, typeColor(move.type))
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
        val groupGap = 4
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + lineCount * 9 + (groups.size - 1) * groupGap + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        draw(guiGraphics, "Learnable Moves", tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        var first = true
        groups.forEach { (method, methodMoves) ->
            if (!first) y += groupGap
            first = false
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
        guiGraphics.fill(x1, y1, x2, y1 + titleH, SECTION_TITLE_BG)
        guiGraphics.fill(x1, y1 + titleH, x2, y1 + h, SECTION_CONTENT_BG)
    }

    private fun sectionBgLight(guiGraphics: GuiGraphics, y: Int, h: Int) {
        guiGraphics.fill(
            screenX + CONTENT_X, screenY + y,
            screenX + CONTENT_X + CONTENT_WIDTH, screenY + y + h,
            SECTION_CONTENT_BG
        )
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
    val topH = SECTION_PAD_TOP + 20 + 56 + SECTION_PAD_BOTTOM
    val formNavH = if (detail.availableForms.size > 1) SECTION_PAD_TOP + TITLE_GAP else 0
    val statsH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 6 * 9 + 9 + SECTION_PAD_BOTTOM
    val abiH =
        if (detail.abilities.isNotEmpty()) {
            val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
            val descW = wrapW - 8
            var lines = 1
            detail.abilities.forEach { a ->
                lines += 1
                if (a.description.isNotEmpty()) lines += wrapText(a.description, descW).size
            }
            SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + lines * 9 + SECTION_PAD_BOTTOM
        } else 0
    val evoH = run {
        val hasPre = detail.preEvolution != null
        val hasEvos = detail.evolutions.isNotEmpty()
        if (!hasPre && !hasEvos) 0
        else {
            val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
            var lines = 0
            if (hasPre) {
                val preName = detail.preEvolution!!.split(":").last().replaceFirstChar { it.uppercase() }
                lines += wrapText("\u2191 $preName", wrapW).size
            }
            detail.evolutions.forEach { evo ->
                val name = evo.targetName.replaceFirstChar { it.uppercase() }
                lines += wrapText("\u2193 $name (${evo.method})", wrapW).size
            }
            SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + lines * 9 + SECTION_PAD_BOTTOM
        }
    }
    val trainH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 4 * 9 + SECTION_PAD_BOTTOM
    val spawnH = if (detail.spawnBiomes.isNotEmpty()) {
        val biomeText = detail.spawnBiomes.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        val lines = wrapText("Biomes: $biomeText", CONTENT_WIDTH - SECTION_PAD * 2).size
        SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + lines * 9 + SECTION_PAD_BOTTOM
    } else 0
    val breedH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 3 * 9 + SECTION_PAD_BOTTOM
    val defH = run {
        val types = listOfNotNull(detail.primaryType, detail.secondaryType)
        val eff = getEffectiveness(types)
        val groups = eff.entries.filter { it.value != 1.0 }
            .groupBy({ it.value }, { it.key })
        val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
        var lines = 0
        if (groups.isEmpty()) {
            lines = 1
        } else {
            groups.forEach { (mult, typeNames) ->
                val label = when {
                    mult > 1.0 -> "Weak (x${multiplierText(mult)})"
                    mult == 0.0 -> "Immune (x0)"
                    else -> "Resist (x${multiplierText(mult)})"
                }
                val text = typeNames.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
                lines += wrapText("$label: $text", wrapW).size
            }
        }
        SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + lines * 9 + SECTION_PAD_BOTTOM
    }
    val movesH =
        SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 7 + minOf(detail.moves.count { it.method == "level" }, 20) * 7 + SECTION_PAD_BOTTOM
    val learnH = run {
        val others = detail.moves.filter { it.method != "level" }
        if (others.isEmpty()) 0
        else {
            val groups = others.groupBy { it.method }
            val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
            var lines = 0
            groups.forEach { (method, methodMoves) ->
                val label = when (method) {
                    "tm" -> "TM"; "egg" -> "Egg"; "tutor" -> "Tutor"; else -> method
                }
                val names = methodMoves.joinToString(", ") { it.name.replaceFirstChar { c -> c.uppercase() } }
                lines += wrapText("$label: $names", wrapW).size
            }
            SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + lines * 9 + (groups.size - 1) * 4 + SECTION_PAD_BOTTOM
        }
    }
    val separators = 10 * 4
    return topH + formNavH + statsH + abiH + evoH + trainH + spawnH + breedH + defH + movesH + learnH + separators
}

private fun isInBackButton(mouseX: Int, mouseY: Int): Boolean {
    val backY = screenY + HEADER_Y + 6
    return mouseX >= screenX + HEADER_BACK_X && mouseX <= screenX + HEADER_BACK_X + 30 &&
            mouseY >= backY - 2 && mouseY <= backY + 10
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

    // --- Type chart & helpers ---

    private val typeChart: Map<String, Map<String, Double>> = mapOf(
        "normal" to mapOf(
            "fighting" to 2.0, "ghost" to 0.0
        ),
        "fire" to mapOf(
            "fire" to 0.5, "water" to 2.0, "grass" to 0.5, "ice" to 0.5,
            "ground" to 2.0, "bug" to 0.5, "rock" to 2.0, "steel" to 0.5, "fairy" to 0.5
        ),
        "water" to mapOf(
            "fire" to 0.5, "water" to 0.5, "grass" to 2.0, "electric" to 2.0,
            "ice" to 0.5, "steel" to 0.5
        ),
        "electric" to mapOf(
            "electric" to 0.5, "ground" to 2.0, "flying" to 0.5, "steel" to 0.5
        ),
        "grass" to mapOf(
            "fire" to 2.0, "water" to 0.5, "electric" to 0.5, "grass" to 0.5, "ice" to 2.0,
            "poison" to 2.0, "ground" to 0.5, "flying" to 2.0, "bug" to 2.0
        ),
        "ice" to mapOf(
            "fire" to 2.0, "ice" to 0.5, "fighting" to 2.0, "rock" to 2.0, "steel" to 2.0
        ),
        "fighting" to mapOf(
            "flying" to 2.0, "psychic" to 2.0, "bug" to 0.5, "rock" to 0.5,
            "dark" to 0.5, "fairy" to 2.0
        ),
        "poison" to mapOf(
            "grass" to 0.5, "fighting" to 0.5, "poison" to 0.5,
            "ground" to 2.0, "psychic" to 2.0, "bug" to 0.5, "fairy" to 0.5
        ),
        "ground" to mapOf(
            "water" to 2.0, "grass" to 2.0, "electric" to 0.0, "ice" to 2.0,
            "poison" to 0.5, "rock" to 0.5
        ),
        "flying" to mapOf(
            "electric" to 2.0, "grass" to 0.5, "ice" to 2.0, "fighting" to 0.5,
            "ground" to 0.0, "bug" to 0.5, "rock" to 2.0
        ),
        "psychic" to mapOf(
            "fighting" to 0.5, "psychic" to 0.5, "bug" to 2.0, "ghost" to 2.0, "dark" to 2.0
        ),
        "bug" to mapOf(
            "fire" to 2.0, "grass" to 0.5, "fighting" to 0.5, "ground" to 0.5,
            "flying" to 2.0, "rock" to 2.0
        ),
        "rock" to mapOf(
            "normal" to 0.5, "fire" to 0.5, "water" to 2.0, "grass" to 2.0,
            "fighting" to 2.0, "poison" to 0.5, "ground" to 2.0, "flying" to 0.5,
            "steel" to 2.0
        ),
        "ghost" to mapOf(
            "normal" to 0.0, "fighting" to 0.0, "poison" to 0.5, "bug" to 0.5,
            "ghost" to 2.0, "dark" to 2.0
        ),
        "dragon" to mapOf(
            "fire" to 0.5, "water" to 0.5, "grass" to 0.5, "electric" to 0.5,
            "ice" to 2.0, "dragon" to 2.0, "fairy" to 2.0
        ),
        "dark" to mapOf(
            "fighting" to 2.0, "psychic" to 0.0, "bug" to 2.0, "ghost" to 0.5,
            "dark" to 0.5, "fairy" to 2.0
        ),
        "steel" to mapOf(
            "normal" to 0.5, "fire" to 2.0, "grass" to 0.5, "ice" to 0.5,
            "fighting" to 2.0, "poison" to 0.0, "ground" to 2.0, "flying" to 0.5,
            "psychic" to 0.5, "bug" to 0.5, "rock" to 0.5, "dragon" to 0.5,
            "steel" to 0.5, "fairy" to 0.5
        ),
        "fairy" to mapOf(
            "fighting" to 0.5, "poison" to 2.0, "bug" to 0.5, "dragon" to 0.0,
            "dark" to 0.5, "steel" to 2.0
        )
    )

    private val allTypes = listOf(
        "normal", "fire", "water", "electric", "grass", "ice",
        "fighting", "poison", "ground", "flying", "psychic", "bug",
        "rock", "ghost", "dragon", "dark", "steel", "fairy"
    )

    private val typeAbbr = mapOf(
        "normal" to "Nor", "fire" to "Fir", "water" to "Wat", "electric" to "Ele",
        "grass" to "Gra", "ice" to "Ice", "fighting" to "Fig", "poison" to "Poi",
        "ground" to "Gro", "flying" to "Fly", "psychic" to "Psy", "bug" to "Bug",
        "rock" to "Roc", "ghost" to "Gho", "dragon" to "Dra", "dark" to "Dar",
        "steel" to "Ste", "fairy" to "Fai"
    )

    fun getEffectiveness(types: List<String>): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        allTypes.forEach { result[it] = 1.0 }
        for (type in types) {
            typeChart[type.lowercase()]?.forEach { (attackType, multiplier) ->
                result[attackType] = (result[attackType] ?: 1.0) * multiplier
            }
        }
        return result
    }

    fun multiplierText(value: Double): String = when (value) {
        0.0 -> "0"
        0.25 -> "\u00BC"
        0.5 -> "\u00BD"
        1.0 -> "-"
        2.0 -> "2"
        4.0 -> "4"
        else -> value.toString()
    }

    fun typeAbbreviation(type: String): String = typeAbbr[type.lowercase()] ?: type.take(3)

    fun typeColor(type: String): Int = when (type.lowercase()) {
        "normal" -> 0xFFA8A77A.toInt()
        "fire" -> 0xFFEE8130.toInt()
        "water" -> 0xFF6390F0.toInt()
        "electric" -> 0xFFF7D02C.toInt()
        "grass" -> 0xFF7AC74C.toInt()
        "ice" -> 0xFF96D9D6.toInt()
        "fighting" -> 0xFFC22E28.toInt()
        "poison" -> 0xFFA33EA1.toInt()
        "ground" -> 0xFFE2BF65.toInt()
        "flying" -> 0xFFA98FF3.toInt()
        "psychic" -> 0xFFF95587.toInt()
        "bug" -> 0xFFA6B91A.toInt()
        "rock" -> 0xFFB6A136.toInt()
        "ghost" -> 0xFF735797.toInt()
        "dragon" -> 0xFF6F35FC.toInt()
        "dark" -> 0xFF705746.toInt()
        "steel" -> 0xFFB7B7CE.toInt()
        "fairy" -> 0xFFD685AD.toInt()
        else -> CONTENT_TEXT
    }

    fun catchPercent(catchRate: Int): Int {
        val a = (catchRate / 3.0).roundToInt().coerceAtLeast(1)
        if (a >= 255) return 100
        val b = 65536.0 / sqrt(sqrt(255.0 / a))
        return (b / 65536.0).pow(4).times(100).roundToInt()
    }
}
}
