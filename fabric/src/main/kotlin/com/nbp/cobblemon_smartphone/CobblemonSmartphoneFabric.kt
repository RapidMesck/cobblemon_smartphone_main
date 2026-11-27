package com.nbp.cobblemon_smartphone

import com.nbp.cobblemon_smartphone.api.DatapackActionLoader
import com.nbp.cobblemon_smartphone.command.SmartphoneCommands
import com.nbp.cobblemon_smartphone.command.SocialCommands
import com.nbp.cobblemon_smartphone.compat.SmartphoneCompatManager
import com.nbp.cobblemon_smartphone.compat.tomsstorage.TomsStorageRemoteSession
import com.nbp.cobblemon_smartphone.client.ResourcePackActivationBehavior
import com.nbp.cobblemon_smartphone.network.packet.SyncActionOrderPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncedActionData
import com.nbp.cobblemon_smartphone.network.packet.SyncDatapackActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncDatapackScreensPacket
import com.nbp.cobblemon_smartphone.energy.ElectricPokemonCharger
import com.nbp.cobblemon_smartphone.network.packet.SyncEnergyConfigPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncHiddenActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncQuickActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncMutedPlayersPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncSocialMutePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncUnreadPacket
import com.nbp.cobblemon_smartphone.network.packet.SocialCapabilitiesPacket
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneGpsItems
import com.nbp.cobblemon_smartphone.network.packet.SyncThemePacket
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneRecipeSerializers
import com.nbp.cobblemon_smartphone.server.RotomServerManager
import com.nbp.cobblemon_smartphone.social.CallManager
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.util.ActionOrderStorage
import com.nbp.cobblemon_smartphone.util.HiddenActionsStorage
import com.nbp.cobblemon_smartphone.util.MutedPlayersStorage
import com.nbp.cobblemon_smartphone.util.QuickActionBindingsStorage
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.SocialMuteStorage
import com.nbp.cobblemon_smartphone.util.ThemeStorage
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.ResourcePackActivationType
import net.minecraft.server.packs.PackType
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

        SmartphoneHelper.getSmartphoneImpl = { player -> SmartphoneCompatManager.getSmartphone(player) }

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

        // Sync datapack actions to players when they join
        ServerPlayConnectionEvents.JOIN.register { handler, _sender, _server ->
            val data = DatapackActionLoader.getDefinitions().map { def ->
                SyncedActionData(
                    def.id,
                    def.texture,
                    def.hoverTexture,
                    def.requireUpgrade,
                    def.requireMod,
                    def.cooldownSeconds,
                    def.patchouliBook,
                    def.openScreen
                )
            }
            SyncDatapackActionsPacket(data).sendToPlayer(handler.player)
            SyncDatapackScreensPacket(com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenLoader.getSyncPayloads())
                .sendToPlayer(handler.player)
            com.nbp.cobblemon_smartphone.network.packet.SyncSmartphoneSkinsPacket(
                com.nbp.cobblemon_smartphone.skin.DatapackSkinLoader.getSkins()
            ).sendToPlayer(handler.player)
            SyncActionOrderPacket(ActionOrderStorage.read(handler.player)).sendToPlayer(handler.player)
            SyncHiddenActionsPacket(HiddenActionsStorage.read(handler.player)).sendToPlayer(handler.player)
            SyncQuickActionsPacket(QuickActionBindingsStorage.read(handler.player)).sendToPlayer(handler.player)
            com.nbp.cobblemon_smartphone.network.packet.SyncNotesPacket(
                com.nbp.cobblemon_smartphone.util.NotesStorage.read(handler.player)
            ).sendToPlayer(handler.player)
            // DMs received while offline are already persisted; this lights up the badge on login.
            SyncUnreadPacket(SocialData.get(_server).totalUnreadFor(handler.player.uuid))
                .sendToPlayer(handler.player)
            SyncSocialMutePacket(SocialMuteStorage.readPrefs(handler.player)).sendToPlayer(handler.player)
            SyncMutedPlayersPacket(MutedPlayersStorage.read(handler.player).toList()).sendToPlayer(handler.player)
            val savedTheme = ThemeStorage.read(handler.player)
            if (savedTheme.isNotBlank()) {
                SyncThemePacket(savedTheme).sendToPlayer(handler.player)
            }
            SocialCapabilitiesPacket.fromServerConfig().sendToPlayer(handler.player)
            SyncEnergyConfigPacket.fromServerConfig().sendToPlayer(handler.player)
            com.nbp.cobblemon_smartphone.network.packet.SyncRotomConfigPacket.fromServerConfig().sendToPlayer(handler.player)
            RotomServerManager.onPlayerJoin(handler.player)
        }

        // End any call a player was in when they disconnect, restoring the other side's voice group.
        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            val player = handler.player
            CallManager.onLogout(server, player)
            TomsStorageRemoteSession.onLogout(player)
            RotomServerManager.onPlayerDisconnect(player)
            com.nbp.cobblemon_smartphone.server.RotomAutoScannerManager.clearPlayerCooldowns(player.uuid)
            com.nbp.cobblemon_smartphone.social.SmartphoneInteractionManager.onLogout(player)
            com.nbp.cobblemon_smartphone.social.SocialPhotoManager.onPlayerDisconnect(player.uuid)
            com.nbp.cobblemon_smartphone.network.handler.RequestGpsSearchHandler.cancelForPlayer(player.uuid)
            com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenExecutor.onPlayerDisconnect(player.uuid)
            com.nbp.cobblemon_smartphone.network.handler.ExecuteDatapackActionHandler.onPlayerDisconnect(player.uuid)
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            com.nbp.cobblemon_smartphone.network.handler.RequestGpsSearchHandler.clearAll()
            RotomServerManager.clearAll()
            com.nbp.cobblemon_smartphone.server.RotomAutoScannerManager.clearAll()
            com.nbp.cobblemon_smartphone.social.SocialPhotoManager.clearAll()
            com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenExecutor.clearAll()
            com.nbp.cobblemon_smartphone.network.handler.ExecuteDatapackActionHandler.clearAll()
        }
        // Fabric API has no generic "container closed" event, so a per-tick poll (see
        // TomsStorageRemoteSession.tick) is what closes out the remote-access bypass once the
        // remotely-opened Toms Storage terminal closes. This poll itself needs no mixin; the
        // held-item range check bypass is a separate mixin, see MixinStorageTerminalBlockEntity.
        ServerTickEvents.END_SERVER_TICK.register { server ->
            TomsStorageRemoteSession.tick(server)
            ElectricPokemonCharger.tick(server)
            RotomServerManager.tick(server)
        }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { server, _, success ->
            if (!success) return@register
            val actionData = DatapackActionLoader.getDefinitions().map { def ->
                SyncedActionData(
                    def.id,
                    def.texture,
                    def.hoverTexture,
                    def.requireUpgrade,
                    def.requireMod,
                    def.cooldownSeconds,
                    def.patchouliBook,
                    def.openScreen
                )
            }
            val screensPayload = com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenLoader.getSyncPayloads()
            val skinsPayload = com.nbp.cobblemon_smartphone.skin.DatapackSkinLoader.getSkins()
            server.playerList.players.forEach { p ->
                SyncDatapackActionsPacket(actionData).sendToPlayer(p)
                SyncDatapackScreensPacket(screensPayload).sendToPlayer(p)
                com.nbp.cobblemon_smartphone.network.packet.SyncSmartphoneSkinsPacket(skinsPayload).sendToPlayer(p)
            }
        }
        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick(CallManager::tick))
    }

    override fun registerItems() {
        CobblemonSmartphoneItems.register { resourceLocation, item -> Registry.register(CobblemonSmartphoneItems.registry, resourceLocation, item) }
        CobblemonSmartphoneGpsItems.register { resourceLocation, item -> Registry.register(CobblemonSmartphoneGpsItems.registry, resourceLocation, item) }
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

    override fun registerRecipeSerializers() {
        CobblemonSmartphoneRecipeSerializers.register { resourceLocation, serializer ->
            Registry.register(CobblemonSmartphoneRecipeSerializers.registry, resourceLocation, serializer)
        }
    }

    override fun registerCommands() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            SocialCommands.register(dispatcher)
            SmartphoneCommands.register(dispatcher)
        }
    }

    override fun registerReloadListeners() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(DatapackActionReloadListenerWrapper())
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(DatapackSkinReloadListenerWrapper())
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(DatapackScreenReloadListenerWrapper())
    }
}
