package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * S2C packet delivering freshly evaluated server placeholders and `for_each` list items for a datapack screen.
 */
class SyncDatapackScreenDataPacket(
    val screenId: String,
    val placeholders: Map<String, String>,
    val lists: Map<String, List<Map<String, String>>>
) : CobblemonSmartphoneNetworkPacket<SyncDatapackScreenDataPacket> {
    companion object {
        val ID = smartphoneResource("sync_datapack_screen_data")

        fun decode(buffer: RegistryFriendlyByteBuf): SyncDatapackScreenDataPacket {
            val screenId = buffer.readUtf(256)
            val placeholderCount = buffer.readVarInt().coerceIn(0, 256)
            val placeholders = LinkedHashMap<String, String>(placeholderCount)
            repeat(placeholderCount) {
                placeholders[buffer.readUtf(128)] = buffer.readUtf(1024)
            }
            val listCount = buffer.readVarInt().coerceIn(0, 64)
            val lists = LinkedHashMap<String, List<Map<String, String>>>(listCount)
            repeat(listCount) {
                val listKey = buffer.readUtf(128)
                val rowCount = buffer.readVarInt().coerceIn(0, 128)
                val rows = (0 until rowCount).map {
                    val colCount = buffer.readVarInt().coerceIn(0, 64)
                    val row = LinkedHashMap<String, String>(colCount)
                    repeat(colCount) {
                        row[buffer.readUtf(128)] = buffer.readUtf(512)
                    }
                    row
                }
                lists[listKey] = rows
            }
            return SyncDatapackScreenDataPacket(screenId, placeholders, lists)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(screenId.take(256), 256)
        buffer.writeVarInt(placeholders.size)
        for ((k, v) in placeholders) {
            buffer.writeUtf(k.take(128), 128)
            buffer.writeUtf(v.take(1024), 1024)
        }
        buffer.writeVarInt(lists.size)
        for ((listKey, rows) in lists) {
            buffer.writeUtf(listKey.take(128), 128)
            buffer.writeVarInt(rows.size)
            for (row in rows) {
                buffer.writeVarInt(row.size)
                for ((colKey, colVal) in row) {
                    buffer.writeUtf(colKey.take(128), 128)
                    buffer.writeUtf(colVal.take(512), 512)
                }
            }
        }
    }
}
