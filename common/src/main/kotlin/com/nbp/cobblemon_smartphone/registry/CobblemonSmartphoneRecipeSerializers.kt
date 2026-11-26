package com.nbp.cobblemon_smartphone.registry

import com.nbp.cobblemon_smartphone.recipe.SmartphoneDyeRecipe
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer

object CobblemonSmartphoneRecipeSerializers :
    RegistryProvider<Registry<RecipeSerializer<*>>, ResourceKey<Registry<RecipeSerializer<*>>>, RecipeSerializer<*>>() {

    override val registry: Registry<RecipeSerializer<*>> = BuiltInRegistries.RECIPE_SERIALIZER
    override val resourceKey: ResourceKey<Registry<RecipeSerializer<*>>> = Registries.RECIPE_SERIALIZER

    val SMARTPHONE_DYEING: RecipeSerializer<SmartphoneDyeRecipe> =
        add("smartphone_dyeing", SimpleCraftingRecipeSerializer(::SmartphoneDyeRecipe))
}
