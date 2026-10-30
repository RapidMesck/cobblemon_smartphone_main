package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * Sends a battle challenge request to another trainer on the server.
 */
class ChallengeBattlePacket(val targetUuid: UUID) : CobblemonSmartphoneNetworkPacket<ChallengeBattlePacket> {
    companion object {
        val ID = smartphoneResource("challenge_battle")
        fun decode(buffer: RegistryFriendlyByteBuf) = ChallengeBattlePacket(buffer.readUUID())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(targetUuid)
    }
}
