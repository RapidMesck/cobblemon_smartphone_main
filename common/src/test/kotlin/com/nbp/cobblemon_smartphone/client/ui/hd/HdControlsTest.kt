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

class HdControlsTest {

    @Test
    fun `switch toggles state on left click and respects disabled`() {
        val state = HdStateStore()
        var toggledTo: Boolean? = null
        val sw = SwitchNode(label = "Notifications", checked = false, onToggle = { toggledTo = it })
        sw.measure(HdMeasureContext(1f), 200f, 40f)
        sw.arrange(0f, 0f, 160f, sw.measuredHeight)

        assertTrue(sw.onMouseDown(20f, 8f, 0, state))
        assertTrue(sw.checked)
        assertEquals(true, toggledTo)

        // Right click ignored
        assertFalse(sw.onMouseDown(20f, 8f, 1, state))
        assertTrue(sw.checked)

        // Disabled ignores click
        sw.enabled = false
        toggledTo = null
        assertFalse(sw.onMouseDown(20f, 8f, 0, state))
        assertTrue(sw.checked)
        assertEquals(null, toggledTo)
    }

    @Test
    fun `slider snaps, clamps, and responds to click and drag`() {
        assertEquals(0.5f, SliderNode.normalize(50f, 0f, 100f), 1e-4f)
        assertEquals(0f, SliderNode.normalize(-10f, 0f, 100f), 1e-4f)
        assertEquals(1f, SliderNode.normalize(150f, 0f, 100f), 1e-4f)

        assertEquals(25f, SliderNode.snap(23f, 0f, 100f, 5f), 1e-4f)
        assertEquals(100f, SliderNode.snap(120f, 0f, 100f, 5f), 1e-4f)
        assertEquals("50", SliderNode.formatDefault(50f, 1f))
        assertEquals("1.5", SliderNode.formatDefault(1.5f, 0.5f))
        assertEquals("1.25", SliderNode.formatDefault(1.25f, 0.05f))

        val state = HdStateStore()
        val changes = mutableListOf<Float>()
        val slider = SliderNode(
            value = 0f,
            min = 0f,
            max = 100f,
            step = 10f,
            label = "Radar Range",
            onChange = { changes.add(it) }
        )
        // padding horizontal = 2, thumbRadius = 6 -> track spans [8, 108] for width = 116
        slider.measure(HdMeasureContext(1f), 116f, 60f)
        slider.arrange(0f, 0f, 116f, slider.measuredHeight)

        // Click midpoint (x = 58 -> ratio 0.5 -> 50)
        assertTrue(slider.onMouseDown(58f, 20f, 0, state))
        assertEquals(50f, slider.value, 1e-3f)

        // Drag to 80% (x = 88 -> ratio 0.8 -> 80)
        assertTrue(slider.onMouseDrag(88f, 20f, state))
        assertEquals(80f, slider.value, 1e-3f)

        // Release stops dragging
        assertTrue(slider.onMouseUp(88f, 20f, 0, state))
        assertFalse(slider.onMouseDrag(18f, 20f, state))
        assertEquals(80f, slider.value, 1e-3f)
        assertEquals(listOf(50f, 80f), changes)
    }

    @Test
    fun `number stepper increments, decrements, and clamps within bounds`() {
        val state = HdStateStore()
        val changes = mutableListOf<Float>()
        val stepper = NumberStepperNode(
            value = 10f,
            min = 5f,
            max = 15f,
            step = 5f,
            label = "",
            buttonSizeDp = 20f,
            valueWidthDp = 40f,
            onChange = { changes.add(it) }
        )
        // padding = 2 -> inner.x = 2; minus = [2..22], value = [22..62], plus = [62..82]
        stepper.measure(HdMeasureContext(1f), 100f, 40f)
        stepper.arrange(0f, 0f, stepper.measuredWidth, stepper.measuredHeight)

        // Click plus -> 15
        assertTrue(stepper.onMouseDown(70f, 10f, 0, state))
        assertEquals(15f, stepper.value, 1e-3f)

        // Click plus again -> clamped at 15, no new change emitted
        assertTrue(stepper.onMouseDown(70f, 10f, 0, state))
        assertEquals(15f, stepper.value, 1e-3f)

        // Click middle value box -> ignored
        assertFalse(stepper.onMouseDown(40f, 10f, 0, state))

        // Click minus twice -> 10, then 5
        assertTrue(stepper.onMouseDown(10f, 10f, 0, state))
        assertTrue(stepper.onMouseDown(10f, 10f, 0, state))
        assertEquals(5f, stepper.value, 1e-3f)

        // Click minus at min -> stays 5
        assertTrue(stepper.onMouseDown(10f, 10f, 0, state))
        assertEquals(5f, stepper.value, 1e-3f)

        assertEquals(listOf(15f, 10f, 5f), changes)
    }

    @Test
    fun `segmented control selects clicked option and respects disabled`() {
        val state = HdStateStore()
        var lastSelected: String? = null
        val seg = SegmentedControlNode(
            options = listOf(
                SegmentedControlNode.Option("auto", "Auto"),
                SegmentedControlNode.Option("hd", "HD", HdIconAtlas.Icon.SPARKLES),
                SegmentedControlNode.Option("pixel", "Pixel")
            ),
            selectedId = "auto",
            onSelect = { lastSelected = it }
        )
        // width = 184 -> inset 2 each side -> contentW = 180 -> 3 slots of 60dp ([2..62], [62..122], [122..182])
        seg.measure(HdMeasureContext(1f), 184f, 40f)
        seg.arrange(0f, 0f, 184f, 24f)

        assertTrue(seg.onMouseDown(90f, 12f, 0, state))
        assertEquals("hd", seg.selectedId)
        assertEquals("hd", lastSelected)

        assertTrue(seg.onMouseDown(150f, 12f, 0, state))
        assertEquals("pixel", seg.selectedId)
        assertEquals("pixel", lastSelected)

        seg.enabled = false
        assertFalse(seg.onMouseDown(20f, 12f, 0, state))
        assertEquals("pixel", seg.selectedId)
    }

    @Test
    fun `datapack compiler builds and wires switch, slider, number_stepper, and segmented_control`() {
        val json = """
            {
              "id": "controls_demo",
              "body": [
                {
                  "type": "switch",
                  "id": "sw1",
                  "label": "Auto Scan",
                  "state_key": "auto_scan",
                  "on_change": "save_cfg"
                },
                {
                  "type": "slider",
                  "id": "sl1",
                  "label": "Range",
                  "state_key": "range",
                  "min": 0,
                  "max": 100,
                  "step": 25,
                  "on_change": "save_cfg"
                },
                {
                  "type": "number_stepper",
                  "id": "st1",
                  "label": "Slots",
                  "state_key": "slots",
                  "min": 1,
                  "max": 10,
                  "step": 1,
                  "on_change": "save_cfg"
                },
                {
                  "type": "segmented_control",
                  "id": "sg1",
                  "state_key": "mode",
                  "on_change": "save_cfg",
                  "options": [
                    { "id": "all", "label": "All", "icon": "FILTER" },
                    { "id": "shiny", "label": "Shiny", "icon": "STAR" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val def = DatapackScreenDefinition.fromJson(json)
        assertNotNull(def)

        val stateMap = mutableMapOf("auto_scan" to "false", "range" to "25", "slots" to "3", "mode" to "all")
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
        val root = hdColumn(store, gap = 6f, width = HdSizeSpec.Fixed(200f), height = HdSizeSpec.Fixed(300f)) { b ->
            DatapackUiCompiler.buildNodes(
                builder = b,
                nodes = def!!.body.orEmpty(),
                ctx = DatapackExpr.EvalContext(state = stateMap),
                callbacks = callbacks
            )
        }
        root.measure(HdMeasureContext(1f), 200f, 300f)
        root.arrange(0f, 0f, 200f, 300f)

        assertEquals(4, root.children.size)
        val sw = root.children[0] as SwitchNode
        val sl = root.children[1] as SliderNode
        val st = root.children[2] as NumberStepperNode
        val sg = root.children[3] as SegmentedControlNode

        assertFalse(sw.checked)
        assertEquals(25f, sl.value, 1e-3f)
        assertEquals(3f, st.value, 1e-3f)
        assertEquals("all", sg.selectedId)

        // Simulate interactions
        sw.onMouseDown(sw.bounds.centerX, sw.bounds.centerY, 0, store)
        assertEquals("true", stateMap["auto_scan"])

        sl.onMouseDown(sl.bounds.right - 2f, sl.bounds.centerY, 0, store)
        assertEquals("100", stateMap["range"])

        st.onMouseDown(st.bounds.right - 6f, st.bounds.centerY, 0, store)
        assertEquals("4", stateMap["slots"])

        sg.onMouseDown(sg.bounds.right - 10f, sg.bounds.centerY, 0, store)
        assertEquals("shiny", stateMap["mode"])

        assertEquals(4, actions.size)
    }
}
