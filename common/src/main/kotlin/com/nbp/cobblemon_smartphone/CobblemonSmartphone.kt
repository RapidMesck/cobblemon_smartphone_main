package com.nbp.cobblemon_smartphone

import com.nbp.cobblemon_smartphone.actions.CraftingAction
import com.nbp.cobblemon_smartphone.actions.EnderAction
import com.nbp.cobblemon_smartphone.actions.GpsAction
import com.nbp.cobblemon_smartphone.actions.HealAction
import com.nbp.cobblemon_smartphone.actions.OpenCobblenavAction
import com.nbp.cobblemon_smartphone.actions.OpenCobblenavFishingnavAction
import com.nbp.cobblemon_smartphone.actions.OpenCobbledollarsAction
import com.nbp.cobblemon_smartphone.actions.OpenAE2Action
import com.nbp.cobblemon_smartphone.actions.OpenAE2CraftingAction
import com.nbp.cobblemon_smartphone.actions.OpenPcAction
import com.nbp.cobblemon_smartphone.actions.OpenRctTrainerCardAction
import com.nbp.cobblemon_smartphone.actions.OpenRefinedStorageAction
import com.nbp.cobblemon_smartphone.actions.OpenTomsStorageAction
import com.nbp.cobblemon_smartphone.actions.OpenWaystonesAction
import com.nbp.cobblemon_smartphone.actions.PokeInfoAction
import com.nbp.cobblemon_smartphone.actions.PokedexAction
import com.nbp.cobblemon_smartphone.actions.SocialAction
import com.nbp.cobblemon_smartphone.actions.StructureCompassAction
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.api.SmartphoneStorageLinkRegistry
import com.nbp.cobblemon_smartphone.compat.tomsstorage.TomsStorageLink
import com.nbp.cobblemon_smartphone.client.BuiltinResourcePack
import com.nbp.cobblemon_smartphone.client.ResourcePackActivationBehavior
import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.cobblemon_smartphone.config.SmartphoneConfig
import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgrade
import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgradeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.server.packs.PackType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CobblemonSmartphone {
    const val ID = "cobblemon_smartphone"
    val LOGGER: Logger = LoggerFactory.getLogger(ID)

    @JvmStatic
    val builtinPacks = listOf(
        BuiltinResourcePack(
            id = "oldsmartphone",
            translationKey = "resourcePack.cobblemon_smartphone.oldsmartphone",
            packType = PackType.CLIENT_RESOURCES,
            activationBehavior = ResourcePackActivationBehavior.NORMAL
        )
    )

    lateinit var config: SmartphoneConfig
    lateinit var implementation: Implementation

    fun registerDefaultActions() {
        SmartphoneActionRegistry.register(CraftingAction)
        SmartphoneActionRegistry.register(HealAction)
        SmartphoneActionRegistry.register(OpenPcAction)
        SmartphoneActionRegistry.register(EnderAction)
        SmartphoneActionRegistry.register(OpenCobblenavAction)
        SmartphoneActionRegistry.register(OpenCobblenavFishingnavAction)
        SmartphoneActionRegistry.register(OpenCobbledollarsAction)
        SmartphoneActionRegistry.register(OpenWaystonesAction)
        SmartphoneActionRegistry.register(OpenTomsStorageAction)
        SmartphoneActionRegistry.register(OpenRefinedStorageAction)
        SmartphoneActionRegistry.register(OpenAE2Action)
        SmartphoneActionRegistry.register(OpenAE2CraftingAction)
        SmartphoneActionRegistry.register(OpenRctTrainerCardAction)
        SmartphoneActionRegistry.register(PokedexAction)
        SmartphoneActionRegistry.register(PokeInfoAction)
        SmartphoneActionRegistry.register(GpsAction)
        SmartphoneActionRegistry.register(StructureCompassAction)
        SmartphoneActionRegistry.register(SocialAction)
    }

    fun registerDefaultUpgrades() {
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = "upgrade_crafting",
                nbtKey = "upgrade_crafting"
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = "upgrade_pokenav",
                nbtKey = "upgrade_pokenav",
                requiredModId = "cobblenav"
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = "upgrade_fishingnav",
                nbtKey = "upgrade_fishingnav",
                requiredModId = "cobblenav"
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = GpsAction.UPGRADE_NBT_KEY,
                nbtKey = GpsAction.UPGRADE_NBT_KEY
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = StructureCompassAction.UPGRADE_NBT_KEY,
                nbtKey = StructureCompassAction.UPGRADE_NBT_KEY
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = "upgrade_waystone",
                nbtKey = "upgrade_waystone",
                requiredModId = "waystones"
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = TomsStorageLink.UPGRADE_NBT_KEY,
                nbtKey = TomsStorageLink.UPGRADE_NBT_KEY,
                requiredModId = TomsStorageLink.MOD_ID
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = OpenRefinedStorageAction.UPGRADE_NBT_KEY,
                nbtKey = OpenRefinedStorageAction.UPGRADE_NBT_KEY,
                requiredModId = OpenRefinedStorageAction.MOD_ID
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = OpenAE2Action.UPGRADE_NBT_KEY,
                nbtKey = OpenAE2Action.UPGRADE_NBT_KEY,
                requiredModId = OpenAE2Action.MOD_ID
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = OpenAE2CraftingAction.UPGRADE_NBT_KEY,
                nbtKey = OpenAE2CraftingAction.UPGRADE_NBT_KEY,
                requiredModId = OpenAE2CraftingAction.MOD_ID
            )
        )
    }

    fun registerDefaultStorageLinks() {
        SmartphoneStorageLinkRegistry.register(TomsStorageLink)
    }

    fun init(implementation: Implementation) {
        config = SmartphoneConfig.load()
        this.implementation = implementation
        implementation.registerItems()
        implementation.registerReloadListeners()
        implementation.registerCommands()
        registerDefaultActions()
        registerDefaultUpgrades()
        registerDefaultStorageLinks()
    }

    fun getSmartphoneActionRegistry() = SmartphoneActionRegistry
}
