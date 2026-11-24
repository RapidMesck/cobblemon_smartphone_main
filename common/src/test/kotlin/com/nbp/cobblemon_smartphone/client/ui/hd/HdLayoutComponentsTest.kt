package com.nbp.cobblemon_smartphone.client.ui.hd

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.nbp.cobblemon_smartphone.client.ui.hd.datapack.DatapackUiCompiler
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackExpr
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HdLayoutComponentsTest {

    @Test
    fun `flowRow wraps children across multiple lines and respects gaps and alignment`() {
        val flow = FlowRowNode(hGap = 10f, vGap = 8f, lineAlign = HdAlign.START, crossAlign = HdAlign.CENTER).apply {
            width = HdSizeSpec.Fixed(150f)
        }
        // Line 1: 60 + 10 + 60 = 130 <= 150
        // Line 2: 50 + 10 + 40 = 100 <= 150 (50 wouldn't fit on line 1: 130 + 10 + 50 = 190 > 150)
        flow.add(SpacerNode(60f, 20f))
        flow.add(SpacerNode(60f, 30f))
        flow.add(SpacerNode(50f, 16f))
        flow.add(SpacerNode(40f, 16f))

        flow.measure(HdMeasureContext(1f), 150f, 300f)
        flow.arrange(0f, 0f, 150f, flow.measuredHeight)

        // Total height = 30 (line 1) + 8 (vGap) + 16 (line 2) = 54
        assertEquals(54f, flow.measuredHeight, 1e-3f)
        // First child vertically centered in 30dp line -> y = 5
        assertEquals(0f, flow.children[0].bounds.x, 1e-3f)
        assertEquals(5f, flow.children[0].bounds.y, 1e-3f)
        // Second child at x = 70, y = 0
        assertEquals(70f, flow.children[1].bounds.x, 1e-3f)
        assertEquals(0f, flow.children[1].bounds.y, 1e-3f)
        // Third child starts line 2 at x = 0, y = 38
        assertEquals(0f, flow.children[2].bounds.x, 1e-3f)
        assertEquals(38f, flow.children[2].bounds.y, 1e-3f)
        // Fourth child at x = 60, y = 38
        assertEquals(60f, flow.children[3].bounds.x, 1e-3f)
        assertEquals(38f, flow.children[3].bounds.y, 1e-3f)
    }

    @Test
    fun `accordion hides children when collapsed and toggles on header click`() {
        val state = HdStateStore()
        var toggledState: Boolean? = null
        val acc = AccordionNode(
            title = "Advanced Settings",
            subtitle = "Radar & Scanner",
            icon = HdIconAtlas.Icon.SETTINGS,
            expanded = false,
            onToggle = { toggledState = it }
        )
        val child = SpacerNode(100f, 40f).apply { id = "inner_child" }
        acc.add(child)

        acc.measure(HdMeasureContext(1f), 200f, 300f)
        val collapsedH = acc.measuredHeight
        acc.arrange(0f, 0f, 200f, collapsedH)

        // When collapsed, hitTest does not include child
        val pathCollapsed = mutableListOf<HdNode>()
        acc.hitTest(50f, collapsedH * 0.5f, pathCollapsed)
        assertEquals(listOf(acc), pathCollapsed)

        // Click header to expand
        assertTrue(acc.onMouseDown(50f, collapsedH * 0.5f, 0, state))
        assertTrue(acc.expanded)
        assertEquals(true, toggledState)

        // Re-measure when expanded -> taller by body + child height
        acc.measure(HdMeasureContext(1f), 200f, 300f)
        val expandedH = acc.measuredHeight
        assertTrue(expandedH > collapsedH + 40f)
        acc.arrange(0f, 0f, 200f, expandedH)

        // Now hitTest on child region finds both accordion and child
        val pathExpanded = mutableListOf<HdNode>()
        acc.hitTest(child.bounds.centerX, child.bounds.centerY, pathExpanded)
        assertEquals(listOf(acc, child), pathExpanded)
    }

    @Test
    fun `listTile arranges trailing child on the right and handles tile click vs child click`() {
        val state = HdStateStore()
        var tileClicks = 0
        var switchToggles = 0
        val tile = ListTileNode(
            title = "Sound Effects",
            subtitle = "Play click audio",
            leadingIcon = HdIconAtlas.Icon.BELL,
            showChevron = false
        ).apply {
            onClick = { tileClicks++ }
        }
        val sw = SwitchNode(checked = false, onToggle = { switchToggles++ }).apply { id = "tile_sw" }
        tile.add(sw)

        tile.measure(HdMeasureContext(1f), 220f, 80f)
        tile.arrange(0f, 0f, 220f, tile.measuredHeight)

        // Switch should be right-aligned inside tile padding (padding right = 9)
        assertEquals(220f - 9f, sw.bounds.right, 1e-3f)

        // Clicking switch region hits switch first
        val path = mutableListOf<HdNode>()
        tile.hitTest(sw.bounds.centerX, sw.bounds.centerY, path)
        assertEquals(sw, path.last())
        assertTrue(sw.onMouseDown(sw.bounds.centerX, sw.bounds.centerY, 0, state))
        assertEquals(1, switchToggles)
        assertEquals(0, tileClicks)

        // Clicking left side of tile hits tile directly
        assertTrue(tile.onMouseDown(20f, tile.bounds.centerY, 0, state))
        assertEquals(1, tileClicks)

        // Disabled tile ignores clicks
        tile.enabled = false
        assertFalse(tile.onMouseDown(20f, tile.bounds.centerY, 0, state))
        assertEquals(1, tileClicks)
    }

    @Test
    fun `datapack compiler builds flow_row, accordion, and list_tile`() {
        val json = """
            {
              "id": "layout_demo",
              "body": [
                {
                  "type": "flow_row",
                  "h_gap": 4,
                  "v_gap": 4,
                  "children": [
                    { "type": "pill", "label": "Fire" },
                    { "type": "pill", "label": "Water" }
                  ]
                },
                {
                  "type": "accordion",
                  "id": "acc1",
                  "title": "Filters",
                  "icon": "FILTER",
                  "state_key": "filters_open",
                  "on_change": "toggle_filters",
                  "children": [
                    { "type": "text", "text": "Inside accordion" }
                  ]
                },
                {
                  "type": "list_tile",
                  "id": "tile1",
                  "title": "Theme",
                  "subtitle": "Cyan Modern",
                  "icon": "SETTINGS",
                  "trailing_text": "Edit",
                  "show_chevron": true,
                  "on_click": "open_theme"
                }
              ]
            }
        """.trimIndent()

        val def = DatapackScreenDefinition.fromJson(json)
        assertNotNull(def)

        val stateMap = mutableMapOf("filters_open" to "false")
        val actions = mutableListOf<String>()
        val callbacks = object : DatapackUiCompiler.HostCallbacks {
            override fun onAction(actionKey: String, itemContext: Map<String, String>) {
                actions.add(actionKey)
            }
            override fun getState(key: String): String = stateMap[key] ?: ""
            override fun setState(key: String, value: String) {
                stateMap[key] = value
            }
            override fun toggleState(key: String) {
                stateMap[key] = (!stateMap[key].toBoolean()).toString()
            }
            override fun onInputChanged(inputId: String, value: String) {}
            override fun getFloatingState(): FloatingState = FloatingState()
        }

        val store = HdStateStore()
        val root = hdColumn(store, gap = 6f, width = HdSizeSpec.Fixed(220f), height = HdSizeSpec.Fixed(320f)) { b ->
            DatapackUiCompiler.buildNodes(
                builder = b,
                nodes = def!!.body.orEmpty(),
                ctx = DatapackExpr.EvalContext(state = stateMap),
                callbacks = callbacks
            )
        }
        root.measure(HdMeasureContext(1f), 220f, 320f)
        root.arrange(0f, 0f, 220f, 320f)

        assertEquals(3, root.children.size)
        val flow = root.children[0] as FlowRowNode
        val acc = root.children[1] as AccordionNode
        val tile = root.children[2] as ListTileNode

        assertEquals(2, flow.children.size)
        assertFalse(acc.expanded)
        assertTrue(tile.showChevron)
        assertEquals("Edit", tile.trailingText)

        // Toggle accordion via header click
        assertTrue(acc.onMouseDown(acc.bounds.centerX, acc.bounds.centerY, 0, store))
        assertEquals("true", stateMap["filters_open"])

        // Click list tile
        assertTrue(tile.onMouseDown(tile.bounds.centerX, tile.bounds.centerY, 0, store))
        assertEquals(listOf("toggle_filters", "open_theme"), actions)
    }
}
