package com.nbp.cobblemon_smartphone.compat.jei

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.cobblemon_smartphone.upgrade.getCustomBatteryUpgradeKey
import com.nbp.cobblemon_smartphone.upgrade.getSdCardUpgrade
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getAccessoryDyeColor
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.constants.RecipeTypes
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter
import mezz.jei.api.ingredients.subtypes.UidContext
import mezz.jei.api.registration.IRecipeRegistration
import mezz.jei.api.registration.ISubtypeRegistration
import net.minecraft.core.NonNullList
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.DyeItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.ShapelessRecipe

@JeiPlugin
class SmartphoneJeiPlugin : IModPlugin {

    override fun getPluginUid(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "jei_plugin")

    override fun registerItemSubtypes(registration: ISubtypeRegistration) {
        registration.registerSubtypeInterpreter(
            CobblemonSmartphoneItems.SD_CARD,
            object : ISubtypeInterpreter<ItemStack> {
                override fun getSubtypeData(ingredient: ItemStack, context: UidContext): Any? =
                    ingredient.getSdCardUpgrade()

                @Deprecated("Deprecated in JEI API")
                override fun getLegacyStringSubtypeInfo(ingredient: ItemStack, context: UidContext): String =
                    ingredient.getSdCardUpgrade() ?: ""
            }
        )

        registration.registerSubtypeInterpreter(
            CobblemonSmartphoneItems.ROTOM_RIBBON,
            object : ISubtypeInterpreter<ItemStack> {
                override fun getSubtypeData(ingredient: ItemStack, context: UidContext): Any =
                    ingredient.getAccessoryDyeColor(DyeColor.PINK).serializedName

                @Deprecated("Deprecated in JEI API")
                override fun getLegacyStringSubtypeInfo(ingredient: ItemStack, context: UidContext): String =
                    ingredient.getAccessoryDyeColor(DyeColor.PINK).serializedName
            }
        )

        registration.registerSubtypeInterpreter(
            CobblemonSmartphoneItems.ROTOM_BOWTIE,
            object : ISubtypeInterpreter<ItemStack> {
                override fun getSubtypeData(ingredient: ItemStack, context: UidContext): Any =
                    ingredient.getAccessoryDyeColor(DyeColor.LIGHT_BLUE).serializedName

                @Deprecated("Deprecated in JEI API")
                override fun getLegacyStringSubtypeInfo(ingredient: ItemStack, context: UidContext): String =
                    ingredient.getAccessoryDyeColor(DyeColor.LIGHT_BLUE).serializedName
            }
        )

        val batteryInterpreter = object : ISubtypeInterpreter<ItemStack> {
            override fun getSubtypeData(ingredient: ItemStack, context: UidContext): Any? =
                ingredient.getCustomBatteryUpgradeKey()

            @Deprecated("Deprecated in JEI API")
            override fun getLegacyStringSubtypeInfo(ingredient: ItemStack, context: UidContext): String =
                ingredient.getCustomBatteryUpgradeKey() ?: ""
        }
        registration.registerSubtypeInterpreter(CobblemonSmartphoneItems.BATTERY_TIER1, batteryInterpreter)
        registration.registerSubtypeInterpreter(CobblemonSmartphoneItems.BATTERY_TIER2, batteryInterpreter)
        registration.registerSubtypeInterpreter(CobblemonSmartphoneItems.BATTERY_TIER3, batteryInterpreter)
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        val group = "${CobblemonSmartphone.ID}.smartphone_dyeing"
        val dyeRecipes: List<RecipeHolder<CraftingRecipe>> = SmartphoneColor.entries.map { targetColor ->
            val targetPhone = CobblemonSmartphoneItems.getSmartphone(targetColor)
            val otherPhones = SmartphoneColor.entries
                .filter { it != targetColor }
                .map { ItemStack(CobblemonSmartphoneItems.getSmartphone(it)) }
            val ingredients = NonNullList.of(
                Ingredient.EMPTY,
                Ingredient.of(otherPhones.stream()),
                Ingredient.of(DyeItem.byColor(targetColor.toDyeColor()))
            )
            val recipe = ShapelessRecipe(
                group,
                CraftingBookCategory.MISC,
                ItemStack(targetPhone),
                ingredients
            )
            val id = ResourceLocation.fromNamespaceAndPath(
                CobblemonSmartphone.ID,
                "smartphone_dyeing_${targetColor.modelName}"
            )
            RecipeHolder(id, recipe)
        }
        registration.addRecipes(RecipeTypes.CRAFTING, dyeRecipes)
    }
}
