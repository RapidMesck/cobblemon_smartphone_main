package com.nbp.cobblemon_smartphone.upgrade

import net.minecraft.network.chat.Component

data class SmartphoneUpgrade(
    val id: String,
    val nbtKey: String,
    val requiredModId: String? = null,
    val displayName: Component? = null
)
