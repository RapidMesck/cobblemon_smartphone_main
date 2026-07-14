package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.RequestSpeciesDetailPacket
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
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

    private val frameTexture get() = color.getLargeScreenTexture()
    private var screenX = 0
    private var screenY = 0
    private var scrollY = 0
    private var maxScroll = 0
    private var draggingScrollbar = false
    private var dragStartMouseY = 0
    private var dragStartScrollY = 0
    private var modelPokemon: RenderablePokemon? = null
    private var posableState = FloatingState()
    private lateinit var detail: PokeInfoDataProvider.SpeciesDetail
    private var currentFormIndex = 0
    private var detailLoading = true
    private val expandedBuckets = mutableSetOf<String>()

    // Clickable header regions: bucketName -> (x1, y1, x2, y2) in screen coords
    private val bucketHeaderRects = mutableMapOf<String, Rect>()

    private data class Rect(val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
        fun contains(mx: Int, my: Int) = mx in x1..x2 && my in y1..y2
    }

    private fun applyDetail(received: PokeInfoDataProvider.SpeciesDetail) {
        detail = received
        detailLoading = false
        val species = PokemonSpecies.getByPokedexNumber(dexNumber)
        if (species != null) {
            val formName = if (currentFormIndex == 0) null else detail.availableForms.getOrNull(currentFormIndex)?.name
            val form = if (formName != null) species.forms.firstOrNull { it.name == formName } else species.standardForm
            modelPokemon = RenderablePokemon(species, (form?.aspects ?: emptyList()).toSet())
        }
        maxScroll = maxOf(0, calculateContentHeight() - (CONTENT_END_Y - CONTENT_START_Y))
        scrollY = 0
    }

    private fun requestDetail() {
        val formName = if (currentFormIndex == 0) null else
            (if (::detail.isInitialized) detail.availableForms.getOrNull(currentFormIndex)?.name else null)
        detailLoading = true
        RequestSpeciesDetailPacket(dexNumber, formName).sendToServer()
    }

    override fun isPauseScreen(): Boolean = false

    private fun lang(key: String, vararg args: Any): String =
        Component.translatable("cobblemon_smartphone.pokeinfo.$key", *args.map { it.toString() }.toTypedArray()).string

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color

        requestDetail()
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

        // Check if detail arrived from server while screen is open
        if (detailLoading) {
            val pending = PokeInfoDataProvider.pendingDetail
            if (pending != null) {
                PokeInfoDataProvider.pendingDetail = null
                applyDetail(pending)
            }
        }

        if (!::detail.isInitialized) {
            // Still loading, show only loading state
            guiGraphics.disableScissor()
            renderLoadingOverlay(guiGraphics)
            return
        }

        var cy = CONTENT_START_Y - scrollY
        val cfg = CobblemonSmartphone.config.pokeInfo

        cy = renderFormSection(guiGraphics, cy, mouseX, mouseY)
        cy = drawSep(guiGraphics, cy)
        cy = renderTopSection(guiGraphics, cy)
        cy = drawSep(guiGraphics, cy)
        if (cfg.showBaseStats) {
            cy = renderBaseStatsSection(guiGraphics, cy); cy = drawSep(guiGraphics, cy)
        }
        if (cfg.showAbilities) {
            cy = renderAbilitiesSection(guiGraphics, cy); cy = drawSep(guiGraphics, cy)
        }
        if (cfg.showEvolution) {
            cy = renderEvolutionSection(guiGraphics, cy); cy = drawSep(guiGraphics, cy)
        }
        if (cfg.showTraining) {
            cy = renderTrainingSection(guiGraphics, cy); cy = drawSep(guiGraphics, cy)
        }
        if (cfg.showBreeding) {
            cy = renderBreedingSection(guiGraphics, cy); cy = drawSep(guiGraphics, cy)
        }
        if (cfg.showTypeDefenses) {
            cy = renderTypeDefensesSection(guiGraphics, cy); cy = drawSep(guiGraphics, cy)
        }
        if (cfg.showLevelMoves) {
            cy = renderLevelMovesTable(guiGraphics, cy); cy = drawSep(guiGraphics, cy)
        }
        if (cfg.showLearnableMoves) {
            cy = renderLearnableMoves(guiGraphics, cy); cy = drawSep(guiGraphics, cy)
        }
        if (cfg.showSpawning) {
            cy = renderSpawningSection(guiGraphics, cy); cy = drawSep(guiGraphics, cy)
        }

        guiGraphics.disableScissor()
        renderScrollbar(guiGraphics, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (isInBackButton(mx, my)) {
            playClickSound()
            Minecraft.getInstance().setScreen(PokeInfoScreen(color, smartphoneStack))
            return true
        }

        // Bucket header toggle
        if (::detail.isInitialized) {
            for ((bucket, rect) in bucketHeaderRects) {
                if (rect.contains(mx, my)) {
                    playClickSound()
                    if (expandedBuckets.contains(bucket)) expandedBuckets.remove(bucket)
                    else expandedBuckets.add(bucket)
                    maxScroll = maxOf(0, calculateContentHeight() - (CONTENT_END_Y - CONTENT_START_Y))
                    return true
                }
            }
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
                requestDetail()
                return true
            }
            if (currentFormIndex < forms.size - 1 && mx >= navCenter + 50 && mx <= navCenter + 60 && my >= navY - 2 && my <= navY + 10) {
                currentFormIndex++
                requestDetail()
                return true
            }
        }
        // Scrollbar drag
        if (maxScroll > 0) {
            val trackH = CONTENT_END_Y - CONTENT_START_Y
            val totalH = calculateContentHeight()
            val handleH = maxOf(12, (trackH.toFloat() * trackH / totalH.toFloat()).toInt())
            val handleY = screenY + CONTENT_START_Y + (scrollY.toFloat() / maxScroll * (trackH - handleH)).toInt()
            val trackX = screenX + CONTENT_X + CONTENT_WIDTH + 1
            val mx = mouseX.toInt()
            val my = mouseY.toInt()
            if (mx >= trackX && mx <= trackX + 4 && my >= handleY && my <= handleY + handleH) {
                draggingScrollbar = true
                dragStartMouseY = my
                dragStartScrollY = scrollY
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingScrollbar = false
        return super.mouseReleased(mouseX, mouseY, button)
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
        draw(guiGraphics, lang("back"), screenX + HEADER_BACK_X, backY, color)
    }

    private fun renderLoadingOverlay(guiGraphics: GuiGraphics) {
        val cx = screenX + CONTENT_X + CONTENT_WIDTH / 2
        val cy = screenY + CONTENT_START_Y + (CONTENT_END_Y - CONTENT_START_Y) / 2
        val text = lang("loading_spawn_data")
        draw(guiGraphics, text, cx - textWidth(text) / 2, cy, 0xFFAAAAAA.toInt())
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
            val leftHover =
                mouseX >= lx - 4 && mouseX <= lx + 10 && mouseY >= screenY + y - 2 && mouseY <= screenY + y + 10
            draw(guiGraphics, "\u003C", lx, screenY + y, if (leftHover) 0xFFFFD700.toInt() else SECTION_TITLE_COLOR)
        }
        draw(guiGraphics, formName, navCenter - textWidth(formName) / 2, screenY + y, 0xFFFFFFFF.toInt())
        if (currentFormIndex < forms.size - 1) {
            val rx = navCenter + 50
            val rightHover =
                mouseX >= rx - 4 && mouseX <= rx + 10 && mouseY >= screenY + y - 2 && mouseY <= screenY + y + 10
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
        draw(guiGraphics, dexAndName, screenX + rightX, screenY + y, CONTENT_GOLD)

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
        draw(guiGraphics, lang("base_stats"), screenX + CONTENT_X + SECTION_PAD, screenY + y, SECTION_TITLE_COLOR)
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
        draw(guiGraphics, lang("abilities"), tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        detail.abilities.forEach { a ->
            val prefix = if (a.isHidden) "(H) " else ""
            if (a.isHidden) {
                draw(guiGraphics, "(H) ", tx, screenY + y, CONTENT_COND)
                draw(
                    guiGraphics,
                    a.name.replaceFirstChar { it.uppercase() },
                    tx + textWidth("(H) "),
                    screenY + y,
                    CONTENT_GOLD
                )
            } else {
                draw(guiGraphics, a.name.replaceFirstChar { it.uppercase() }, tx, screenY + y, CONTENT_GOLD)
            }
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
        draw(guiGraphics, lang("evolution"), tx, screenY + y, SECTION_TITLE_COLOR)
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
        draw(guiGraphics, lang("training"), tx, screenY + y, SECTION_TITLE_COLOR)
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
        val evText = if (evs.isEmpty()) lang("none")
        else evs.joinToString(", ") { "${it.first} ${it.second}" }
        draw(guiGraphics, lang("ev") + ": ", tx, screenY + y, CONTENT_DIM)
        draw(guiGraphics, evText, tx + textWidth(lang("ev") + ": "), screenY + y, CONTENT_GOLD)
        y += 9

        // Catch rate
        draw(guiGraphics, lang("catch_rate") + ": ", tx, screenY + y, CONTENT_DIM)
        draw(
            guiGraphics,
            "${detail.catchRate} (${catchPercent(detail.catchRate)}%)",
            tx + textWidth(lang("catch_rate") + ": "),
            screenY + y,
            CONTENT_TEXT
        )
        y += 9

        // Base Friendship
        draw(guiGraphics, lang("friendship") + ": ", tx, screenY + y, CONTENT_DIM)
        draw(
            guiGraphics,
            "${detail.baseFriendship}",
            tx + textWidth(lang("friendship") + ": "),
            screenY + y,
            CONTENT_TEXT
        )
        y += 9

        // Base Exp
        draw(guiGraphics, lang("base_exp") + ": ", tx, screenY + y, CONTENT_DIM)
        draw(guiGraphics, "${detail.baseExp}", tx + textWidth(lang("base_exp") + ": "), screenY + y, CONTENT_TEXT)
        y += 9

        return sy + totalH
    }

    private fun renderSpawningSection(guiGraphics: GuiGraphics, sy: Int): Int {
        if (detailLoading) {
            val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 9 + SECTION_PAD_BOTTOM
            sectionBox(guiGraphics, sy, totalH)
            val tx = screenX + CONTENT_X + SECTION_PAD
            var y = sy + SECTION_PAD_TOP
            draw(guiGraphics, lang("spawning"), tx, screenY + y, SECTION_TITLE_COLOR)
            y += TITLE_GAP + CONTENT_GAP
            draw(guiGraphics, lang("loading_spawn_data"), tx, screenY + y, 0xFFAAAAAA.toInt())
            return sy + totalH
        }

        val entries = detail.spawnEntries
        if (entries.isEmpty()) return sy

        // Group by bucket
        val groups = entries.groupBy { it.bucket }
        val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
        val tx = screenX + CONTENT_X + SECTION_PAD

        bucketHeaderRects.clear()
        val allLines = mutableListOf<LineSegments>()

        for ((bucket, bucketEntries) in groups) {
            val expanded = expandedBuckets.contains(bucket)
            val hasMultiple = bucketEntries.size > 1

            // ── Header line ──
            val arrow = if (expanded) "\u25BC" else "\u25B6"
            val weight = bucketEntries.firstOrNull()?.weight ?: -1f
            val weightStr = if (weight >= 0f) " (${weight.toInt()})" else " (Default)"
            val headerText = "$arrow $bucket$weightStr"
            allLines.add(LineSegments(listOf(Segment(headerText, CONTENT_GOLD))))

            if (expanded) {
                // ── Expanded: show each entry ──
                for (entry in bucketEntries) {
                    // Biomes
                    val biomeLabel = lang("biomes") + ": "
                    val biomeValue = entry.biomes.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
                    val biomePrefixW = textWidth(biomeLabel)
                    val biomeWrapped = wrapText(biomeValue, wrapW - biomePrefixW)
                    for ((bi, line) in biomeWrapped.withIndex()) {
                        val items = mutableListOf<Segment>()
                        if (bi == 0) items.add(Segment(biomeLabel, CONTENT_DIM))
                        else items.add(Segment(" ".repeat(8), CONTENT_DIM))
                        items.add(Segment(line, CONTENT_TEXT))
                        allLines.add(LineSegments(items))
                    }
                    // Level
                    allLines.add(
                        LineSegments(
                            listOf(
                                Segment(lang("level_label") + ": ", CONTENT_DIM),
                                Segment("${entry.minLevel} \u2013 ${entry.maxLevel}", CONTENT_GOLD)
                            )
                        )
                    )
                    // Context
                    val ctxValue = entry.context.replaceFirstChar { c -> c.uppercase() }
                    allLines.add(
                        LineSegments(
                            listOf(
                                Segment(lang("context_label") + ": ", CONTENT_DIM),
                                Segment(ctxValue, CONTENT_TEXT)
                            )
                        )
                    )
                    // Time
                    if (!entry.time.isNullOrBlank()) {
                        allLines.add(
                            LineSegments(
                                listOf(
                                    Segment(lang("time_label") + ": ", CONTENT_DIM),
                                    Segment(entry.time, CONTENT_TEXT)
                                )
                            )
                        )
                    }
                    // Conditions
                    if (entry.conditions.isNotEmpty()) {
                        val condLabel = lang("cond") + ": "
                        val condValue = entry.conditions.joinToString(", ")
                        val condPrefixW = textWidth(condLabel)
                        val condWrapped = wrapText(condValue, wrapW - condPrefixW)
                        for ((ci, line) in condWrapped.withIndex()) {
                            val items = mutableListOf<Segment>()
                            if (ci == 0) items.add(Segment(condLabel, CONTENT_DIM))
                            else items.add(Segment(" ".repeat(8), CONTENT_DIM))
                            items.add(Segment(line, CONTENT_COND))
                            allLines.add(LineSegments(items))
                        }
                    }
                    // Anti-conditions
                    if (entry.antiConditions.isNotEmpty()) {
                        val antiLabel = lang("anti") + ": "
                        val antiValue = entry.antiConditions.joinToString(", ")
                        val antiPrefixW = textWidth(antiLabel)
                        val antiWrapped = wrapText(antiValue, wrapW - antiPrefixW)
                        for ((ai, line) in antiWrapped.withIndex()) {
                            val items = mutableListOf<Segment>()
                            if (ai == 0) items.add(Segment(antiLabel, CONTENT_DIM))
                            else items.add(Segment(" ".repeat(8), CONTENT_DIM))
                            items.add(Segment(line, CONTENT_WEAK))
                            allLines.add(LineSegments(items))
                        }
                    }
                    allLines.add(LineSegments(emptyList())) // gap between entries
                }
            } else {
                // ── Collapsed summary ──
                val allBiomes = bucketEntries.flatMap { it.biomes }.distinct()
                val shown = allBiomes.take(2).joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
                val more = if (allBiomes.size > 2) " +${allBiomes.size - 2} more" else ""
                val biomeLabel = lang("biomes") + ": "
                val biomeText = shown + more
                val biomePrefixW = textWidth(biomeLabel)
                val biomeWrapped = wrapText(biomeText, wrapW - biomePrefixW)
                for ((bi, line) in biomeWrapped.withIndex()) {
                    val items = mutableListOf<Segment>()
                    if (bi == 0) items.add(Segment(biomeLabel, CONTENT_DIM))
                    else items.add(Segment(" ".repeat(8), CONTENT_DIM))
                    items.add(Segment(line, CONTENT_TEXT))
                    allLines.add(LineSegments(items))
                }
                // Level: min-max across entries
                val minLv = bucketEntries.minOf { it.minLevel }
                val maxLv = bucketEntries.maxOf { it.maxLevel }
                allLines.add(
                    LineSegments(
                        listOf(
                            Segment(lang("level_label") + ": ", CONTENT_DIM),
                            Segment("$minLv \u2013 $maxLv", CONTENT_GOLD)
                        )
                    )
                )
                if (hasMultiple) {
                    allLines.add(
                        LineSegments(
                            listOf(
                                Segment("  (${bucketEntries.size} entries)", CONTENT_DIM)
                            )
                        )
                    )
                }
            }
            allLines.add(LineSegments(emptyList())) // gap between buckets
        }

        // Track bucket click regions (full bucket area, not just header)
        bucketHeaderRects.clear()
        var currentBucketStart: String? = null
        var currentBucketStartY = 0
        var cy = sy + SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP
        for (line in allLines) {
            if (line.segments.size == 1 && line.segments[0].color == CONTENT_GOLD &&
                (line.segments[0].text.startsWith("\u25BC") || line.segments[0].text.startsWith("\u25B6"))
            ) {
                // Close previous bucket region
                if (currentBucketStart != null) {
                    bucketHeaderRects[currentBucketStart!!] = Rect(
                        tx, screenY + currentBucketStartY,
                        tx + CONTENT_WIDTH - SECTION_PAD * 2, screenY + cy - 1
                    )
                }
                // Start new bucket
                val text = line.segments[0].text
                currentBucketStart = text.substring(2).split(" (")[0]
                currentBucketStartY = cy
            }
            cy += 9
        }
        // Close last bucket region
        if (currentBucketStart != null) {
            bucketHeaderRects[currentBucketStart!!] = Rect(
                tx, screenY + currentBucketStartY,
                tx + CONTENT_WIDTH - SECTION_PAD * 2, screenY + cy - 1
            )
        }

        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + allLines.size * 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        draw(guiGraphics, lang("spawning"), tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        for (line in allLines) {
            var cx = tx
            for (seg in line.segments) {
                draw(guiGraphics, seg.text, cx, screenY + y, seg.color)
                cx += textWidth(seg.text)
            }
            y += 9
        }
        return sy + totalH
    }

    private data class Segment(val text: String, val color: Int)
    private data class LineSegments(val segments: List<Segment>)

    private fun renderBreedingSection(guiGraphics: GuiGraphics, sy: Int): Int {
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 3 * 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        val tx = screenX + CONTENT_X + SECTION_PAD
        draw(guiGraphics, lang("breeding"), tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        // Egg Groups
        val groups = detail.eggGroups.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        draw(guiGraphics, lang("egg_groups") + ": ", tx, screenY + y, CONTENT_DIM)
        draw(guiGraphics, groups, tx + textWidth(lang("egg_groups") + ": "), screenY + y, CONTENT_TEXT)
        y += 9

        // Gender
        draw(guiGraphics, lang("gender") + ": ", tx, screenY + y, CONTENT_DIM)
        when {
            detail.maleRatio < 0 -> {
                draw(guiGraphics, lang("genderless"), tx + textWidth(lang("gender") + ": "), screenY + y, CONTENT_DIM)
                y += 9
            }

            detail.maleRatio == 0f -> {
                draw(guiGraphics, "100% F", tx + textWidth(lang("gender") + ": "), screenY + y, CONTENT_FEMALE)
                y += 9
            }

            detail.maleRatio == 1f -> {
                draw(guiGraphics, "100% M", tx + textWidth(lang("gender") + ": "), screenY + y, CONTENT_MALE)
                y += 9
            }

            else -> {
                val m = "${(detail.maleRatio * 100).toInt()}% M"
                val f = "${((1 - detail.maleRatio) * 100).toInt()}% F"
                draw(guiGraphics, "$m / $f", tx + textWidth(lang("gender") + ": "), screenY + y, CONTENT_TEXT)
                y += 9
            }
        }

        // Egg Cycles
        draw(guiGraphics, lang("egg_cycles") + ": ", tx, screenY + y, CONTENT_DIM)
        draw(guiGraphics, "${detail.eggCycles}", tx + textWidth(lang("egg_cycles") + ": "), screenY + y, CONTENT_TEXT)
        y += 9

        return sy + totalH
    }

    private fun renderTypeDefensesSection(guiGraphics: GuiGraphics, sy: Int): Int {
        val types = listOfNotNull(detail.primaryType, detail.secondaryType)
        val eff = getEffectiveness(types)

        // Filter neutral, preserve multiplier for color mapping
        val byMultiplier = eff.entries
            .filter { it.value != 1.0 }
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, typeNames) ->
                typeNames.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
            }

        val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
        var totalLines = 0
        if (byMultiplier.isEmpty()) {
            totalLines = 1
        } else {
            byMultiplier.forEach { (mult, text) ->
                val label = when {
                    mult > 1.0 -> "Weak (x${multiplierText(mult)})"
                    mult == 0.0 -> "Immune (x0)"
                    else -> "Resist (x${multiplierText(mult)})"
                }
                totalLines += wrapText("$label: $text", wrapW).size
            }
        }
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + totalLines * 9 + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        val tx = screenX + CONTENT_X + SECTION_PAD
        draw(guiGraphics, lang("type_defenses"), tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        if (byMultiplier.isEmpty()) {
            draw(guiGraphics, lang("none"), tx, screenY + y, CONTENT_DIM)
        } else {
            // Sort: worst weaknesses first, then immunities, then best resistances
            val sorted = byMultiplier.entries.sortedByDescending { (mult, _) -> mult }
            for ((mult, text) in sorted) {
                val label = when {
                    mult > 1.0 -> lang("weak") + " (x${multiplierText(mult)})"
                    mult == 0.0 -> lang("immune") + " (x0)"
                    else -> lang("resist") + " (x${multiplierText(mult)})"
                }
                val color = multiplierColor(mult)
                wrapText("$label: $text", wrapW).forEach {
                    draw(guiGraphics, it, tx, screenY + y, color); y += 9
                }
            }
        }
        return sy + totalH
    }

    private fun multiplierColor(mult: Double): Int = when (mult) {
        4.0 -> 0xFFCC1111.toInt()
        2.0 -> 0xFFCC3333.toInt()
        0.5 -> 0xFF339933.toInt()
        0.25 -> 0xFF116611.toInt()
        0.0 -> 0xFF9966CC.toInt()
        else -> CONTENT_TEXT
    }

    private fun renderLevelMovesTable(guiGraphics: GuiGraphics, sy: Int): Int {
        val levelUp = detail.moves.filter { it.method == "level" }.sortedBy { it.level }
        val moveCount = minOf(levelUp.size, 20)
        val scale = 0.8f
        val totalRowsHeight = Math.ceil((moveCount + 1) * 9.0 * scale).toInt()
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + totalRowsHeight + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        draw(guiGraphics, lang("level_moves"), screenX + CONTENT_X + SECTION_PAD, screenY + y, SECTION_TITLE_COLOR)
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

        draw(guiGraphics, lang("lvl"), 0, 0, CONTENT_DIM)
        draw(guiGraphics, lang("move"), LV_W, 0, CONTENT_DIM)
        draw(guiGraphics, lang("type"), LV_W + MOVE_W, 0, CONTENT_DIM)
        draw(guiGraphics, lang("ct"), LV_W + MOVE_W + TYPE_W, 0, CONTENT_DIM)
        draw(guiGraphics, lang("pw"), LV_W + MOVE_W + TYPE_W + CAT_W, 0, CONTENT_DIM)
        draw(guiGraphics, lang("ac"), LV_W + MOVE_W + TYPE_W + CAT_W + PW_W, 0, CONTENT_DIM)
        var ry = 9

        levelUp.take(20).forEach { move ->
            val pw = if (move.power == 0) "\u2014" else move.power.toString()
            val ac = if (move.accuracy == 0) "\u221E" else move.accuracy.toString()
            val cat = when (move.category) {
                "physical" -> "Ph"; "special" -> "Sp"; else -> "St"
            }
            val catColor = when (move.category) {
                "physical" -> 0xFFE07030.toInt()
                "special" -> 0xFF5070B8.toInt()
                else -> 0xFF888888.toInt()
            }
            val typeAbbr = typeAbbreviation(move.type)
            val moveName = truncate(move.name.replaceFirstChar { it.uppercase() }, MOVE_W - 2)

            draw(guiGraphics, "${move.level}", 0, ry, CONTENT_TEXT)
            draw(guiGraphics, moveName, LV_W, ry, CONTENT_TEXT)
            draw(guiGraphics, typeAbbr, LV_W + MOVE_W, ry, typeColor(move.type))
            draw(guiGraphics, cat, LV_W + MOVE_W + TYPE_W, ry, catColor)
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

        val methodOrder = mapOf("tm" to 0, "egg" to 1, "tutor" to 2)
        val sorted = others.sortedWith(compareBy({ methodOrder[it.method] ?: 99 }, { it.name }))
        val rowLimit = sorted.size

        val scale = 0.8f
        val totalRowsHeight = kotlin.math.ceil((rowLimit + 1) * 9.0 * scale.toDouble()).toInt()
        val totalH = SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + totalRowsHeight + SECTION_PAD_BOTTOM
        sectionBox(guiGraphics, sy, totalH)

        var y = sy + SECTION_PAD_TOP
        val tx = screenX + CONTENT_X + SECTION_PAD
        draw(guiGraphics, lang("learnable_moves"), tx, screenY + y, SECTION_TITLE_COLOR)
        y += TITLE_GAP + CONTENT_GAP

        val tableW = MET_W + MOVE_W + TYPE_W + CAT_W + PW_W + AC_W
        val availW = CONTENT_WIDTH - SECTION_PAD * 2
        val offsetX = (availW - tableW * scale) / 2f
        val baseX = (screenX + CONTENT_X + SECTION_PAD + offsetX) / scale
        val baseY = (screenY + y) / scale

        val matrices = guiGraphics.pose()
        matrices.pushPose()
        matrices.translate(baseX * scale, baseY * scale, 0f)
        matrices.scale(scale, scale, 1f)

        // Header
        draw(guiGraphics, lang("met"), 0, 0, CONTENT_DIM)
        draw(guiGraphics, lang("move"), MET_W, 0, CONTENT_DIM)
        draw(guiGraphics, lang("type"), MET_W + MOVE_W, 0, CONTENT_DIM)
        draw(guiGraphics, lang("ct"), MET_W + MOVE_W + TYPE_W, 0, CONTENT_DIM)
        draw(guiGraphics, lang("pw"), MET_W + MOVE_W + TYPE_W + CAT_W, 0, CONTENT_DIM)
        draw(guiGraphics, lang("ac"), MET_W + MOVE_W + TYPE_W + CAT_W + PW_W, 0, CONTENT_DIM)
        var ry = 9

        for (move in sorted) {
            val methodLabel = when (move.method) {
                "tm" -> "TM"; "egg" -> "Egg"; "tutor" -> "Tut"; else -> move.method
            }
            val pw = if (move.power == 0) "\u2014" else move.power.toString()
            val ac = if (move.accuracy == 0) "\u221E" else move.accuracy.toString()
            val cat = when (move.category) {
                "physical" -> "Ph"; "special" -> "Sp"; else -> "St"
            }
            val catColor = when (move.category) {
                "physical" -> 0xFFE07030.toInt()
                "special" -> 0xFF5070B8.toInt()
                else -> 0xFF888888.toInt()
            }
            val typeAbbr = typeAbbreviation(move.type)
            val moveName = truncate(move.name.replaceFirstChar { it.uppercase() }, MOVE_W - 2)

            draw(guiGraphics, methodLabel, 0, ry, CONTENT_DIM)
            draw(guiGraphics, moveName, MET_W, ry, CONTENT_TEXT)
            draw(guiGraphics, typeAbbr, MET_W + MOVE_W, ry, typeColor(move.type))
            draw(guiGraphics, cat, MET_W + MOVE_W + TYPE_W, ry, catColor)
            draw(guiGraphics, pw, MET_W + MOVE_W + TYPE_W + CAT_W, ry, CONTENT_TEXT)
            draw(guiGraphics, ac, MET_W + MOVE_W + TYPE_W + CAT_W + PW_W, ry, CONTENT_TEXT)
            ry += 9
        }

        matrices.popPose()
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

    private fun renderScrollbar(guiGraphics: GuiGraphics, mouseY: Int) {
        if (maxScroll <= 0) return
        val trackX = screenX + CONTENT_X + CONTENT_WIDTH + 1
        val trackY = screenY + CONTENT_START_Y
        val trackH = CONTENT_END_Y - CONTENT_START_Y
        val totalH = calculateContentHeight()
        val handleH = maxOf(12, (trackH.toFloat() * trackH / totalH.toFloat()).toInt())
        val handleY = trackY + (scrollY.toFloat() / maxScroll * (trackH - handleH)).toInt()

        if (draggingScrollbar) {
            val dy = mouseY - dragStartMouseY
            val scrollRange = trackH - handleH
            if (scrollRange > 0) {
                scrollY = (dragStartScrollY + (dy.toFloat() / scrollRange * maxScroll).toInt()).coerceIn(0, maxScroll)
            }
        }

        guiGraphics.fill(trackX, trackY, trackX + 4, trackY + trackH, 0x25FFFFFF.toInt())
        val color = if (draggingScrollbar) 0x80FFFFFF.toInt() else 0x50FFFFFF.toInt()
        guiGraphics.fill(trackX, handleY, trackX + 4, handleY + handleH, color)
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
        val spawnH = when {
            detailLoading -> SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + 9 + SECTION_PAD_BOTTOM
            detail.spawnEntries.isNotEmpty() -> {
                val wrapW = CONTENT_WIDTH - SECTION_PAD * 2
                val groups = detail.spawnEntries.groupBy { it.bucket }
                var lines = 0
                for ((bucket, bucketEntries) in groups) {
                    val expanded = expandedBuckets.contains(bucket)
                    lines++ // header
                    if (expanded) {
                        for (entry in bucketEntries) {
                            val biomeText =
                                entry.biomes.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
                            lines += wrapText(biomeText, wrapW - textWidth(lang("biomes") + ": ")).size
                            lines++ // level
                            lines++ // context
                            if (!entry.time.isNullOrBlank()) lines++
                            if (entry.conditions.isNotEmpty()) {
                                val condText = entry.conditions.joinToString(", ")
                                lines += wrapText(condText, wrapW - textWidth(lang("cond") + ": ")).size
                            }
                            if (entry.antiConditions.isNotEmpty()) {
                                val antiText = entry.antiConditions.joinToString(", ")
                                lines += wrapText(antiText, wrapW - textWidth(lang("anti") + ": ")).size
                            }
                            lines++ // gap between entries in bucket
                        }
                    } else {
                        val allBiomes = bucketEntries.flatMap { it.biomes }.distinct()
                        val shown = allBiomes.take(2).joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
                        val more = if (allBiomes.size > 2) " +${allBiomes.size - 2} more" else ""
                        val biomeText = shown + more
                        lines += wrapText(biomeText, wrapW - textWidth(lang("biomes") + ": ")).size
                        lines++ // level
                        if (bucketEntries.size > 1) lines++ // entries count
                    }
                    lines++ // gap between buckets
                }
                SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + lines * 9 + SECTION_PAD_BOTTOM
            }

            else -> 0
        }
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
        val movesH = run {
            val levelCount = minOf(detail.moves.count { it.method == "level" }, 20)
            val totalRowsHeight = kotlin.math.ceil((levelCount + 1) * 9.0 * 0.8).toInt()
            SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + totalRowsHeight + SECTION_PAD_BOTTOM
        }
        val learnH = run {
            val others = detail.moves.filter { it.method != "level" }
            if (others.isEmpty()) 0
            else {
                val totalRowsHeight = kotlin.math.ceil((others.size + 1) * 9.0 * 0.8).toInt()
                SECTION_PAD_TOP + TITLE_GAP + CONTENT_GAP + totalRowsHeight + SECTION_PAD_BOTTOM
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
        private const val CONTENT_WIDTH = 166
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
        private const val CONTENT_COND = 0xFFCC6633.toInt()
        private const val CONTENT_RESIST = 0xFF339933.toInt()
        private const val CONTENT_IMMUNE = 0xFF9966CC.toInt()
        private const val CONTENT_MALE = 0xFF5599DD.toInt()
        private const val CONTENT_FEMALE = 0xFFDD5599.toInt()

        private const val SCROLL_SPEED = 10

        private const val LV_W = 22
        private const val MET_W = 22
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
