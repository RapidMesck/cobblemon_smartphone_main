package com.nbp.cobblemon_smartphone.social

import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import net.minecraft.server.MinecraftServer
import java.util.Base64
import java.util.UUID

/**
 * Extracts the skin texture URL from a player's [GameProfile] so the client can resolve
 * the skin even when the player is offline.
 */
object SkinUrlResolver {

    fun resolve(server: MinecraftServer, uuid: UUID): String? {
        val onlinePlayer = server.playerList.getPlayer(uuid)
        if (onlinePlayer != null) return extractSkinUrl(onlinePlayer.gameProfile)
        val profile = server.profileCache?.get(uuid)?.orElse(null)
        if (profile != null) return extractSkinUrl(profile)
        return null
    }

    fun extractSkinUrl(profile: GameProfile): String? {
        val texturesProperty = profile.properties.get("textures")?.firstOrNull() ?: return null
        return try {
            val decoded = String(Base64.getDecoder().decode(texturesProperty.value))
            val json = JsonParser.parseString(decoded).asJsonObject
            json.getAsJsonObject("textures")
                ?.getAsJsonObject("SKIN")
                ?.get("url")?.asString
        } catch (_: Exception) {
            null
        }
    }
}
