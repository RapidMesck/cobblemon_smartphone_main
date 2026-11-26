package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class InstallUpgradePacket(val upgradeKey: String) : CobblemonSmartphoneNetworkPacket<InstallUpgradePacket> {
    companion object {
        val ID = smartphoneResource("install_upgrade")
        private const val MAX_KEY_LENGTH = 128

        fun decode(buffer: RegistryFriendlyByteBuf): InstallUpgradePacket {
            val upgradeKey = buffer.readUtf(MAX_KEY_LENGTH)
            return InstallUpgradePacket(upgradeKey)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(upgradeKey, MAX_KEY_LENGTH)
    }
}
