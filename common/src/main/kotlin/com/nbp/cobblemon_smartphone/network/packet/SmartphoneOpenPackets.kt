package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * Envia do cliente para o servidor informando se a tela do smartphone foi aberta ou fechada,
 * se está em modo horizontal (scanner de pokédex ou câmera fotográfica), modo selfie e modo foto.
 */
class SetSmartphoneOpenStatePacket(
    val isOpen: Boolean,
    val isHorizontal: Boolean = false,
    val isSelfie: Boolean = false,
    val isPhotoMode: Boolean = false
) : CobblemonSmartphoneNetworkPacket<SetSmartphoneOpenStatePacket> {
    companion object {
        val ID = smartphoneResource("set_smartphone_open_state")
        fun decode(buffer: RegistryFriendlyByteBuf): SetSmartphoneOpenStatePacket {
            val isOpen = buffer.readBoolean()
            val isHorizontal = if (buffer.isReadable) buffer.readBoolean() else false
            val isSelfie = if (buffer.isReadable) buffer.readBoolean() else false
            val isPhotoMode = if (buffer.isReadable) buffer.readBoolean() else false
            return SetSmartphoneOpenStatePacket(isOpen, isHorizontal, isSelfie, isPhotoMode)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(isOpen)
        buffer.writeBoolean(isHorizontal)
        buffer.writeBoolean(isSelfie)
        buffer.writeBoolean(isPhotoMode)
    }
}

/**
 * Envia do servidor para os clientes sincronizando o estado de abertura do smartphone de um jogador,
 * orientação horizontal, modo selfie e modo foto.
 */
class SyncSmartphoneOpenStatePacket(
    val playerUuid: UUID,
    val isOpen: Boolean,
    val isHorizontal: Boolean = false,
    val isSelfie: Boolean = false,
    val isPhotoMode: Boolean = false
) : CobblemonSmartphoneNetworkPacket<SyncSmartphoneOpenStatePacket> {
    companion object {
        val ID = smartphoneResource("sync_smartphone_open_state")
        fun decode(buffer: RegistryFriendlyByteBuf): SyncSmartphoneOpenStatePacket {
            val playerUuid = buffer.readUUID()
            val isOpen = buffer.readBoolean()
            val isHorizontal = if (buffer.isReadable) buffer.readBoolean() else false
            val isSelfie = if (buffer.isReadable) buffer.readBoolean() else false
            val isPhotoMode = if (buffer.isReadable) buffer.readBoolean() else false
            return SyncSmartphoneOpenStatePacket(playerUuid, isOpen, isHorizontal, isSelfie, isPhotoMode)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(playerUuid)
        buffer.writeBoolean(isOpen)
        buffer.writeBoolean(isHorizontal)
        buffer.writeBoolean(isSelfie)
        buffer.writeBoolean(isPhotoMode)
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
 * de uma interação social em grupo entre Rotom Phones.
 */
class SyncRotomSocialSessionPacket(
    val sessionId: UUID,
    val participants: List<UUID>,
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
            val sessionId = buffer.readUUID()
            val count = buffer.readVarInt()
            val participants = ArrayList<UUID>(count)
            for (i in 0 until count) {
                participants.add(buffer.readUUID())
            }
            return SyncRotomSocialSessionPacket(
                sessionId = sessionId,
                participants = participants,
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
        buffer.writeUUID(sessionId)
        buffer.writeVarInt(participants.size)
        for (u in participants) {
            buffer.writeUUID(u)
        }
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
