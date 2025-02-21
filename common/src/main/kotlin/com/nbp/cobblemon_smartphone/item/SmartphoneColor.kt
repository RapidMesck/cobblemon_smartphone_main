package com.nbp.cobblemon_smartphone.item

import net.minecraft.resources.ResourceLocation

enum class SmartphoneColor(val modelName: String) {
    RED("red"),
    YELLOW("yellow"),
    GREEN("green"),
    BLUE("blue"),
    PINK("pink"),
    BLACK("black"),
    WHITE("white");

    // 2D (inventário)
    fun getInventoryModelPath(): ResourceLocation {
        // Ex.: cobblemon_smartphone:item/black_smartphone
        return ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "item/${modelName}_smartphone")
    }

    // 3D (quando na mão)
    fun getHandModelPath(): ResourceLocation {
        // Ex.: cobblemon_smartphone:item/black_smartphone_3d
        return ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "item/${modelName}_smartphone_3d")
    }
}