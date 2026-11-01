package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class SyncRotomDialogueClosePacket(
    val playerUuid: UUID
) : CobblemonSmartphoneNetworkPacket<SyncRotomDialogueClosePacket> {
    companion object {
        val ID = smartphoneResource("sync_rotom_dialogue_close")

        fun decode(buffer: RegistryFriendlyByteBuf): SyncRotomDialogueClosePacket {
            val playerUuid = buffer.readUUID()
            return SyncRotomDialogueClosePacket(playerUuid)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(playerUuid)
    }
}
