package com.nbp.cobblemon_smartphone.client.ui.hd

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.client.gui.SmartphoneBatteryRenderer
import com.nbp.cobblemon_smartphone.client.gui.SmartphoneScreen
import com.nbp.cobblemon_smartphone.energy.getEnergy
import com.nbp.cobblemon_smartphone.energy.getMaxEnergy
import com.nbp.cobblemon_smartphone.energy.isInfiniteEnergy
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.skin.SmartphoneSkinHelper
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

/**
 * Unified host [Screen] for every HD UI screen (both Kotlin-authored screens and future
 * datapack-authored screens). Supports Compact phone (`131x207 @ 2x`), Large phone
 * (`211x207 @ 2x`), and Free panel layouts, preserving the 2D frame and pixel wallpaper by
 * default while rendering all UI nodes, inputs, modals, and tooltips in resolution-independent HD.
 */
abstract class HdPhoneScreen(
    protected val color: SmartphoneColor,
    protected val smartphoneStack: ItemStack? = null,
    title: Component = Component.literal("Smartphone"),
    private val scaleOverride: Float? = null,
    private val styleOverride: HdUiStyle? = null
) : Screen(title) {

    sealed interface ViewportMode {
        data object Compact : ViewportMode
        data object Large : ViewportMode
        data class Free(
            val widthDp: Float,
            val heightDp: Float,
            val cornerRadius: Float = 16f,
            val backdropColor: HdColorSpec = HdColorSpec.Token("surface"),
            val borderColor: HdColorSpec = HdColorSpec.Token("primary_border")
        ) : ViewportMode
    }

    data class ModalSpec(
        val title: String,
        val widthDp: Float = 264f,
        val onClose: () -> Unit,
        val buildBody: (HdBuilder) -> Unit
    )

    protected open val viewportMode: ViewportMode = ViewportMode.Large
    protected open val showBezelBackButton: Boolean = true
    protected open val showBezelBattery: Boolean = true
    protected open val showBezelHdBadge: Boolean = false
    protected open val screenBackdropOverride: HdColorSpec? = null
    protected open val frameOverride: ResourceLocation? = null
    protected open val wallpaperOverride: ResourceLocation? = null

    val stateStore = HdStateStore()

    private var rootNode: HdNode? = null
    private var modalRootNode: HdNode? = null
    private var activeModalCloseBounds: HdBounds = HdBounds.EMPTY
    private var activeModalBoxBounds: HdBounds = HdBounds.EMPTY
    private var activeBackBounds: HdBounds = HdBounds.EMPTY
    private var activeDraggedNode: HdNode? = null

    protected val currentTheme: SmartphoneTheme
        get() = SmartphoneThemeHelper.resolveCurrentTheme(smartphoneStack)

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color
        minecraft?.resourceManager?.let { HdTextLayout.ensureResourcesLoaded(it) }
    }

    override fun removed() {
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    protected abstract fun buildContent(theme: SmartphoneTheme, builder: HdBuilder)

    protected open fun buildModal(theme: SmartphoneTheme): ModalSpec? = null

    protected open fun onBackPressed() {
        Minecraft.getInstance().setScreen(SmartphoneScreen(color, smartphoneStack))
    }

    fun showToast(
        message: String,
        kind: HdStateStore.ToastKind = HdStateStore.ToastKind.INFO,
        icon: HdIconAtlas.Icon? = null,
        durationMs: Long = 2600L
    ) {
        stateStore.showToast(message, kind, icon, durationMs)
    }

    protected fun currentStyle(): HdUiStyle = styleOverride ?: HdUiPreferences.getUiStyle()

    protected fun currentViewport(): HdViewport {
        val window = Minecraft.getInstance().window
        val (dw, dh) = designSize()
        return HdViewport.compute(
            windowWidth = window.width,
            windowHeight = window.height,
            guiScale = window.guiScale,
            designWidth = dw,
            designHeight = dh,
            userScale = scaleOverride ?: HdUiPreferences.getUserScale()
        )
    }

    protected fun designSize(): Pair<Float, Float> = when (val m = viewportMode) {
        ViewportMode.Compact -> 262f to 414f
        ViewportMode.Large -> 422f to 414f
        is ViewportMode.Free -> m.widthDp to m.heightDp
    }

    protected fun screenRectDp(): HdBounds = when (val m = viewportMode) {
        ViewportMode.Compact -> HdBounds(40f, 48f, 182f, 340f)
        ViewportMode.Large -> HdBounds(40f, 48f, 342f, 340f)
        is ViewportMode.Free -> HdBounds(0f, 0f, m.widthDp, m.heightDp)
    }

    private fun buildAndLayout(viewport: HdViewport, theme: SmartphoneTheme): Pair<HdNode, HdNode?> {
        val screenRect = screenRectDp()
        val root = hdColumn(stateStore, width = HdSizeSpec.Fill, height = HdSizeSpec.Fill) { b ->
            buildContent(theme, b)
        }
        val mCtx = HdMeasureContext(viewport.scale, theme.colors)
        root.measure(mCtx, screenRect.width, screenRect.height)
        root.arrange(screenRect.x, screenRect.y, screenRect.width, screenRect.height)
        rootNode = root

        val modalSpec = buildModal(theme)
        val modalNode = modalSpec?.let { spec ->
            val maxModalW = (screenRect.width - 24f).coerceAtLeast(120f)
            val mw = spec.widthDp.coerceAtMost(maxModalW)
            val box = ColumnNode().apply {
                width = HdSizeSpec.Fixed(mw)
                height = HdSizeSpec.Wrap
                background = HdBackground(
                    color = HdColorSpec.Token("surface"),
                    radius = 13f,
                    borderWidth = 0.9f,
                    borderColor = HdColorSpec.Token("primary_border"),
                    shadowBlur = 12f,
                    shadowOffsetY = 3f,
                    shadowColor = HdColorSpec.Fixed(0x60000000)
                )
            }
            val b = HdBuilder(box, stateStore)
            b.row(
                crossAlign = HdAlign.CENTER,
                padding = HdPadding.symmetric(horizontal = 12f, vertical = 8.5f),
                width = HdSizeSpec.Fill
            ) { hb ->
                hb.text(spec.title, size = 10.6f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark")) {
                    width = HdSizeSpec.Weight(1f)
                }
                hb.icon(HdIconAtlas.Icon.CLOSE, size = 11.5f, color = HdColorSpec.Token("text_dark_muted"))
            }
            b.divider(thickness = 0.8f, color = HdColorSpec.Token("primary_border", 0.45f))
            val bodyCol = ColumnNode(gap = 6f, crossAlign = HdAlign.STRETCH).apply {
                width = HdSizeSpec.Fill
                padding = HdPadding.all(12f)
            }
            spec.buildBody(HdBuilder(bodyCol, stateStore))
            box.add(bodyCol)

            box.measure(mCtx, mw, (screenRect.height - 24f).coerceAtLeast(80f))
            val bx = screenRect.x + (screenRect.width - box.measuredWidth) * 0.5f
            val by = screenRect.y + (screenRect.height - box.measuredHeight) * 0.5f
            box.arrange(bx, by, box.measuredWidth, box.measuredHeight)
            activeModalBoxBounds = box.bounds
            activeModalCloseBounds = HdBounds(box.bounds.right - 28f, box.bounds.y + 4f, 24f, 24f)
            box
        }
        if (modalSpec == null) {
            activeModalBoxBounds = HdBounds.EMPTY
            activeModalCloseBounds = HdBounds.EMPTY
        }
        modalRootNode = modalNode
        return root to modalNode
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val viewport = currentViewport()
        val theme = currentTheme
        val style = currentStyle()
        HdTextLayout.activeStyle = style
        val mx = viewport.mcGuiMouseToDpX(mouseX.toDouble())
        val my = viewport.mcGuiMouseToDpY(mouseY.toDouble())

        val (root, modalNode) = buildAndLayout(viewport, theme)
        val screenRect = screenRectDp()
        val activeTree = modalNode ?: root
        val hitPath = mutableListOf<HdNode>()
        activeTree.hitTest(mx, my, hitPath)
        stateStore.hoveredId = hitPath.lastOrNull { it.id != null }?.id

        val canvas = HdCanvas(viewport, style)
        canvas.begin(guiGraphics)
        try {
            renderShellAndWallpaper(canvas, theme, screenRect)
            if (viewportMode !is ViewportMode.Free) {
                renderTopBezel(canvas, theme, screenRect, mx, my)
            }

            val rc = HdRenderContext(
                canvas = canvas,
                guiGraphics = guiGraphics,
                colors = theme.colors,
                state = stateStore,
                mouseDpX = mx,
                mouseDpY = my,
                isModalOverlayOpen = modalNode != null
            )

            canvas.clipped(screenRect.x, screenRect.y, screenRect.width, screenRect.height) {
                screenBackdropOverride?.let { bgSpec ->
                    val col = bgSpec.resolve(theme.colors)
                    if (((col ushr 24) and 0xFF) > 0) {
                        canvas.rect(screenRect.x, screenRect.y, screenRect.width, screenRect.height, col)
                    }
                }
                root.render(rc)

                if (modalNode != null) {
                    canvas.rect(screenRect.x, screenRect.y, screenRect.width, screenRect.height, 0x9907101C.toInt())
                    modalNode.render(rc)
                }

                stateStore.currentToast()?.let { toast ->
                    renderToastDp(canvas, theme, screenRect, toast)
                }
            }

            if (modalNode == null) {
                val hoveredTooltip = hitPath.asReversed().firstNotNullOfOrNull { it.tooltip }
                if (!hoveredTooltip.isNullOrBlank()) {
                    renderTooltipDp(canvas, screenRect, hoveredTooltip, mx, my)
                }
            }
        } finally {
            canvas.end()
        }

        if (viewportMode !is ViewportMode.Free && showBezelBattery && CobblemonSmartphone.config.energy.enableEnergy) {
            val isLarge = viewportMode == ViewportMode.Large
            val texW = if (isLarge) 211f else 131f
            val texH = 207f
            val (dw, dh) = designSize()
            val scaleX = viewport.toMcGuiScale(dw / texW)
            val scaleY = viewport.toMcGuiScale(dh / texH)
            val originMcX = viewport.toMcGuiX(0f)
            val originMcY = viewport.toMcGuiY(0f)

            val matrices = guiGraphics.pose()
            matrices.pushPose()
            matrices.translate(originMcX, originMcY, 0f)
            matrices.scale(scaleX, scaleY, 1f)
            SmartphoneBatteryRenderer.render(guiGraphics, 0, 0, isLarge, smartphoneStack)
            matrices.popPose()

            if (modalNode == null) {
                val texMouseX = (mx / (dw / texW)).toInt()
                val texMouseY = (my / (dh / texH)).toInt()
                if (SmartphoneBatteryRenderer.isHovered(0, 0, texMouseX, texMouseY, isLarge)) {
                    val phone = SmartphoneBatteryRenderer.resolveSmartphone(smartphoneStack)
                    if (phone != null) {
                        val current = phone.getEnergy()
                        val max = phone.getMaxEnergy()
                        val isInfinite = phone.isInfiniteEnergy()
                        val tip = if (isInfinite) {
                            Component.translatable("tooltip.cobblemon_smartphone.energy_rotom_infinite")
                        } else {
                            val percent = if (max > 0) ((current.toDouble() / max) * 100).toInt() else 0
                            Component.translatable(
                                "tooltip.cobblemon_smartphone.energy_status",
                                "%,d".format(current),
                                "%,d".format(max),
                                percent
                            )
                        }
                        guiGraphics.renderTooltip(font, tip, mouseX, mouseY)
                    }
                }
            }
        }
    }

    private fun renderShellAndWallpaper(canvas: HdCanvas, theme: SmartphoneTheme, screenRect: HdBounds) {
        val (dw, dh) = designSize()
        when (val mode = viewportMode) {
            is ViewportMode.Free -> {
                canvas.shadow(0f, 0f, dw, dh, mode.cornerRadius, blur = 14f, offsetY = 4f, color = 0x55000000)
                canvas.rect(
                    0f, 0f, dw, dh,
                    mode.backdropColor.resolve(theme.colors),
                    mode.cornerRadius,
                    1f,
                    mode.borderColor.resolve(theme.colors)
                )
            }
            ViewportMode.Compact -> {
                val resMgr = Minecraft.getInstance().resourceManager
                val rawWall = wallpaperOverride ?: theme.wallpaper
                val wallTex = if (resMgr.getResource(rawWall).isPresent) rawWall else SmartphoneTheme.DEFAULT_WALLPAPER
                val frameTex = frameOverride ?: SmartphoneSkinHelper.resolveFrameTexture(smartphoneStack, theme, color)
                canvas.texture(wallTex, 0f, 0f, dw, dh, filter = HdCanvas.TextureFilter.NEAREST)
                canvas.texture(frameTex, 0f, 0f, dw, dh, filter = HdCanvas.TextureFilter.NEAREST)
            }
            ViewportMode.Large -> {
                val resMgr = Minecraft.getInstance().resourceManager
                val rawWall = wallpaperOverride ?: theme.getLargeWallpaperTexture()
                val wallTex = if (resMgr.getResource(rawWall).isPresent) rawWall else SmartphoneTheme.DEFAULT_LARGE_WALLPAPER
                val frameTex = frameOverride ?: SmartphoneSkinHelper.resolveLargeFrameTexture(null, theme, color)
                canvas.texture(wallTex, 0f, 0f, dw, dh, filter = HdCanvas.TextureFilter.NEAREST)
                canvas.texture(frameTex, 0f, 0f, dw, dh, filter = HdCanvas.TextureFilter.NEAREST)
            }
        }
    }

    private fun renderTopBezel(
        canvas: HdCanvas,
        theme: SmartphoneTheme,
        screenRect: HdBounds,
        mx: Float,
        my: Float
    ) {
        val c = theme.colors
        val bezelY = 27f

        if (showBezelBackButton) {
            val backX = screenRect.x + 5f
            val backY = if (viewportMode == ViewportMode.Compact) bezelY + 6f else bezelY + 2f
            val backBounds = HdBounds(backX, backY, 50f, 15f)
            activeBackBounds = backBounds
            val hovered = backBounds.contains(mx, my)
            if (hovered) {
                canvas.rect(backBounds.x, backBounds.y, backBounds.width, backBounds.height, 0x26FFFFFF, 5f)
            }
            val label = Component.translatable("cobblemon_smartphone.social.back").string.trimStart('<', '«', ' ')
            canvas.icon(HdIconAtlas.Icon.BACK, backBounds.x + 2f, backBounds.y + 2f, 11f, if (hovered) c.gold else c.textPrimary)
            canvas.text(label, backBounds.x + 14f, backBounds.y + 3f, 8.8f, if (hovered) c.gold else c.textPrimary, HdTextLayout.Weight.BOLD)
        } else {
            activeBackBounds = HdBounds.EMPTY
        }
    }

    private fun renderTooltipDp(canvas: HdCanvas, screenRect: HdBounds, tip: String, mx: Float, my: Float) {
        val fontSize = 8.6f
        val weight = HdTextLayout.Weight.MEDIUM
        val padX = 7f
        val padY = 4.5f
        val lineGap = 1.5f
        val maxTooltipW = minOf(screenRect.width - 12f, 220f).coerceAtLeast(40f)
        val maxTextW = (maxTooltipW - padX * 2f).coerceAtLeast(16f)
        val lines = HdTextLayout.wrapPlainText(tip, maxTextW, fontSize, weight, canvas.viewport.scale)
            .ifEmpty { listOf(tip) }
        val lineH = canvas.lineHeight(fontSize, weight)
        val contentW = lines.maxOf { canvas.measureText(it, fontSize, weight) }
        val tw = (contentW + padX * 2f).coerceAtMost(maxTooltipW)
        val totalTextH = lines.size * lineH + (lines.size - 1) * lineGap
        val th = maxOf(18f, totalTextH + padY * 2f)
        val minX = screenRect.x + 2f
        val maxX = maxOf(minX, screenRect.right - tw - 2f)
        val minY = screenRect.y + 2f
        val maxY = maxOf(minY, screenRect.bottom - th - 2f)
        val tx = (mx + 8f).coerceIn(minX, maxX)
        val preferredY = if (my + 12f + th <= screenRect.bottom - 2f) my + 12f else my - th - 6f
        val ty = preferredY.coerceIn(minY, maxY)
        canvas.shadow(tx, ty, tw, th, 6f, blur = 5f, offsetY = 1.5f, color = 0x55000000)
        canvas.rect(tx, ty, tw, th, 0xEE101824.toInt(), 6f, 0.7f, 0x55FFFFFF)
        var curY = ty + (th - totalTextH) * 0.5f
        for (line in lines) {
            canvas.text(line, tx + padX, curY, fontSize, 0xFFFFFFFF.toInt(), weight)
            curY += lineH + lineGap
        }
    }

    private fun renderToastDp(
        canvas: HdCanvas,
        theme: SmartphoneTheme,
        screenRect: HdBounds,
        toast: HdStateStore.ToastState
    ) {
        val alpha = toast.alpha()
        if (alpha <= 0.01f) return
        val c = theme.colors
        val accent = when (toast.kind) {
            HdStateStore.ToastKind.INFO -> c.primary
            HdStateStore.ToastKind.SUCCESS -> c.success
            HdStateStore.ToastKind.WARNING -> 0xFFD97706.toInt()
            HdStateStore.ToastKind.ERROR -> c.danger
        }
        val maxW = (screenRect.width - 24f).coerceAtLeast(80f)
        val iconSize = if (toast.icon != null) 11f else 0f
        val iconGap = if (toast.icon != null) 5f else 0f
        val maxTextW = (maxW - 18f - iconSize - iconGap).coerceAtLeast(24f)
        val trimmed = canvas.truncate(toast.message, maxTextW, 8.8f, HdTextLayout.Weight.BOLD)
        val textW = canvas.measureText(trimmed, 8.8f, HdTextLayout.Weight.BOLD)
        val toastW = (textW + iconSize + iconGap + 18f).coerceAtMost(maxW)
        val toastH = 22f
        val tx = screenRect.x + (screenRect.width - toastW) * 0.5f
        val ty = screenRect.bottom - toastH - 12f

        val bgCol = HdColors.multiplyAlpha(0xEE101824.toInt(), alpha)
        val brdCol = HdColors.multiplyAlpha(accent, alpha * 0.9f)
        val shCol = HdColors.multiplyAlpha(0x66000000, alpha)
        val fgCol = HdColors.multiplyAlpha(0xFFFFFFFF.toInt(), alpha)

        canvas.shadow(tx, ty, toastW, toastH, 10f, blur = 6f, offsetY = 2f, color = shCol)
        canvas.rect(tx, ty, toastW, toastH, bgCol, 10f, 1.0f, brdCol)

        var penX = tx + 9f
        toast.icon?.let { ic ->
            canvas.icon(ic, penX, ty + (toastH - iconSize) * 0.5f, iconSize, HdColors.multiplyAlpha(accent, alpha))
            penX += iconSize + iconGap
        }
        val lineH = canvas.lineHeight(8.8f, HdTextLayout.Weight.BOLD)
        canvas.text(trimmed, penX, ty + (toastH - lineH) * 0.5f, 8.8f, fgCol, HdTextLayout.Weight.BOLD)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        HdTextLayout.activeStyle = currentStyle()
        val viewport = currentViewport()
        val mx = viewport.mcGuiMouseToDpX(mouseX)
        val my = viewport.mcGuiMouseToDpY(mouseY)
        val theme = currentTheme

        val modalSpec = buildModal(theme)
        val modalNode = modalRootNode
        if (modalSpec != null && modalNode != null) {
            if (activeModalCloseBounds.contains(mx, my) || !activeModalBoxBounds.contains(mx, my)) {
                playClickSound()
                modalSpec.onClose()
                return true
            }
            val path = mutableListOf<HdNode>()
            modalNode.hitTest(mx, my, path)
            for (node in path.asReversed()) {
                if (node.onMouseDown(mx, my, button, stateStore)) {
                    activeDraggedNode = node
                    return true
                }
            }
            return true
        }

        if (button == 0 && activeBackBounds.contains(mx, my)) {
            playClickSound()
            onBackPressed()
            return true
        }

        val root = rootNode ?: return super.mouseClicked(mouseX, mouseY, button)
        val path = mutableListOf<HdNode>()
        root.hitTest(mx, my, path)

        var handled = false
        for (node in path.asReversed()) {
            if (node.onMouseDown(mx, my, button, stateStore)) {
                activeDraggedNode = node
                if (node !is TextInputNode) {
                    stateStore.clearFocus()
                }
                handled = true
                break
            }
        }
        if (!handled) {
            stateStore.clearFocus()
        }
        return handled || super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        val dragged = activeDraggedNode ?: return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        val viewport = currentViewport()
        val mx = viewport.mcGuiMouseToDpX(mouseX)
        val my = viewport.mcGuiMouseToDpY(mouseY)
        return dragged.onMouseDrag(mx, my, stateStore) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val dragged = activeDraggedNode
        activeDraggedNode = null
        if (dragged != null) {
            val viewport = currentViewport()
            val mx = viewport.mcGuiMouseToDpX(mouseX)
            val my = viewport.mcGuiMouseToDpY(mouseY)
            if (dragged.onMouseUp(mx, my, button, stateStore)) return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (verticalAmount == 0.0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        if (hasControlDown()) {
            HdUiPreferences.adjustUserScale(if (verticalAmount > 0) 0.05f else -0.05f)
            return true
        }
        val viewport = currentViewport()
        val mx = viewport.mcGuiMouseToDpX(mouseX)
        val my = viewport.mcGuiMouseToDpY(mouseY)
        val activeTree = modalRootNode ?: rootNode ?: return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        val path = mutableListOf<HdNode>()
        activeTree.hitTest(mx, my, path)
        for (node in path.asReversed()) {
            if (node.onScroll(mx, my, verticalAmount, stateStore)) return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 256) {
            val modalSpec = buildModal(currentTheme)
            if (modalSpec != null) {
                modalSpec.onClose()
                return true
            }
            if (stateStore.focusedId != null) {
                stateStore.clearFocus()
                return true
            }
        }
        val handler = stateStore.activeInputHandler
        if (handler != null && handler(HdStateStore.TextInputKeyEvent(keyCode, scanCode, modifiers))) {
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val handler = stateStore.activeCharHandler
        if (handler != null && handler(codePoint)) {
            return true
        }
        return super.charTyped(codePoint, modifiers)
    }

    protected fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }
}
