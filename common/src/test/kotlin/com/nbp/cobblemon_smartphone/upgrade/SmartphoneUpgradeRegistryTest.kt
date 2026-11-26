package com.nbp.cobblemon_smartphone.upgrade

import com.google.gson.JsonParser
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SmartphoneUpgradeRegistryTest {

    @Test
    fun `default upgrades separate app upgrades from hardware battery upgrades`() {
        CobblemonSmartphone.registerDefaultUpgrades()

        val appUpgrades = SmartphoneUpgradeRegistry.getAppUpgrades()
        val appKeys = appUpgrades.map { it.nbtKey }.toSet()

        assertTrue("upgrade_crafting" in appKeys)
        assertTrue("upgrade_gps" in appKeys)
        assertTrue("upgrade_structure_compass" in appKeys)
        assertTrue("upgrade_pokenav" in appKeys)
        assertTrue("upgrade_fishingnav" in appKeys)
        assertTrue("upgrade_waystone" in appKeys)
        assertTrue("upgrade_toms_storage" in appKeys)
        assertTrue("upgrade_refinedstorage" in appKeys)
        assertTrue("upgrade_ae2" in appKeys)
        assertTrue("upgrade_ae2_crafting" in appKeys)

        assertFalse("upgrade_battery_tier1" in appKeys)
        assertFalse("upgrade_battery_tier2" in appKeys)
        assertFalse("upgrade_battery_tier3" in appKeys)

        assertNotNull(SmartphoneUpgradeRegistry.getUpgrade("upgrade_battery_tier1"))
        assertNotNull(SmartphoneUpgradeRegistry.getUpgrade("upgrade_battery_tier2"))
        assertNotNull(SmartphoneUpgradeRegistry.getUpgrade("upgrade_battery_tier3"))
    }

    @Test
    fun `sd card crafting recipes exist and target registered app upgrades while battery smithing recipes remain`() {
        CobblemonSmartphone.registerDefaultUpgrades()

        val recipeDir = Path.of("src/main/resources/data/cobblemon_smartphone/recipe")
        assertTrue(Files.isDirectory(recipeDir), "Recipe directory should exist at $recipeDir")

        val expectedSdRecipes = mapOf(
            "sd_card_crafting.json" to "upgrade_crafting",
            "sd_card_gps.json" to "upgrade_gps",
            "sd_card_structure_compass.json" to "upgrade_structure_compass",
            "sd_card_pokenav.json" to "upgrade_pokenav",
            "sd_card_fishingnav.json" to "upgrade_fishingnav",
            "sd_card_waystone.json" to "upgrade_waystone",
            "sd_card_toms_storage.json" to "upgrade_toms_storage",
            "sd_card_refinedstorage.json" to "upgrade_refinedstorage",
            "sd_card_ae2.json" to "upgrade_ae2",
            "sd_card_ae2_crafting.json" to "upgrade_ae2_crafting"
        )

        for ((filename, expectedUpgrade) in expectedSdRecipes) {
            val file = recipeDir.resolve(filename)
            assertTrue(Files.exists(file), "Missing SD Card recipe: $filename")
            val json = JsonParser.parseString(Files.readString(file)).asJsonObject
            assertEquals("minecraft:crafting_shapeless", json.get("type").asString)
            val result = json.getAsJsonObject("result")
            assertEquals("cobblemon_smartphone:sd_card", result.get("id").asString)
            val customData = result.getAsJsonObject("components").getAsJsonObject("minecraft:custom_data")
            assertEquals(expectedUpgrade, customData.get(SD_CARD_UPGRADE_TAG).asString)
        }

        for (tier in 1..3) {
            val batteryFile = recipeDir.resolve("battery_tier$tier.json")
            assertTrue(Files.exists(batteryFile), "Battery crafting recipe should exist: $batteryFile")
            val json = JsonParser.parseString(Files.readString(batteryFile)).asJsonObject
            assertEquals("minecraft:crafting_shapeless", json.get("type").asString)
            assertEquals("cobblemon_smartphone:battery_tier$tier", json.getAsJsonObject("result").get("id").asString)

            val oldBatteryFile = recipeDir.resolve("battery_upgrade_tier$tier.json")
            assertFalse(Files.exists(oldBatteryFile), "Old battery smithing recipe should be removed: $oldBatteryFile")
        }

        val removedSmithing = listOf(
            "crafting_upgrade.json",
            "gps_upgrade.json",
            "structure_compass_upgrade.json",
            "pokenav_upgrade.json",
            "fishingnav_upgrade.json",
            "waystone_upgrade.json",
            "toms_storage_upgrade.json",
            "refinedstorage_upgrade.json",
            "ae2_upgrade.json",
            "ae2_crafting_upgrade.json"
        )
        for (old in removedSmithing) {
            assertFalse(Files.exists(recipeDir.resolve(old)), "Old smithing recipe should be removed: $old")
        }
    }
}
