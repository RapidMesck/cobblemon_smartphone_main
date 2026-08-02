package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.client.social.SocialClientSession
import com.nbp.cobblemon_smartphone.compat.voicechat.VoiceChatBridge
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SocialCapabilitiesPacket(
    val capabilities: SocialClientSession.Capabilities
) : CobblemonSmartphoneNetworkPacket<SocialCapabilitiesPacket> {
    companion object {
        val ID = smartphoneResource("social_capabilities")

        fun decode(buffer: RegistryFriendlyByteBuf) = SocialCapabilitiesPacket(
            SocialClientSession.Capabilities(
                enabled = buffer.readBoolean(),
                callsEnabled = buffer.readBoolean(),
                maxPostLength = buffer.readVarInt(),
                maxMessageLength = buffer.readVarInt(),
                postCooldownSeconds = buffer.readVarInt(),
                messageCooldownSeconds = buffer.readVarInt(),
                threadPageSize = buffer.readVarInt()
            )
        )

        fun fromServerConfig(): SocialCapabilitiesPacket {
            val config = CobblemonSmartphone.config
            return SocialCapabilitiesPacket(
                SocialClientSession.Capabilities(
                    enabled = config.features.enableSocial,
                    callsEnabled = config.features.enableSocial && config.features.enableCalls && VoiceChatBridge.isAvailable(),
                    maxPostLength = config.social.maxPostLength.coerceIn(1, 4_096),
                    maxMessageLength = config.social.maxMessageLength.coerceIn(1, 4_096),
                    postCooldownSeconds = config.cooldowns.socialPost.coerceAtLeast(0),
                    messageCooldownSeconds = config.cooldowns.socialMessage.coerceAtLeast(0),
                    threadPageSize = config.social.threadPageSize.coerceIn(1, 100)
                )
            )
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        with(capabilities) {
            buffer.writeBoolean(enabled)
            buffer.writeBoolean(callsEnabled)
            buffer.writeVarInt(maxPostLength)
            buffer.writeVarInt(maxMessageLength)
            buffer.writeVarInt(postCooldownSeconds)
            buffer.writeVarInt(messageCooldownSeconds)
            buffer.writeVarInt(threadPageSize)
        }
    }
}
