package com.nbp.neoforge

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.Implementation
import com.nbp.cobblemon_smartphone.client.ResourcePackActivationBehavior
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.neoforge.compat.SmartphoneCompatManager
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.BuiltInPackSource
import net.minecraft.server.packs.repository.KnownPack
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.repository.Pack.Position
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.ModList
import net.neoforged.neoforge.event.AddPackFindersEvent
import net.neoforged.neoforge.registries.RegisterEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import java.util.Optional

@Mod(CobblemonSmartphone.ID)
class CobblemonSmartphoneNeoForge : Implementation {
    override val networkManager = CobblemonSmartphoneNeoForgeNetworkManager

    init {
        CobblemonSmartphone.init(this)
        with(MOD_BUS) {
            addListener(networkManager::registerMessages)
            addListener(::onCommonSetup)
            addListener(::onAddPackFinders)
        }
    }
    
    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork {
            // Initialize optional mod compatibility (Curios)
            SmartphoneCompatManager.init()
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
                        ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "cobblemon_smartphone")),
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

    private fun onAddPackFinders(event: AddPackFindersEvent) {
        val modFile = ModList.get().getModContainerById(CobblemonSmartphone.ID).orElse(null)?.modInfo ?: return

        CobblemonSmartphone.builtinPacks
            .filter { it.packType == event.packType }
            .filter { it.neededMods.all(ModList.get()::isLoaded) }
            .forEach {
                val subPath = if (it.packType == net.minecraft.server.packs.PackType.CLIENT_RESOURCES) "resourcepacks" else "datapacks"
                val packLocation = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "$subPath/${it.id}")
                val resourcePath = modFile.owningFile.file.findResource(packLocation.path)

                val required = it.activationBehavior == ResourcePackActivationBehavior.ALWAYS_ENABLED
                val pack = Pack.readMetaAndCreate(
                    PackLocationInfo(
                        "mod/$packLocation",
                        it.displayName,
                        PackSource.BUILT_IN,
                        Optional.of(KnownPack("neoforge", "mod/$packLocation", modFile.version.toString()))
                    ),
                    BuiltInPackSource.fromName { packId -> PathPackResources(packId, resourcePath) },
                    it.packType,
                    PackSelectionConfig(required, Position.TOP, false)
                )

                if (pack == null) {
                    CobblemonSmartphone.LOGGER.error("Failed to register built-in pack {}", it.id)
                    return@forEach
                }

                event.addRepositorySource { consumer -> consumer.accept(pack) }
            }
    }
}