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

    val RED_SMARTPHONE = smartphoneItem(SmartphoneColor.RED)
    val GREEN_SMARTPHONE = smartphoneItem(SmartphoneColor.GREEN)
    val BLUE_SMARTPHONE = smartphoneItem(SmartphoneColor.BLUE)
    val YELLOW_SMARTPHONE = smartphoneItem(SmartphoneColor.YELLOW)
    val PINK_SMARTPHONE = smartphoneItem(SmartphoneColor.PINK)
    val BLACK_SMARTPHONE = smartphoneItem(SmartphoneColor.BLACK)
    val WHITE_SMARTPHONE = smartphoneItem(SmartphoneColor.WHITE)


    private fun smartphoneItem(model: SmartphoneColor): Item {
        return add(model.modelName + SmartphoneItem.BASE_REGISTRY_KEY, SmartphoneItem(model))
    }

    fun addToGroup(displayContext: ItemDisplayParameters, entries: Output) {
        entries.accept(RED_SMARTPHONE)
        entries.accept(GREEN_SMARTPHONE)
        entries.accept(BLUE_SMARTPHONE)
        entries.accept(YELLOW_SMARTPHONE)
        entries.accept(PINK_SMARTPHONE)
        entries.accept(BLACK_SMARTPHONE)
        entries.accept(WHITE_SMARTPHONE)
    }
}