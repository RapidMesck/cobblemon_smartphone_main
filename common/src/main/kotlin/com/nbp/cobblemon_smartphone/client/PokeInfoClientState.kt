package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.network.packet.SpeciesDetailResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.SpeciesListResponsePacket
import java.util.concurrent.atomic.AtomicLong

/** Accepts only the response for the request currently owned by the open PokeInfo screen. */
object PokeInfoClientState {
    private val sequence = AtomicLong()

    @Volatile private var expectedDetailRequestId = -1L
    @Volatile private var expectedSpeciesId = ""
    @Volatile private var detailResponse: SpeciesDetailResponsePacket? = null
    @Volatile private var expectedListRequestId = -1L
    @Volatile private var listResponse: SpeciesListResponsePacket? = null

    fun nextRequestId(): Long = sequence.incrementAndGet()

    fun beginListRequest(requestId: Long) {
        expectedListRequestId = requestId
        listResponse = null
    }

    fun accept(packet: SpeciesListResponsePacket) {
        if (packet.requestId == expectedListRequestId) listResponse = packet
    }

    fun consumeListResponse(requestId: Long): SpeciesListResponsePacket? {
        if (requestId != expectedListRequestId) return null
        val response = listResponse ?: return null
        listResponse = null
        return response
    }

    fun cancelListRequest(requestId: Long) {
        if (requestId == expectedListRequestId) {
            expectedListRequestId = -1L
            listResponse = null
        }
    }

    fun beginDetailRequest(requestId: Long, speciesId: String) {
        expectedDetailRequestId = requestId
        expectedSpeciesId = speciesId
        detailResponse = null
    }

    fun beginDetailRequest(requestId: Long, dexNumber: Int) {
        beginDetailRequest(requestId, dexNumber.toString())
    }

    fun accept(packet: SpeciesDetailResponsePacket) {
        if (matchesExpected(packet.requestId, packet.speciesId)) {
            detailResponse = packet
        }
    }

    fun consumeDetailResponse(requestId: Long): SpeciesDetailResponsePacket? {
        if (requestId != expectedDetailRequestId) return null
        val response = detailResponse ?: return null
        detailResponse = null
        return response
    }

    fun cancelDetailRequest(requestId: Long) {
        if (requestId == expectedDetailRequestId) {
            expectedDetailRequestId = -1L
            expectedSpeciesId = ""
            detailResponse = null
        }
    }

    internal fun matchesExpected(requestId: Long, speciesId: String): Boolean =
        requestId == expectedDetailRequestId && speciesId.equals(expectedSpeciesId, ignoreCase = true)

    internal fun matchesExpected(requestId: Long, dexNumber: Int): Boolean =
        requestId == expectedDetailRequestId && (expectedSpeciesId == dexNumber.toString() || expectedSpeciesId.toIntOrNull() == dexNumber)
}
