package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.client.StructureCompassClientState
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.RequestStructureListPacket
import com.nbp.cobblemon_smartphone.network.packet.StructureListResponsePacket
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.util.StructureDataProvider.StructureInfo
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Structure compass tracker screen built on the HD [HdPhoneScreen] UI engine.
 */
class StructureCompassScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    scaleOverride: Float? = null
) : HdPhoneScreen(
    color,
    smartphoneStack,
    Component.translatable("cobblemon_smartphone.screen.structure_compass"),
    scaleOverride
) {

    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelHdBadge: Boolean = true

    private var results: List<StructureInfo> = emptyList()
    private var allStructures: List<StructureInfo> = emptyList()
    private var listRequestId = -1L
    private var listRequestStartedAt = 0L
    private var listLoading = true
    private var listErrorKey: String? = null
    private var displayLimit = PAGE_BATCH

    override fun init() {
        super.init()
        val cached = StructureCompassClientState.cachedStructures
        if (cached != null) {
            allStructures = cached
            listLoading = false
            listErrorKey = null
            onSearchChanged(stateStore.getInputText(SEARCH_INPUT_ID))
        } else {
            listRequestId = StructureCompassClientState.nextRequestId()
            StructureCompassClientState.beginListRequest(listRequestId)
            listRequestStartedAt = System.currentTimeMillis()
            listLoading = true
            listErrorKey = null
            RequestStructureListPacket(listRequestId).sendToServer()
        }
    }

    override fun removed() {
        StructureCompassClientState.cancelListRequest(listRequestId)
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        receiveStructureList()
        super.render(guiGraphics, mouseX, mouseY, delta)
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        TrackerComponents.buildHeader(
            builder = builder,
            icon = HdIconAtlas.Icon.FLAG,
            title = TrackerComponents.tr("cobblemon_smartphone.screen.structure_compass"),
            countText = if (!listLoading && listErrorKey == null) "${results.size}" else null,
            tracking = StructureCompassClientState.tracking,
            stopLabel = lang("stop_tracking"),
            stopTooltip = TrackerComponents.tr("cobblemon_smartphone.tooltip.stop_structure_tracking"),
            onStopTracking = { StructureCompassClientState.stopTracking() }
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
                    TrackerComponents.tr(listErrorKey ?: "cobblemon_smartphone.structure_compass.loading_structures")
                } else {
                    "0 / ${allStructures.size}"
                }
                TrackerComponents.buildEmptyState(list, message)
            } else {
                val count = displayLimit.coerceAtMost(results.size)
                for (i in 0 until count) {
                    val structure = results[i]
                    val trackable = TrackerComponents.isInCurrentDimension(structure.dimensions)
                    TrackerComponents.buildTrackerCard(
                        builder = list,
                        theme = theme,
                        title = prettyName(structure),
                        idLabel = lang("id_label"),
                        idValue = structure.id,
                        dimsLabel = lang("dimensions_label"),
                        dimsValue = TrackerComponents.formatDimensions(structure.dimensions, lang("any_dimension")),
                        modLabel = lang("mod_label"),
                        modValue = structure.sourceMod,
                        trackable = trackable,
                        tooltipText = TrackerComponents.tr(
                            if (trackable) "cobblemon_smartphone.structure_compass.select"
                            else "cobblemon_smartphone.structure_compass.other_dimension"
                        ),
                        onSelect = {
                            StructureCompassClientState.startTracking(structure)
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
            allStructures
        } else {
            allStructures.filter { matchesQuery(it, normalized) }
        }
        displayLimit = PAGE_BATCH
        stateStore.scroll(SCROLL_ID).offset = 0f
    }

    private fun matchesQuery(structure: StructureInfo, q: String): Boolean {
        return prettyName(structure).lowercase().contains(q) ||
            structure.id.lowercase().contains(q) ||
            structure.id.substringBefore(':').lowercase().contains(q) ||
            structure.dimensions.any { it.lowercase().contains(q) } ||
            structure.sourceMod.lowercase().contains(q)
    }

    private fun receiveStructureList() {
        if (!listLoading) return
        val cached = StructureCompassClientState.cachedStructures
        if (cached != null) {
            listLoading = false
            listErrorKey = null
            allStructures = cached
            onSearchChanged(stateStore.getInputText(SEARCH_INPUT_ID))
            return
        }
        val response = StructureCompassClientState.consumeListResponse(listRequestId)
        if (response != null) {
            listLoading = false
            when (response.status) {
                StructureListResponsePacket.Status.SUCCESS -> {
                    allStructures = response.structures
                    onSearchChanged(stateStore.getInputText(SEARCH_INPUT_ID))
                }
                StructureListResponsePacket.Status.RATE_LIMITED -> {
                    listErrorKey = "cobblemon_smartphone.structure_compass.error.rate_limited"
                }
                StructureListResponsePacket.Status.ERROR -> {
                    listErrorKey = "cobblemon_smartphone.structure_compass.error.server"
                }
            }
        } else if (System.currentTimeMillis() - listRequestStartedAt >= REQUEST_TIMEOUT_MS) {
            listLoading = false
            listErrorKey = "cobblemon_smartphone.structure_compass.error.timeout"
        }
    }

    private fun prettyName(structure: StructureInfo): String {
        val path = structure.id.substringAfter(':')
        return path.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    private fun lang(key: String): String =
        TrackerComponents.tr("cobblemon_smartphone.structure_compass.$key")

    companion object {
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val PAGE_BATCH = 60
        private const val SEARCH_INPUT_ID = "structure_search"
        private const val SCROLL_ID = "structure_list"
    }
}
