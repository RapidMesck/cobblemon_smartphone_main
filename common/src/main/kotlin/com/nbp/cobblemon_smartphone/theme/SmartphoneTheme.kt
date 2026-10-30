package com.nbp.cobblemon_smartphone.theme

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

data class SmartphoneThemeElements(
    val homeButton: ResourceLocation? = null,
    val prevButton: ResourceLocation? = null,
    val nextButton: ResourceLocation? = null,
    val settingsButton: ResourceLocation? = null,
    val dragHandle: ResourceLocation? = null,
    val viewSelect: ResourceLocation? = null,
    val socialPokeball: ResourceLocation? = null
)

data class SmartphoneTheme(
    val id: String,
    val name: Component,
    val wallpaper: ResourceLocation,
    val elements: SmartphoneThemeElements = SmartphoneThemeElements(),
    val colors: SmartphoneThemeColors = SmartphoneThemeColors.DEFAULT,
    val order: Int = 100,
    val isCobblemonPc: Boolean = false,
    val largeWallpaper: ResourceLocation? = null,
    val frame: ResourceLocation? = null,
    val largeFrame: ResourceLocation? = null,
    val appIcons: Map<String, ResourceLocation> = emptyMap(),
    val appHoverIcons: Map<String, ResourceLocation> = emptyMap()
) {
    constructor(
        id: String,
        name: Component,
        wallpaper: ResourceLocation,
        elements: SmartphoneThemeElements = SmartphoneThemeElements(),
        timeColor: Int,
        order: Int = 100,
        isCobblemonPc: Boolean = false,
        largeWallpaper: ResourceLocation? = null
    ) : this(
        id = id,
        name = name,
        wallpaper = wallpaper,
        elements = elements,
        colors = SmartphoneThemeColors(time = timeColor),
        order = order,
        isCobblemonPc = isCobblemonPc,
        largeWallpaper = largeWallpaper
    )

    val timeColor: Int get() = colors.timeColor
    fun getHomeButtonTexture(): ResourceLocation =
        elements.homeButton ?: DEFAULT_HOME_BUTTON

    fun getPrevButtonTexture(): ResourceLocation =
        elements.prevButton ?: DEFAULT_PREV_BUTTON

    fun getNextButtonTexture(): ResourceLocation =
        elements.nextButton ?: DEFAULT_NEXT_BUTTON

    fun getSettingsButtonTexture(): ResourceLocation =
        elements.settingsButton ?: DEFAULT_SETTINGS_BUTTON

    fun getDragHandleTexture(): ResourceLocation =
        elements.dragHandle ?: DEFAULT_DRAG_HANDLE

    fun getViewSelectTexture(): ResourceLocation =
        elements.viewSelect ?: DEFAULT_VIEW_SELECT

    fun getLargeWallpaperTexture(): ResourceLocation =
        largeWallpaper ?: DEFAULT_LARGE_WALLPAPER

    fun getFrameTexture(defaultColorTexture: ResourceLocation): ResourceLocation =
        frame ?: defaultColorTexture

    fun getLargeFrameTexture(defaultColorTexture: ResourceLocation): ResourceLocation =
        largeFrame ?: defaultColorTexture

    fun getAppIcon(actionId: String, defaultTexture: ResourceLocation): ResourceLocation =
        appIcons[actionId] ?: defaultTexture

    fun getAppHoverIcon(actionId: String, defaultHoverTexture: ResourceLocation): ResourceLocation =
        appHoverIcons[actionId] ?: appIcons[actionId] ?: defaultHoverTexture

    fun getSocialPokeballTexture(): ResourceLocation =
        elements.socialPokeball ?: DEFAULT_POKEBALL_BACKGROUND

    companion object {
        const val DEFAULT_ID = "cobblemon_smartphone:default"

        val DEFAULT_WALLPAPER = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/home_screen.png"
        )
        val DEFAULT_LARGE_WALLPAPER = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/large_screen.png"
        )
        val DEFAULT_HOME_BUTTON = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/home_button.png"
        )
        val DEFAULT_PREV_BUTTON = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/prev_button.png"
        )
        val DEFAULT_NEXT_BUTTON = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/next_button.png"
        )
        val DEFAULT_SETTINGS_BUTTON = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/settings_button.png"
        )
        val DEFAULT_DRAG_HANDLE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/drag_handle.png"
        )
        val DEFAULT_VIEW_SELECT = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/view_select.png"
        )
        val DEFAULT_POKEBALL_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/social/background_poke_ball.png"
        )

        val DEFAULT = SmartphoneTheme(
            id = DEFAULT_ID,
            name = Component.translatable("theme.cobblemon_smartphone.default"),
            wallpaper = DEFAULT_WALLPAPER,
            largeWallpaper = DEFAULT_LARGE_WALLPAPER,
            order = 0
        )
    }
}
