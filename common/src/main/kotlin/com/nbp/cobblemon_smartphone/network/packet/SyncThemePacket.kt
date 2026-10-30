package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SyncThemePacket(val themeId: String) : CobblemonSmartphoneNetworkPacket<SyncThemePacket> {
    companion object {
        val ID = smartphoneResource("sync_theme")
        private const val MAX_STRING_LENGTH = 256

        fun decode(buffer: RegistryFriendlyByteBuf): SyncThemePacket {
            val themeId = buffer.readUtf(MAX_STRING_LENGTH)
            return SyncThemePacket(themeId)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(themeId, MAX_STRING_LENGTH)
    }
}
