package com.nbp.cobblemon_smartphone.item

import com.cobblemon.mod.common.client.pokedex.PokedexType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.DyeColor

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

    fun toDyeColor(): DyeColor = when (this) {
        WHITE -> DyeColor.WHITE
        ORANGE -> DyeColor.ORANGE
        MAGENTA -> DyeColor.MAGENTA
        LIGHT_BLUE -> DyeColor.LIGHT_BLUE
        YELLOW -> DyeColor.YELLOW
        LIME -> DyeColor.LIME
        PINK -> DyeColor.PINK
        GRAY -> DyeColor.GRAY
        LIGHT_GRAY -> DyeColor.LIGHT_GRAY
        CYAN -> DyeColor.CYAN
        PURPLE -> DyeColor.PURPLE
        BLUE -> DyeColor.BLUE
        BROWN -> DyeColor.BROWN
        GREEN -> DyeColor.GREEN
        RED -> DyeColor.RED
        BLACK -> DyeColor.BLACK
    }

    companion object {
        private val byModelName: Map<String, SmartphoneColor> = entries.associateBy { it.modelName }

        /**
         * Maps a [DyeColor] to its matching [SmartphoneColor], or returns `null` for non-vanilla
         * colors injected by enum-extending mods such as Dye Depot.
         */
        fun fromDyeColorOrNull(dyeColor: DyeColor): SmartphoneColor? = byModelName[dyeColor.serializedName]
    }

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

    // 3D Rotom
    fun getRotomModelPath(): ResourceLocation {
        // Ex.: cobblemon_smartphone:item/rotom_black
        return ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "item/rotom_${modelName}")
    }

    fun getSmallScreenTexture(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/smartphone_${modelName}.png"
        )
    }

    fun getLargeScreenTexture(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/large_smartphone_${modelName}.png"
        )
    }
}
