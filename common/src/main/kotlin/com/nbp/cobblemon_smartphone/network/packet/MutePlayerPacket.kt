package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/** Client -> server: mute or unmute a specific player for the sender. */
class MutePlayerPacket(val targetUuid: UUID, val muted: Boolean) : CobblemonSmartphoneNetworkPacket<MutePlayerPacket> {
    companion object {
        val ID = smartphoneResource("mute_player")
        fun decode(buffer: RegistryFriendlyByteBuf) = MutePlayerPacket(buffer.readUUID(), buffer.readBoolean())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(targetUuid)
        buffer.writeBoolean(muted)
    }
}
