package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.client.PokeInfoClientState
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
import com.nbp.cobblemon_smartphone.network.packet.RequestSpeciesDetailPacket
import com.nbp.cobblemon_smartphone.network.packet.SpeciesDetailResponsePacket
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Detailed PokéInfo entry screen built on the HD [HdPhoneScreen] UI engine.
 */
class PokeInfoDetailScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack?,
    private val speciesId: String,
    private val dexNumber: Int = 0,
    scaleOverride: Float? = null
) : HdPhoneScreen(
    color,
    smartphoneStack,
    Component.translatable("cobblemon_smartphone.screen.pokeinfo_detail"),
    scaleOverride
) {

    constructor(color: SmartphoneColor, smartphoneStack: ItemStack?, dexNumber: Int) :
        this(color, smartphoneStack, dexNumber.toString(), dexNumber)

    private enum class DetailPage(val translationKey: String) {
        INFO("page.info"),
        SPAWNS("page.spawns"),
        MOVES("page.moves")
    }

    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelHdBadge: Boolean = true

    private val posableState = FloatingState()
    private lateinit var detail: PokeInfoDataProvider.SpeciesDetail
    private var formDetails: List<PokeInfoDataProvider.SpeciesDetail> = emptyList()
    private var currentFormIndex = 0
    private var resolvedSpeciesIdentifier = speciesId
    private var resolvedAspects: Set<String> = emptySet()

    private var detailLoading = true
    private var detailRequestId = -1L
    private var detailRequestStartedAt = 0L
    private var detailErrorKey: String? = null
    private val expandedBuckets = mutableSetOf<String>()
    private var currentPage = DetailPage.INFO
    private var learnableLimit = MOVES_BATCH

    override fun init() {
        super.init()
        requestDetail()
    }

    override fun removed() {
        PokeInfoClientState.cancelDetailRequest(detailRequestId)
        super.removed()
    }

    override fun onBackPressed() {
        Minecraft.getInstance().setScreen(PokeInfoScreen(color, smartphoneStack))
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        receiveDetailResponse()
        super.render(guiGraphics, mouseX, mouseY, delta)
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        buildHeader(builder)

        if (!::detail.isInitialized) {
            val message = Component.translatable(detailErrorKey ?: "cobblemon_smartphone.pokeinfo.loading_detail").string
            builder.column(
                crossAlign = HdAlign.CENTER,
                mainAlign = HdAlign.CENTER,
                padding = HdPadding.all(20f),
                width = HdSizeSpec.Fill,
                height = HdSizeSpec.Weight(1f)
            ) { empty ->
                empty.text(
                    text = message,
                    size = 10f,
                    color = HdColorSpec.Token("text_secondary"),
                    align = HdAlign.CENTER,
                    wrap = true
                ) {
                    width = HdSizeSpec.Fill
                }
            }
            return
        }

        val cfg = CobblemonSmartphone.config.pokeInfo
        builder.scroll(
            scrollId = "pokeinfo_detail_${currentPage.name}",
            gap = 7f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f),
            onNearBottom = {
                if (currentPage == DetailPage.MOVES) {
                    learnableLimit += MOVES_BATCH
                }
            }
        ) { body ->
            if (detail.availableForms.size > 1) {
                buildFormSwitcher(body)
            }
            when (currentPage) {
                DetailPage.INFO -> {
                    buildTopSection(body)
                    if (cfg.showBaseStats) buildBaseStatsSection(body)
                    if (cfg.showAbilities && detail.abilities.isNotEmpty()) buildAbilitiesSection(body)
                    if (cfg.showEvolution && (detail.preEvolution != null || detail.evolutions.isNotEmpty())) {
                        buildEvolutionSection(body)
                    }
                    if (cfg.showTraining) buildTrainingSection(body)
                    if (cfg.showBreeding) buildBreedingSection(body)
                    if (cfg.showTypeDefenses) buildTypeDefensesSection(body)
                }
                DetailPage.SPAWNS -> {
                    if (cfg.showSpawning) buildSpawningSection(body)
                }
                DetailPage.MOVES -> {
                    if (cfg.showLevelMoves) buildLevelMovesSection(body)
                    if (cfg.showLearnableMoves) buildLearnableMovesSection(body)
                }
            }
        }
    }

    private fun buildHeader(builder: HdBuilder) {
        val headerTitle = if (::detail.isInitialized) {
            "#${String.format("%03d", detail.dexNumber)} ${pokemonName(detail.name, detail.speciesId)}"
        } else {
            Component.translatable("cobblemon_smartphone.screen.pokeinfo_detail").string
        }

        builder.appHeader(
            title = headerTitle,
            icon = HdIconAtlas.Icon.SPARKLES,
            iconColor = HdColorSpec.Token("primary"),
            bottomContent = if (::detail.isInitialized) {
                { hdr ->
                    hdr.segmentedControl(
                        options = DetailPage.entries.map {
                            com.nbp.cobblemon_smartphone.client.ui.hd.SegmentedControlNode.Option(
                                it.name,
                                lang(it.translationKey)
                            )
                        },
                        selectedId = currentPage.name,
                        onSelect = { id ->
                            val next = DetailPage.valueOf(id)
                            if (currentPage != next) {
                                playClickSound()
                                currentPage = next
                                learnableLimit = MOVES_BATCH
                                stateStore.scroll("pokeinfo_detail_${next.name}").offset = 0f
                            }
                        }
                    )
                }
            } else null
        )
    }

    private fun buildFormSwitcher(builder: HdBuilder) {
        val forms = detail.availableForms
        val form = forms[currentFormIndex]
        val formName = translatedOrFallback(form.displayName, humanize(form.name))

        builder.row(
            gap = 4f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(horizontal = 6f, vertical = 3.5f),
            width = HdSizeSpec.Fill,
            background = HdBackground(
                color = HdColorSpec.Token("primary"),
                radius = 7f,
                borderWidth = 0.6f,
                borderColor = HdColorSpec.Token("primary_border")
            )
        ) { bar ->
            bar.button(
                label = "<",
                variant = ButtonNode.Variant.GHOST,
                fontSize = 11f,
                enabled = currentFormIndex > 0,
                onClick = {
                    if (currentFormIndex > 0) {
                        playClickSound()
                        applyForm(currentFormIndex - 1)
                    }
                }
            ) {
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 2f)
            }
            bar.text(
                text = formName,
                size = 9.4f,
                weight = HdTextLayout.Weight.BOLD,
                color = HdColorSpec.Token("text_primary"),
                align = HdAlign.CENTER
            ) {
                width = HdSizeSpec.Weight(1f)
            }
            bar.button(
                label = ">",
                variant = ButtonNode.Variant.GHOST,
                fontSize = 11f,
                enabled = currentFormIndex < forms.size - 1,
                onClick = {
                    if (currentFormIndex < forms.size - 1) {
                        playClickSound()
                        applyForm(currentFormIndex + 1)
                    }
                }
            ) {
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 2f)
            }
        }
    }

    private fun buildTopSection(builder: HdBuilder) {
        val types = listOfNotNull(detail.primaryType, detail.secondaryType)
        builder.card(gap = 6f, padding = HdPadding.all(8f), radius = 9f, shadowBlur = 4f) { card ->
            card.row(gap = 10f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row ->
                row.row(
                    padding = HdPadding.all(3f),
                    background = HdBackground(
                        color = HdColorSpec.Token("surface_alt"),
                        radius = 8f,
                        borderWidth = 0.6f,
                        borderColor = HdColorSpec.Token("primary_border")
                    )
                ) { modelBox ->
                    modelBox.pokemonView(
                        speciesId = resolvedSpeciesIdentifier,
                        aspects = resolvedAspects,
                        size = 74f,
                        floatingState = posableState,
                        showShinyBadge = false
                    )
                }

                row.column(gap = 5f, width = HdSizeSpec.Weight(1f)) { info ->
                    info.text(
                        text = "#${String.format("%03d", detail.dexNumber)} ${pokemonName(detail.name, detail.speciesId)}",
                        size = 11.4f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("content_gold"),
                        wrap = true
                    ) {
                        width = HdSizeSpec.Fill
                    }

                    info.row(gap = 5f, crossAlign = HdAlign.CENTER) { typeRow ->
                        types.forEach { type ->
                            typeRow.row(
                                padding = HdPadding.symmetric(horizontal = 7f, vertical = 2.5f),
                                background = HdBackground(color = HdColorSpec.Fixed(typeColor(type)), radius = 5f)
                            ) { pill ->
                                pill.text(
                                    text = typeName(type),
                                    size = 8.2f,
                                    weight = HdTextLayout.Weight.BOLD,
                                    color = HdColorSpec.Fixed(0xFFFFFFFF.toInt())
                                )
                            }
                        }
                    }

                    info.text(
                        text = lang("dimensions", detail.height / 10f, detail.weight / 10f),
                        size = 8.8f,
                        color = HdColorSpec.Token("text_dark_muted"),
                        wrap = true
                    ) {
                        width = HdSizeSpec.Fill
                    }
                }
            }
        }
    }

    private fun buildSectionCard(
        builder: HdBuilder,
        title: String,
        trailingText: String? = null,
        buildBody: (HdBuilder) -> Unit
    ) {
        builder.column(
            gap = 5f,
            padding = HdPadding.all(5f),
            width = HdSizeSpec.Fill,
            background = HdBackground(
                color = HdColorSpec.Token("surface"),
                radius = 9f,
                borderWidth = 0.7f,
                borderColor = HdColorSpec.Token("primary_border"),
                shadowBlur = 4f,
                shadowOffsetY = 1.5f,
                shadowColor = HdColorSpec.Fixed(0x28000000)
            )
        ) { sec ->
            sec.row(
                gap = 4f,
                crossAlign = HdAlign.CENTER,
                padding = HdPadding.symmetric(horizontal = 7f, vertical = 4f),
                width = HdSizeSpec.Fill,
                background = HdBackground(color = HdColorSpec.Token("primary"), radius = 6f)
            ) { hdr ->
                hdr.text(
                    text = title,
                    size = 9.6f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = HdColorSpec.Token("text_primary")
                ) {
                    width = HdSizeSpec.Weight(1f)
                }
                if (trailingText != null) {
                    hdr.text(
                        text = trailingText,
                        size = 8.4f,
                        color = HdColorSpec.Token("text_secondary")
                    )
                }
            }

            sec.column(
                gap = 3.5f,
                padding = HdPadding.symmetric(horizontal = 4f, vertical = 2f),
                width = HdSizeSpec.Fill
            ) { content ->
                buildBody(content)
            }
        }
    }

    private fun buildBaseStatsSection(builder: HdBuilder) {
        val stats = listOf(
            lang("stat.hp") to detail.baseStats.hp,
            lang("stat.attack") to detail.baseStats.attack,
            lang("stat.defence") to detail.baseStats.defence,
            lang("stat.special_attack") to detail.baseStats.specialAttack,
            lang("stat.special_defence") to detail.baseStats.specialDefence,
            lang("stat.speed") to detail.baseStats.speed
        )

        buildSectionCard(builder, lang("base_stats"), lang("total", detail.baseStats.total)) { body ->
            stats.forEach { (label, value) ->
                body.row(gap = 6f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row ->
                    row.text(
                        text = label,
                        size = 8.6f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("content_gold")
                    ) {
                        width = HdSizeSpec.Fixed(28f)
                    }
                    row.progressBar(
                        progress = (value / 255f).coerceIn(0f, 1f),
                        height = 6.5f
                    ) {
                        radius = 3.2f
                        fillColor = HdColorSpec.Fixed(statBarColor(value))
                        width = HdSizeSpec.Weight(1f)
                    }
                    row.text(
                        text = "$value",
                        size = 8.6f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("text_dark"),
                        align = HdAlign.END
                    ) {
                        width = HdSizeSpec.Fixed(24f)
                    }
                }
            }
        }
    }

    private fun buildAbilitiesSection(builder: HdBuilder) {
        buildSectionCard(builder, lang("abilities")) { body ->
            detail.abilities.forEach { ability ->
                val name = abilityName(ability.name)
                body.column(gap = 1.5f, width = HdSizeSpec.Fill) { item ->
                    if (ability.isHidden) {
                        item.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { r ->
                            r.text(
                                text = lang("hidden_ability_prefix"),
                                size = 8.6f,
                                weight = HdTextLayout.Weight.BOLD,
                                color = HdColorSpec.Fixed(CONTENT_COND)
                            )
                            r.text(
                                text = name,
                                size = 9.0f,
                                weight = HdTextLayout.Weight.BOLD,
                                color = HdColorSpec.Token("content_gold"),
                                wrap = true
                            ) {
                                width = HdSizeSpec.Weight(1f)
                            }
                        }
                    } else {
                        item.text(
                            text = name,
                            size = 9.0f,
                            weight = HdTextLayout.Weight.BOLD,
                            color = HdColorSpec.Token("content_gold"),
                            wrap = true
                        ) {
                            width = HdSizeSpec.Fill
                        }
                    }
                    val description = Component.translatable(ability.descriptionKey).string
                    if (description.isNotEmpty()) {
                        item.text(
                            text = description,
                            size = 8.2f,
                            color = HdColorSpec.Token("text_dark_muted"),
                            wrap = true
                        ) {
                            padding = HdPadding.only(left = 8f)
                            width = HdSizeSpec.Fill
                        }
                    }
                }
            }
        }
    }

    private fun buildEvolutionSection(builder: HdBuilder) {
        buildSectionCard(builder, lang("evolution")) { body ->
            detail.preEvolution?.let { pre ->
                body.text(
                    text = "↑ ${pokemonName(pre)}",
                    size = 8.8f,
                    color = HdColorSpec.Token("text_dark_muted"),
                    wrap = true
                ) {
                    width = HdSizeSpec.Fill
                }
            }
            detail.evolutions.forEach { evo ->
                body.column(gap = 1.5f, width = HdSizeSpec.Fill) { evoCol ->
                    evoCol.text(
                        text = "↓ ${pokemonName(evo.targetName)}",
                        size = 9.2f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("text_dark"),
                        wrap = true
                    ) {
                        width = HdSizeSpec.Fill
                    }
                    val methods = evo.methods.joinToString("  •  ", transform = ::localized)
                    if (methods.isNotEmpty()) {
                        evoCol.text(
                            text = "• $methods",
                            size = 8.2f,
                            color = HdColorSpec.Token("text_dark_muted"),
                            wrap = true
                        ) {
                            padding = HdPadding.only(left = 8f)
                            width = HdSizeSpec.Fill
                        }
                    }
                }
            }
        }
    }

    private fun buildTrainingSection(builder: HdBuilder) {
        val evs = listOf(
            lang("stat.hp") to detail.evYield.hp,
            lang("stat.attack") to detail.evYield.attack,
            lang("stat.defence") to detail.evYield.defence,
            lang("stat.special_attack") to detail.evYield.specialAttack,
            lang("stat.special_defence") to detail.evYield.specialDefence,
            lang("stat.speed") to detail.evYield.speed
        ).filter { it.second > 0 }
        val evText = if (evs.isEmpty()) lang("none") else evs.joinToString(", ") { "${it.first} ${it.second}" }

        buildSectionCard(builder, lang("training")) { body ->
            buildKeyValueRow(body, lang("ev"), evText, HdColorSpec.Token("content_gold"))
            buildKeyValueRow(body, lang("catch_rate"), "${detail.catchRate} (${catchPercent(detail.catchRate)}%)")
            buildKeyValueRow(body, lang("friendship"), "${detail.baseFriendship}")
            buildKeyValueRow(body, lang("base_exp"), "${detail.baseExp}")
            buildKeyValueRow(body, lang("growth_rate"), humanize(detail.growthRate))
        }
    }

    private fun buildBreedingSection(builder: HdBuilder) {
        val groups = detail.eggGroups.joinToString(", ", transform = ::eggGroupName)
        buildSectionCard(builder, lang("breeding")) { body ->
            buildKeyValueRow(body, lang("egg_groups"), groups)
            when {
                detail.maleRatio < 0f ->
                    buildKeyValueRow(body, lang("gender"), lang("genderless"), HdColorSpec.Token("text_dark_muted"))
                detail.maleRatio == 0f ->
                    buildKeyValueRow(body, lang("gender"), lang("gender_female", 100), HdColorSpec.Fixed(CONTENT_FEMALE))
                detail.maleRatio == 1f ->
                    buildKeyValueRow(body, lang("gender"), lang("gender_male", 100), HdColorSpec.Fixed(CONTENT_MALE))
                else -> {
                    val m = lang("gender_male", (detail.maleRatio * 100).toInt())
                    val f = lang("gender_female", ((1 - detail.maleRatio) * 100).toInt())
                    buildKeyValueRow(body, lang("gender"), "$m / $f")
                }
            }
            buildKeyValueRow(body, lang("egg_cycles"), "${detail.eggCycles}")
        }
    }

    private fun buildTypeDefensesSection(builder: HdBuilder) {
        val types = listOfNotNull(detail.primaryType, detail.secondaryType)
        val eff = getEffectiveness(types)
        val byMultiplier = eff.entries
            .filter { it.value != 1.0 }
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, typeNames) -> typeNames.joinToString(", ", transform = ::typeName) }

        buildSectionCard(builder, lang("type_defenses")) { body ->
            if (byMultiplier.isEmpty()) {
                body.text(lang("none"), size = 8.6f, color = HdColorSpec.Token("text_dark_muted"))
            } else {
                byMultiplier.entries.sortedByDescending { it.key }.forEach { (mult, text) ->
                    val label = when {
                        mult > 1.0 -> "${lang("weak")} (x${multiplierText(mult)})"
                        mult == 0.0 -> "${lang("immune")} (x0)"
                        else -> "${lang("resist")} (x${multiplierText(mult)})"
                    }
                    body.text(
                        text = "$label: $text",
                        size = 8.5f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Fixed(multiplierColor(mult)),
                        wrap = true
                    ) {
                        width = HdSizeSpec.Fill
                    }
                }
            }
        }
    }

    private fun buildSpawningSection(builder: HdBuilder) {
        if (detailLoading) {
            buildSectionCard(builder, lang("spawning")) { body ->
                body.text(lang("loading_spawn_data"), size = 8.8f, color = HdColorSpec.Token("text_dark_muted"))
            }
            return
        }
        val entries = detail.spawnEntries
        if (entries.isEmpty()) {
            buildSectionCard(builder, lang("spawning")) { body ->
                body.text(lang("none"), size = 8.8f, color = HdColorSpec.Token("text_dark_muted"))
            }
            return
        }

        val groups = entries.groupBy { it.bucket }
        for ((bucket, bucketEntries) in groups) {
            val expanded = expandedBuckets.contains(bucket)
            val weight = bucketEntries.firstOrNull()?.weight ?: -1f
            val weightStr = if (weight >= 0f) " (${weight.toInt()})" else " (${lang("default_weight")})"
            val arrow = if (expanded) "▼" else "▶"
            val headerTitle = "$arrow ${spawnValue("bucket", bucket)}$weightStr"

            buildSectionCard(builder, lang("spawning"), if (bucketEntries.size > 1) "${bucketEntries.size}" else null) { body ->
                body.row(
                    gap = 4f,
                    padding = HdPadding.symmetric(horizontal = 6f, vertical = 4f),
                    width = HdSizeSpec.Fill,
                    background = HdBackground(color = HdColorSpec.Token("surface_alt"), radius = 6f)
                ) { toggle ->
                    hoverBackground = HdBackground(color = HdColorSpec.Token("surface_hover"), radius = 6f)
                    onClick = {
                        playClickSound()
                        if (expanded) expandedBuckets.remove(bucket) else expandedBuckets.add(bucket)
                    }
                    toggle.text(
                        text = headerTitle,
                        size = 9.0f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("content_gold")
                    ) {
                        width = HdSizeSpec.Fill
                    }
                }

                if (expanded) {
                    bucketEntries.forEachIndexed { idx, entry ->
                        if (idx > 0) body.divider()
                        buildKeyValueRow(body, lang("biomes"), entry.biomes.joinToString(", ", transform = ::biomeName))
                        buildKeyValueRow(body, lang("level_label"), "${entry.minLevel} – ${entry.maxLevel}", HdColorSpec.Token("content_gold"))
                        buildKeyValueRow(body, lang("context_label"), spawnValue("context", entry.context))
                        entry.time?.let { buildKeyValueRow(body, lang("time_label"), localized(it)) }
                        if (entry.conditions.isNotEmpty()) {
                            buildKeyValueRow(body, lang("cond"), entry.conditions.joinToString(", ", transform = ::localized), HdColorSpec.Fixed(CONTENT_COND))
                        }
                        if (entry.antiConditions.isNotEmpty()) {
                            buildKeyValueRow(body, lang("anti"), entry.antiConditions.joinToString(", ", transform = ::localized), HdColorSpec.Fixed(CONTENT_WEAK))
                        }
                    }
                } else {
                    val allBiomes = bucketEntries.flatMap { it.biomes }.distinct()
                    val shown = allBiomes.take(2).joinToString(", ", transform = ::biomeName)
                    val more = if (allBiomes.size > 2) lang("more_count", allBiomes.size - 2) else ""
                    buildKeyValueRow(body, lang("biomes"), shown + more)
                    val minLv = bucketEntries.minOf { it.minLevel }
                    val maxLv = bucketEntries.maxOf { it.maxLevel }
                    buildKeyValueRow(body, lang("level_label"), "$minLv – $maxLv", HdColorSpec.Token("content_gold"))
                    if (bucketEntries.size > 1) {
                        body.text(lang("entry_count", bucketEntries.size), size = 8.0f, color = HdColorSpec.Token("text_dark_muted"))
                    }
                }
            }
        }
    }

    private fun buildLevelMovesSection(builder: HdBuilder) {
        val levelUp = detail.moves.filter { it.method == "level" }.sortedBy { it.level }
        buildSectionCard(builder, lang("level_moves"), "${levelUp.size}") { body ->
            buildMoveTableHeader(body, lang("lvl"))
            levelUp.forEach { move ->
                buildMoveTableRow(body, "${move.level}", move)
            }
        }
    }

    private fun buildLearnableMovesSection(builder: HdBuilder) {
        val others = detail.moves.filter { it.method != "level" }
        if (others.isEmpty()) return
        val methodOrder = mapOf("tm" to 0, "egg" to 1, "tutor" to 2)
        val sorted = others.sortedWith(compareBy({ methodOrder[it.method] ?: 99 }, { it.name }))

        buildSectionCard(builder, lang("learnable_moves"), "${sorted.size}") { body ->
            buildMoveTableHeader(body, lang("met"))
            val count = learnableLimit.coerceAtMost(sorted.size)
            for (i in 0 until count) {
                val move = sorted[i]
                val methodLabel = when (move.method) {
                    "tm" -> lang("method.tm")
                    "egg" -> lang("method.egg")
                    "tutor" -> lang("method.tutor")
                    else -> humanize(move.method)
                }
                buildMoveTableRow(body, methodLabel, move)
            }
        }
    }

    private fun buildMoveTableHeader(builder: HdBuilder, col1Label: String) {
        builder.row(
            gap = 4f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(horizontal = 4f, vertical = 2f),
            width = HdSizeSpec.Fill,
            background = HdBackground(color = HdColorSpec.Token("surface_alt"), radius = 4f)
        ) { row ->
            row.text(col1Label, size = 7.8f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark_muted")) {
                width = HdSizeSpec.Fixed(28f)
            }
            row.text(lang("move"), size = 7.8f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark_muted")) {
                width = HdSizeSpec.Weight(1f)
            }
            row.text(lang("type"), size = 7.8f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark_muted"), align = HdAlign.CENTER) {
                width = HdSizeSpec.Fixed(34f)
            }
            row.text(lang("ct"), size = 7.8f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark_muted"), align = HdAlign.CENTER) {
                width = HdSizeSpec.Fixed(28f)
            }
            row.text(lang("pw"), size = 7.8f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark_muted"), align = HdAlign.END) {
                width = HdSizeSpec.Fixed(22f)
            }
            row.text(lang("ac"), size = 7.8f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark_muted"), align = HdAlign.END) {
                width = HdSizeSpec.Fixed(22f)
            }
        }
    }

    private fun buildMoveTableRow(builder: HdBuilder, col1: String, move: PokeInfoDataProvider.MoveInfo) {
        val pw = if (move.power == 0) "—" else move.power.toString()
        val ac = if (move.accuracy == 0) "∞" else move.accuracy.toString()
        val cat = when (move.category) {
            "physical" -> lang("category.physical")
            "special" -> lang("category.special")
            else -> lang("category.status")
        }
        val catColor = when (move.category) {
            "physical" -> 0xFFE07030.toInt()
            "special" -> 0xFF5070B8.toInt()
            else -> 0xFF777777.toInt()
        }
        val typeAbbr = lang("type_abbr.${move.type.lowercase()}")

        builder.row(
            gap = 4f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(horizontal = 4f, vertical = 1.5f),
            width = HdSizeSpec.Fill
        ) { row ->
            tooltip = moveDescription(move.name)
            hoverBackground = HdBackground(color = HdColorSpec.Token("surface_hover"), radius = 4f)
            row.text(col1, size = 8.0f, color = HdColorSpec.Token("text_dark_muted")) {
                width = HdSizeSpec.Fixed(28f)
            }
            row.text(moveName(move.name), size = 8.2f, color = HdColorSpec.Token("text_dark"), wrap = true) {
                width = HdSizeSpec.Weight(1f)
            }
            row.text(
                text = typeAbbr,
                size = 7.8f,
                weight = HdTextLayout.Weight.BOLD,
                color = HdColorSpec.Fixed(typeColor(move.type)),
                align = HdAlign.CENTER
            ) {
                width = HdSizeSpec.Fixed(34f)
            }
            row.text(
                text = cat,
                size = 7.8f,
                weight = HdTextLayout.Weight.BOLD,
                color = HdColorSpec.Fixed(catColor),
                align = HdAlign.CENTER
            ) {
                width = HdSizeSpec.Fixed(28f)
            }
            row.text(pw, size = 8.0f, color = HdColorSpec.Token("text_dark"), align = HdAlign.END) {
                width = HdSizeSpec.Fixed(22f)
            }
            row.text(ac, size = 8.0f, color = HdColorSpec.Token("text_dark"), align = HdAlign.END) {
                width = HdSizeSpec.Fixed(22f)
            }
        }
    }

    private fun buildKeyValueRow(
        builder: HdBuilder,
        label: String,
        value: String,
        valueColor: HdColorSpec = HdColorSpec.Token("text_dark")
    ) {
        builder.row(gap = 4f, crossAlign = HdAlign.START, width = HdSizeSpec.Fill) { row ->
            row.text("$label:", size = 8.6f, color = HdColorSpec.Token("text_dark_muted"))
            row.text(value, size = 8.6f, color = valueColor, wrap = true) {
                width = HdSizeSpec.Weight(1f)
            }
        }
    }

    private fun applyDetail(received: PokeInfoDataProvider.SpeciesDetail) {
        detail = received
        detailLoading = false
        detailErrorKey = null
        currentFormIndex = detail.availableForms.indexOfFirst { it.name == detail.selectedForm }.coerceAtLeast(0)
        val species = PokeInfoDataProvider.findSpecies(detail.speciesId)
            ?: PokeInfoDataProvider.findSpecies(detail.name)
        resolvedSpeciesIdentifier = species?.resourceIdentifier?.toString() ?: detail.speciesId
        val form = species?.forms?.firstOrNull { it.name == detail.selectedForm } ?: species?.standardForm
        resolvedAspects = (form?.aspects ?: emptyList()).toSet()
        stateStore.scroll("pokeinfo_detail_${currentPage.name}").offset = 0f
    }

    private fun requestDetail() {
        detailRequestId = PokeInfoClientState.nextRequestId()
        PokeInfoClientState.beginDetailRequest(detailRequestId, speciesId)
        detailRequestStartedAt = System.currentTimeMillis()
        detailLoading = true
        detailErrorKey = null
        RequestSpeciesDetailPacket(detailRequestId, speciesId).sendToServer()
    }

    private fun receiveDetailResponse() {
        if (!detailLoading) return
        val response = PokeInfoClientState.consumeDetailResponse(detailRequestId)
        if (response != null) {
            when (response.status) {
                SpeciesDetailResponsePacket.Status.SUCCESS -> {
                    formDetails = response.details
                    val received = formDetails.firstOrNull()
                    if (received != null) applyDetail(received)
                    else failDetail("cobblemon_smartphone.pokeinfo.error.server")
                }
                SpeciesDetailResponsePacket.Status.NOT_FOUND -> failDetail("cobblemon_smartphone.pokeinfo.error.not_found")
                SpeciesDetailResponsePacket.Status.RATE_LIMITED -> failDetail("cobblemon_smartphone.pokeinfo.error.rate_limited")
                SpeciesDetailResponsePacket.Status.ERROR -> failDetail("cobblemon_smartphone.pokeinfo.error.server")
            }
        } else if (System.currentTimeMillis() - detailRequestStartedAt >= REQUEST_TIMEOUT_MS) {
            failDetail("cobblemon_smartphone.pokeinfo.error.timeout")
        }
    }

    private fun failDetail(key: String) {
        detailLoading = false
        detailErrorKey = key
    }

    private fun applyForm(index: Int) {
        val received = formDetails.getOrNull(index) ?: return
        applyDetail(received)
    }

    private fun multiplierColor(mult: Double): Int = when (mult) {
        4.0 -> 0xFFCC1111.toInt()
        2.0 -> 0xFFCC3333.toInt()
        0.5 -> 0xFF339933.toInt()
        0.25 -> 0xFF116611.toInt()
        0.0 -> 0xFF9966CC.toInt()
        else -> 0xFF1A1A2E.toInt()
    }

    private fun statBarColor(value: Int): Int = when {
        value >= 120 -> 0xFF00C2B8.toInt()
        value >= 100 -> 0xFF23CD5E.toInt()
        value >= 80 -> 0xFFA0E515.toInt()
        value >= 60 -> 0xFFFFDD57.toInt()
        value >= 40 -> 0xFFFF7F0F.toInt()
        else -> 0xFFF34444.toInt()
    }

    private fun lang(key: String, vararg args: Any): String =
        Component.translatable("cobblemon_smartphone.pokeinfo.$key", *args.map { it.toString() }.toTypedArray()).string

    private fun localized(text: PokeInfoDataProvider.LocalizedText): String =
        Component.translatable(text.key, *text.args.map(::localizedArgument).toTypedArray()).string

    private fun localizedArgument(argument: String): String = when {
        argument.startsWith("pokemon:") -> pokemonName(argument.removePrefix("pokemon:"))
        argument.startsWith("move:") -> moveName(argument.removePrefix("move:"))
        argument.startsWith("type:") -> typeName(argument.removePrefix("type:"))
        argument.startsWith("item:") -> {
            val raw = argument.removePrefix("item:")
            val location = ResourceLocation.tryParse(raw)
            if (location != null && BuiltInRegistries.ITEM.containsKey(location)) {
                Component.translatable(BuiltInRegistries.ITEM.get(location).descriptionId).string
            } else humanize(raw)
        }
        else -> argument
    }

    private fun translatedOrFallback(key: String, fallback: String): String =
        if (I18n.exists(key)) Component.translatable(key).string else fallback

    private fun humanize(value: String): String = value.substringAfter(':')
        .replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter(String::isNotEmpty)
        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    private fun pokemonName(rawName: String, speciesId: String? = null): String {
        val species = speciesId?.let { PokeInfoDataProvider.findSpecies(it) }
            ?: PokeInfoDataProvider.findSpecies(rawName)
        return species?.translatedName?.string ?: humanize(rawName)
    }

    private fun typeName(type: String): String =
        translatedOrFallback("cobblemon.type.${type.lowercase()}", humanize(type))

    private fun moveName(move: String): String =
        translatedOrFallback("cobblemon.move.${move.lowercase()}", humanize(move))

    private fun moveDescription(move: String): String? {
        val key = "cobblemon.move.${move.lowercase()}.desc"
        return if (I18n.exists(key)) Component.translatable(key).string.takeIf { it.isNotBlank() } else null
    }

    private fun abilityName(ability: String): String =
        translatedOrFallback("cobblemon.ability.${ability.lowercase()}", humanize(ability))

    private fun eggGroupName(group: String): String =
        translatedOrFallback("cobblemon.egg_group.${group.lowercase()}", humanize(group))

    private fun biomeName(id: String): String {
        val location = ResourceLocation.tryParse(id)
        val key = location?.let { "biome.${it.namespace}.${it.path.replace('/', '.')}" }
        return if (key != null) translatedOrFallback(key, humanize(id)) else humanize(id)
    }

    private fun spawnValue(kind: String, value: String): String = translatedOrFallback(
        "cobblemon_smartphone.pokeinfo.spawn.$kind.${value.lowercase().replace(' ', '_').replace('-', '_')}",
        humanize(value)
    )

    companion object {
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val MOVES_BATCH = 45
        private const val CONTENT_WEAK = 0xFFCC3333.toInt()
        private const val CONTENT_COND = 0xFFCC6633.toInt()
        private const val CONTENT_MALE = 0xFF5599DD.toInt()
        private const val CONTENT_FEMALE = 0xFFDD5599.toInt()

        private val typeChart: Map<String, Map<String, Double>> = mapOf(
            "normal" to mapOf("fighting" to 2.0, "ghost" to 0.0),
            "fire" to mapOf(
                "fire" to 0.5, "water" to 2.0, "grass" to 0.5, "ice" to 0.5,
                "ground" to 2.0, "bug" to 0.5, "rock" to 2.0, "steel" to 0.5, "fairy" to 0.5
            ),
            "water" to mapOf(
                "fire" to 0.5, "water" to 0.5, "grass" to 2.0, "electric" to 2.0,
                "ice" to 0.5, "steel" to 0.5
            ),
            "electric" to mapOf("electric" to 0.5, "ground" to 2.0, "flying" to 0.5, "steel" to 0.5),
            "grass" to mapOf(
                "fire" to 2.0, "water" to 0.5, "electric" to 0.5, "grass" to 0.5, "ice" to 2.0,
                "poison" to 2.0, "ground" to 0.5, "flying" to 2.0, "bug" to 2.0
            ),
            "ice" to mapOf("fire" to 2.0, "ice" to 0.5, "fighting" to 2.0, "rock" to 2.0, "steel" to 2.0),
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
            "psychic" to mapOf("fighting" to 0.5, "psychic" to 0.5, "bug" to 2.0, "ghost" to 2.0, "dark" to 2.0),
            "bug" to mapOf("fire" to 2.0, "grass" to 0.5, "fighting" to 0.5, "ground" to 0.5, "flying" to 2.0, "rock" to 2.0),
            "rock" to mapOf(
                "normal" to 0.5, "fire" to 0.5, "water" to 2.0, "grass" to 2.0,
                "fighting" to 2.0, "poison" to 0.5, "ground" to 2.0, "flying" to 0.5, "steel" to 2.0
            ),
            "ghost" to mapOf(
                "normal" to 0.0, "fighting" to 0.0, "poison" to 0.5, "bug" to 0.5,
                "ghost" to 2.0, "dark" to 2.0
            ),
            "dragon" to mapOf(
                "fire" to 0.5, "water" to 0.5, "grass" to 0.5, "electric" to 0.5,
                "ice" to 2.0, "dragon" to 2.0, "fairy" to 2.0
            ),
            "dark" to mapOf("fighting" to 2.0, "psychic" to 0.0, "bug" to 2.0, "ghost" to 0.5, "dark" to 0.5, "fairy" to 2.0),
            "steel" to mapOf(
                "normal" to 0.5, "fire" to 2.0, "grass" to 0.5, "ice" to 0.5,
                "fighting" to 2.0, "poison" to 0.0, "ground" to 2.0, "flying" to 0.5,
                "psychic" to 0.5, "bug" to 0.5, "rock" to 0.5, "dragon" to 0.5,
                "steel" to 0.5, "fairy" to 0.5
            ),
            "fairy" to mapOf("fighting" to 0.5, "poison" to 2.0, "bug" to 0.5, "dragon" to 0.0, "dark" to 0.5, "steel" to 2.0)
        )

        private val allTypes = listOf(
            "normal", "fire", "water", "electric", "grass", "ice",
            "fighting", "poison", "ground", "flying", "psychic", "bug",
            "rock", "ghost", "dragon", "dark", "steel", "fairy"
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
            0.25 -> "¼"
            0.5 -> "½"
            1.0 -> "-"
            2.0 -> "2"
            4.0 -> "4"
            else -> value.toString()
        }

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
            else -> 0xFF1A1A2E.toInt()
        }

        fun catchPercent(catchRate: Int): Int {
            val a = (catchRate / 3.0).roundToInt().coerceAtLeast(1)
            if (a >= 255) return 100
            val b = 65536.0 / sqrt(sqrt(255.0 / a))
            return (b / 65536.0).pow(4).times(100).roundToInt()
        }
    }
}
