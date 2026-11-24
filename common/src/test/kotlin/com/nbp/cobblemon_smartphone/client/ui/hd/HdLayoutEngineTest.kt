package com.nbp.cobblemon_smartphone.client.ui.hd

import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeColors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HdLayoutEngineTest {

    @Test
    fun `column distributes remaining height across weighted children`() {
        val state = HdStateStore()
        val root = hdColumn(state, gap = 10f, width = HdSizeSpec.Fixed(200f), height = HdSizeSpec.Fixed(300f)) { b ->
            b.spacer(h = 40f)
            b.add(SpacerNode().apply {
                width = HdSizeSpec.Fill
                height = HdSizeSpec.Weight(1f)
            })
            b.add(SpacerNode().apply {
                width = HdSizeSpec.Fill
                height = HdSizeSpec.Weight(2f)
            })
        }

        root.measure(HdMeasureContext(1.5f), 200f, 300f)
        root.arrange(0f, 0f, 200f, 300f)

        // Total height 300, gaps = 20, fixed = 40 -> remaining = 240 -> 1:2 => 80 and 160
        assertEquals(40f, root.children[0].bounds.height, 1e-3f)
        assertEquals(80f, root.children[1].bounds.height, 1e-3f)
        assertEquals(160f, root.children[2].bounds.height, 1e-3f)
        assertEquals(140f, root.children[2].bounds.y, 1e-3f)
    }

    @Test
    fun `row distributes remaining width across weighted children`() {
        val row = RowNode(gap = 8f).apply {
            width = HdSizeSpec.Fixed(208f)
            height = HdSizeSpec.Fixed(40f)
        }
        val left = SpacerNode(sizeW = 40f, sizeH = 20f)
        val mid = SpacerNode().apply { width = HdSizeSpec.Weight(1f); height = HdSizeSpec.Fixed(20f) }
        val right = SpacerNode(sizeW = 52f, sizeH = 20f)
        row.add(left)
        row.add(mid)
        row.add(right)

        row.measure(HdMeasureContext(1.0f), 208f, 40f)
        row.arrange(10f, 20f, 208f, 40f)

        // 208 - 16(gaps) - 40 - 52 = 100
        assertEquals(10f, left.bounds.x, 1e-3f)
        assertEquals(58f, mid.bounds.x, 1e-3f)
        assertEquals(100f, mid.bounds.width, 1e-3f)
        assertEquals(166f, right.bounds.x, 1e-3f)
    }

    @Test
    fun `grid arranges items in equal columns and multiple rows`() {
        val grid = GridNode(columns = 2, colGap = 10f, rowGap = 6f).apply {
            width = HdSizeSpec.Fixed(210f)
        }
        repeat(3) {
            grid.add(SpacerNode(sizeW = 20f, sizeH = 30f))
        }
        grid.measure(HdMeasureContext(1.0f), 210f, 400f)
        grid.arrange(0f, 0f, 210f, grid.measuredHeight)

        // Col width = (210 - 10) / 2 = 100
        assertEquals(66f, grid.measuredHeight, 1e-3f)
        assertEquals(0f, grid.children[0].bounds.x, 1e-3f)
        assertEquals(110f, grid.children[1].bounds.x, 1e-3f)
        assertEquals(100f, grid.children[1].bounds.width, 1e-3f)
        assertEquals(36f, grid.children[2].bounds.y, 1e-3f)
    }

    @Test
    fun `scroll view clamps scroll offset and blocks hitTest outside viewport`() {
        val state = HdStateStore()
        var clickedItem = -1
        val scroll = ScrollViewNode("feed_scroll", state, showScrollbar = false).apply {
            width = HdSizeSpec.Fixed(200f)
            height = HdSizeSpec.Fixed(100f)
        }
        val list = ColumnNode(gap = 0f).apply { width = HdSizeSpec.Fill }
        repeat(5) { idx ->
            list.add(SpacerNode(sizeW = 200f, sizeH = 50f).apply {
                id = "item_$idx"
                width = HdSizeSpec.Fill
                onClick = { clickedItem = idx }
            })
        }
        scroll.add(list)

        scroll.measure(HdMeasureContext(1.5f), 200f, 100f)
        scroll.arrange(0f, 0f, 200f, 100f)

        assertEquals(150f, state.scroll("feed_scroll").maxScroll, 1e-3f)

        // Scroll down 60dp -> item_1 (50..100) moves to (-10..40), item_2 (100..150) moves to (40..90)
        scroll.onScroll(50f, 50f, -2.0, state) // deltaY=-2 * speed 22 = +44dp
        scroll.arrange(0f, 0f, 200f, 100f)
        assertEquals(44f, state.scroll("feed_scroll").offset, 1e-3f)

        // Hit inside viewport at y=60 hits item_2 (which spans 100-44=56 .. 150-44=106)
        val insidePath = mutableListOf<HdNode>()
        scroll.hitTest(50f, 60f, insidePath)
        assertEquals("item_2", insidePath.last().id)

        // Hit outside viewport (y=120) hits nothing
        val outsidePath = mutableListOf<HdNode>()
        scroll.hitTest(50f, 120f, outsidePath)
        assertTrue(outsidePath.isEmpty())

        insidePath.last().onMouseDown(50f, 60f, 0, state)
        assertEquals(2, clickedItem)
    }

    @Test
    fun `color spec parses hex and theme tokens with alpha`() {
        val hex = HdColorSpec.parse("#FF3A96B6")
        assertEquals(0xFF3A96B6.toInt(), hex!!.resolve(SmartphoneThemeColors.DEFAULT))

        val tok = HdColorSpec.parse("@theme.primary")
        assertEquals(SmartphoneThemeColors.DARK.primary, tok!!.resolve(SmartphoneThemeColors.DARK))

        val half = HdColorSpec.parse("primary@0.5")
        val resolved = half!!.resolve(SmartphoneThemeColors.DEFAULT)
        assertTrue(((resolved ushr 24) and 0xFF) in 126..129)

        assertNull(HdColorSpec.parse("   "))
        assertFalse(HdColorSpec.Token("transparent").resolve(SmartphoneThemeColors.DEFAULT) != 0)
    }
}
