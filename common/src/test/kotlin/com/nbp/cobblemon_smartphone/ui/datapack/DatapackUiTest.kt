package com.nbp.cobblemon_smartphone.ui.datapack

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatapackUiTest {

    @Test
    fun `resolveTemplate substitutes item, state, inputs, and placeholders`() {
        val ctx = DatapackExpr.EvalContext(
            placeholders = mapOf("player.name" to "Red", "coins" to "250"),
            lists = mapOf("party" to listOf(mapOf("name" to "Pikachu"), mapOf("name" to "Eevee"))),
            state = mapOf("tab" to "shop"),
            inputs = mapOf("note" to "Hello"),
            item = mapOf("name" to "Pikachu", "level" to "25")
        )
        val out = DatapackExpr.resolveTemplate(
            "Trainer {player.name} ({coins}) | Party={lists.party.size} | Tab={state.tab} | {item.name} Lv.{level} | {inputs.note}",
            ctx
        )
        assertEquals("Trainer Red (250) | Party=2 | Tab=shop | Pikachu Lv.25 | Hello", out)
    }

    @Test
    fun `evaluateCondition supports comparisons, boolean ops, parentheses, and empty checks`() {
        val ctx = DatapackExpr.EvalContext(
            placeholders = mapOf("player.level" to "15"),
            lists = mapOf(
                "party" to listOf(mapOf("name" to "Pikachu")),
                "bag" to emptyList()
            ),
            state = mapOf("tab" to "party", "show_advanced" to "true"),
            item = mapOf("shiny" to "true", "hp" to "35")
        )

        assertTrue(DatapackExpr.evaluateCondition(null, ctx))
        assertTrue(DatapackExpr.evaluateCondition("state.tab == 'party'", ctx))
        assertFalse(DatapackExpr.evaluateCondition("state.tab == 'shop'", ctx))
        assertTrue(DatapackExpr.evaluateCondition("player.level >= 10 && state.show_advanced", ctx))
        assertTrue(DatapackExpr.evaluateCondition("item.shiny == true && !(item.hp < 20)", ctx))
        assertTrue(DatapackExpr.evaluateCondition("lists.party.not_empty && lists.bag.empty", ctx))
        assertTrue(DatapackExpr.evaluateCondition("lists.party.size == 1", ctx))
        assertFalse(DatapackExpr.evaluateCondition("lists.bag.size > 0 || state.tab == 'shop'", ctx))
    }

    @Test
    fun `sanitizeInputForCommand strips selectors, slashes, and control characters`() {
        assertEquals("Pikachu 25", DatapackExpr.sanitizeInputForCommand("Pikachu 25"))
        assertEquals("e", DatapackExpr.sanitizeInputForCommand("@e"))
        assertEquals("say higive me", DatapackExpr.sanitizeInputForCommand("/say hi;\n\rgive me$"))
    }

    @Test
    fun `parseScreen parses complete screen definition and derives fallback id`() {
        val json = """
            {
              "viewport": "large",
              "default_state": { "tab": "party", "filter": "" },
              "placeholders": { "coins": "scoreboard:coins" },
              "lists": {
                "party": { "type": "party", "limit": 6 }
              },
              "actions": {
                "heal_all": {
                  "cooldown_seconds": 30,
                  "fe_cost": 50,
                  "effects": [
                    { "type": "function", "function": "heal_pokemon" },
                    { "type": "message", "text": "Healed {player.name}'s team!", "actionbar": true }
                  ]
                }
              },
              "body": [
                {
                  "type": "tabs",
                  "state_key": "tab",
                  "tabs": [
                    { "id": "party", "label": "Party" },
                    { "id": "shop", "label": "Shop" }
                  ]
                },
                {
                  "type": "scroll",
                  "visible_if": "state.tab == 'party'",
                  "for_each": "lists.party",
                  "empty_text": "No Pokemon",
                  "children": [
                    { "type": "text", "text": "{item.name} Lv.{item.level}" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val fallback = DatapackScreenLoader.deriveIdFromResource(
            ResourceLocation.fromNamespaceAndPath("my_pack", "smartphone_screens/hub/main.json")
        )
        assertEquals("my_pack:hub/main", fallback)

        val def = DatapackScreenLoader.parseScreen(json, fallback)
        assertNotNull(def)
        assertEquals("my_pack:hub/main", def!!.id)
        assertEquals("large", def.viewport)
        assertEquals("scoreboard:coins", def.placeholders?.get("coins"))
        assertEquals(6, def.lists?.get("party")?.limit)
        assertEquals(50, def.actions?.get("heal_all")?.feCost)
        val body = def.body.orEmpty()
        assertEquals(2, body.size)
        assertEquals("state.tab == 'party'", body[1].visibleIf)
        assertEquals("lists.party", body[1].forEach)

        // Minimal omitted JSON fields should remain safe under Gson Unsafe allocation
        val minimal = DatapackScreenLoader.parseScreen("""{"body":[{"type":"text","text":"Hi"}]}""", "min:test")!!
        assertTrue(minimal.showBezelBackButton != false)
        assertEquals(1, minimal.body.orEmpty().size)
    }

    @Test
    fun `ui style defaults to PIXEL and toggles cleanly to HD`() {
        val prefs = com.nbp.cobblemon_smartphone.client.ui.hd.HdUiPreferences
        val pixel = com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle.PIXEL
        val hd = com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle.HD
        prefs.resetOverridesForTest()
        assertEquals(pixel, prefs.getUiStyle())
        assertFalse(prefs.isHdStyle())
        assertEquals(hd, prefs.toggleUiStyle())
        assertTrue(prefs.isHdStyle())
        assertEquals(pixel, prefs.toggleUiStyle())
        assertFalse(prefs.isHdStyle())
        prefs.resetOverridesForTest()
    }
}
