package com.nbp.cobblemon_smartphone.recipe

import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneRecipeSerializers
import net.minecraft.core.HolderLookup
import net.minecraft.world.item.DyeItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.CustomRecipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.level.Level

/**
 * Allows dyeing any crafted smartphone with a vanilla/modded [DyeItem] to change its color
 * while preserving all item data components (upgrades, battery capacity & charge, Rotom fusion,
 * accessories, skins, storage links, custom name, etc.).
 */
class SmartphoneDyeRecipe internal constructor(
    category: CraftingBookCategory,
    private val smartphoneColorOf: (Item) -> SmartphoneColor?,
    private val targetItemFor: (SmartphoneColor) -> Item
) : CustomRecipe(category) {

    constructor(category: CraftingBookCategory) : this(
        category,
        { (it as? SmartphoneItem)?.getColor() },
        CobblemonSmartphoneItems::getSmartphone
    )

    override fun matches(input: CraftingInput, level: Level): Boolean {
        return findIngredients(input) != null
    }

    override fun assemble(input: CraftingInput, registries: HolderLookup.Provider): ItemStack {
        val (smartphoneStack, targetColor) = findIngredients(input) ?: return ItemStack.EMPTY
        val targetItem = targetItemFor(targetColor)
        return smartphoneStack.transmuteCopy(targetItem, 1)
    }

    override fun canCraftInDimensions(width: Int, height: Int): Boolean = width * height >= 2

    override fun getSerializer(): RecipeSerializer<*> = CobblemonSmartphoneRecipeSerializers.SMARTPHONE_DYEING

    internal fun matchesInput(input: CraftingInput): Boolean = findIngredients(input) != null

    private fun findIngredients(input: CraftingInput): Pair<ItemStack, SmartphoneColor>? {
        var smartphoneStack: ItemStack? = null
        var smartphoneColor: SmartphoneColor? = null
        var dyeItem: DyeItem? = null

        for (slot in 0 until input.size()) {
            val stack = input.getItem(slot)
            if (stack.isEmpty) continue

            val item = stack.item
            val color = smartphoneColorOf(item)
            when {
                color != null -> {
                    if (smartphoneStack != null) return null
                    smartphoneStack = stack
                    smartphoneColor = color
                }
                item is DyeItem -> {
                    if (dyeItem != null) return null
                    dyeItem = item
                }
                else -> return null
            }
        }

        val phone = smartphoneStack ?: return null
        val currentColor = smartphoneColor ?: return null
        val dye = dyeItem ?: return null
        val targetColor = SmartphoneColor.fromDyeColorOrNull(dye.dyeColor) ?: return null
        if (currentColor == targetColor) return null

        return phone to targetColor
    }
}
