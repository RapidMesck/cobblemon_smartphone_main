package com.nbp.cobblemon_smartphone

import com.nbp.cobblemon_smartphone.actions.AppStoreAction
import com.nbp.cobblemon_smartphone.actions.CalculatorAction
import com.nbp.cobblemon_smartphone.actions.CalendarAction
import com.nbp.cobblemon_smartphone.actions.CraftingAction
import com.nbp.cobblemon_smartphone.actions.EnderAction
import com.nbp.cobblemon_smartphone.actions.GpsAction
import com.nbp.cobblemon_smartphone.actions.HealAction
import com.nbp.cobblemon_smartphone.actions.NotesAction
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
import com.nbp.cobblemon_smartphone.actions.RotomAppAction
import com.nbp.cobblemon_smartphone.actions.SocialAction
import com.nbp.cobblemon_smartphone.actions.StructureCompassAction
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.api.SmartphoneStorageLinkRegistry
import com.nbp.cobblemon_smartphone.compat.tomsstorage.TomsStorageLink
import com.nbp.cobblemon_smartphone.client.BuiltinResourcePack
import com.nbp.cobblemon_smartphone.client.ResourcePackActivationBehavior
import com.nbp.cobblemon_smartphone.config.SmartphoneConfig
import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgrade
import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgradeRegistry
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
        SmartphoneActionRegistry.register(RotomAppAction)
        SmartphoneActionRegistry.register(CalculatorAction)
        SmartphoneActionRegistry.register(NotesAction)
        SmartphoneActionRegistry.register(CalendarAction)
        SmartphoneActionRegistry.register(AppStoreAction)
    }

    fun registerDefaultUpgrades() {
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = "upgrade_crafting",
                nbtKey = "upgrade_crafting",
                isAppUpgrade = true,
                actionId = CraftingAction.id,
                accentColor = 0xD98A4E
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = "upgrade_pokenav",
                nbtKey = "upgrade_pokenav",
                requiredModId = "cobblenav",
                isAppUpgrade = true,
                actionId = OpenCobblenavAction.id,
                accentColor = 0x4FC3F7
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = "upgrade_fishingnav",
                nbtKey = "upgrade_fishingnav",
                requiredModId = "cobblenav",
                isAppUpgrade = true,
                actionId = OpenCobblenavFishingnavAction.id,
                accentColor = 0x26A69A
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = GpsAction.UPGRADE_NBT_KEY,
                nbtKey = GpsAction.UPGRADE_NBT_KEY,
                isAppUpgrade = true,
                actionId = GpsAction.id,
                accentColor = 0x66BB6A
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = StructureCompassAction.UPGRADE_NBT_KEY,
                nbtKey = StructureCompassAction.UPGRADE_NBT_KEY,
                isAppUpgrade = true,
                actionId = StructureCompassAction.id,
                accentColor = 0x26C6DA
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = "upgrade_waystone",
                nbtKey = "upgrade_waystone",
                requiredModId = "waystones",
                isAppUpgrade = true,
                actionId = OpenWaystonesAction.id,
                accentColor = 0xAB47BC
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = TomsStorageLink.UPGRADE_NBT_KEY,
                nbtKey = TomsStorageLink.UPGRADE_NBT_KEY,
                requiredModId = TomsStorageLink.MOD_ID,
                isAppUpgrade = true,
                actionId = OpenTomsStorageAction.id,
                accentColor = 0xFFA726
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = OpenRefinedStorageAction.UPGRADE_NBT_KEY,
                nbtKey = OpenRefinedStorageAction.UPGRADE_NBT_KEY,
                requiredModId = OpenRefinedStorageAction.MOD_ID,
                isAppUpgrade = true,
                actionId = OpenRefinedStorageAction.id,
                accentColor = 0x42A5F5
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = OpenAE2Action.UPGRADE_NBT_KEY,
                nbtKey = OpenAE2Action.UPGRADE_NBT_KEY,
                requiredModId = OpenAE2Action.MOD_ID,
                isAppUpgrade = true,
                actionId = OpenAE2Action.id,
                accentColor = 0x7E57C2
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = OpenAE2CraftingAction.UPGRADE_NBT_KEY,
                nbtKey = OpenAE2CraftingAction.UPGRADE_NBT_KEY,
                requiredModId = OpenAE2CraftingAction.MOD_ID,
                isAppUpgrade = true,
                actionId = OpenAE2CraftingAction.id,
                accentColor = 0xEC407A
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = "upgrade_battery_tier1",
                nbtKey = "upgrade_battery_tier1",
                isAppUpgrade = false
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = "upgrade_battery_tier2",
                nbtKey = "upgrade_battery_tier2",
                isAppUpgrade = false
            )
        )
        SmartphoneUpgradeRegistry.register(
            SmartphoneUpgrade(
                id = "upgrade_battery_tier3",
                nbtKey = "upgrade_battery_tier3",
                isAppUpgrade = false
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
        implementation.registerRecipeSerializers()
        implementation.registerReloadListeners()
        implementation.registerCommands()
        registerDefaultActions()
        registerDefaultUpgrades()
        registerDefaultStorageLinks()
        com.nbp.cobblemon_smartphone.social.SocialEvents.register()
    }

    fun getSmartphoneActionRegistry() = SmartphoneActionRegistry
}
