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

    /**
     * Number shown as a notification badge on this app's home-screen icon. Return 0 (the default)
     * for no badge.
     *
     * Called on the render thread every frame — keep it a cheap read of already-synced client
     * state, never a network call or file I/O.
     */
    fun badgeCount(): Int = 0
}
