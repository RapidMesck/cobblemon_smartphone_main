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

/**
 * Sincroniza do servidor para os clientes se um jogador possui um Rotom Phone (mesmo no inventário),
 * incluindo a cor do aparelho, acessório ("bow", "tie" ou "") e cor do laço.
 */
class SyncRotomEquipStatePacket(
    val playerUuid: UUID,
    val hasRotom: Boolean,
    val phoneColor: String,
    val accessory: String,
    val ribbonColor: String
) : CobblemonSmartphoneNetworkPacket<SyncRotomEquipStatePacket> {
    companion object {
        val ID = smartphoneResource("sync_rotom_equip_state")
        fun decode(buffer: RegistryFriendlyByteBuf): SyncRotomEquipStatePacket {
            return SyncRotomEquipStatePacket(
                playerUuid = buffer.readUUID(),
                hasRotom = buffer.readBoolean(),
                phoneColor = buffer.readUtf(),
                accessory = buffer.readUtf(),
                ribbonColor = buffer.readUtf()
            )
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(playerUuid)
        buffer.writeBoolean(hasRotom)
        buffer.writeUtf(phoneColor)
        buffer.writeUtf(accessory)
        buffer.writeUtf(ribbonColor)
    }
}

/**
 * Sincroniza do servidor para os clientes o ponto âncora compartilhado e o início/fim
 * de uma interação social entre dois Rotom Phones.
 */
class SyncRotomSocialSessionPacket(
    val player1Uuid: UUID,
    val player2Uuid: UUID,
    val active: Boolean,
    val anchorX: Double,
    val anchorY: Double,
    val anchorZ: Double,
    val dirX: Double,
    val dirZ: Double,
    val patternSeed: Int,
    val durationTicks: Int
) : CobblemonSmartphoneNetworkPacket<SyncRotomSocialSessionPacket> {
    companion object {
        val ID = smartphoneResource("sync_rotom_social_session")
        fun decode(buffer: RegistryFriendlyByteBuf): SyncRotomSocialSessionPacket {
            return SyncRotomSocialSessionPacket(
                player1Uuid = buffer.readUUID(),
                player2Uuid = buffer.readUUID(),
                active = buffer.readBoolean(),
                anchorX = buffer.readDouble(),
                anchorY = buffer.readDouble(),
                anchorZ = buffer.readDouble(),
                dirX = buffer.readDouble(),
                dirZ = buffer.readDouble(),
                patternSeed = buffer.readInt(),
                durationTicks = buffer.readInt()
            )
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(player1Uuid)
        buffer.writeUUID(player2Uuid)
        buffer.writeBoolean(active)
        buffer.writeDouble(anchorX)
        buffer.writeDouble(anchorY)
        buffer.writeDouble(anchorZ)
        buffer.writeDouble(dirX)
        buffer.writeDouble(dirZ)
        buffer.writeInt(patternSeed)
        buffer.writeInt(durationTicks)
    }
}
