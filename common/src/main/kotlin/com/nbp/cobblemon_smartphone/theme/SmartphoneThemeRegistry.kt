package com.nbp.cobblemon_smartphone.theme

import com.google.gson.JsonParser
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

object SmartphoneThemeRegistry {
    private val themes = mutableListOf<SmartphoneTheme>()
    private var lastResourceManager: ResourceManager? = null

    val STANDARD_APP_BUTTONS = listOf(
        "ae2", "cloud", "cobbledollars", "crafting", "fishnav", "gps", "heal",
        "patchouli", "pc", "pokedex", "pokeinfo", "pokenav", "refinedstorage",
        "rotom", "social", "structure_compass", "toms_storage", "trainer",
        "waystone", "wireless"
    )

    val STANDARD_BUTTON_ACTIONS = mapOf(
        "ae2" to listOf("cobblemon_smartphone:ae2_terminal", "cobblemon_smartphone:ae2_crafting_terminal"),
        "cloud" to listOf("cobblemon_smartphone:cloud"),
        "cobbledollars" to listOf("cobblemon_smartphone:cobbledollars_shop"),
        "crafting" to listOf("cobblemon_smartphone:crafting"),
        "fishnav" to listOf("cobblemon_smartphone:cobblenav_fishingnav"),
        "gps" to listOf("cobblemon_smartphone:gps"),
        "heal" to listOf("cobblemon_smartphone:heal"),
        "pc" to listOf("cobblemon_smartphone:pc"),
        "pokedex" to listOf("cobblemon_smartphone:pokedex"),
        "pokeinfo" to listOf("cobblemon_smartphone:pokeinfo"),
        "pokenav" to listOf("cobblemon_smartphone:cobblenav_pokenav"),
        "refinedstorage" to listOf("cobblemon_smartphone:refinedstorage_grid"),
        "rotom" to listOf("cobblemon_smartphone:rotom"),
        "social" to listOf("cobblemon_smartphone:social"),
        "structure_compass" to listOf("cobblemon_smartphone:structure_compass"),
        "toms_storage" to listOf("cobblemon_smartphone:toms_storage_terminal"),
        "trainer" to listOf("cobblemon_smartphone:rct_trainer_card"),
        "waystone" to listOf("cobblemon_smartphone:waystones_warp_stone"),
        "wireless" to listOf("cobblemon_smartphone:wireless")
    )

    fun getThemes(): List<SmartphoneTheme> {
        val rm = try { net.minecraft.client.Minecraft.getInstance()?.resourceManager } catch (_: Exception) { null }
        if (rm != null && (lastResourceManager !== rm || themes.isEmpty())) {
            ensureLoaded(rm)
        }
        return themes.toList()
    }

    fun getTheme(id: String?): SmartphoneTheme {
        if (id == null) return SmartphoneTheme.DEFAULT
        val rm = try { net.minecraft.client.Minecraft.getInstance()?.resourceManager } catch (_: Exception) { null }
        if (rm != null && (lastResourceManager !== rm || themes.isEmpty())) {
            ensureLoaded(rm)
        }
        return themes.firstOrNull { it.id == id } ?: SmartphoneTheme.DEFAULT
    }

    fun ensureLoaded(resourceManager: ResourceManager) {
        if (lastResourceManager !== resourceManager || themes.isEmpty()) {
            reload(resourceManager)
        }
    }

    private fun isTexturePresent(resourceManager: ResourceManager, loc: ResourceLocation?): Boolean {
        if (loc == null) return false
        return try {
            resourceManager.getResource(loc).isPresent
        } catch (_: Exception) {
            false
        }
    }

    fun reload(resourceManager: ResourceManager) {
        lastResourceManager = resourceManager
        val list = mutableListOf<SmartphoneTheme>()

        // 1. Default theme (always available)
        list.add(SmartphoneTheme.DEFAULT)

        // 2. Scan JSON definitions: assets/<namespace>/themes/*.json & assets/<namespace>/wallpapers/*.json
        val jsonDirs = listOf("themes", "wallpapers")
        for (dir in jsonDirs) {
            val jsonResources = resourceManager.listResources(dir) { it.path.endsWith(".json") }
            for ((location, resource) in jsonResources) {
                try {
                    resource.open().use { stream ->
                        val reader = stream.bufferedReader()
                        val obj = JsonParser.parseReader(reader).asJsonObject

                        val rawId = obj.get("id")?.asString
                        val id = if (!rawId.isNullOrBlank()) rawId else {
                            val pathWithoutExt = location.path.removePrefix("$dir/").removeSuffix(".json")
                            "${location.namespace}:$pathWithoutExt"
                        }

                        val nameStr = obj.get("name")?.asString ?: "theme.$id"
                        val name = if (nameStr.contains('.')) Component.translatable(nameStr) else Component.literal(nameStr)

                        val wallpaperStr = obj.get("wallpaper")?.asString
                            ?: obj.get("texture")?.asString
                            ?: return@use
                        val parsedWallpaperLoc = ResourceLocation.tryParse(wallpaperStr) ?: return@use

                        // Fallback check: if the wallpaper texture doesn't exist, log and fall back to default wallpaper
                        val wallpaperLoc = if (isTexturePresent(resourceManager, parsedWallpaperLoc)) {
                            parsedWallpaperLoc
                        } else {
                            CobblemonSmartphone.LOGGER.warn(
                                "Theme '{}' specifies missing wallpaper texture '{}'. Falling back to default wallpaper.",
                                id,
                                parsedWallpaperLoc
                            )
                            SmartphoneTheme.DEFAULT_WALLPAPER
                        }

                        // Large wallpaper check
                        val largeWallpaperStr = obj.get("large_wallpaper")?.asString
                            ?: obj.get("large_texture")?.asString
                        var largeWallpaperLoc: ResourceLocation? = null
                        if (largeWallpaperStr != null) {
                            val parsedLarge = ResourceLocation.tryParse(largeWallpaperStr)
                            if (parsedLarge != null && isTexturePresent(resourceManager, parsedLarge)) {
                                largeWallpaperLoc = parsedLarge
                            } else if (parsedLarge != null) {
                                CobblemonSmartphone.LOGGER.warn(
                                    "Theme '{}' specifies missing large wallpaper texture '{}'. Falling back to default.",
                                    id,
                                    parsedLarge
                                )
                            }
                        }
                        if (largeWallpaperLoc == null) {
                            val autoLarge = ResourceLocation.fromNamespaceAndPath(
                                wallpaperLoc.namespace,
                                wallpaperLoc.path.removeSuffix(".png") + "_large.png"
                            )
                            if (isTexturePresent(resourceManager, autoLarge)) {
                                largeWallpaperLoc = autoLarge
                            }
                        }

                        // Custom phone frame textures (bezel/case)
                        val frameStr = obj.get("frame")?.asString
                        var frameLoc: ResourceLocation? = null
                        if (frameStr != null) {
                            val parsedFrame = ResourceLocation.tryParse(frameStr)
                            if (parsedFrame != null && isTexturePresent(resourceManager, parsedFrame)) {
                                frameLoc = parsedFrame
                            } else if (parsedFrame != null) {
                                CobblemonSmartphone.LOGGER.warn(
                                    "Theme '{}' specifies missing frame texture '{}'. Falling back to default.",
                                    id,
                                    parsedFrame
                                )
                            }
                        }

                        val largeFrameStr = obj.get("large_frame")?.asString
                        var largeFrameLoc: ResourceLocation? = null
                        if (largeFrameStr != null) {
                            val parsedLarge = ResourceLocation.tryParse(largeFrameStr)
                            if (parsedLarge != null && isTexturePresent(resourceManager, parsedLarge)) {
                                largeFrameLoc = parsedLarge
                            } else if (parsedLarge != null) {
                                CobblemonSmartphone.LOGGER.warn(
                                    "Theme '{}' specifies missing large frame texture '{}'. Falling back to default.",
                                    id,
                                    parsedLarge
                                )
                            }
                        }

                        val order = obj.get("order")?.asInt ?: 50
                        val isPc = obj.get("is_cobblemon_pc")?.asBoolean ?: false

                        // Elements: validate existence or fallback to null (which uses default button texture)
                        fun parseElement(elementName: String, elObj: com.google.gson.JsonObject): ResourceLocation? {
                            val elStr = elObj.get(elementName)?.asString ?: return null
                            val loc = ResourceLocation.tryParse(elStr) ?: return null
                            return if (isTexturePresent(resourceManager, loc)) {
                                loc
                            } else {
                                CobblemonSmartphone.LOGGER.warn(
                                    "Theme '{}' element '{}' specifies missing texture '{}'. Falling back to default.",
                                    id,
                                    elementName,
                                    loc
                                )
                                null
                            }
                        }

                        var homeBtn: ResourceLocation? = null
                        var prevBtn: ResourceLocation? = null
                        var nextBtn: ResourceLocation? = null
                        var settingsBtn: ResourceLocation? = null
                        var dragHandle: ResourceLocation? = null
                        var viewSelect: ResourceLocation? = null
                        var socialPokeball: ResourceLocation? = null

                        if (obj.has("elements") && obj.get("elements").isJsonObject) {
                            val elObj = obj.getAsJsonObject("elements")
                            homeBtn = parseElement("home_button", elObj)
                            prevBtn = parseElement("prev_button", elObj)
                            nextBtn = parseElement("next_button", elObj)
                            settingsBtn = parseElement("settings_button", elObj)
                            dragHandle = parseElement("drag_handle", elObj)
                            viewSelect = parseElement("view_select", elObj)
                            socialPokeball = parseElement("social_pokeball", elObj)
                        }

                        // App icons
                        val appIcons = mutableMapOf<String, ResourceLocation>()
                        val appHoverIcons = mutableMapOf<String, ResourceLocation>()

                        if (obj.has("app_icons") && obj.get("app_icons").isJsonObject) {
                            val iconsObj = obj.getAsJsonObject("app_icons")
                            for ((key, elem) in iconsObj.entrySet()) {
                                if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                                    val loc = ResourceLocation.tryParse(elem.asString)
                                    if (loc != null && isTexturePresent(resourceManager, loc)) {
                                        if (key.endsWith("_hover")) {
                                            appHoverIcons[key.removeSuffix("_hover")] = loc
                                        } else {
                                            appIcons[key] = loc
                                        }
                                    }
                                } else if (elem.isJsonObject) {
                                    val nested = elem.asJsonObject
                                    val texStr = nested.get("texture")?.asString
                                    val hovStr = nested.get("hover")?.asString
                                    if (texStr != null) {
                                        val loc = ResourceLocation.tryParse(texStr)
                                        if (loc != null && isTexturePresent(resourceManager, loc)) {
                                            appIcons[key] = loc
                                        }
                                    }
                                    if (hovStr != null) {
                                        val hovLoc = ResourceLocation.tryParse(hovStr)
                                        if (hovLoc != null && isTexturePresent(resourceManager, hovLoc)) {
                                            appHoverIcons[key] = hovLoc
                                        }
                                    }
                                }
                            }
                        }

                        if (obj.has("app_hover_icons") && obj.get("app_hover_icons").isJsonObject) {
                            val hoverObj = obj.getAsJsonObject("app_hover_icons")
                            for ((key, elem) in hoverObj.entrySet()) {
                                if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                                    val loc = ResourceLocation.tryParse(elem.asString)
                                    if (loc != null && isTexturePresent(resourceManager, loc)) {
                                        appHoverIcons[key] = loc
                                    }
                                }
                            }
                        }

                        // Auto-discover themed buttons and elements if available for this theme
                        val themeSlug = id.substringAfter(':')
                        for (btn in STANDARD_APP_BUTTONS) {
                            val btnLoc = ResourceLocation.fromNamespaceAndPath(
                                CobblemonSmartphone.ID,
                                "textures/gui/themes/$themeSlug/buttons/$btn.png"
                            )
                            if (isTexturePresent(resourceManager, btnLoc)) {
                                if (!appIcons.containsKey(btn)) appIcons[btn] = btnLoc
                                if (!appIcons.containsKey("$btn.png")) appIcons["$btn.png"] = btnLoc
                                STANDARD_BUTTON_ACTIONS[btn]?.forEach { actId ->
                                    if (!appIcons.containsKey(actId)) appIcons[actId] = btnLoc
                                }
                            }
                            val hoverLoc = ResourceLocation.fromNamespaceAndPath(
                                CobblemonSmartphone.ID,
                                "textures/gui/themes/$themeSlug/buttons/${btn}_hover.png"
                            )
                            if (isTexturePresent(resourceManager, hoverLoc)) {
                                if (!appHoverIcons.containsKey(btn)) appHoverIcons[btn] = hoverLoc
                                if (!appHoverIcons.containsKey("${btn}_hover.png")) appHoverIcons["${btn}_hover.png"] = hoverLoc
                                STANDARD_BUTTON_ACTIONS[btn]?.forEach { actId ->
                                    if (!appHoverIcons.containsKey(actId)) appHoverIcons[actId] = hoverLoc
                                }
                            }
                        }

                        if (homeBtn == null) {
                            val loc = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/themes/$themeSlug/elements/home_button.png")
                            if (isTexturePresent(resourceManager, loc)) homeBtn = loc
                        }
                        if (prevBtn == null) {
                            val loc = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/themes/$themeSlug/elements/prev_button.png")
                            if (isTexturePresent(resourceManager, loc)) prevBtn = loc
                        }
                        if (nextBtn == null) {
                            val loc = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/themes/$themeSlug/elements/next_button.png")
                            if (isTexturePresent(resourceManager, loc)) nextBtn = loc
                        }
                        if (settingsBtn == null) {
                            val loc = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/themes/$themeSlug/elements/settings_button.png")
                            if (isTexturePresent(resourceManager, loc)) settingsBtn = loc
                        }

                        // Colors
                        val baseColors = SmartphoneThemeColors.DEFAULT
                        var themeColors = baseColors
                        if (obj.has("colors") && obj.get("colors").isJsonObject) {
                            val colObj = obj.getAsJsonObject("colors")
                            themeColors = SmartphoneThemeColors(
                                primary = SmartphoneThemeColors.parseHex(colObj.get("primary")?.asString) ?: baseColors.primary,
                                primaryHover = SmartphoneThemeColors.parseHex(colObj.get("primary_hover")?.asString) ?: baseColors.primaryHover,
                                primaryBorder = SmartphoneThemeColors.parseHex(colObj.get("primary_border")?.asString) ?: baseColors.primaryBorder,
                                surface = SmartphoneThemeColors.parseHex(colObj.get("surface")?.asString) ?: baseColors.surface,
                                surfaceAlt = SmartphoneThemeColors.parseHex(colObj.get("surface_alt")?.asString) ?: baseColors.surfaceAlt,
                                surfaceHover = SmartphoneThemeColors.parseHex(colObj.get("surface_hover")?.asString) ?: baseColors.surfaceHover,
                                shadow = SmartphoneThemeColors.parseHex(colObj.get("shadow")?.asString) ?: baseColors.shadow,
                                headerNavy = SmartphoneThemeColors.parseHex(colObj.get("header_navy")?.asString) ?: baseColors.headerNavy,
                                headerNavyLight = SmartphoneThemeColors.parseHex(colObj.get("header_navy_light")?.asString) ?: baseColors.headerNavyLight,
                                textPrimary = SmartphoneThemeColors.parseHex(colObj.get("text_primary")?.asString) ?: baseColors.textPrimary,
                                textSecondary = SmartphoneThemeColors.parseHex(colObj.get("text_secondary")?.asString) ?: baseColors.textSecondary,
                                textMuted = SmartphoneThemeColors.parseHex(colObj.get("text_muted")?.asString) ?: baseColors.textMuted,
                                textDark = SmartphoneThemeColors.parseHex(colObj.get("text_dark")?.asString) ?: baseColors.textDark,
                                textDarkMuted = SmartphoneThemeColors.parseHex(colObj.get("text_dark_muted")?.asString) ?: baseColors.textDarkMuted,
                                gold = SmartphoneThemeColors.parseHex(colObj.get("gold")?.asString) ?: baseColors.gold,
                                danger = SmartphoneThemeColors.parseHex(colObj.get("danger")?.asString) ?: baseColors.danger,
                                success = SmartphoneThemeColors.parseHex(colObj.get("success")?.asString) ?: baseColors.success,
                                time = SmartphoneThemeColors.parseHex(colObj.get("time")?.asString) ?: baseColors.time,
                                batteryBorder = SmartphoneThemeColors.parseHex(colObj.get("battery_border")?.asString) ?: baseColors.batteryBorder,
                                batteryEmpty = SmartphoneThemeColors.parseHex(colObj.get("battery_empty")?.asString) ?: baseColors.batteryEmpty,
                                batteryHigh = SmartphoneThemeColors.parseHex(colObj.get("battery_high")?.asString ?: colObj.get("battery_fill")?.asString) ?: baseColors.batteryHigh,
                                batteryMed = SmartphoneThemeColors.parseHex(colObj.get("battery_med")?.asString) ?: baseColors.batteryMed,
                                batteryLow = SmartphoneThemeColors.parseHex(colObj.get("battery_low")?.asString) ?: baseColors.batteryLow
                            )
                        }

                        val themeElements = SmartphoneThemeElements(
                            homeButton = homeBtn,
                            prevButton = prevBtn,
                            nextButton = nextBtn,
                            settingsButton = settingsBtn,
                            dragHandle = dragHandle,
                            viewSelect = viewSelect,
                            socialPokeball = socialPokeball
                        )

                        list.removeAll { it.id == id }
                        list.removeAll { it.id == id }
                        list.add(
                            SmartphoneTheme(
                                id = id,
                                name = name,
                                wallpaper = wallpaperLoc,
                                elements = themeElements,
                                colors = themeColors,
                                order = order,
                                isCobblemonPc = isPc,
                                largeWallpaper = largeWallpaperLoc,
                                frame = frameLoc,
                                largeFrame = largeFrameLoc,
                                appIcons = appIcons,
                                appHoverIcons = appHoverIcons
                            )
                        )
                    }
                } catch (e: Exception) {
                    CobblemonSmartphone.LOGGER.warn("Failed to load theme definition {}: {}", location, e.message)
                }
            }
        }

        // 4. Auto-discover PNG wallpapers/themes: assets/<namespace>/textures/gui/themes/*.png and wallpapers/*.png
        val pngDirs = listOf("textures/gui/themes", "textures/gui/wallpapers")
        for (dir in pngDirs) {
            val pngResources = resourceManager.listResources(dir) {
                it.path.endsWith(".png") && !it.path.contains("/buttons/") && !it.path.contains("/elements/")
            }
            val (largePngs, regularPngs) = pngResources.entries.partition {
                it.key.path.endsWith("_large.png") || it.key.path.endsWith("-large.png")
            }

            for ((location, _) in regularPngs) {
                val baseName = location.path.substringAfterLast('/').removeSuffix(".png")
                val id = "${location.namespace}:$baseName"
                if (list.none { it.id == id || it.wallpaper == location }) {
                    val translationKey = "theme.${location.namespace}.${baseName}"
                    val prettyName = baseName.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    val name = Component.translatable(translationKey).let {
                        if (it.string == translationKey) Component.literal(prettyName) else it
                    }

                    // Check if there is a matching large PNG
                    val matchingLarge = largePngs.firstOrNull {
                        val largeBase = it.key.path.substringAfterLast('/').removeSuffix("_large.png").removeSuffix("-large.png")
                        it.key.namespace == location.namespace && largeBase == baseName
                    }?.key

                    list.add(SmartphoneTheme(id, name, location, order = 100, largeWallpaper = matchingLarge))
                }
            }
        }

        list.sortBy { it.order }
        themes.clear()
        themes.addAll(list)
    }
}
