package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/** Client -> server: persist the player's Social Do Not Disturb flag. */
class SaveSocialMutePacket(val muted: Boolean) : CobblemonSmartphoneNetworkPacket<SaveSocialMutePacket> {
    companion object {
        val ID = smartphoneResource("save_social_mute")
        fun decode(buffer: RegistryFriendlyByteBuf) = SaveSocialMutePacket(buffer.readBoolean())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(muted)
    }
}
