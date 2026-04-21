package com.nbp.cobblemon_smartphone.client

import net.minecraft.network.chat.Component
import net.minecraft.server.packs.PackType

class BuiltinResourcePack(
    val id: String,
    val name: String,
    val packType: PackType,
    val activationBehavior: ResourcePackActivationBehavior,
    val neededMods: Set<String> = emptySet()
) {
    val displayName: Component = Component.literal(name)
}

enum class ResourcePackActivationBehavior {
    NORMAL,
    DEFAULT_ENABLED,
    ALWAYS_ENABLED
}