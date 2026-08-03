package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.network.packet.BiomeListResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.GpsSearchResultPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestGpsSearchPacket
import com.nbp.cobblemon_smartphone.util.GpsDataProvider.BiomeInfo
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import java.util.concurrent.atomic.AtomicLong

/**
 * Client-side GPS state: the biome-list request/response handshake (same pattern as PokeInfo, with
 * the result cached so re-opening the GPS screen doesn't re-hit the server) and the active tracking
 * session consumed by [com.nbp.cobblemon_smartphone.client.gui.GpsCompassOverlay]. The actual biome
 * search runs server-side (see RequestGpsSearchHandler/GpsBiomeSearchTask); this just tracks the
 * request/response handshake for it, mirroring the biome-list handshake below.
 */
object GpsClientState {
    private val sequence = AtomicLong()

    @Volatile private var expectedListRequestId = -1L
    @Volatile private var listResponse: BiomeListResponsePacket? = null
    @Volatile var cachedBiomes: List<BiomeInfo>? = null
        private set

    enum class SearchStatus { IDLE, SEARCHING, FOUND, NOT_FOUND }

    @Volatile var tracking = false
        private set
    @Volatile var target: BiomeInfo? = null
        private set
    @Volatile var searchStatus = SearchStatus.IDLE
        private set
    @Volatile var targetPos: BlockPos? = null
        private set
    @Volatile private var targetDimension: ResourceKey<Level>? = null

    @Volatile private var expectedSearchRequestId = -1L
    @Volatile private var searchRequestedAt = 0L
    @Volatile private var searchRetryAt = 0L

    // --- Biome list handshake (mirrors PokeInfoClientState) ---

    fun nextRequestId(): Long = sequence.incrementAndGet()

    fun beginListRequest(requestId: Long) {
        expectedListRequestId = requestId
        listResponse = null
    }

    fun accept(packet: BiomeListResponsePacket) {
        if (packet.requestId == expectedListRequestId) listResponse = packet
    }

    fun consumeListResponse(requestId: Long): BiomeListResponsePacket? {
        if (requestId != expectedListRequestId) return null
        val response = listResponse ?: return null
        listResponse = null
        if (response.status == BiomeListResponsePacket.Status.SUCCESS) cachedBiomes = response.biomes
        return response
    }

    fun cancelListRequest(requestId: Long) {
        if (requestId == expectedListRequestId) {
            expectedListRequestId = -1L
            listResponse = null
        }
    }

    // --- Tracking ---

    fun startTracking(biome: BiomeInfo) {
        val level = Minecraft.getInstance().level ?: return
        if (Minecraft.getInstance().player == null) return
        target = biome
        targetDimension = level.dimension()
        targetPos = null
        searchStatus = SearchStatus.SEARCHING
        tracking = true
        requestSearch(biome)
    }

    fun stopTracking() {
        expectedSearchRequestId = -1L
        tracking = false
        target = null
        targetPos = null
        targetDimension = null
        searchStatus = SearchStatus.IDLE
    }

    /** Called on disconnect: the cached biome list and any in-flight requests belong to the server
     *  we just left and must not leak into the next session. */
    fun reset() {
        stopTracking()
        expectedListRequestId = -1L
        listResponse = null
        cachedBiomes = null
    }

    private fun requestSearch(biome: BiomeInfo) {
        val id = nextRequestId()
        expectedSearchRequestId = id
        searchRequestedAt = System.currentTimeMillis()
        searchRetryAt = 0L
        RequestGpsSearchPacket(id, biome.id).sendToServer()
    }

    fun acceptSearchResult(packet: GpsSearchResultPacket) {
        if (packet.requestId != expectedSearchRequestId) return
        // A response for a dimension we've since left (search restarted) is stale; ignore it.
        val expectedDimension = targetDimension?.location()?.toString()
        if (expectedDimension != null && packet.dimension != expectedDimension) return

        when (packet.status) {
            GpsSearchResultPacket.Status.FOUND -> {
                targetPos = packet.pos
                searchStatus = SearchStatus.FOUND
            }
            GpsSearchResultPacket.Status.NOT_FOUND, GpsSearchResultPacket.Status.ERROR -> {
                searchStatus = SearchStatus.NOT_FOUND
            }
            GpsSearchResultPacket.Status.RATE_LIMITED -> {
                searchRetryAt = System.currentTimeMillis() + RATE_LIMIT_RETRY_DELAY_MS
            }
        }
    }

    /** Called by the overlay every frame. Restarts the search on dimension change (the found spot
     *  belongs to the dimension we left and is meaningless in the new one), retries after a
     *  rate-limit response, and times out a request that never got a response (e.g. dropped
     *  packet). Once a spot is found it stays cached as-is — walking around doesn't re-trigger a
     *  search; only picking a biome again from the GPS screen does. */
    fun tick() {
        if (!tracking) return
        val level = Minecraft.getInstance().level ?: run { stopTracking(); return }
        Minecraft.getInstance().player ?: run { stopTracking(); return }
        val biome = target ?: return stopTracking()

        if (level.dimension() != targetDimension) {
            targetDimension = level.dimension()
            restartSearch(biome)
            return
        }

        val now = System.currentTimeMillis()
        if (searchRetryAt != 0L && now >= searchRetryAt) {
            requestSearch(biome)
            return
        }

        if (searchStatus == SearchStatus.SEARCHING && now - searchRequestedAt >= SEARCH_TIMEOUT_MS) {
            searchStatus = SearchStatus.NOT_FOUND
        }
    }

    private fun restartSearch(biome: BiomeInfo) {
        targetPos = null
        searchStatus = SearchStatus.SEARCHING
        requestSearch(biome)
    }

    private const val SEARCH_TIMEOUT_MS = 15_000L
    private const val RATE_LIMIT_RETRY_DELAY_MS = 750L
}
