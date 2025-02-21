package com.nbp.cobblemon_smartphone

import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

class CobblemonSmartphoneFabric : ModInitializer, Implementation {
    override val networkManager = CobblemonSmartphoneFabricNetworkManager

    override fun onInitialize() {
        CobblemonSmartphone.init(this)
        networkManager.registerMessages()
        networkManager.registerServerHandlers()
    }

    override fun registerItems() {
        CobblemonSmartphoneItems.register { resourceLocation, item -> Registry.register(CobblemonSmartphoneItems.registry, resourceLocation, item) }
        Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            smartphoneResource("cobblemon_smartphone"),
            FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.cobblemon_smartphone.smartphone_group"))
                .icon { ItemStack(CobblemonSmartphoneItems.RED_SMARTPHONE) }
                .displayItems(CobblemonSmartphoneItems::addToGroup)
                .build()
        )
    }

    override fun registerCommands() {
    }
}