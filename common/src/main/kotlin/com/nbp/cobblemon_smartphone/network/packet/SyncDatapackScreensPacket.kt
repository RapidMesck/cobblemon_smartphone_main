package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * S2C packet synchronizing datapack screen definitions (`id` -> raw JSON) on login and reload.
 */
class SyncDatapackScreensPacket(
    val screens: List<Pair<String, String>>
) : CobblemonSmartphoneNetworkPacket<SyncDatapackScreensPacket> {
    companion object {
        val ID = smartphoneResource("sync_datapack_screens")
        private const val MAX_JSON_LEN = 262_144

        fun decode(buffer: RegistryFriendlyByteBuf): SyncDatapackScreensPacket {
            val count = buffer.readVarInt().coerceIn(0, 256)
            val entries = (0 until count).map {
                val id = buffer.readUtf(256)
                val rawJson = buffer.readUtf(MAX_JSON_LEN)
                id to rawJson
            }
            return SyncDatapackScreensPacket(entries)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        val capped = screens.take(256)
        buffer.writeVarInt(capped.size)
        for ((screenId, rawJson) in capped) {
            buffer.writeUtf(screenId.take(256), 256)
            buffer.writeUtf(rawJson.take(MAX_JSON_LEN), MAX_JSON_LEN)
        }
    }
}
