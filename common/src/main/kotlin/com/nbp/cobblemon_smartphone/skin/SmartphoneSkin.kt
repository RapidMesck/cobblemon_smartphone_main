package com.nbp.cobblemon_smartphone.skin

import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Defines a 3D skin/case ("capinha") for the Smartphone and/or Rotom Phone.
 * Can be provided by a Resource Pack (`assets/<ns>/smartphone_skins/<id>.json`)
 * or a Data Pack (`data/<ns>/smartphone_skins/<id>.json`).
 */
data class SmartphoneSkin(
    val id: String,
    val name: Component,
    val handModel: ResourceLocation? = null,
    val rotomModel: ResourceLocation? = null,
    val inventoryModel: ResourceLocation? = null,
    val frameTexture: ResourceLocation? = null,
    val largeFrameTexture: ResourceLocation? = null,
    val allowedColors: Set<String> = emptySet(),
    val locked: Boolean = false,
    val requiredUpgrade: String? = null
) {
    fun supportsColor(color: SmartphoneColor): Boolean {
        return allowedColors.isEmpty() || "*" in allowedColors || color.modelName.lowercase() in allowedColors
    }

    companion object {
        const val DEFAULT_ID = "cobblemon_smartphone:default"

        val DEFAULT = SmartphoneSkin(
            id = DEFAULT_ID,
            name = Component.translatable("cobblemon_smartphone.skin.default"),
            handModel = null,
            rotomModel = null,
            inventoryModel = null,
            allowedColors = emptySet()
        )
    }
}
