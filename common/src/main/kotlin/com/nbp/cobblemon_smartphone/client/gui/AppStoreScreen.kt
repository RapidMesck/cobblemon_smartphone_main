package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.actions.AppStoreAction
import com.nbp.cobblemon_smartphone.api.DatapackAction
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.client.ui.hd.ButtonNode
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdCanvas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdStateStore
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle
import com.nbp.cobblemon_smartphone.client.ui.hd.TabsNode
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.item.SdCardItem
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.InstallUpgradePacket
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgradeRegistry
import com.nbp.cobblemon_smartphone.upgrade.addUpgrade
import com.nbp.cobblemon_smartphone.upgrade.getSdCardUpgrade
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import com.nbp.cobblemon_smartphone.upgrade.isSdCardFor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * Play Store-like app for installing smartphone app upgrades using SD Cards.
 * Installing consumes one matching SD Card from the player's inventory on the server
 * and writes the exact same `cobblemon_smartphone:upgrades` NBT flag onto the smartphone.
 */
class AppStoreScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    scaleOverride: Float? = null,
    styleOverride: HdUiStyle? = null
) : HdPhoneScreen(
    color = color,
    smartphoneStack = smartphoneStack,
    title = Component.translatable("cobblemon_smartphone.screen.app_store"),
    scaleOverride = scaleOverride,
    styleOverride = styleOverride
) {
    enum class StoreTab {
        ALL,
        READY,
        INSTALLED
    }

    private data class StoreEntry(
        val upgradeKey: String,
        val actionId: String?,
        val title: String,
        val description: String,
        val iconTexture: ResourceLocation,
        val isInstalled: Boolean,
        val hasSdCard: Boolean
    )

    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelBackButton: Boolean = true
    override val showBezelBattery: Boolean = true
    override val showBezelHdBadge: Boolean = true

    private var currentTab: StoreTab = StoreTab.ALL
    private var pendingInstallKey: String? = null
    private val installedThisSession = mutableSetOf<String>()

    private val activeSmartphone: ItemStack?
        get() = SmartphoneBatteryRenderer.resolveSmartphone(smartphoneStack) ?: smartphoneStack

    override fun onBackPressed() {
        Minecraft.getInstance().setScreen(SmartphoneScreen(color, activeSmartphone))
    }

    fun onInstallResult(upgradeKey: String, success: Boolean, messageKey: String) {
        if (pendingInstallKey == upgradeKey) {
            pendingInstallKey = null
        }
        if (success) {
            installedThisSession.add(upgradeKey)
            val targets = listOfNotNull(
                activeSmartphone,
                smartphoneStack,
                SmartphoneHelper.contextSmartphone
            )
            for (stack in targets) {
                if (!stack.hasUpgrade(upgradeKey)) {
                    stack.addUpgrade(upgradeKey)
                }
            }
        }
        val msg = Component.translatable(messageKey).string
        showToast(
            message = msg,
            kind = if (success) HdStateStore.ToastKind.SUCCESS else HdStateStore.ToastKind.ERROR,
            icon = if (success) HdIconAtlas.Icon.CHECK else HdIconAtlas.Icon.WARNING
        )
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        val player = Minecraft.getInstance().player
        val phone = activeSmartphone
        val allEntries = collectStoreEntries(player, phone)
        val installedCount = allEntries.count { it.isInstalled }
        val readyCount = allEntries.count { !it.isInstalled && it.hasSdCard }

        val subtitle = Component.translatable(
            "cobblemon_smartphone.app_store.subtitle",
            installedCount,
            allEntries.size,
            readyCount
        ).string

        builder.appHeader(
            title = tr("cobblemon_smartphone.screen.app_store"),
            subtitle = subtitle,
            icon = HdIconAtlas.Icon.SPARKLES,
            iconColor = HdColorSpec.Token("primary")
        )

        val visibleEntries = when (currentTab) {
            StoreTab.ALL -> allEntries.sortedWith(
                compareByDescending<StoreEntry> { !it.isInstalled && it.hasSdCard }
                    .thenBy { it.isInstalled }
                    .thenBy { it.title.lowercase() }
            )
            StoreTab.READY -> allEntries.filter { !it.isInstalled && it.hasSdCard }
                .sortedBy { it.title.lowercase() }
            StoreTab.INSTALLED -> allEntries.filter { it.isInstalled }
                .sortedBy { it.title.lowercase() }
        }

        builder.scroll(
            scrollId = "app_store_scroll_${currentTab.name}",
            gap = 6f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 5f)
        ) { body ->
            if (visibleEntries.isEmpty()) {
                val emptyTitle = when (currentTab) {
                    StoreTab.READY -> tr("cobblemon_smartphone.app_store.empty.ready")
                    StoreTab.INSTALLED -> tr("cobblemon_smartphone.app_store.empty.installed")
                    StoreTab.ALL -> tr("cobblemon_smartphone.app_store.empty.all")
                }
                val emptySub = if (currentTab == StoreTab.READY) {
                    tr("cobblemon_smartphone.app_store.empty.ready_hint")
                } else {
                    null
                }
                body.emptyState(
                    title = emptyTitle,
                    description = emptySub,
                    icon = HdIconAtlas.Icon.SPARKLES
                )
            } else {
                for (entry in visibleEntries) {
                    buildAppCard(body, theme, entry)
                }
            }
        }

        builder.tabs(
            items = listOf(
                TabsNode.TabItem(StoreTab.ALL.name, tr("cobblemon_smartphone.app_store.tab.all"), HdIconAtlas.Icon.FEED),
                TabsNode.TabItem(StoreTab.READY.name, tr("cobblemon_smartphone.app_store.tab.ready"), HdIconAtlas.Icon.PLUS),
                TabsNode.TabItem(StoreTab.INSTALLED.name, tr("cobblemon_smartphone.app_store.tab.installed"), HdIconAtlas.Icon.CHECK)
            ),
            activeId = currentTab.name,
            style = TabsNode.Style.FLOATING_DOCK,
            onSelect = { id ->
                val next = StoreTab.valueOf(id)
                if (currentTab != next) {
                    playClickSound()
                    currentTab = next
                }
            }
        )
    }

    private fun buildAppCard(body: HdBuilder, theme: SmartphoneTheme, entry: StoreEntry) {
        val isPending = pendingInstallKey == entry.upgradeKey

        body.card(
            gap = 0f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f),
            radius = 10f,
            shadowBlur = 4f
        ) { card ->
            card.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row ->
                row.appIcon(
                    theme = theme,
                    actionId = entry.actionId,
                    defaultTexture = entry.iconTexture,
                    size = 30f,
                    alphaScale = if (!entry.isInstalled && !entry.hasSdCard) 0.6f else 1f
                )

                row.column(gap = 2f, width = HdSizeSpec.Weight(1f)) { info ->
                    info.text(
                        text = entry.title,
                        size = 9.4f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("text_dark")
                    )
                    if (entry.description.isNotBlank()) {
                        info.text(
                            text = entry.description,
                            size = 7.4f,
                            color = HdColorSpec.Token("text_dark_muted"),
                            wrap = true
                        ) {
                            width = HdSizeSpec.Fill
                        }
                    }
                    val statusText = when {
                        entry.isInstalled -> tr("cobblemon_smartphone.app_store.status.installed")
                        entry.hasSdCard -> tr("cobblemon_smartphone.app_store.status.sd_ready")
                        else -> tr("cobblemon_smartphone.app_store.status.sd_required")
                    }
                    val statusColor = when {
                        entry.isInstalled -> HdColorSpec.Fixed(0xFF2E7D32.toInt())
                        entry.hasSdCard -> HdColorSpec.Token("primary")
                        else -> HdColorSpec.Token("text_dark_muted")
                    }
                    info.text(
                        text = statusText,
                        size = 7.2f,
                        weight = HdTextLayout.Weight.MEDIUM,
                        color = statusColor
                    )
                }

                when {
                    entry.isInstalled -> {
                        row.button(
                            label = tr("cobblemon_smartphone.app_store.button.installed"),
                            icon = HdIconAtlas.Icon.CHECK,
                            variant = ButtonNode.Variant.SURFACE,
                            fontSize = 7.8f,
                            radius = 7f,
                            enabled = false,
                            onClick = {}
                        ) {
                            iconSizeDp = 9f
                            padding = HdPadding.symmetric(horizontal = 7f, vertical = 4f)
                        }
                    }
                    entry.hasSdCard -> {
                        row.button(
                            label = if (isPending) "..." else tr("cobblemon_smartphone.app_store.button.install"),
                            icon = HdIconAtlas.Icon.PLUS,
                            variant = ButtonNode.Variant.PRIMARY,
                            fontSize = 7.8f,
                            radius = 7f,
                            enabled = !isPending,
                            onClick = {
                                playClickSound()
                                pendingInstallKey = entry.upgradeKey
                                InstallUpgradePacket(entry.upgradeKey).sendToServer()
                            }
                        ) {
                            iconSizeDp = 9f
                            padding = HdPadding.symmetric(horizontal = 7f, vertical = 4f)
                        }
                    }
                    else -> {
                        row.button(
                            label = tr("cobblemon_smartphone.app_store.button.need_sd"),
                            icon = HdIconAtlas.Icon.LOCK,
                            variant = ButtonNode.Variant.SURFACE,
                            fontSize = 7.6f,
                            radius = 7f,
                            enabled = false,
                            onClick = {}
                        ) {
                            iconSizeDp = 9f
                            padding = HdPadding.symmetric(horizontal = 6f, vertical = 4f)
                        }
                    }
                }
            }
        }
    }

    private fun collectStoreEntries(player: Player?, phone: ItemStack?): List<StoreEntry> {
        val entries = LinkedHashMap<String, StoreEntry>()

        // 1. Registered built-in app upgrades (hardware upgrades like batteries have isAppUpgrade = false)
        for (upgrade in SmartphoneUpgradeRegistry.getAppUpgrades()) {
            val reqMod = upgrade.requiredModId
            if (reqMod != null && !isModLoaded(reqMod)) continue

            val action = upgrade.actionId?.let { SmartphoneActionRegistry.getActionById(it) }
            val title = action?.displayName?.string ?: SdCardItem.resolveUpgradeLabel(upgrade.nbtKey).string
            val descKey = "cobblemon_smartphone.app_store.desc.${upgrade.nbtKey}"
            val description = Component.translatableWithFallback(descKey, "").string
            val iconTex = action?.texture ?: AppStoreAction.texture
            val installed = isInstalled(phone, upgrade.nbtKey, upgrade.actionId)
            val hasSd = player != null && playerHasSdCard(player, upgrade.nbtKey)

            entries[upgrade.nbtKey] = StoreEntry(
                upgradeKey = upgrade.nbtKey,
                actionId = upgrade.actionId,
                title = title,
                description = description,
                iconTexture = iconTex,
                isInstalled = installed,
                hasSdCard = hasSd
            )
        }

        // 2. Datapack actions that declare require_upgrade
        for (action in SmartphoneActionRegistry.getAllActions()) {
            if (action !is DatapackAction) continue
            val upgradeKey = action.requireUpgrade?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val reqMod = action.requireMod
            if (reqMod != null && !isModLoaded(reqMod)) continue
            if (entries.containsKey(upgradeKey)) continue

            val installed = isInstalled(phone, upgradeKey, action.id)
            val hasSd = player != null && playerHasSdCard(player, upgradeKey)
            entries[upgradeKey] = StoreEntry(
                upgradeKey = upgradeKey,
                actionId = action.id,
                title = action.displayName.string,
                description = Component.translatableWithFallback(
                    "cobblemon_smartphone.app_store.desc.$upgradeKey",
                    ""
                ).string,
                iconTexture = action.texture,
                isInstalled = installed,
                hasSdCard = hasSd
            )
        }

        // 3. Any additional SD Cards in player's inventory for custom upgrades not yet in the list
        if (player != null) {
            val inventoryStacks = player.inventory.items + listOf(player.offhandItem)
            for (stack in inventoryStacks) {
                if (stack.item !is SdCardItem) continue
                val key = stack.getSdCardUpgrade() ?: continue
                if (entries.containsKey(key)) continue
                entries[key] = StoreEntry(
                    upgradeKey = key,
                    actionId = null,
                    title = SdCardItem.resolveUpgradeLabel(key).string,
                    description = Component.translatableWithFallback(
                        "cobblemon_smartphone.app_store.desc.$key",
                        ""
                    ).string,
                    iconTexture = AppStoreAction.texture,
                    isInstalled = phone?.hasUpgrade(key) == true,
                    hasSdCard = true
                )
            }
        }

        return entries.values.toList()
    }

    private fun isInstalled(phone: ItemStack?, upgradeKey: String, actionId: String?): Boolean {
        if (upgradeKey in installedThisSession) return true
        if (actionId != null && CobblemonSmartphone.config.ignoreUpgrades.contains(actionId)) {
            return true
        }
        return phone?.hasUpgrade(upgradeKey) == true
    }

    private fun playerHasSdCard(player: Player, upgradeKey: String): Boolean {
        if (player.inventory.items.any { it.isSdCardFor(upgradeKey) }) return true
        return player.offhandItem.isSdCardFor(upgradeKey)
    }

    private fun tr(key: String): String = Component.translatable(key).string
}
