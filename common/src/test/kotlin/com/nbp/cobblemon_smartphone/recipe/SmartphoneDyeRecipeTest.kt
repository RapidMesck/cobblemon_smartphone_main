package com.nbp.cobblemon_smartphone.recipe

import com.google.gson.JsonParser
import com.nbp.cobblemon_smartphone.energy.ENERGY_TAG
import com.nbp.cobblemon_smartphone.energy.EXTRA_CAPACITY_TAG
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.skin.getSkinId
import com.nbp.cobblemon_smartphone.skin.setSkinId
import com.nbp.cobblemon_smartphone.upgrade.addExtraEnergyCapacity
import com.nbp.cobblemon_smartphone.upgrade.addUpgrade
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRibbonDyeColor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRotomAccessory
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRotomPhone
import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.DyeItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.CraftingInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SmartphoneDyeRecipeTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }

        // Stand-in registered items for unit tests (Minecraft's item registry is frozen after
        // Bootstrap.bootStrap(), so mod items cannot be constructed in standalone JUnit tests).
        private val colorToMockItem: Map<SmartphoneColor, Item> by lazy {
            mapOf(
                SmartphoneColor.WHITE to Items.WHITE_WOOL,
                SmartphoneColor.ORANGE to Items.ORANGE_WOOL,
                SmartphoneColor.MAGENTA to Items.MAGENTA_WOOL,
                SmartphoneColor.LIGHT_BLUE to Items.LIGHT_BLUE_WOOL,
                SmartphoneColor.YELLOW to Items.YELLOW_WOOL,
                SmartphoneColor.LIME to Items.LIME_WOOL,
                SmartphoneColor.PINK to Items.PINK_WOOL,
                SmartphoneColor.GRAY to Items.GRAY_WOOL,
                SmartphoneColor.LIGHT_GRAY to Items.LIGHT_GRAY_WOOL,
                SmartphoneColor.CYAN to Items.CYAN_WOOL,
                SmartphoneColor.PURPLE to Items.PURPLE_WOOL,
                SmartphoneColor.BLUE to Items.BLUE_WOOL,
                SmartphoneColor.BROWN to Items.BROWN_WOOL,
                SmartphoneColor.GREEN to Items.GREEN_WOOL,
                SmartphoneColor.RED to Items.RED_WOOL,
                SmartphoneColor.BLACK to Items.BLACK_WOOL
            )
        }

        private val mockItemToColor: Map<Item, SmartphoneColor> by lazy {
            colorToMockItem.entries.associate { (color, item) -> item to color }
        }
    }

    private fun testRecipe(): SmartphoneDyeRecipe = SmartphoneDyeRecipe(
        CraftingBookCategory.MISC,
        smartphoneColorOf = { mockItemToColor[it] },
        targetItemFor = { colorToMockItem.getValue(it) }
    )

    private fun craftingInput(vararg stacks: ItemStack): CraftingInput {
        val list = stacks.toList()
        return CraftingInput.of(list.size.coerceAtLeast(1), 1, list.ifEmpty { listOf(ItemStack.EMPTY) })
    }

    @Test
    fun `all 16 dye colors map bijectively to smartphone colors`() {
        assertEquals(DyeColor.entries.size, SmartphoneColor.entries.size)
        val mappedColors = mutableSetOf<SmartphoneColor>()
        for (dyeColor in DyeColor.entries) {
            val phoneColor = SmartphoneColor.fromDyeColorOrNull(dyeColor)
            assertNotNull(phoneColor)
            assertEquals(dyeColor, phoneColor!!.toDyeColor())
            mappedColors.add(phoneColor)
        }
        assertEquals(SmartphoneColor.entries.toSet(), mappedColors)
    }

    @Test
    fun `dyeing smartphone changes color and preserves upgrades, energy, rotom, skin, and custom name`() {
        val recipe = testRecipe()
        val phone = ItemStack(colorToMockItem.getValue(SmartphoneColor.RED))
        phone.addUpgrade("upgrade_crafting")
        phone.addUpgrade("upgrade_gps")
        phone.addUpgrade("upgrade_battery_tier2")
        phone.addExtraEnergyCapacity(2500)
        phone.setRotomPhone(true)
        phone.setRotomAccessory("bow")
        phone.setRibbonDyeColor(DyeColor.PINK)
        phone.setSkinId("custom_skin")
        phone.set(DataComponents.CUSTOM_NAME, Component.literal("My Phone"))
        val existingTag = phone.get(DataComponents.CUSTOM_DATA)!!.copyTag()
        existingTag.putInt(ENERGY_TAG, 4200)
        phone.set(DataComponents.CUSTOM_DATA, CustomData.of(existingTag))

        val input = craftingInput(phone, ItemStack(DyeItem.byColor(DyeColor.CYAN)))
        assertTrue(recipe.matchesInput(input))

        val result = recipe.assemble(input, RegistryAccess.EMPTY)

        assertFalse(result.isEmpty)
        assertEquals(colorToMockItem.getValue(SmartphoneColor.CYAN), result.item)
        assertEquals(1, result.count)

        assertTrue(result.hasUpgrade("upgrade_crafting"))
        assertTrue(result.hasUpgrade("upgrade_gps"))
        assertTrue(result.hasUpgrade("upgrade_battery_tier2"))
        assertEquals("custom_skin", result.getSkinId())
        assertEquals(Component.literal("My Phone"), result.get(DataComponents.CUSTOM_NAME))
        assertNotNull(result.get(DataComponents.DYED_COLOR))

        val resultTag = result.get(DataComponents.CUSTOM_DATA)!!.copyTag()
        assertTrue(resultTag.getBoolean(SmartphoneHelper.ROTOM_PHONE_TAG))
        assertEquals("bow", resultTag.getString(SmartphoneHelper.ROTOM_ACCESSORY_TAG))
        assertEquals(4200, resultTag.getInt(ENERGY_TAG))
        assertEquals(2500, resultTag.getInt(EXTRA_CAPACITY_TAG))
    }

    @Test
    fun `dyeing smartphone with its own color does not match or assemble`() {
        val recipe = testRecipe()
        val phone = ItemStack(colorToMockItem.getValue(SmartphoneColor.BLUE))
        val input = craftingInput(phone, ItemStack(DyeItem.byColor(DyeColor.BLUE)))
        assertFalse(recipe.matchesInput(input))
        assertTrue(recipe.assemble(input, RegistryAccess.EMPTY).isEmpty)
    }

    @Test
    fun `recipe rejects extra items, multiple dyes, multiple phones, or missing ingredients`() {
        val recipe = testRecipe()
        val phone = ItemStack(colorToMockItem.getValue(SmartphoneColor.RED))
        val otherPhone = ItemStack(colorToMockItem.getValue(SmartphoneColor.WHITE))
        val blueDye = ItemStack(DyeItem.byColor(DyeColor.BLUE))
        val greenDye = ItemStack(DyeItem.byColor(DyeColor.GREEN))

        val invalidInputs = listOf(
            craftingInput(phone, blueDye, ItemStack(Items.DIRT)),
            craftingInput(phone, blueDye, greenDye),
            craftingInput(phone, otherPhone, blueDye),
            craftingInput(phone),
            craftingInput(blueDye),
            craftingInput(ItemStack.EMPTY, ItemStack.EMPTY)
        )

        for (input in invalidInputs) {
            assertFalse(recipe.matchesInput(input))
            assertTrue(recipe.assemble(input, RegistryAccess.EMPTY).isEmpty)
        }
    }

    @Test
    fun `can craft in 2x2 inventory grid`() {
        val recipe = testRecipe()
        assertTrue(recipe.canCraftInDimensions(2, 2))
        assertTrue(recipe.canCraftInDimensions(2, 1))
        assertTrue(recipe.canCraftInDimensions(1, 2))
        assertFalse(recipe.canCraftInDimensions(1, 1))
    }

    @Test
    fun `smartphone dyeing recipe json exists and references custom serializer`() {
        val file = Path.of("src/main/resources/data/cobblemon_smartphone/recipe/smartphone_dyeing.json")
        assertTrue(Files.exists(file), "Missing recipe file: $file")
        val json = JsonParser.parseString(Files.readString(file)).asJsonObject
        assertEquals("cobblemon_smartphone:smartphone_dyeing", json.get("type").asString)
        assertEquals("misc", json.get("category").asString)
    }
}
