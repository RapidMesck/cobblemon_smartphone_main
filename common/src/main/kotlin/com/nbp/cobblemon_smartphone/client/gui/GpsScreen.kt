package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.client.GpsClientState
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.BiomeListResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.RequestBiomeListPacket
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.util.GpsDataProvider.BiomeInfo
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Biome GPS tracker screen built on the HD [HdPhoneScreen] UI engine.
 */
class GpsScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    scaleOverride: Float? = null
) : HdPhoneScreen(color, smartphoneStack, Component.translatable("cobblemon_smartphone.screen.gps"), scaleOverride) {

    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelHdBadge: Boolean = true

    private var results: List<BiomeInfo> = emptyList()
    private var allBiomes: List<BiomeInfo> = emptyList()
    private var listRequestId = -1L
    private var listRequestStartedAt = 0L
    private var listLoading = true
    private var listErrorKey: String? = null
    private var displayLimit = PAGE_BATCH

    override fun init() {
        super.init()
        val cached = GpsClientState.cachedBiomes
        if (cached != null) {
            allBiomes = cached
            listLoading = false
            listErrorKey = null
            onSearchChanged(stateStore.getInputText(SEARCH_INPUT_ID))
        } else {
            listRequestId = GpsClientState.nextRequestId()
            GpsClientState.beginListRequest(listRequestId)
            listRequestStartedAt = System.currentTimeMillis()
            listLoading = true
            listErrorKey = null
            RequestBiomeListPacket(listRequestId).sendToServer()
        }
    }

    override fun removed() {
        GpsClientState.cancelListRequest(listRequestId)
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        receiveBiomeList()
        super.render(guiGraphics, mouseX, mouseY, delta)
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        TrackerComponents.buildHeader(
            builder = builder,
            icon = HdIconAtlas.Icon.GLOBE,
            title = TrackerComponents.tr("cobblemon_smartphone.screen.gps"),
            countText = if (!listLoading && listErrorKey == null) "${results.size}" else null,
            tracking = GpsClientState.tracking,
            stopLabel = lang("stop_tracking"),
            stopTooltip = TrackerComponents.tr("cobblemon_smartphone.tooltip.stop_gps_tracking"),
            onStopTracking = { GpsClientState.stopTracking() }
        )

        TrackerComponents.buildSearchBar(
            builder = builder,
            inputId = SEARCH_INPUT_ID,
            placeholder = lang("search"),
            onChange = { query -> onSearchChanged(query) }
        )

        builder.scroll(
            scrollId = SCROLL_ID,
            gap = 6f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 4f),
            onNearBottom = {
                if (displayLimit < results.size) {
                    displayLimit = (displayLimit + PAGE_BATCH).coerceAtMost(results.size)
                }
            }
        ) { list ->
            if (results.isEmpty()) {
                val message = if (listLoading || listErrorKey != null) {
                    TrackerComponents.tr(listErrorKey ?: "cobblemon_smartphone.gps.loading_biomes")
                } else {
                    "0 / ${allBiomes.size}"
                }
                TrackerComponents.buildEmptyState(list, message)
            } else {
                val count = displayLimit.coerceAtMost(results.size)
                for (i in 0 until count) {
                    val biome = results[i]
                    val trackable = TrackerComponents.isInCurrentDimension(biome.dimensions)
                    TrackerComponents.buildTrackerCard(
                        builder = list,
                        theme = theme,
                        title = localizedBiomeName(biome),
                        idLabel = lang("id_label"),
                        idValue = biome.id,
                        dimsLabel = lang("dimensions_label"),
                        dimsValue = TrackerComponents.formatDimensions(biome.dimensions, lang("any_dimension")),
                        modLabel = lang("mod_label"),
                        modValue = biome.sourceMod,
                        trackable = trackable,
                        tooltipText = TrackerComponents.tr(
                            if (trackable) "cobblemon_smartphone.gps.select" else "cobblemon_smartphone.gps.other_dimension"
                        ),
                        onSelect = {
                            GpsClientState.startTracking(biome)
                            Minecraft.getInstance().setScreen(null)
                        }
                    )
                }
            }
        }
    }

    private fun onSearchChanged(query: String) {
        val normalized = query.trim().lowercase()
        results = if (normalized.isEmpty()) {
            allBiomes
        } else {
            allBiomes.filter { matchesQuery(it, normalized) }
        }
        displayLimit = PAGE_BATCH
        stateStore.scroll(SCROLL_ID).offset = 0f
    }

    private fun matchesQuery(biome: BiomeInfo, q: String): Boolean {
        val tagQuery = q.removePrefix("#")
        return localizedBiomeName(biome).lowercase().contains(q) ||
            biome.id.lowercase().contains(q) ||
            biome.id.substringBefore(':').lowercase().contains(q) ||
            biome.tags.any { it.lowercase().removePrefix("#").contains(tagQuery) } ||
            biome.dimensions.any { it.lowercase().contains(q) } ||
            biome.sourceMod.lowercase().contains(q)
    }

    private fun receiveBiomeList() {
        if (!listLoading) return
        val cached = GpsClientState.cachedBiomes
        if (cached != null) {
            listLoading = false
            listErrorKey = null
            allBiomes = cached
            onSearchChanged(stateStore.getInputText(SEARCH_INPUT_ID))
            return
        }
        val response = GpsClientState.consumeListResponse(listRequestId)
        if (response != null) {
            listLoading = false
            when (response.status) {
                BiomeListResponsePacket.Status.SUCCESS -> {
                    allBiomes = response.biomes
                    onSearchChanged(stateStore.getInputText(SEARCH_INPUT_ID))
                }
                BiomeListResponsePacket.Status.RATE_LIMITED -> listErrorKey = "cobblemon_smartphone.gps.error.rate_limited"
                BiomeListResponsePacket.Status.ERROR -> listErrorKey = "cobblemon_smartphone.gps.error.server"
            }
        } else if (System.currentTimeMillis() - listRequestStartedAt >= REQUEST_TIMEOUT_MS) {
            listLoading = false
            listErrorKey = "cobblemon_smartphone.gps.error.timeout"
        }
    }

    private fun localizedBiomeName(biome: BiomeInfo): String {
        val split = biome.id.split(':', limit = 2)
        if (split.size != 2) return biome.id
        val key = "biome.${split[0]}.${split[1]}"
        return if (I18n.exists(key)) I18n.get(key) else biome.id
    }

    private fun lang(key: String): String = TrackerComponents.tr("cobblemon_smartphone.gps.$key")

    companion object {
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val PAGE_BATCH = 60
        private const val SEARCH_INPUT_ID = "gps_search"
        private const val SCROLL_ID = "gps_list"
    }
}
