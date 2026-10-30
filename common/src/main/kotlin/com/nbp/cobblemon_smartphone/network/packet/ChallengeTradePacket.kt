package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * Sends a Pokémon trade offer request to another trainer on the server.
 */
class ChallengeTradePacket(val targetUuid: UUID) : CobblemonSmartphoneNetworkPacket<ChallengeTradePacket> {
    companion object {
        val ID = smartphoneResource("challenge_trade")
        fun decode(buffer: RegistryFriendlyByteBuf) = ChallengeTradePacket(buffer.readUUID())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(targetUuid)
    }
}
