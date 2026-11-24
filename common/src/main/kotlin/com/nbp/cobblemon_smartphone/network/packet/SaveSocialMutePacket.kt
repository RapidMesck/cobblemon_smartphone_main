package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.social.SocialNotificationPrefs
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/** Client -> server: persist the player's granular Social notification preferences. */
class SaveSocialMutePacket(val prefs: SocialNotificationPrefs) : CobblemonSmartphoneNetworkPacket<SaveSocialMutePacket> {
    constructor(muted: Boolean) : this(SocialNotificationPrefs(doNotDisturb = muted))

    val muted: Boolean get() = prefs.doNotDisturb

    companion object {
        val ID = smartphoneResource("save_social_mute")
        fun decode(buffer: RegistryFriendlyByteBuf) =
            SaveSocialMutePacket(SocialNotificationPrefs.fromBitmask(buffer.readVarInt()))
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(prefs.toBitmask())
    }
}
