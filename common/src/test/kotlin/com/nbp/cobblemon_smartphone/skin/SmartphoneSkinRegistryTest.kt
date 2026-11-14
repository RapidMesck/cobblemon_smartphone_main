package com.nbp.cobblemon_smartphone.skin

import com.google.gson.JsonParser
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmartphoneSkinRegistryTest {

    @AfterEach
    fun cleanup() {
        SmartphoneSkinRegistry.clearDatapackSkins()
    }

    @Test
    fun `parseSkinJson parses hand, rotom, inventory models and colors`() {
        val json = JsonParser.parseString(
            """
            {
              "name": "Pikachu Case",
              "hand_model": "my_pack:item/pikachu_hand",
              "rotom_model": "my_pack:item/pikachu_rotom",
              "inventory_model": "my_pack:item/pikachu_inv",
              "colors": ["RED", "yellow"]
            }
            """.trimIndent()
        ).asJsonObject

        val location = ResourceLocation.fromNamespaceAndPath("my_pack", "smartphone_skins/pikachu_case.json")
        val skin = SmartphoneSkinRegistry.parseSkinJson(location, json, null)
        assertNotNull(skin)
        skin!!
        assertEquals("my_pack:pikachu_case", skin.id)
        assertEquals("Pikachu Case", skin.name.string)
        assertEquals(ResourceLocation.fromNamespaceAndPath("my_pack", "item/pikachu_hand"), skin.handModel)
        assertEquals(ResourceLocation.fromNamespaceAndPath("my_pack", "item/pikachu_rotom"), skin.rotomModel)
        assertEquals(ResourceLocation.fromNamespaceAndPath("my_pack", "item/pikachu_inv"), skin.inventoryModel)
        assertTrue(skin.supportsColor(SmartphoneColor.RED))
        assertTrue(skin.supportsColor(SmartphoneColor.YELLOW))
        assertFalse(skin.supportsColor(SmartphoneColor.BLUE))
    }

    @Test
    fun `parseSkinJson rejects skin without any model`() {
        val json = JsonParser.parseString("""{"name": "Empty"}""").asJsonObject
        val location = ResourceLocation.fromNamespaceAndPath("my_pack", "smartphone_skins/empty.json")
        assertNull(SmartphoneSkinRegistry.parseSkinJson(location, json, null))
    }

    @Test
    fun `parseSkinJson parses locked and require_upgrade fields`() {
        val json = JsonParser.parseString(
            """
            {
              "name": "Locked Case",
              "hand_model": "my_pack:item/locked_hand",
              "locked": true,
              "require_upgrade": "case_unlock"
            }
            """.trimIndent()
        ).asJsonObject
        val location = ResourceLocation.fromNamespaceAndPath("my_pack", "smartphone_skins/locked_case.json")
        val skin = SmartphoneSkinRegistry.parseSkinJson(location, json, null)
        assertNotNull(skin)
        assertTrue(skin!!.locked)
        assertEquals("case_unlock", skin.requiredUpgrade)
    }

    @Test
    fun `parseSkinJson parses frame and large_frame textures`() {
        val json = JsonParser.parseString(
            """
            {
              "name": "Framed Case",
              "frame": "my_pack:textures/gui/framed.png",
              "large_frame": "my_pack:textures/gui/framed_large.png"
            }
            """.trimIndent()
        ).asJsonObject
        val location = ResourceLocation.fromNamespaceAndPath("my_pack", "smartphone_skins/framed_case.json")
        val skin = SmartphoneSkinRegistry.parseSkinJson(location, json, null)
        assertNotNull(skin)
        assertEquals(ResourceLocation.fromNamespaceAndPath("my_pack", "textures/gui/framed.png"), skin!!.frameTexture)
        assertEquals(ResourceLocation.fromNamespaceAndPath("my_pack", "textures/gui/framed_large.png"), skin.largeFrameTexture)
    }

    @Test
    fun `datapack skins appear in getSkins and getAllModelLocations`() {
        val rotomOnly = SmartphoneSkin(
            id = "pack:rotom_gold",
            name = net.minecraft.network.chat.Component.literal("Gold Rotom"),
            rotomModel = ResourceLocation.fromNamespaceAndPath("pack", "item/rotom_gold"),
            allowedColors = setOf("gold", "yellow")
        )
        val universal = SmartphoneSkin(
            id = "pack:carbon",
            name = net.minecraft.network.chat.Component.literal("Carbon"),
            handModel = ResourceLocation.fromNamespaceAndPath("pack", "item/carbon_hand"),
            rotomModel = ResourceLocation.fromNamespaceAndPath("pack", "item/carbon_rotom")
        )

        SmartphoneSkinRegistry.updateDatapackSkins(listOf(rotomOnly, universal))

        val blueSkins = SmartphoneSkinRegistry.getSkins(SmartphoneColor.BLUE).map { it.id }
        assertTrue(SmartphoneSkin.DEFAULT_ID in blueSkins)
        assertTrue("pack:carbon" in blueSkins)
        assertFalse("pack:rotom_gold" in blueSkins)

        val yellowSkins = SmartphoneSkinRegistry.getSkins(SmartphoneColor.YELLOW).map { it.id }
        assertTrue("pack:rotom_gold" in yellowSkins)
        assertTrue("pack:carbon" in yellowSkins)

        val models = SmartphoneSkinRegistry.getAllModelLocations()
        assertTrue(ResourceLocation.fromNamespaceAndPath("pack", "item/rotom_gold") in models)
        assertTrue(ResourceLocation.fromNamespaceAndPath("pack", "item/carbon_hand") in models)
        assertTrue(ResourceLocation.fromNamespaceAndPath("pack", "item/carbon_rotom") in models)
    }
}
