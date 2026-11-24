package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.nbp.cobblemon_smartphone.client.PokeInfoClientState
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBackground
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.RequestSpeciesListPacket
import com.nbp.cobblemon_smartphone.network.packet.SpeciesListResponsePacket
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * PokéInfo species browser screen built on the HD [HdPhoneScreen] UI engine.
 */
class PokeInfoScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    scaleOverride: Float? = null
) : HdPhoneScreen(
    color,
    smartphoneStack,
    Component.translatable("cobblemon_smartphone.screen.pokeinfo"),
    scaleOverride
) {

    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelHdBadge: Boolean = true

    private var results: List<PokeInfoDataProvider.SpeciesInfo> = emptyList()
    private var allSpecies: List<PokeInfoDataProvider.SpeciesInfo> = emptyList()
    private var listRequestId = -1L
    private var listRequestStartedAt = 0L
    private var listLoading = true
    private var listErrorKey: String? = null
    private var displayLimit = PAGE_BATCH
    private val floatingState = FloatingState()

    override fun init() {
        super.init()
        listRequestId = PokeInfoClientState.nextRequestId()
        PokeInfoClientState.beginListRequest(listRequestId)
        listRequestStartedAt = System.currentTimeMillis()
        listLoading = true
        listErrorKey = null
        RequestSpeciesListPacket(listRequestId).sendToServer()
    }

    override fun removed() {
        PokeInfoClientState.cancelListRequest(listRequestId)
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        receiveSpeciesList()
        super.render(guiGraphics, mouseX, mouseY, delta)
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        val countSubtitle = if (!listLoading && listErrorKey == null) "${results.size} Pokémon" else null
        builder.appHeader(
            title = Component.translatable("cobblemon_smartphone.screen.pokeinfo").string,
            subtitle = countSubtitle,
            icon = HdIconAtlas.Icon.SEARCH,
            iconColor = HdColorSpec.Token("primary")
        )

        TrackerComponents.buildSearchBar(
            builder = builder,
            inputId = SEARCH_INPUT_ID,
            placeholder = lang("search"),
            onChange = { query -> onSearchChanged(query) }
        )

        builder.scroll(
            scrollId = SCROLL_ID,
            gap = 5f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 4f),
            onNearBottom = {
                if (displayLimit < results.size) {
                    displayLimit = (displayLimit + PAGE_BATCH).coerceAtMost(results.size)
                }
            }
        ) { list ->
            if (results.isEmpty()) {
                val message = if (listLoading || listErrorKey != null) {
                    Component.translatable(listErrorKey ?: "cobblemon_smartphone.pokeinfo.loading_species").string
                } else {
                    "0 / ${allSpecies.size}"
                }
                TrackerComponents.buildEmptyState(list, message)
            } else {
                val count = displayLimit.coerceAtMost(results.size)
                for (i in 0 until count) {
                    buildSpeciesCard(list, results[i])
                }
            }
        }
    }

    private fun buildSpeciesCard(builder: HdBuilder, species: PokeInfoDataProvider.SpeciesInfo) {
        val resolvedId = resolveSpeciesIdentifier(species)
        val dexAndName = "#${String.format("%03d", species.dexNumber)} ${localizedPokemonName(species)}"
        val types = listOfNotNull(species.primaryType, species.secondaryType)

        builder.row(
            gap = 8f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 5f),
            width = HdSizeSpec.Fill,
            background = HdBackground(
                color = HdColorSpec.Token("primary", 0.90f),
                radius = 8f,
                borderWidth = 0.7f,
                borderColor = HdColorSpec.Token("primary_border"),
                shadowBlur = 4f,
                shadowOffsetY = 1.5f,
                shadowColor = HdColorSpec.Fixed(0x28000000)
            )
        ) { card ->
            hoverBackground = HdBackground(
                color = HdColorSpec.Token("primary_hover", 0.95f),
                radius = 8f,
                borderWidth = 0.8f,
                borderColor = HdColorSpec.Token("gold"),
                shadowBlur = 5f,
                shadowOffsetY = 1.5f,
                shadowColor = HdColorSpec.Fixed(0x32000000)
            )
            tooltip = Component.translatable("cobblemon_smartphone.tooltip.view_pokemon_details").string
            onClick = {
                playClickSound()
                Minecraft.getInstance().setScreen(
                    PokeInfoDetailScreen(color, smartphoneStack, species.speciesId, species.dexNumber)
                )
            }

            card.column(gap = 3.5f, width = HdSizeSpec.Weight(1f)) { left ->
                left.text(
                    text = dexAndName,
                    size = 10.2f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = HdColorSpec.Token("text_primary"),
                    wrap = true
                ) {
                    width = HdSizeSpec.Fill
                }
                left.row(gap = 4f, crossAlign = HdAlign.CENTER) { typeRow ->
                    types.forEach { type ->
                        val typeBg = PokeInfoDetailScreen.typeColor(type)
                        typeRow.row(
                            padding = HdPadding.symmetric(horizontal = 6f, vertical = 2f),
                            background = HdBackground(
                                color = HdColorSpec.Fixed(typeBg),
                                radius = 5f,
                                borderWidth = 0.5f,
                                borderColor = HdColorSpec.Fixed(0x45FFFFFF)
                            )
                        ) { pill ->
                            pill.text(
                                text = localizedTypeName(type),
                                size = 7.8f,
                                weight = HdTextLayout.Weight.BOLD,
                                color = HdColorSpec.Fixed(0xFFFFFFFF.toInt())
                            )
                        }
                    }
                }
            }

            card.pokemonView(
                speciesId = resolvedId,
                size = 34f,
                floatingState = floatingState,
                showShinyBadge = false
            )
        }
    }

    private fun resolveSpeciesIdentifier(species: PokeInfoDataProvider.SpeciesInfo): String {
        val sp = PokeInfoDataProvider.findSpecies(species.speciesId)
            ?: PokeInfoDataProvider.findSpecies(species.name)
        return sp?.resourceIdentifier?.toString() ?: species.speciesId
    }

    private fun onSearchChanged(query: String) {
        val normalized = query.trim().lowercase()
        results = if (normalized.isEmpty()) {
            allSpecies
        } else {
            allSpecies.filter { species ->
                localizedPokemonName(species).lowercase().contains(normalized) ||
                    species.name.lowercase().contains(normalized) ||
                    species.speciesId.lowercase().contains(normalized) ||
                    species.dexNumber.toString().startsWith(normalized)
            }
        }
        displayLimit = PAGE_BATCH
        stateStore.scroll(SCROLL_ID).offset = 0f
    }

    private fun receiveSpeciesList() {
        if (!listLoading) return
        val response = PokeInfoClientState.consumeListResponse(listRequestId)
        if (response != null) {
            listLoading = false
            when (response.status) {
                SpeciesListResponsePacket.Status.SUCCESS -> {
                    allSpecies = response.species
                    onSearchChanged(stateStore.getInputText(SEARCH_INPUT_ID))
                }
                SpeciesListResponsePacket.Status.RATE_LIMITED -> listErrorKey = "cobblemon_smartphone.pokeinfo.error.rate_limited"
                SpeciesListResponsePacket.Status.ERROR -> listErrorKey = "cobblemon_smartphone.pokeinfo.error.server"
            }
        } else if (System.currentTimeMillis() - listRequestStartedAt >= REQUEST_TIMEOUT_MS) {
            listLoading = false
            listErrorKey = "cobblemon_smartphone.pokeinfo.error.timeout"
        }
    }

    private fun localizedPokemonName(species: PokeInfoDataProvider.SpeciesInfo): String {
        val sp = PokeInfoDataProvider.findSpecies(species.speciesId) ?: PokeInfoDataProvider.findSpecies(species.name)
        return sp?.translatedName?.string ?: species.name
    }

    private fun localizedTypeName(type: String): String {
        val key = "cobblemon.type.${type.lowercase()}"
        return if (I18n.exists(key)) Component.translatable(key).string else type
    }

    private fun lang(key: String): String =
        Component.translatable("cobblemon_smartphone.pokeinfo.$key").string

    companion object {
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val PAGE_BATCH = 60
        private const val SEARCH_INPUT_ID = "pokeinfo_search"
        private const val SCROLL_ID = "pokeinfo_list"
    }
}
