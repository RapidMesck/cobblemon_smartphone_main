package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.SyncThemePacket
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeHelper
import com.nbp.cobblemon_smartphone.theme.setTheme
import com.nbp.cobblemon_smartphone.util.PreferencesSaver
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft

object SyncThemeHandler : ClientNetworkPacketHandler<SyncThemePacket> {
    override fun handle(packet: SyncThemePacket, client: Minecraft) {
        SmartphoneThemeHelper.clientPreferredTheme = packet.themeId

        val player = client.player
        if (player != null) {
            if (player is PreferencesSaver) {
                val prefs = player.`cobblemonsmartphone$getSavedPreferences`()
                if (prefs != null) {
                    prefs.putString(PreferencesSaver.THEME_KEY, packet.themeId)
                    prefs.putString(PreferencesSaver.WALLPAPER_KEY, packet.themeId)
                }
            }

            // Also update any smartphone in player possession locally
            SmartphoneHelper.getSmartphone(player)?.setTheme(packet.themeId)
        }
    }
}
