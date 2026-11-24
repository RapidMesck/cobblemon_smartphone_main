package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.api.QuickActionBindings
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.api.SmartphoneActionOrder
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.api.SmartphoneHiddenActions
import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.cobblemon_smartphone.client.ui.hd.BadgeNode
import com.nbp.cobblemon_smartphone.client.ui.hd.ButtonNode
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBackground
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdCanvas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdStateStore
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.client.ui.hd.HdUiPreferences
import com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle
import com.nbp.cobblemon_smartphone.client.ui.hd.HdViewport
import com.nbp.cobblemon_smartphone.client.ui.hd.SegmentedControlNode
import com.nbp.cobblemon_smartphone.client.ui.hd.TabsNode
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.SaveActionOrderPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveHiddenActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveQuickActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveSkinPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveThemePacket
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.cobblemon_smartphone.skin.SmartphoneSkin
import com.nbp.cobblemon_smartphone.skin.SmartphoneSkinHelper
import com.nbp.cobblemon_smartphone.skin.SmartphoneSkinRegistry
import com.nbp.cobblemon_smartphone.skin.isSkinUnlocked
import com.nbp.cobblemon_smartphone.skin.setSkinId
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeHelper
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeRegistry
import com.nbp.cobblemon_smartphone.theme.setTheme
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

/**
 * Large-screen smartphone settings built on the unified HD/Pixel [HdPhoneScreen] UI engine.
 * Provides dedicated tabs for Apps (ordering, visibility, hotkeys), Themes, Skins, and Display (HD/Pixel & UI Scale).
 */
class SmartphoneSettingsScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    initialTab: SettingsTab = SettingsTab.APPS,
    scaleOverride: Float? = null,
    styleOverride: HdUiStyle? = null
) : HdPhoneScreen(
    color = color,
    smartphoneStack = smartphoneStack,
    title = Component.translatable("cobblemon_smartphone.screen.settings"),
    scaleOverride = scaleOverride,
    styleOverride = styleOverride
) {

    enum class SettingsTab {
        APPS,
        THEMES,
        SKINS,
        DISPLAY
    }

    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelBackButton: Boolean = true
    override val showBezelBattery: Boolean = true
    override val showBezelHdBadge: Boolean = true

    private val orderedIds = SmartphoneActionOrder.apply(SmartphoneActionRegistry.getEnabledActions())
        .map { it.id }
        .toMutableList()
    private val slotBindings = QuickActionBindings.currentBindings().toMutableMap()
    private val hiddenIds = SmartphoneHiddenActions.currentHidden().toMutableSet()

    private var currentTab = initialTab
    private var selectedThemeId: String = SmartphoneTheme.DEFAULT_ID
    private var selectedSkinId: String = SmartphoneSkin.DEFAULT_ID
    private var selectorActionId: String? = null

    private val themes: List<SmartphoneTheme>
        get() = SmartphoneThemeRegistry.getThemes()

    private val skins: List<SmartphoneSkin>
        get() = SmartphoneSkinRegistry.getSkins(color)

    override fun init() {
        super.init()
        minecraft?.resourceManager?.let {
            SmartphoneThemeRegistry.ensureLoaded(it)
            SmartphoneSkinRegistry.ensureLoaded(it)
        }
        selectedThemeId = SmartphoneThemeHelper.resolveCurrentTheme(smartphoneStack).id
        selectedSkinId = SmartphoneSkinHelper.resolveSkin(smartphoneStack).id
    }

    override fun removed() {
        SmartphoneActionOrder.setOrder(orderedIds)
        SaveActionOrderPacket(orderedIds).sendToServer()
        QuickActionBindings.setBindings(slotBindings)
        SaveQuickActionsPacket(slotBindings).sendToServer()
        SmartphoneHiddenActions.setHidden(hiddenIds.toList())
        SaveHiddenActionsPacket(hiddenIds.toList()).sendToServer()
        SaveThemePacket(selectedThemeId).sendToServer()
        SaveSkinPacket(selectedSkinId).sendToServer()
        super.removed()
    }

    override fun onBackPressed() {
        Minecraft.getInstance().setScreen(SmartphoneScreen(color, smartphoneStack))
    }

    override fun buildModal(theme: SmartphoneTheme): ModalSpec? {
        val actionId = selectorActionId ?: return null
        val actionName = actionById(actionId)?.displayName?.string ?: actionId
        val currentSlot = slotForAction(actionId)
        return ModalSpec(
            title = actionName,
            widthDp = 230f,
            onClose = { selectorActionId = null }
        ) { body ->
            body.text(
                text = tr("cobblemon_smartphone.quick_actions.hint"),
                size = 8.4f,
                color = HdColorSpec.Token("text_dark_muted"),
                wrap = true
            ) { width = HdSizeSpec.Fill }

            for (row in 0 until selectorRowCount()) {
                val slot = row - 1
                val isCurrent = if (slot < 0) currentSlot == null else currentSlot == slot
                body.button(
                    label = selectorRowLabel(slot),
                    icon = if (isCurrent) HdIconAtlas.Icon.CHECK else null,
                    variant = if (isCurrent) ButtonNode.Variant.PRIMARY else ButtonNode.Variant.SURFACE,
                    fontSize = 9.0f,
                    radius = 8f,
                    onClick = {
                        playClickSound()
                        assignSlot(actionId, if (slot < 0) null else slot)
                        selectorActionId = null
                    }
                ) {
                    width = HdSizeSpec.Fill
                }
            }
        }
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        val subtitle = when (currentTab) {
            SettingsTab.APPS -> "${orderedIds.size - hiddenIds.size}/${orderedIds.size} ${tr("cobblemon_smartphone.settings.tab.apps")}"
            SettingsTab.THEMES -> "${themes.size} ${tr("cobblemon_smartphone.settings.tab.themes")}"
            SettingsTab.SKINS -> "${skins.size} ${tr("cobblemon_smartphone.settings.tab.skins")}"
            SettingsTab.DISPLAY -> "${currentStyle().name} • ${(HdUiPreferences.getUserScale() * 100f).roundToInt()}%"
        }
        val headerIcon = when (currentTab) {
            SettingsTab.APPS -> HdIconAtlas.Icon.FEED
            SettingsTab.THEMES -> HdIconAtlas.Icon.SPARKLES
            SettingsTab.SKINS -> HdIconAtlas.Icon.STAR
            SettingsTab.DISPLAY -> HdIconAtlas.Icon.SETTINGS
        }

        builder.appHeader(
            title = tr("cobblemon_smartphone.screen.settings"),
            subtitle = subtitle,
            icon = headerIcon,
            iconColor = HdColorSpec.Token("primary")
        )

        builder.scroll(
            scrollId = "settings_scroll_${currentTab.name}",
            gap = 6f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 5f)
        ) { body ->
            when (currentTab) {
                SettingsTab.APPS -> buildAppsList(body, theme)
                SettingsTab.THEMES -> buildThemesGrid(body, theme)
                SettingsTab.SKINS -> buildSkinsGrid(body)
                SettingsTab.DISPLAY -> buildDisplayTab(body)
            }
        }

        builder.tabs(
            items = listOf(
                TabsNode.TabItem(SettingsTab.APPS.name, tr("cobblemon_smartphone.settings.tab.apps"), HdIconAtlas.Icon.FEED),
                TabsNode.TabItem(SettingsTab.THEMES.name, tr("cobblemon_smartphone.settings.tab.themes"), HdIconAtlas.Icon.SPARKLES),
                TabsNode.TabItem(SettingsTab.SKINS.name, tr("cobblemon_smartphone.settings.tab.skins"), HdIconAtlas.Icon.STAR),
                TabsNode.TabItem(SettingsTab.DISPLAY.name, tr("cobblemon_smartphone.settings.tab.display"), HdIconAtlas.Icon.SETTINGS)
            ),
            activeId = currentTab.name,
            style = TabsNode.Style.FLOATING_DOCK,
            onSelect = { id ->
                val next = SettingsTab.valueOf(id)
                if (currentTab != next) {
                    playClickSound()
                    currentTab = next
                }
            }
        )
    }

    // -------------------------------------------------------------------------
    // APPS TAB — Scrollable cards with icon, hotkey picker, up/down reorder, and visibility switch
    // -------------------------------------------------------------------------
    private fun buildAppsList(body: HdBuilder, theme: SmartphoneTheme) {
        if (orderedIds.isEmpty()) {
            body.emptyState(
                title = "No Apps Available",
                icon = HdIconAtlas.Icon.FEED
            )
            return
        }

        orderedIds.forEachIndexed { idx, actionId ->
            val action = actionById(actionId) ?: return@forEachIndexed
            val isHidden = hiddenIds.contains(action.id)
            val boundSlot = slotForAction(action.id)

            body.card(gap = 0f, padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f), radius = 10f, shadowBlur = 4f) { card ->
                card.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row ->
                    // App icon
                    row.image(
                        texture = theme.getAppIcon(action.id, action.texture),
                        w = 30f,
                        h = 30f,
                        filter = HdCanvas.TextureFilter.NEAREST
                    ) {
                        if (isHidden) tint = HdColorSpec.Fixed(0x66FFFFFF)
                    }

                    // Name + Hotkey button
                    row.column(gap = 3f, width = HdSizeSpec.Weight(1f)) { info ->
                        info.text(
                            text = action.displayName.string,
                            size = 9.6f,
                            weight = HdTextLayout.Weight.BOLD,
                            color = if (isHidden) HdColorSpec.Token("text_dark_muted") else HdColorSpec.Token("text_dark")
                        )
                        val hotkeyLabel = if (boundSlot != null) {
                            "Hotkey: ${selectorRowLabel(boundSlot)}"
                        } else {
                            "Bind Hotkey"
                        }
                        info.button(
                            label = hotkeyLabel,
                            icon = HdIconAtlas.Icon.EDIT,
                            variant = if (boundSlot != null) ButtonNode.Variant.PRIMARY else ButtonNode.Variant.SURFACE,
                            fontSize = 7.6f,
                            radius = 6f,
                            onClick = {
                                playClickSound()
                                selectorActionId = action.id
                            }
                        ) {
                            iconSizeDp = 9f
                            padding = HdPadding.symmetric(horizontal = 6f, vertical = 2.5f)
                        }
                    }

                    // Up / Down reorder buttons
                    row.row(gap = 3f, crossAlign = HdAlign.CENTER) { order ->
                        order.button(
                            icon = HdIconAtlas.Icon.CHEVRON_UP,
                            variant = ButtonNode.Variant.SURFACE,
                            radius = 6f,
                            enabled = idx > 0,
                            onClick = {
                                if (idx > 0) {
                                    playClickSound()
                                    moveAction(idx, idx - 1)
                                }
                            }
                        ) {
                            iconSizeDp = 10f
                            padding = HdPadding.all(4f)
                        }
                        order.button(
                            icon = HdIconAtlas.Icon.CHEVRON_DOWN,
                            variant = ButtonNode.Variant.SURFACE,
                            radius = 6f,
                            enabled = idx < orderedIds.lastIndex,
                            onClick = {
                                if (idx < orderedIds.lastIndex) {
                                    playClickSound()
                                    moveAction(idx, idx + 1)
                                }
                            }
                        ) {
                            iconSizeDp = 10f
                            padding = HdPadding.all(4f)
                        }
                    }

                    // Show / Hide Switch
                    row.switch(
                        checked = !isHidden,
                        onToggle = { visible ->
                            playClickSound()
                            if (visible) hiddenIds.remove(action.id) else hiddenIds.add(action.id)
                        }
                    )
                }
            }
        }
    }

    private data class PreviewCrop(
        val tex: ResourceLocation,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float
    )

    // -------------------------------------------------------------------------
    // THEMES TAB — 3-column gallery with wallpaper preview & color swatches
    // -------------------------------------------------------------------------
    private fun buildThemesGrid(body: HdBuilder, activeTheme: SmartphoneTheme) {
        if (themes.isEmpty()) {
            body.emptyState(title = tr("cobblemon_smartphone.settings.no_themes"), icon = HdIconAtlas.Icon.SPARKLES)
            return
        }
        val resMgr = Minecraft.getInstance().resourceManager
        body.grid(columns = 3, colGap = 6f, rowGap = 6f) { grid ->
            for (targetTheme in themes) {
                val isSelected = targetTheme.id == selectedThemeId
                grid.column(
                    gap = 4f,
                    crossAlign = HdAlign.CENTER,
                    padding = HdPadding.all(5f),
                    width = HdSizeSpec.Fill,
                    background = HdBackground(
                        color = HdColorSpec.Token("surface"),
                        radius = 9f,
                        borderWidth = if (isSelected) 1.5f else 0.8f,
                        borderColor = if (isSelected) HdColorSpec.Token("gold") else HdColorSpec.Token("primary_border"),
                        shadowBlur = 4f,
                        shadowOffsetY = 1.5f,
                        shadowColor = HdColorSpec.Fixed(0x26000000)
                    )
                ) { card ->
                    hoverBackground = HdBackground(
                        color = HdColorSpec.Token("surface_hover"),
                        radius = 9f,
                        borderWidth = 1.2f,
                        borderColor = if (isSelected) HdColorSpec.Token("gold") else HdColorSpec.Token("primary")
                    )
                    onClick = {
                        playClickSound()
                        applyTheme(targetTheme)
                    }

                    // Wallpaper preview box — aspect-preserving center-crop (no stretching, no frame transparency)
                    val largeWall = targetTheme.getLargeWallpaperTexture()
                    val (wallTex, u0, v0, u1, v1) = when {
                        resMgr.getResource(largeWall).isPresent ->
                            // Large wallpaper is 211x207; inner usable width ~181px (0.07..0.93).
                            // For a ~82x50dp (1.64:1) card preview, 181 / 1.64 = 110px tall -> 0.53 of 207px.
                            PreviewCrop(largeWall, 0.07f, 0.24f, 0.93f, 0.77f)
                        resMgr.getResource(targetTheme.wallpaper).isPresent ->
                            // Compact wallpaper is 131x207; inner usable width ~107px (0.09..0.91).
                            // For a ~82x50dp (1.64:1) card preview, 107 / 1.64 = 65px tall -> 0.315 of 207px.
                            PreviewCrop(targetTheme.wallpaper, 0.09f, 0.34f, 0.91f, 0.66f)
                        else ->
                            PreviewCrop(SmartphoneTheme.DEFAULT_LARGE_WALLPAPER, 0.07f, 0.24f, 0.93f, 0.77f)
                    }
                    card.stack(
                        hAlign = HdAlign.CENTER,
                        vAlign = HdAlign.CENTER,
                        width = HdSizeSpec.Fill,
                        height = HdSizeSpec.Fixed(50f)
                    ) { prev ->
                        prev.image(
                            texture = wallTex,
                            w = 82f,
                            h = 50f,
                            radius = 6f,
                            filter = HdCanvas.TextureFilter.NEAREST
                        ) {
                            this.u0 = u0
                            this.v0 = v0
                            this.u1 = u1
                            this.v1 = v1
                            width = HdSizeSpec.Fill
                            height = HdSizeSpec.Fixed(50f)
                            borderWidth = 0.8f
                            borderColor = HdColorSpec.Fixed(targetTheme.colors.primaryBorder)
                        }
                        if (isSelected) {
                            val checkBadge = com.nbp.cobblemon_smartphone.client.ui.hd.RowNode().apply {
                                padding = HdPadding.all(2.5f)
                                background = HdBackground(color = HdColorSpec.Token("gold"), radius = 6f)
                                add(com.nbp.cobblemon_smartphone.client.ui.hd.IconNode(HdIconAtlas.Icon.CHECK, 9f, HdColorSpec.Fixed(0xFF1B1917.toInt())))
                            }
                            addAligned(checkBadge, HdAlign.END, HdAlign.START)
                        }
                    }

                    // 3 color swatch dots
                    card.row(gap = 4f, crossAlign = HdAlign.CENTER, mainAlign = HdAlign.CENTER) { swatches ->
                        for (col in listOf(targetTheme.colors.primary, targetTheme.colors.headerNavy, targetTheme.colors.gold)) {
                            swatches.spacer(w = 9f, h = 9f).apply {
                                background = HdBackground(
                                    color = HdColorSpec.Fixed(col),
                                    radius = 4.5f,
                                    borderWidth = 0.6f,
                                    borderColor = HdColorSpec.Fixed(0x44000000)
                                )
                            }
                        }
                    }

                    card.text(
                        text = targetTheme.name.string,
                        size = 8.2f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = if (isSelected) HdColorSpec.Token("primary") else HdColorSpec.Token("text_dark"),
                        align = HdAlign.CENTER
                    ) {
                        width = HdSizeSpec.Fill
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // SKINS TAB — 3-column gallery with live ItemIcon preview & unlock status
    // -------------------------------------------------------------------------
    private fun buildSkinsGrid(body: HdBuilder) {
        if (skins.isEmpty()) {
            body.emptyState(title = tr("cobblemon_smartphone.settings.no_skins"), icon = HdIconAtlas.Icon.STAR)
            return
        }
        val phone = smartphoneStack ?: Minecraft.getInstance().player?.let { SmartphoneHelper.getSmartphone(it) }
        body.grid(columns = 3, colGap = 6f, rowGap = 6f) { grid ->
            for (skin in skins) {
                val unlocked = skin.requiredUpgrade == null || (phone != null && phone.isSkinUnlocked(skin))
                val isSelected = skin.id == selectedSkinId
                grid.column(
                    gap = 4f,
                    crossAlign = HdAlign.CENTER,
                    padding = HdPadding.all(6f),
                    width = HdSizeSpec.Fill,
                    background = HdBackground(
                        color = if (unlocked) HdColorSpec.Token("surface") else HdColorSpec.Token("surface_alt"),
                        radius = 9f,
                        borderWidth = if (isSelected) 1.5f else 0.8f,
                        borderColor = if (isSelected) HdColorSpec.Token("gold") else HdColorSpec.Token("primary_border"),
                        shadowBlur = 4f,
                        shadowOffsetY = 1.5f,
                        shadowColor = HdColorSpec.Fixed(0x26000000)
                    )
                ) { card ->
                    if (unlocked) {
                        hoverBackground = HdBackground(
                            color = HdColorSpec.Token("surface_hover"),
                            radius = 9f,
                            borderWidth = 1.2f,
                            borderColor = if (isSelected) HdColorSpec.Token("gold") else HdColorSpec.Token("primary")
                        )
                        onClick = {
                            playClickSound()
                            applySkin(skin)
                        }
                    }
                    tooltip = when {
                        unlocked -> skin.name.string
                        skin.requiredUpgrade.isNotBlank() -> "${skin.name.string} — ${tr("cobblemon_smartphone.skin.locked_upgrade", skin.requiredUpgrade)}"
                        else -> "${skin.name.string} — ${tr("cobblemon_smartphone.skin.locked")}"
                    }

                    card.stack(
                        hAlign = HdAlign.CENTER,
                        vAlign = HdAlign.CENTER,
                        width = HdSizeSpec.Fill,
                        height = HdSizeSpec.Fixed(60f)
                    ) { prev ->
                        background = HdBackground(
                            color = HdColorSpec.Token("surface_alt"),
                            radius = 7f,
                            borderWidth = 0.6f,
                            borderColor = HdColorSpec.Token("primary_border", 0.5f)
                        )
                        if (!unlocked) {
                            prev.icon(HdIconAtlas.Icon.LOCK, size = 16f, color = HdColorSpec.Token("text_dark_muted"))
                        } else {
                            val previewStack = (smartphoneStack?.copy() ?: ItemStack(CobblemonSmartphoneItems.RED_SMARTPHONE)).apply {
                                setSkinId(skin.id)
                            }
                            val isRotom = SmartphoneHelper.isRotomEnabled() && SmartphoneHelper.run { previewStack.isRotomPhone() }
                            val modelLoc = SmartphoneSkinHelper.resolveHandModel(previewStack, color, isRotom)
                            prev.model3d(
                                modelLocation = modelLoc,
                                fallbackStack = previewStack,
                                size = 50f,
                                tiltXDegrees = 14f,
                                spinDegreesPerSec = 65f,
                                initialAngleY = 25f
                            )
                        }
                        if (isSelected) {
                            val checkBadge = com.nbp.cobblemon_smartphone.client.ui.hd.RowNode().apply {
                                padding = HdPadding.all(2.5f)
                                background = HdBackground(color = HdColorSpec.Token("gold"), radius = 6f)
                                add(com.nbp.cobblemon_smartphone.client.ui.hd.IconNode(HdIconAtlas.Icon.CHECK, 9f, HdColorSpec.Fixed(0xFF1B1917.toInt())))
                            }
                            addAligned(checkBadge, HdAlign.END, HdAlign.START)
                        }
                    }

                    card.text(
                        text = skin.name.string,
                        size = 8.2f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = if (isSelected) HdColorSpec.Token("primary") else HdColorSpec.Token("text_dark"),
                        align = HdAlign.CENTER
                    ) {
                        width = HdSizeSpec.Fill
                    }

                    if (!unlocked) {
                        card.badge(tr("cobblemon_smartphone.settings.skin_locked"), icon = HdIconAtlas.Icon.LOCK, variant = BadgeNode.Variant.DANGER, fontSize = 6.8f)
                    } else if (isSelected) {
                        card.badge(tr("cobblemon_smartphone.settings.skin_equipped"), icon = HdIconAtlas.Icon.CHECK, variant = BadgeNode.Variant.SUCCESS, fontSize = 6.8f)
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // DISPLAY TAB — HD vs Pixel Style + UI Scale Slider
    // -------------------------------------------------------------------------
    private fun buildDisplayTab(body: HdBuilder) {
        val currentStyle = currentStyle()
        val currentScalePct = (HdUiPreferences.getUserScale() * 100f).roundToInt().toFloat()

        body.card(gap = 7f, padding = HdPadding.all(10f)) { c ->
            c.text(tr("cobblemon_smartphone.settings.display.style_title"), size = 10.0f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
            c.text(
                tr("cobblemon_smartphone.settings.display.style_desc"),
                size = 8.2f,
                color = HdColorSpec.Token("text_dark_muted"),
                wrap = true
            ) { width = HdSizeSpec.Fill }

            c.segmentedControl(
                options = listOf(
                    SegmentedControlNode.Option(HdUiStyle.PIXEL.name, tr("cobblemon_smartphone.settings.display.style_pixel"), HdIconAtlas.Icon.SETTINGS),
                    SegmentedControlNode.Option(HdUiStyle.HD.name, tr("cobblemon_smartphone.settings.display.style_hd"), HdIconAtlas.Icon.SPARKLES)
                ),
                selectedId = currentStyle.name,
                onSelect = { id ->
                    val next = HdUiStyle.valueOf(id)
                    if (next != HdUiPreferences.getUiStyle()) {
                        playClickSound()
                        HdUiPreferences.setUiStyle(next)
                        showToast(tr("cobblemon_smartphone.settings.display.style_toast", next.name), HdStateStore.ToastKind.SUCCESS)
                    }
                }
            )
        }

        body.card(gap = 7f, padding = HdPadding.all(10f)) { c ->
            c.slider(
                value = currentScalePct,
                min = HdViewport.MIN_USER_SCALE * 100f,
                max = HdViewport.MAX_USER_SCALE * 100f,
                step = 5f,
                label = tr("cobblemon_smartphone.settings.display.scale_label"),
                valueFormat = { "${it.roundToInt()}%" },
                onChange = { pct ->
                    HdUiPreferences.setUserScale(pct / 100f)
                }
            )
            c.row(gap = 6f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { r ->
                r.text(tr("cobblemon_smartphone.settings.display.scale_tip"), size = 7.8f, color = HdColorSpec.Token("text_dark_muted"), wrap = true) {
                    width = HdSizeSpec.Weight(1f)
                }
                r.button(
                    label = tr("cobblemon_smartphone.settings.display.scale_reset"),
                    icon = HdIconAtlas.Icon.REFRESH,
                    variant = ButtonNode.Variant.SURFACE,
                    fontSize = 8.0f,
                    radius = 7f,
                    onClick = {
                        playClickSound()
                        HdUiPreferences.setUserScale(1.0f)
                        showToast(tr("cobblemon_smartphone.settings.display.scale_reset_toast"), HdStateStore.ToastKind.INFO)
                    }
                ) {
                    iconSizeDp = 10f
                    padding = HdPadding.symmetric(horizontal = 7f, vertical = 3.5f)
                }
            }
        }
    }

    private fun applyTheme(targetTheme: SmartphoneTheme) {
        selectedThemeId = targetTheme.id
        smartphoneStack?.setTheme(targetTheme.id)
        Minecraft.getInstance().player?.let { p ->
            SmartphoneHelper.getSmartphone(p)?.setTheme(targetTheme.id)
        }
        SmartphoneThemeHelper.clientPreferredTheme = targetTheme.id
        SaveThemePacket(targetTheme.id).sendToServer()
        showToast("Theme: ${targetTheme.name.string}", HdStateStore.ToastKind.SUCCESS)
    }

    private fun applySkin(skin: SmartphoneSkin) {
        selectedSkinId = skin.id
        smartphoneStack?.setSkinId(skin.id)
        Minecraft.getInstance().player?.let { p ->
            SmartphoneHelper.getSmartphone(p)?.setSkinId(skin.id)
        }
        SaveSkinPacket(skin.id).sendToServer()
        showToast("Skin: ${skin.name.string}", HdStateStore.ToastKind.SUCCESS)
    }

    private fun moveAction(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in orderedIds.indices || toIndex !in orderedIds.indices) return
        val id = orderedIds.removeAt(fromIndex)
        orderedIds.add(toIndex, id)
    }

    private fun actionById(id: String): SmartphoneAction? =
        SmartphoneActionRegistry.getEnabledActions().firstOrNull { it.id == id }

    private fun slotForAction(actionId: String): Int? =
        slotBindings.entries.firstOrNull { it.value == actionId }?.key

    private fun assignSlot(actionId: String, slot: Int?) {
        slotBindings.values.removeIf { it == actionId }
        if (slot != null) {
            slotBindings[slot] = actionId
        }
    }

    private fun selectorRowCount(): Int = SmartphoneKeybinds.QUICK_ACTION_SLOT_COUNT + 1

    private fun selectorRowLabel(slot: Int): String {
        if (slot < 0) return tr("cobblemon_smartphone.quick_actions.none")
        val keyMapping = SmartphoneKeybinds.QUICK_ACTION_SLOTS[slot]
        val number = (slot + 1).toString()
        return if (keyMapping.isUnbound) number else "$number  ${keyMapping.translatedKeyMessage.string}"
    }

    private fun tr(key: String, vararg args: Any): String =
        if (args.isEmpty()) Component.translatable(key).string else Component.translatable(key, *args).string
}
