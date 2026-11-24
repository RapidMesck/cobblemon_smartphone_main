package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * C2S packet requesting execution of a named action declared inside a datapack screen definition.
 */
class ExecuteDatapackScreenActionPacket(
    val screenId: String,
    val actionId: String,
    val inputs: Map<String, String> = emptyMap(),
    val itemContext: Map<String, String> = emptyMap()
) : CobblemonSmartphoneNetworkPacket<ExecuteDatapackScreenActionPacket> {
    companion object {
        val ID = smartphoneResource("execute_datapack_screen_action")

        fun decode(buffer: RegistryFriendlyByteBuf): ExecuteDatapackScreenActionPacket {
            val screenId = buffer.readUtf(256)
            val actionId = buffer.readUtf(128)
            val inputCount = buffer.readVarInt().coerceIn(0, 64)
            val inputs = LinkedHashMap<String, String>(inputCount)
            repeat(inputCount) {
                inputs[buffer.readUtf(128)] = buffer.readUtf(512)
            }
            val itemCount = buffer.readVarInt().coerceIn(0, 64)
            val itemContext = LinkedHashMap<String, String>(itemCount)
            repeat(itemCount) {
                itemContext[buffer.readUtf(128)] = buffer.readUtf(512)
            }
            return ExecuteDatapackScreenActionPacket(screenId, actionId, inputs, itemContext)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(screenId.take(256), 256)
        buffer.writeUtf(actionId.take(128), 128)
        val cappedInputs = inputs.entries.take(64)
        buffer.writeVarInt(cappedInputs.size)
        for ((k, v) in cappedInputs) {
            buffer.writeUtf(k.take(128), 128)
            buffer.writeUtf(v.take(512), 512)
        }
        val cappedItems = itemContext.entries.take(64)
        buffer.writeVarInt(cappedItems.size)
        for ((k, v) in cappedItems) {
            buffer.writeUtf(k.take(128), 128)
            buffer.writeUtf(v.take(512), 512)
        }
    }
}
