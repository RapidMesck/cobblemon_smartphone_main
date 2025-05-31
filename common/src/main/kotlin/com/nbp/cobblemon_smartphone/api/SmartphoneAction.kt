package com.nbp.cobblemon_smartphone.api

import net.minecraft.resources.ResourceLocation

interface SmartphoneAction {
    val id: String
    val texture: ResourceLocation
    val hoverTexture: ResourceLocation
    fun onClick()
    fun isEnabled(): Boolean = true
}