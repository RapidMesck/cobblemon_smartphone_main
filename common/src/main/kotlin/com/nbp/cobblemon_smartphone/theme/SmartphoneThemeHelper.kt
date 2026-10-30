package com.nbp.cobblemon_smartphone.theme

import com.nbp.cobblemon_smartphone.util.PreferencesSaver
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

fun ItemStack.getTheme(): String? {
    val customData = this.get(DataComponents.CUSTOM_DATA) ?: return null
    val tag = customData.copyTag()
    val theme = tag.getString(SmartphoneThemeHelper.THEME_TAG).takeIf { it.isNotBlank() }
    if (theme != null) return theme
    val wallpaper = tag.getString(SmartphoneThemeHelper.WALLPAPER_TAG).takeIf { it.isNotBlank() }
    if (wallpaper != null) return wallpaper
    return null
}

fun ItemStack.setTheme(themeId: String) {
    val customData = this.get(DataComponents.CUSTOM_DATA)
    val tag = customData?.copyTag() ?: CompoundTag()
    tag.putString(SmartphoneThemeHelper.THEME_TAG, themeId)
    // Also store wallpaper tag for backward compatibility
    tag.putString(SmartphoneThemeHelper.WALLPAPER_TAG, themeId)
    this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
}

object SmartphoneThemeHelper {
    const val THEME_TAG = "cobblemon_smartphone:theme"
    const val WALLPAPER_TAG = "cobblemon_smartphone:wallpaper"

    @Volatile
    var clientPreferredTheme: String? = null

    fun resolveCurrentTheme(smartphoneStack: ItemStack?): SmartphoneTheme {
        if (smartphoneStack != null && !smartphoneStack.isEmpty) {
            val itemTheme = smartphoneStack.getTheme()
            if (!itemTheme.isNullOrBlank()) {
                return SmartphoneThemeRegistry.getTheme(itemTheme)
            }
        }

        val player = try { Minecraft.getInstance()?.player } catch (_: Exception) { null }
        if (player != null) {
            // Also check any smartphone item on the player
            val playerPhone = SmartphoneHelper.getSmartphone(player)
            if (playerPhone != null && !playerPhone.isEmpty) {
                val phoneTheme = playerPhone.getTheme()
                if (!phoneTheme.isNullOrBlank()) {
                    return SmartphoneThemeRegistry.getTheme(phoneTheme)
                }
            }

            if (player is PreferencesSaver) {
                val prefs = player.`cobblemonsmartphone$getSavedPreferences`()
                if (prefs != null) {
                    val saved = prefs.getString(PreferencesSaver.THEME_KEY).takeIf { it.isNotBlank() }
                        ?: prefs.getString(PreferencesSaver.WALLPAPER_KEY).takeIf { it.isNotBlank() }
                    if (saved != null) {
                        return SmartphoneThemeRegistry.getTheme(saved)
                    }
                }
            }
        }

        val preferred = clientPreferredTheme
        if (!preferred.isNullOrBlank()) {
            return SmartphoneThemeRegistry.getTheme(preferred)
        }

        return SmartphoneTheme.DEFAULT
    }
}
