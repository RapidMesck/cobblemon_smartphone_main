package com.nbp.cobblemon_smartphone

import com.nbp.cobblemon_smartphone.compat.SmartphoneCompatManager
import com.nbp.cobblemon_smartphone.client.ResourcePackActivationBehavior
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.ResourcePackActivationType
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

class CobblemonSmartphoneFabric : ModInitializer, Implementation {
    override val networkManager = CobblemonSmartphoneFabricNetworkManager

    override fun onInitialize() {
        CobblemonSmartphone.init(this)
        networkManager.registerMessages()
        networkManager.registerServerHandlers()

        val modContainer = FabricLoader.getInstance().getModContainer(CobblemonSmartphone.ID).orElse(null)
        if (modContainer != null) {
            CobblemonSmartphone.builtinPacks
                .filter { it.neededMods.all(FabricLoader.getInstance()::isModLoaded) }
                .forEach {
                    val activationType = when (it.activationBehavior) {
                        ResourcePackActivationBehavior.NORMAL -> ResourcePackActivationType.NORMAL
                        ResourcePackActivationBehavior.DEFAULT_ENABLED -> ResourcePackActivationType.DEFAULT_ENABLED
                        ResourcePackActivationBehavior.ALWAYS_ENABLED -> ResourcePackActivationType.ALWAYS_ENABLED
                    }
                    val id = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, it.id)
                    ResourceManagerHelper.registerBuiltinResourcePack(id, modContainer, activationType)
                }
        }
        
        // Initialize mod compatibility (Trinkets, etc.)
        SmartphoneCompatManager.init()
    }

    override fun registerItems() {
        CobblemonSmartphoneItems.register { resourceLocation, item -> Registry.register(CobblemonSmartphoneItems.registry, resourceLocation, item) }
        Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "cobblemon_smartphone"),
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