package com.nbp.cobblemon_smartphone.item

import com.cobblemon.mod.common.client.pokedex.PokedexType
import net.minecraft.resources.ResourceLocation

enum class SmartphoneColor(val modelName: String) {

    WHITE("white"),
    ORANGE("orange"),
    MAGENTA("magenta"),
    LIGHT_BLUE("light_blue"),
    YELLOW("yellow"),
    LIME("lime"),
    PINK("pink"),
    GRAY("gray"),
    LIGHT_GRAY("light_gray"),
    CYAN("cyan"),
    PURPLE("purple"),
    BLUE("blue"),
    BROWN("brown"),
    GREEN("green"),
    RED("red"),
    BLACK("black");

    fun toPokedexType(): PokedexType = when (this) {
        WHITE -> PokedexType.WHITE
        YELLOW -> PokedexType.YELLOW
        PINK -> PokedexType.PINK
        BLUE -> PokedexType.BLUE
        GREEN -> PokedexType.GREEN
        RED -> PokedexType.RED
        BLACK -> PokedexType.BLACK
        else -> PokedexType.RED // fallback for colors without a matching Pokedex
    }

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

    fun getLargeScreenTexture(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/large_smartphone_${modelName}.png"
        )
    }
}
