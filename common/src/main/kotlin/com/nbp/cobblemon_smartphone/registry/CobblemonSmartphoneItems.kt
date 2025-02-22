package com.nbp.cobblemon_smartphone.registry

import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters
import net.minecraft.world.item.CreativeModeTab.Output
import net.minecraft.world.item.Item

object CobblemonSmartphoneItems : RegistryProvider<Registry<Item>, ResourceKey<Registry<Item>>, Item>() {
    override val registry: Registry<Item> = BuiltInRegistries.ITEM
    override val resourceKey: ResourceKey<Registry<Item>> = Registries.ITEM

    val WHITE_SMARTPHONE = smartphoneItem(SmartphoneColor.WHITE)
    val ORANGE_SMARTPHONE = smartphoneItem(SmartphoneColor.ORANGE)
    val MAGENTA_SMARTPHONE = smartphoneItem(SmartphoneColor.MAGENTA)
    val LIGHT_BLUE_SMARTPHONE = smartphoneItem(SmartphoneColor.LIGHT_BLUE)

    val YELLOW_SMARTPHONE = smartphoneItem(SmartphoneColor.YELLOW)
    val LIME_SMARTPHONE = smartphoneItem(SmartphoneColor.LIME)
    val PINK_SMARTPHONE = smartphoneItem(SmartphoneColor.PINK)
    val GRAY_SMARTPHONE = smartphoneItem(SmartphoneColor.GRAY)

    val LIGHT_GRAY_SMARTPHONE = smartphoneItem(SmartphoneColor.LIGHT_GRAY)
    val CYAN_SMARTPHONE = smartphoneItem(SmartphoneColor.CYAN)
    val PURPLE_SMARTPHONE = smartphoneItem(SmartphoneColor.PURPLE)
    val BLUE_SMARTPHONE = smartphoneItem(SmartphoneColor.BLUE)

    val BROWN_SMARTPHONE = smartphoneItem(SmartphoneColor.BROWN)
    val GREEN_SMARTPHONE = smartphoneItem(SmartphoneColor.GREEN)
    val RED_SMARTPHONE = smartphoneItem(SmartphoneColor.RED)
    val BLACK_SMARTPHONE = smartphoneItem(SmartphoneColor.BLACK)

    private fun smartphoneItem(model: SmartphoneColor): Item {
        return add(model.modelName + SmartphoneItem.BASE_REGISTRY_KEY, SmartphoneItem(model))
    }

    fun addToGroup(displayContext: ItemDisplayParameters, entries: Output) {
        entries.accept(WHITE_SMARTPHONE)
        entries.accept(ORANGE_SMARTPHONE)
        entries.accept(MAGENTA_SMARTPHONE)
        entries.accept(LIGHT_BLUE_SMARTPHONE)

        entries.accept(YELLOW_SMARTPHONE)
        entries.accept(LIME_SMARTPHONE)
        entries.accept(PINK_SMARTPHONE)
        entries.accept(GRAY_SMARTPHONE)

        entries.accept(LIGHT_GRAY_SMARTPHONE)
        entries.accept(CYAN_SMARTPHONE)
        entries.accept(PURPLE_SMARTPHONE)
        entries.accept(BLUE_SMARTPHONE)

        entries.accept(BROWN_SMARTPHONE)
        entries.accept(GREEN_SMARTPHONE)
        entries.accept(RED_SMARTPHONE)
        entries.accept(BLACK_SMARTPHONE)
    }
}