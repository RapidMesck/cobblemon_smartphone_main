package com.nbp.cobblemon_smartphone.api

import net.minecraft.resources.ResourceLocation
import net.minecraft.network.chat.Component

interface SmartphoneAction {
    val id: String
    val texture: ResourceLocation
    val hoverTexture: ResourceLocation
    val displayName: Component
        get() = Component.translatable(
            "cobblemon_smartphone.action.${id.substringAfter(':').replace('/', '.')}"
        )
    fun onClick()
    fun isEnabled(): Boolean = true
}
