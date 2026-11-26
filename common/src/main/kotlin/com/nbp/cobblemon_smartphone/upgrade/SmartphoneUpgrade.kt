package com.nbp.cobblemon_smartphone.upgrade

import net.minecraft.network.chat.Component

data class SmartphoneUpgrade(
    val id: String,
    val nbtKey: String,
    val requiredModId: String? = null,
    val displayName: Component? = null,
    val isAppUpgrade: Boolean = true,
    val actionId: String? = null,
    val accentColor: Int = 0x4FC3F7
)
