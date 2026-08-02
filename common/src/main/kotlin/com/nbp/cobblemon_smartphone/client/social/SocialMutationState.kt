package com.nbp.cobblemon_smartphone.client.social

import com.nbp.cobblemon_smartphone.network.packet.SocialMutationResultPacket

object SocialMutationState {
    private var nextId = 1L
    private val results = mutableMapOf<Long, SocialMutationResultPacket.Status>()

    @Synchronized fun nextRequestId(): Long = nextId++
    @Synchronized fun accept(id: Long, status: SocialMutationResultPacket.Status) { results[id] = status }
    @Synchronized fun consume(id: Long): SocialMutationResultPacket.Status? = results.remove(id)
    @Synchronized fun clear() { results.clear(); nextId = 1L }
}
