package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.network.packet.RequestStructureSearchPacket
import com.nbp.cobblemon_smartphone.network.packet.StructureListResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.StructureSearchResultPacket
import com.nbp.cobblemon_smartphone.util.StructureDataProvider.StructureInfo
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import java.util.concurrent.atomic.AtomicLong

/**
 * Client-side state for the structure compass — a near-identical mirror of [GpsClientState], just
 * tracking a structure instead of a biome. The actual search runs server-side (see
 * RequestStructureSearchHandler/StructureSearchTask); this tracks the request/response handshake
 * and the active tracking session consumed by
 * [com.nbp.cobblemon_smartphone.client.gui.StructureCompassOverlay].
 */
object StructureCompassClientState {
    private val sequence = AtomicLong()

    @Volatile private var expectedListRequestId = -1L
    @Volatile private var listResponse: StructureListResponsePacket? = null
    @Volatile var cachedStructures: List<StructureInfo>? = null
        private set

    enum class SearchStatus { IDLE, SEARCHING, FOUND, NOT_FOUND }

    @Volatile var tracking = false
        private set
    @Volatile var target: StructureInfo? = null
        private set
    @Volatile var searchStatus = SearchStatus.IDLE
        private set
    @Volatile var targetPos: BlockPos? = null
        private set
    @Volatile private var targetDimension: ResourceKey<Level>? = null

    @Volatile private var expectedSearchRequestId = -1L
    @Volatile private var searchRequestedAt = 0L
    @Volatile private var searchRetryAt = 0L

    // --- Structure list handshake (mirrors GpsClientState's biome-list handshake) ---

    fun nextRequestId(): Long = sequence.incrementAndGet()

    fun beginListRequest(requestId: Long) {
        expectedListRequestId = requestId
        listResponse = null
    }

    fun accept(packet: StructureListResponsePacket) {
        if (packet.requestId == expectedListRequestId) listResponse = packet
    }

    fun consumeListResponse(requestId: Long): StructureListResponsePacket? {
        if (requestId != expectedListRequestId) return null
        val response = listResponse ?: return null
        listResponse = null
        if (response.status == StructureListResponsePacket.Status.SUCCESS) cachedStructures = response.structures
        return response
    }

    fun cancelListRequest(requestId: Long) {
        if (requestId == expectedListRequestId) {
            expectedListRequestId = -1L
            listResponse = null
        }
    }

    // --- Tracking ---

    fun startTracking(structure: StructureInfo) {
        val level = Minecraft.getInstance().level ?: return
        if (Minecraft.getInstance().player == null) return
        target = structure
        targetDimension = level.dimension()
        targetPos = null
        searchStatus = SearchStatus.SEARCHING
        tracking = true
        requestSearch(structure)
    }

    fun stopTracking() {
        expectedSearchRequestId = -1L
        tracking = false
        target = null
        targetPos = null
        targetDimension = null
        searchStatus = SearchStatus.IDLE
    }

    /** Called on disconnect: the cached structure list and any in-flight requests belong to the
     *  server we just left and must not leak into the next session. */
    fun reset() {
        stopTracking()
        expectedListRequestId = -1L
        listResponse = null
        cachedStructures = null
    }

    private fun requestSearch(structure: StructureInfo) {
        val id = nextRequestId()
        expectedSearchRequestId = id
        searchRequestedAt = System.currentTimeMillis()
        searchRetryAt = 0L
        RequestStructureSearchPacket(id, structure.id).sendToServer()
    }

    fun acceptSearchResult(packet: StructureSearchResultPacket) {
        if (packet.requestId != expectedSearchRequestId) return
        // A response for a dimension we've since left (search restarted) is stale; ignore it.
        val expectedDimension = targetDimension?.location()?.toString()
        if (expectedDimension != null && packet.dimension != expectedDimension) return

        when (packet.status) {
            StructureSearchResultPacket.Status.FOUND -> {
                targetPos = packet.pos
                searchStatus = SearchStatus.FOUND
            }
            StructureSearchResultPacket.Status.NOT_FOUND, StructureSearchResultPacket.Status.ERROR -> {
                searchStatus = SearchStatus.NOT_FOUND
            }
            StructureSearchResultPacket.Status.RATE_LIMITED -> {
                searchRetryAt = System.currentTimeMillis() + RATE_LIMIT_RETRY_DELAY_MS
            }
        }
    }

    /** Called by the overlay every frame. Restarts the search on dimension change (the found spot
     *  belongs to the dimension we left and is meaningless in the new one), retries after a
     *  rate-limit response, and times out a request that never got a response. Once a spot is found
     *  it stays cached as-is; only picking a structure again from the screen searches again. */
    fun tick() {
        if (!tracking) return
        val level = Minecraft.getInstance().level ?: run { stopTracking(); return }
        Minecraft.getInstance().player ?: run { stopTracking(); return }
        val structure = target ?: return stopTracking()

        if (level.dimension() != targetDimension) {
            targetDimension = level.dimension()
            restartSearch(structure)
            return
        }

        val now = System.currentTimeMillis()
        if (searchRetryAt != 0L && now >= searchRetryAt) {
            requestSearch(structure)
            return
        }

        if (searchStatus == SearchStatus.SEARCHING && now - searchRequestedAt >= SEARCH_TIMEOUT_MS) {
            searchStatus = SearchStatus.NOT_FOUND
        }
    }

    private fun restartSearch(structure: StructureInfo) {
        targetPos = null
        searchStatus = SearchStatus.SEARCHING
        requestSearch(structure)
    }

    private const val SEARCH_TIMEOUT_MS = 15_000L
    private const val RATE_LIMIT_RETRY_DELAY_MS = 750L
}
