package com.nbp.cobblemon_smartphone.client.ui.hd

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.nbp.cobblemon_smartphone.client.ui.hd.datapack.DatapackUiCompiler
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackExpr
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HdFeedbackComponentsTest {

    @Test
    fun `badge measures width including optional icon and padding`() {
        val plain = BadgeNode("Online", variant = BadgeNode.Variant.SUCCESS)
        val withIcon = BadgeNode("Online", icon = HdIconAtlas.Icon.CHECK, variant = BadgeNode.Variant.SUCCESS)

        plain.measure(HdMeasureContext(1f), 200f, 40f)
        withIcon.measure(HdMeasureContext(1f), 200f, 40f)

        assertTrue(plain.measuredWidth > 0f)
        assertTrue(withIcon.measuredWidth > plain.measuredWidth)
        assertTrue(plain.measuredHeight >= 10f)
    }

    @Test
    fun `statBar normalizes ratio and formats value cleanly`() {
        val bar = StatBarNode(label = "HP", value = 127.5f, maxValue = 255f)
        assertEquals(0.5f, bar.normalizedRatio(), 1e-4f)
        assertEquals("128", bar.formattedValue())

        val overflow = StatBarNode(label = "ATK", value = 400f, maxValue = 255f, valueText = "MAX")
        assertEquals(1f, overflow.normalizedRatio(), 1e-4f)
        assertEquals("MAX", overflow.formattedValue())

        val zeroMax = StatBarNode(label = "DEF", value = 50f, maxValue = 0f)
        assertEquals(0f, zeroMax.normalizedRatio(), 1e-4f)
    }

    @Test
    fun `emptyState invokes action only when action button is clicked`() {
        val state = HdStateStore()
        var clicked = 0
        val empty = EmptyStateNode(
            title = "No Pokémon Found",
            description = "Try adjusting your search filters.",
            icon = HdIconAtlas.Icon.SEARCH,
            actionLabel = "Reset Filters",
            actionIcon = HdIconAtlas.Icon.REFRESH,
            onAction = { clicked++ }
        )

        empty.measure(HdMeasureContext(1f), 200f, 300f)
        empty.arrange(0f, 0f, 200f, empty.measuredHeight)

        // Click near top (icon area) -> not the action button
        assertFalse(empty.onMouseDown(100f, 25f, 0, state))
        assertEquals(0, clicked)

        // Click near bottom center (action button) -> fires onAction
        assertTrue(empty.onMouseDown(100f, empty.bounds.bottom - 26f, 0, state))
        assertEquals(1, clicked)
    }

    @Test
    fun `toast state fades in, holds, fades out, and auto-expires`() {
        val store = HdStateStore()
        val t0 = 10_000L
        store.showToast("Saved!", kind = HdStateStore.ToastKind.SUCCESS, durationMs = 2000L, nowMs = t0)

        val active = store.currentToast(nowMs = t0 + 500L)
        assertNotNull(active)
        assertEquals(HdIconAtlas.Icon.CHECK, active!!.icon)
        assertEquals(1f, active.alpha(t0 + 500L), 1e-3f)

        // Fade-in near start
        assertTrue(active.alpha(t0 + 80L) in 0.4f..0.6f)

        // Fade-out near end
        assertTrue(active.alpha(t0 + 1900L) < 0.5f)
        assertTrue(active.alpha(t0 + 1900L) > 0f)

        // Expired after duration
        assertNull(store.currentToast(nowMs = t0 + 2100L))
        assertNull(store.activeToast)
    }

    @Test
    fun `datapack compiler builds badge, stat_bar, and empty_state`() {
        val json = """
            {
              "id": "feedback_demo",
              "body": [
                {
                  "type": "badge",
                  "label": "Shiny",
                  "icon": "SPARKLES",
                  "variant": "gold"
                },
                {
                  "type": "stat_bar",
                  "label": "SPE",
                  "value": "130",
                  "max_value": 200,
                  "fill_color": "primary"
                },
                {
                  "type": "empty_state",
                  "title": "Nothing here",
                  "description": "Click refresh to reload.",
                  "icon": "INFO",
                  "action_label": "Refresh",
                  "action_icon": "REFRESH",
                  "on_click": "reload_list"
                }
              ]
            }
        """.trimIndent()

        val def = DatapackScreenDefinition.fromJson(json)
        assertNotNull(def)

        val actions = mutableListOf<String>()
        val callbacks = object : DatapackUiCompiler.HostCallbacks {
            override fun onAction(actionKey: String, itemContext: Map<String, String>) {
                actions.add(actionKey)
            }
            override fun getState(key: String): String = ""
            override fun setState(key: String, value: String) {}
            override fun toggleState(key: String) {}
            override fun onInputChanged(inputId: String, value: String) {}
            override fun getFloatingState(): FloatingState = FloatingState()
        }

        val store = HdStateStore()
        val root = hdColumn(store, gap = 6f, width = HdSizeSpec.Fixed(220f), height = HdSizeSpec.Fixed(320f)) { b ->
            DatapackUiCompiler.buildNodes(
                builder = b,
                nodes = def!!.body.orEmpty(),
                ctx = DatapackExpr.EvalContext(),
                callbacks = callbacks
            )
        }
        root.measure(HdMeasureContext(1f), 220f, 320f)
        root.arrange(0f, 0f, 220f, 320f)

        assertEquals(3, root.children.size)
        val badge = root.children[0] as BadgeNode
        val stat = root.children[1] as StatBarNode
        val empty = root.children[2] as EmptyStateNode

        assertEquals(BadgeNode.Variant.GOLD, badge.variant)
        assertEquals(0.65f, stat.normalizedRatio(), 1e-4f)
        assertEquals("Refresh", empty.actionLabel)

        assertTrue(empty.onMouseDown(empty.bounds.centerX, empty.bounds.bottom - 26f, 0, store))
        assertEquals(listOf("reload_list"), actions)
    }
}
