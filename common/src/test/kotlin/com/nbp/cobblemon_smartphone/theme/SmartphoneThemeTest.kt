package com.nbp.cobblemon_smartphone.theme

import com.google.gson.JsonParser
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SmartphoneThemeTest {

    @Test
    fun `default theme has standard textures and defaults`() {
        val def = SmartphoneTheme.DEFAULT
        assertEquals(SmartphoneTheme.DEFAULT_ID, def.id)
        assertEquals(SmartphoneTheme.DEFAULT_WALLPAPER, def.wallpaper)
        assertEquals(SmartphoneTheme.DEFAULT_HOME_BUTTON, def.getHomeButtonTexture())
        assertEquals(SmartphoneTheme.DEFAULT_PREV_BUTTON, def.getPrevButtonTexture())
        assertEquals(SmartphoneTheme.DEFAULT_NEXT_BUTTON, def.getNextButtonTexture())
        assertEquals(SmartphoneTheme.DEFAULT_SETTINGS_BUTTON, def.getSettingsButtonTexture())
        assertEquals(SmartphoneTheme.DEFAULT_DRAG_HANDLE, def.getDragHandleTexture())
        assertEquals(SmartphoneTheme.DEFAULT_VIEW_SELECT, def.getViewSelectTexture())
        assertEquals(SmartphoneTheme.DEFAULT_LARGE_WALLPAPER, def.getLargeWallpaperTexture())
    }

    @Test
    fun `custom theme elements override default button textures`() {
        val customHome = ResourceLocation.fromNamespaceAndPath("custom_pack", "textures/gui/custom_home.png")
        val customDrag = ResourceLocation.fromNamespaceAndPath("custom_pack", "textures/gui/custom_drag.png")
        val elements = SmartphoneThemeElements(
            homeButton = customHome,
            dragHandle = customDrag
        )

        val theme = SmartphoneTheme(
            id = "custom_pack:custom_theme",
            name = Component.literal("Custom"),
            wallpaper = ResourceLocation.fromNamespaceAndPath("custom_pack", "textures/gui/wp.png"),
            elements = elements,
            timeColor = 0xFF123456.toInt()
        )

        assertEquals(customHome, theme.getHomeButtonTexture())
        assertEquals(customDrag, theme.getDragHandleTexture())
        // Others should fall back to default
        assertEquals(SmartphoneTheme.DEFAULT_PREV_BUTTON, theme.getPrevButtonTexture())
        assertEquals(SmartphoneTheme.DEFAULT_NEXT_BUTTON, theme.getNextButtonTexture())
        assertEquals(SmartphoneTheme.DEFAULT_SETTINGS_BUTTON, theme.getSettingsButtonTexture())
        assertEquals(SmartphoneTheme.DEFAULT_VIEW_SELECT, theme.getViewSelectTexture())
        assertEquals(0xFF123456.toInt(), theme.timeColor)
    }

    @Test
    fun `theme json definition parses correctly`() {
        val json = """
        {
          "id": "my_pack:cyberpunk",
          "name": "theme.my_pack.cyberpunk",
          "wallpaper": "my_pack:textures/gui/cyberpunk_bg.png",
          "elements": {
            "home_button": "my_pack:textures/gui/elements/home.png",
            "prev_button": "my_pack:textures/gui/elements/prev.png"
          },
          "colors": {
            "time": "#00FFAA"
          },
          "order": 5
        }
        """.trimIndent()

        val obj = JsonParser.parseString(json).asJsonObject
        val id = obj.get("id").asString
        val wallpaperLoc = ResourceLocation.tryParse(obj.get("wallpaper").asString)
        val elementsObj = obj.getAsJsonObject("elements")
        val homeBtn = ResourceLocation.tryParse(elementsObj.get("home_button").asString)
        val prevBtn = ResourceLocation.tryParse(elementsObj.get("prev_button").asString)
        val timeColor = (0xFF000000 or java.lang.Long.parseLong(obj.getAsJsonObject("colors").get("time").asString.removePrefix("#"), 16)).toInt()

        val theme = SmartphoneTheme(
            id = id,
            name = Component.translatable(obj.get("name").asString),
            wallpaper = wallpaperLoc!!,
            elements = SmartphoneThemeElements(homeButton = homeBtn, prevButton = prevBtn),
            timeColor = timeColor,
            order = obj.get("order").asInt
        )

        assertEquals("my_pack:cyberpunk", theme.id)
        assertEquals(ResourceLocation.tryParse("my_pack:textures/gui/cyberpunk_bg.png"), theme.wallpaper)
        assertEquals(homeBtn, theme.getHomeButtonTexture())
        assertEquals(prevBtn, theme.getPrevButtonTexture())
        assertEquals(SmartphoneTheme.DEFAULT_NEXT_BUTTON, theme.getNextButtonTexture())
        assertEquals(5, theme.order)
    }

    @Test
    fun `large wallpaper resolves custom or falls back properly`() {
        val wp = ResourceLocation.fromNamespaceAndPath("test", "textures/gui/test.png")
        val largeWp = ResourceLocation.fromNamespaceAndPath("test", "textures/gui/test_large.png")

        val withLarge = SmartphoneTheme(
            id = "test:large",
            name = Component.literal("Test Large"),
            wallpaper = wp,
            largeWallpaper = largeWp
        )
        assertEquals(largeWp, withLarge.getLargeWallpaperTexture())

        val withoutLarge = SmartphoneTheme(
            id = "test:normal",
            name = Component.literal("Test Normal"),
            wallpaper = wp,
            largeWallpaper = null
        )
        assertEquals(SmartphoneTheme.DEFAULT_LARGE_WALLPAPER, withoutLarge.getLargeWallpaperTexture())
    }

    @Test
    fun `getTheme returns default theme when id is null or not found`() {
        assertEquals(SmartphoneTheme.DEFAULT, SmartphoneThemeRegistry.getTheme(null))
        assertEquals(SmartphoneTheme.DEFAULT, SmartphoneThemeRegistry.getTheme("non_existent:theme_id"))
    }

    @Test
    fun `parseHex handles various hex formats and invalid inputs`() {
        assertEquals(0xFF5BD1DA.toInt(), SmartphoneThemeColors.parseHex("#5BD1DA"))
        assertEquals(0xFF5BD1DA.toInt(), SmartphoneThemeColors.parseHex("5BD1DA"))
        assertEquals(0x88071322.toInt(), SmartphoneThemeColors.parseHex("#88071322"))
        assertNull(SmartphoneThemeColors.parseHex(null))
        assertNull(SmartphoneThemeColors.parseHex(""))
        assertNull(SmartphoneThemeColors.parseHex("invalid_hex"))
    }

    @Test
    fun `colors fallback to defaults when omitted in json`() {
        val json = """
        {
          "id": "my_pack:partial_colors",
          "name": "Partial Colors",
          "wallpaper": "my_pack:textures/gui/bg.png",
          "colors": {
            "primary": "#FF1122"
          }
        }
        """.trimIndent()

        val obj = JsonParser.parseString(json).asJsonObject
        val colObj = obj.getAsJsonObject("colors")
        val baseColors = SmartphoneThemeColors.DEFAULT
        val parsedColors = SmartphoneThemeColors(
            primary = SmartphoneThemeColors.parseHex(colObj.get("primary")?.asString) ?: baseColors.primary,
            surface = SmartphoneThemeColors.parseHex(colObj.get("surface")?.asString) ?: baseColors.surface
        )

        assertEquals(0xFFFF1122.toInt(), parsedColors.primary)
        assertEquals(SmartphoneThemeColors.DEFAULT.surface, parsedColors.surface)
        assertEquals(SmartphoneThemeColors.DEFAULT.gold, parsedColors.gold)
    }

    @Test
    fun `timeColor uses time override if present else textSecondary`() {
        val withTimeOverride = SmartphoneThemeColors(time = 0xFFAABBCC.toInt(), textSecondary = 0xFF112233.toInt())
        assertEquals(0xFFAABBCC.toInt(), withTimeOverride.timeColor)

        val withoutTimeOverride = SmartphoneThemeColors(time = null, textSecondary = 0xFF112233.toInt())
        assertEquals(0xFF112233.toInt(), withoutTimeOverride.timeColor)
    }

    @Test
    fun `battery colors support custom palette and default fallbacks`() {
        val def = SmartphoneThemeColors.DEFAULT
        assertEquals(0xFF5BD1DA.toInt(), def.batteryBorder)
        assertEquals(0xFF1D4A59.toInt(), def.batteryEmpty)
        assertEquals(0xFF22C55E.toInt(), def.batteryHigh)
        assertEquals(0xFFEAB308.toInt(), def.batteryMed)
        assertEquals(0xFFEF4444.toInt(), def.batteryLow)

        val rotom = SmartphoneThemeColors.ROTOM
        assertEquals(0xFFF97316.toInt(), rotom.batteryBorder)
        assertEquals(0xFF160B07.toInt(), rotom.batteryEmpty)
        assertEquals(0xFF2EB85C.toInt(), rotom.batteryHigh)

        val dark = SmartphoneThemeColors.DARK
        assertEquals(0xFF5BD1DA.toInt(), dark.batteryBorder)
        assertEquals(0xFF111114.toInt(), dark.batteryEmpty)
        assertEquals(0xFF48BB78.toInt(), dark.batteryHigh)
    }

    @Test
    fun `custom frames and app icons resolve or fallback properly`() {
        val defaultSmallFrame = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/smartphone_red.png")
        val defaultLargeFrame = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/large_smartphone_red.png")
        val customFrame = ResourceLocation.fromNamespaceAndPath("custom", "textures/gui/custom_frame.png")
        val customLargeFrame = ResourceLocation.fromNamespaceAndPath("custom", "textures/gui/custom_large_frame.png")
        val customPokedexIcon = ResourceLocation.fromNamespaceAndPath("custom", "textures/gui/pokedex.png")
        val customPokedexHover = ResourceLocation.fromNamespaceAndPath("custom", "textures/gui/pokedex_hover.png")
        val defaultPokedexIcon = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/buttons/pokedex.png")
        val defaultPokedexHover = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/buttons/pokedex_hover.png")

        val theme = SmartphoneTheme(
            id = "custom:theme",
            name = Component.literal("Custom"),
            wallpaper = ResourceLocation.fromNamespaceAndPath("custom", "textures/gui/wp.png"),
            frame = customFrame,
            largeFrame = customLargeFrame,
            appIcons = mapOf("cobblemon_smartphone:pokedex" to customPokedexIcon),
            appHoverIcons = mapOf("cobblemon_smartphone:pokedex" to customPokedexHover),
            elements = SmartphoneThemeElements(
                socialPokeball = ResourceLocation.fromNamespaceAndPath("custom", "textures/gui/ball.png")
            )
        )

        assertEquals(customFrame, theme.getFrameTexture(defaultSmallFrame))
        assertEquals(customLargeFrame, theme.getLargeFrameTexture(defaultLargeFrame))
        assertEquals(customPokedexIcon, theme.getAppIcon("cobblemon_smartphone:pokedex", defaultPokedexIcon))
        assertEquals(customPokedexHover, theme.getAppHoverIcon("cobblemon_smartphone:pokedex", defaultPokedexHover))
        // Non-overridden app falls back
        assertEquals(defaultPokedexIcon, theme.getAppIcon("cobblemon_smartphone:social", defaultPokedexIcon))
        assertEquals(ResourceLocation.fromNamespaceAndPath("custom", "textures/gui/ball.png"), theme.getSocialPokeballTexture())

        // Default theme fallbacks
        val def = SmartphoneTheme.DEFAULT
        assertEquals(defaultSmallFrame, def.getFrameTexture(defaultSmallFrame))
        assertEquals(defaultLargeFrame, def.getLargeFrameTexture(defaultLargeFrame))
        assertEquals(SmartphoneTheme.DEFAULT_POKEBALL_BACKGROUND, def.getSocialPokeballTexture())
    }

    @Test
    fun `resolveCurrentTheme respects clientPreferredTheme`() {
        SmartphoneThemeHelper.clientPreferredTheme = "cobblemon_smartphone:red"
        val resolved = SmartphoneThemeHelper.resolveCurrentTheme(null)
        assertNotNull(resolved)
        SmartphoneThemeHelper.clientPreferredTheme = null
        assertEquals(SmartphoneTheme.DEFAULT, SmartphoneThemeHelper.resolveCurrentTheme(null))
    }

    @Test
    fun `resolveCurrentTheme does not automatically change theme based on contextColor or item when unset`() {
        com.nbp.cobblemon_smartphone.util.SmartphoneHelper.contextColor = com.nbp.cobblemon_smartphone.item.SmartphoneColor.RED
        assertEquals(SmartphoneTheme.DEFAULT, SmartphoneThemeHelper.resolveCurrentTheme(null))
        com.nbp.cobblemon_smartphone.util.SmartphoneHelper.contextColor = null
    }

    @Test
    fun `isDark and semantic helpers differentiate light and dark surfaces`() {
        val lightTheme = SmartphoneThemeColors.DEFAULT
        assertFalse(lightTheme.isDark)
        assertEquals(0xFFB8860B.toInt(), lightTheme.contentGold)
        assertEquals(0xFFB9C4C6.toInt(), lightTheme.cardDisabledContentBg)

        val darkTheme = SmartphoneThemeColors.DARK
        assertTrue(darkTheme.isDark)
        assertEquals(darkTheme.gold, darkTheme.contentGold)
        assertEquals(darkTheme.surfaceAlt, darkTheme.cardDisabledContentBg)
    }

    @Test
    fun `getAppIcon resolves by actionId, filename or basename`() {
        val rotomDefault = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/buttons/rotom.png")
        val rotomHoverDefault = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/buttons/rotom_hover.png")
        val redRotom = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/themes/red/buttons/rotom.png")
        val redRotomHover = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/themes/red/buttons/rotom_hover.png")

        val theme = SmartphoneTheme(
            id = "cobblemon_smartphone:red",
            name = Component.literal("Red"),
            wallpaper = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/wallpapers/red.png"),
            appIcons = mapOf("rotom.png" to redRotom),
            appHoverIcons = mapOf("rotom_hover.png" to redRotomHover)
        )

        // Resolves via fileName fallback
        assertEquals(redRotom, theme.getAppIcon("cobblemon_smartphone:rotom", rotomDefault))
        assertEquals(redRotomHover, theme.getAppHoverIcon("cobblemon_smartphone:rotom", rotomHoverDefault))

        // Non-mapped action falls back cleanly
        val craftingDefault = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/buttons/crafting.png")
        assertEquals(craftingDefault, theme.getAppIcon("cobblemon_smartphone:crafting", craftingDefault))
    }
}
