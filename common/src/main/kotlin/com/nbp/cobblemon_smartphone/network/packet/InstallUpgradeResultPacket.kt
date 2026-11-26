package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class InstallUpgradeResultPacket(
    val upgradeKey: String,
    val success: Boolean,
    val messageKey: String,
    val addedCapacity: Int = 0
) : CobblemonSmartphoneNetworkPacket<InstallUpgradeResultPacket> {
    companion object {
        val ID = smartphoneResource("install_upgrade_result")
        private const val MAX_STRING_LENGTH = 128

        fun decode(buffer: RegistryFriendlyByteBuf): InstallUpgradeResultPacket {
            val upgradeKey = buffer.readUtf(MAX_STRING_LENGTH)
            val success = buffer.readBoolean()
            val messageKey = buffer.readUtf(MAX_STRING_LENGTH)
            val addedCapacity = buffer.readVarInt()
            return InstallUpgradeResultPacket(upgradeKey, success, messageKey, addedCapacity)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(upgradeKey, MAX_STRING_LENGTH)
        buffer.writeBoolean(success)
        buffer.writeUtf(messageKey, MAX_STRING_LENGTH)
        buffer.writeVarInt(addedCapacity)
    }
}
