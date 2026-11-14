package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.skin.SmartphoneSkin
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class SyncSmartphoneSkinsPacket(val skins: List<SmartphoneSkin>) : CobblemonSmartphoneNetworkPacket<SyncSmartphoneSkinsPacket> {
    companion object {
        val ID = smartphoneResource("sync_smartphone_skins")
        private const val MAX_SKINS = 256
        private const val MAX_STR = 256

        fun decode(buffer: RegistryFriendlyByteBuf): SyncSmartphoneSkinsPacket {
            val count = buffer.readVarInt().coerceIn(0, MAX_SKINS)
            val list = ArrayList<SmartphoneSkin>(count)
            for (i in 0 until count) {
                val id = buffer.readUtf(MAX_STR)
                val nameRaw = buffer.readUtf(MAX_STR)
                val name = if (nameRaw.contains('.')) Component.translatable(nameRaw) else Component.literal(nameRaw)
                val handStr = buffer.readUtf(MAX_STR)
                val rotomStr = buffer.readUtf(MAX_STR)
                val invStr = buffer.readUtf(MAX_STR)
                val frameStr = buffer.readUtf(MAX_STR)
                val largeFrameStr = buffer.readUtf(MAX_STR)
                val colorCount = buffer.readVarInt().coerceIn(0, 64)
                val colors = LinkedHashSet<String>()
                for (c in 0 until colorCount) {
                    colors.add(buffer.readUtf(64))
                }
                val locked = buffer.readBoolean()
                val reqUpgrade = buffer.readUtf(MAX_STR).takeIf { it.isNotEmpty() }
                list.add(
                    SmartphoneSkin(
                        id = id,
                        name = name,
                        handModel = handStr.takeIf { it.isNotEmpty() }?.let(ResourceLocation::tryParse),
                        rotomModel = rotomStr.takeIf { it.isNotEmpty() }?.let(ResourceLocation::tryParse),
                        inventoryModel = invStr.takeIf { it.isNotEmpty() }?.let(ResourceLocation::tryParse),
                        frameTexture = frameStr.takeIf { it.isNotEmpty() }?.let(ResourceLocation::tryParse),
                        largeFrameTexture = largeFrameStr.takeIf { it.isNotEmpty() }?.let(ResourceLocation::tryParse),
                        allowedColors = colors,
                        locked = locked,
                        requiredUpgrade = reqUpgrade
                    )
                )
            }
            return SyncSmartphoneSkinsPacket(list)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        val capped = skins.take(MAX_SKINS)
        buffer.writeVarInt(capped.size)
        for (skin in capped) {
            buffer.writeUtf(skin.id, MAX_STR)
            buffer.writeUtf(skin.name.string, MAX_STR)
            buffer.writeUtf(skin.handModel?.toString() ?: "", MAX_STR)
            buffer.writeUtf(skin.rotomModel?.toString() ?: "", MAX_STR)
            buffer.writeUtf(skin.inventoryModel?.toString() ?: "", MAX_STR)
            buffer.writeUtf(skin.frameTexture?.toString() ?: "", MAX_STR)
            buffer.writeUtf(skin.largeFrameTexture?.toString() ?: "", MAX_STR)
            val colors = skin.allowedColors.take(64)
            buffer.writeVarInt(colors.size)
            for (c in colors) {
                buffer.writeUtf(c, 64)
            }
            buffer.writeBoolean(skin.locked)
            buffer.writeUtf(skin.requiredUpgrade ?: "", MAX_STR)
        }
    }
}
