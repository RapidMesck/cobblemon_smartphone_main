package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.social.SocialNotificationPrefs
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/** Server -> client: the player's persisted granular Social notification preferences, sent on join. */
class SyncSocialMutePacket(val prefs: SocialNotificationPrefs) : CobblemonSmartphoneNetworkPacket<SyncSocialMutePacket> {
    constructor(muted: Boolean) : this(SocialNotificationPrefs(doNotDisturb = muted))

    val muted: Boolean get() = prefs.doNotDisturb

    companion object {
        val ID = smartphoneResource("sync_social_mute")
        fun decode(buffer: RegistryFriendlyByteBuf) =
            SyncSocialMutePacket(SocialNotificationPrefs.fromBitmask(buffer.readVarInt()))
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(prefs.toBitmask())
    }
}
