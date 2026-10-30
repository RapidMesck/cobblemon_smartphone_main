package com.nbp.cobblemon_smartphone.compat

import com.nbp.cobblemon_smartphone.api.DatapackActionDefinition
import com.nbp.cobblemon_smartphone.api.DatapackActionFunction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class DatapackExampleTest {

    private val actionsDir = listOf(
        File("../examples/datapacks/cobblemon_smartphone_upgrades_example/data/smartphone_upgrades/smartphone_actions"),
        File("examples/datapacks/cobblemon_smartphone_upgrades_example/data/smartphone_upgrades/smartphone_actions")
    ).firstOrNull { it.exists() } ?: error("actionsDir not found")

    @Test
    fun `heal action definition parses and validates`() {
        val file = File(actionsDir, "heal.json")
        assertTrue(file.exists(), "heal.json must exist")
        val def = DatapackActionDefinition.fromJson(file.readText())
        assertNotNull(def)
        assertEquals("smartphone_upgrades:heal", def?.id)
        assertEquals("upgrade_heal", def?.requireUpgrade)
        assertEquals(listOf("heal_party"), def?.functions)
        assertTrue(DatapackActionFunction.isKnown("heal_party"))
        assertEquals(60, def?.cooldownSeconds)
    }

    @Test
    fun `pc action definition parses and validates`() {
        val file = File(actionsDir, "pc.json")
        assertTrue(file.exists(), "pc.json must exist")
        val def = DatapackActionDefinition.fromJson(file.readText())
        assertNotNull(def)
        assertEquals("smartphone_upgrades:pc", def?.id)
        assertEquals("upgrade_pc", def?.requireUpgrade)
        assertEquals(listOf("open_pc"), def?.functions)
        assertTrue(DatapackActionFunction.isKnown("open_pc"))
        assertEquals(5, def?.cooldownSeconds)
    }

    @Test
    fun `cloud action definition parses and validates`() {
        val file = File(actionsDir, "cloud.json")
        assertTrue(file.exists(), "cloud.json must exist")
        val def = DatapackActionDefinition.fromJson(file.readText())
        assertNotNull(def)
        assertEquals("smartphone_upgrades:cloud", def?.id)
        assertEquals("upgrade_ender_chest", def?.requireUpgrade)
        assertEquals(listOf("open_ender_chest"), def?.functions)
        assertTrue(DatapackActionFunction.isKnown("open_ender_chest"))
        assertEquals(5, def?.cooldownSeconds)
    }

    private val recipesDir = listOf(
        File("../examples/datapacks/cobblemon_smartphone_upgrades_example/data/smartphone_upgrades/recipe"),
        File("examples/datapacks/cobblemon_smartphone_upgrades_example/data/smartphone_upgrades/recipe")
    ).firstOrNull { it.exists() } ?: error("recipesDir not found")

    @Test
    fun `smithing recipes parse and have correct upgrades`() {
        val cases = mapOf(
            "heal_upgrade.json" to "upgrade_heal",
            "pc_upgrade.json" to "upgrade_pc",
            "ender_chest_upgrade.json" to "upgrade_ender_chest"
        )

        for ((fileName, expectedUpgrade) in cases) {
            val file = File(recipesDir, fileName)
            assertTrue(file.exists(), "$fileName must exist")
            val json = com.google.gson.JsonParser.parseString(file.readText()).asJsonObject
            assertEquals("minecraft:smithing_transform", json.get("type").asString)
            assertEquals("cobblemon:upgrade", json.getAsJsonObject("template").get("item").asString)
            assertEquals("cobblemon_smartphone:smartphones", json.getAsJsonObject("base").get("tag").asString)

            val resultObj = json.getAsJsonObject("result")
            val upgradesObj = resultObj.getAsJsonObject("components")
                .getAsJsonObject("minecraft:custom_data")
                .getAsJsonObject("cobblemon_smartphone:upgrades")

            assertTrue(upgradesObj.has(expectedUpgrade), "$fileName should contain $expectedUpgrade")
            assertTrue(upgradesObj.get(expectedUpgrade).asBoolean)
        }
    }
}
