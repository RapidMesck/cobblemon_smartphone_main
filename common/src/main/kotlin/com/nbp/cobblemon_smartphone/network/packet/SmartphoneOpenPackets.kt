package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * Envia do cliente para o servidor informando se a tela do smartphone foi aberta ou fechada.
 */
class SetSmartphoneOpenStatePacket(val isOpen: Boolean) : CobblemonSmartphoneNetworkPacket<SetSmartphoneOpenStatePacket> {
    companion object {
        val ID = smartphoneResource("set_smartphone_open_state")
        fun decode(buffer: RegistryFriendlyByteBuf): SetSmartphoneOpenStatePacket {
            return SetSmartphoneOpenStatePacket(buffer.readBoolean())
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(isOpen)
    }
}

/**
 * Envia do servidor para os clientes sincronizando o estado de abertura do smartphone de um jogador.
 */
class SyncSmartphoneOpenStatePacket(val playerUuid: UUID, val isOpen: Boolean) : CobblemonSmartphoneNetworkPacket<SyncSmartphoneOpenStatePacket> {
    companion object {
        val ID = smartphoneResource("sync_smartphone_open_state")
        fun decode(buffer: RegistryFriendlyByteBuf): SyncSmartphoneOpenStatePacket {
            return SyncSmartphoneOpenStatePacket(buffer.readUUID(), buffer.readBoolean())
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(playerUuid)
        buffer.writeBoolean(isOpen)
    }
}
