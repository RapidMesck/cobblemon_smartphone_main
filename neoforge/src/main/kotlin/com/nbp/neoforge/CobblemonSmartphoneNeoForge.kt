package com.nbp.neoforge

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.Implementation
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.registries.RegisterEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(CobblemonSmartphone.ID)
class CobblemonSmartphoneNeoForge : Implementation {
    override val networkManager = CobblemonSmartphoneNeoForgeNetworkManager

    init {
        CobblemonSmartphone.init(this)
        CobblemonSmartphoneNeoForgeClient
        CobblemonSmartphoneNeoForgeTickHandler
        with(MOD_BUS) {
            addListener(networkManager::registerMessages)
        }
    }

    override fun registerItems() {
        with(MOD_BUS) {
            addListener<RegisterEvent> { event ->
                event.register(CobblemonSmartphoneItems.resourceKey) { helper ->
                    CobblemonSmartphoneItems.register { resourceLocation, item -> helper.register(resourceLocation, item) }
                }
            }
            addListener<RegisterEvent> { event ->
                event.register(Registries.CREATIVE_MODE_TAB) { helper ->
                    helper.register(
                        ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), smartphoneResource("com/nbp/cobblemon_smartphone")),
                        CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.cobblemon_smartphone.smartphone_group"))
                            .icon { ItemStack(CobblemonSmartphoneItems.RED_SMARTPHONE) }
                            .displayItems(CobblemonSmartphoneItems::addToGroup)
                            .build()
                    )
                }
            }
        }
    }

    override fun registerCommands() {
    }
}