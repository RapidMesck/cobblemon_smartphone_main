package com.nbp.cobblemon_smartphone.registry

import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.item.BatteryUpgradeItem
import com.nbp.cobblemon_smartphone.item.SdCardItem
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgradeRegistry
import com.nbp.cobblemon_smartphone.upgrade.createSdCardStack
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters
import net.minecraft.world.item.CreativeModeTab.Output
import net.minecraft.world.item.DyeColor
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

    val ROTOM_RIBBON = add("rotom_ribbon", com.nbp.cobblemon_smartphone.item.RotomAccessoryItem("bow"))
    val ROTOM_BOWTIE = add("rotom_bowtie", com.nbp.cobblemon_smartphone.item.RotomAccessoryItem("tie"))
    val ROTOM_ACCESSORY_REMOVER = add("rotom_accessory_remover", com.nbp.cobblemon_smartphone.item.RotomAccessoryItem("remove", Item.Properties().stacksTo(1)))
    val SD_CARD = add("sd_card", SdCardItem())
    val BATTERY_TIER1 = add("battery_tier1", BatteryUpgradeItem(1, "upgrade_battery_tier1", 1000))
    val BATTERY_TIER2 = add("battery_tier2", BatteryUpgradeItem(2, "upgrade_battery_tier2", 5000))
    val BATTERY_TIER3 = add("battery_tier3", BatteryUpgradeItem(3, "upgrade_battery_tier3", 15000))

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

        for (color in DyeColor.entries) {
            entries.accept(SmartphoneHelper.createColoredRibbon(color))
        }
        for (color in DyeColor.entries) {
            entries.accept(SmartphoneHelper.createColoredBowtie(color))
        }
        entries.accept(ROTOM_ACCESSORY_REMOVER)
        entries.accept(BATTERY_TIER1)
        entries.accept(BATTERY_TIER2)
        entries.accept(BATTERY_TIER3)

        for (upgrade in SmartphoneUpgradeRegistry.getAppUpgrades()) {
            val reqMod = upgrade.requiredModId
            if (reqMod == null || isModLoaded(reqMod)) {
                entries.accept(createSdCardStack(upgrade.nbtKey))
            }
        }
    }
}