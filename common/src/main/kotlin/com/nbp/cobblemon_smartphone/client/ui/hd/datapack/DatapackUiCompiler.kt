package com.nbp.cobblemon_smartphone.client.ui.hd.datapack

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.google.gson.JsonElement
import com.nbp.cobblemon_smartphone.client.ui.hd.BadgeNode
import com.nbp.cobblemon_smartphone.client.ui.hd.ButtonNode
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBackground
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdCanvas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdNode
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.client.ui.hd.SegmentedControlNode
import com.nbp.cobblemon_smartphone.client.ui.hd.SliderNode
import com.nbp.cobblemon_smartphone.client.ui.hd.TabsNode
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackExpr
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackNodeDef
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Compiles a list of declarative [DatapackNodeDef] JSON nodes into a live [HdNode] subtree.
 */
object DatapackUiCompiler {

    interface HostCallbacks {
        fun onAction(actionKey: String, itemContext: Map<String, String>)
        fun getState(key: String): String
        fun setState(key: String, value: String)
        fun toggleState(key: String)
        fun onInputChanged(inputId: String, value: String)
        fun getFloatingState(): FloatingState
    }

    fun buildNodes(
        builder: HdBuilder,
        nodes: List<DatapackNodeDef>,
        ctx: DatapackExpr.EvalContext,
        callbacks: HostCallbacks
    ) {
        for (def in nodes) {
            buildNode(builder, def, ctx, callbacks)
        }
    }

    private fun buildNode(
        builder: HdBuilder,
        def: DatapackNodeDef,
        ctx: DatapackExpr.EvalContext,
        callbacks: HostCallbacks
    ) {
        if (!DatapackExpr.evaluateCondition(def.visibleIf, ctx)) return

        val type = (def.type ?: "column").trim().lowercase()
        if (type == "for_each") {
            buildForEachChildren(builder, def, ctx, callbacks)
            return
        }

        val itemCtx = ctx.item
        val common: HdNode.() -> Unit = {
            if (!def.id.isNullOrBlank()) id = DatapackExpr.resolveTemplate(def.id, ctx)
            parseSize(def.width, ctx)?.let { width = it }
            parseSize(def.height, ctx)?.let { height = it }
            def.minWidth?.let { minWidth = it }
            def.minHeight?.let { minHeight = it }
            def.maxWidth?.let { maxWidth = it }
            def.maxHeight?.let { maxHeight = it }
            parsePadding(def.padding)?.let { padding = it }
            clipChildren = def.clipChildren
            def.tooltip?.let { tooltip = DatapackExpr.resolveTemplate(it, ctx) }
            parseBackground(def, ctx)?.let { background = it }
            parseHoverBackground(def, ctx)?.let { hoverBackground = it }
            def.onClick?.takeIf { it.isNotBlank() }?.let { act ->
                onClick = { callbacks.onAction(act, itemCtx) }
            }
        }

        when (type) {
            "column" -> {
                builder.column(
                    gap = def.gap,
                    crossAlign = parseAlign(def.crossAlign, HdAlign.START),
                    mainAlign = parseAlign(def.mainAlign, HdAlign.START)
                ) { cb ->
                    common()
                    compileChildrenOrForEach(cb, def, ctx, callbacks)
                }
            }
            "row" -> {
                builder.row(
                    gap = def.gap,
                    crossAlign = parseAlign(def.crossAlign, HdAlign.START),
                    mainAlign = parseAlign(def.mainAlign, HdAlign.START)
                ) { rb ->
                    common()
                    compileChildrenOrForEach(rb, def, ctx, callbacks)
                }
            }
            "stack" -> {
                builder.stack(
                    hAlign = parseAlign(def.hAlign, HdAlign.START),
                    vAlign = parseAlign(def.vAlign, HdAlign.START)
                ) { sb ->
                    common()
                    compileChildrenOrForEach(sb, def, ctx, callbacks)
                }
            }
            "grid" -> {
                builder.grid(
                    columns = (def.columns ?: 2).coerceIn(1, 6),
                    colGap = def.colGap ?: 8f,
                    rowGap = def.rowGap ?: 8f
                ) { gb ->
                    common()
                    compileChildrenOrForEach(gb, def, ctx, callbacks)
                }
            }
            "flow_row", "wrap" -> {
                builder.flowRow(
                    hGap = def.hGap ?: def.colGap ?: 6f,
                    vGap = def.vGap ?: def.rowGap ?: 6f,
                    lineAlign = parseAlign(def.lineAlign ?: def.mainAlign, HdAlign.START),
                    crossAlign = parseAlign(def.crossAlign, HdAlign.CENTER)
                ) { fb ->
                    common()
                    compileChildrenOrForEach(fb, def, ctx, callbacks)
                }
            }
            "accordion", "collapsible" -> {
                val titleStr = DatapackExpr.resolveTemplate(def.title ?: def.label ?: "", ctx)
                val subtitleStr = def.subtitle?.let { DatapackExpr.resolveTemplate(it, ctx) }
                val stateKey = def.stateKey ?: def.id
                val isExpanded = when {
                    def.expanded != null -> DatapackExpr.evaluateCondition(def.expanded, ctx)
                    !stateKey.isNullOrBlank() -> DatapackExpr.asTrue(callbacks.getState(stateKey))
                    else -> false
                }
                builder.accordion(
                    title = titleStr,
                    expanded = isExpanded,
                    subtitle = subtitleStr,
                    icon = parseIcon(def.icon),
                    gap = if (def.gap > 0f) def.gap else 6f,
                    radius = def.radius ?: 9f,
                    onToggle = { next ->
                        if (!stateKey.isNullOrBlank()) callbacks.setState(stateKey, next.toString())
                        def.onChange?.takeIf { it.isNotBlank() }?.let { callbacks.onAction(it, itemCtx) }
                    }
                ) { ab ->
                    common()
                    compileChildrenOrForEach(ab, def, ctx, callbacks)
                }
            }
            "list_tile" -> {
                val titleStr = DatapackExpr.resolveTemplate(def.title ?: def.label ?: def.text ?: "", ctx)
                val subtitleStr = def.subtitle?.let { DatapackExpr.resolveTemplate(it, ctx) }
                val trailingStr = def.trailingText?.let { DatapackExpr.resolveTemplate(it, ctx) }
                val enabledVal = if (def.enabled != null) DatapackExpr.evaluateCondition(def.enabled, ctx) else true
                builder.listTile(
                    title = titleStr,
                    subtitle = subtitleStr,
                    leadingIcon = parseIcon(def.icon),
                    leadingIconColor = parseColor(def.color, ctx) ?: HdColorSpec.Token("primary"),
                    trailingText = trailingStr,
                    showChevron = def.showChevron,
                    enabled = enabledVal,
                    radius = def.radius ?: 8f,
                    showBackground = def.showBackground != false,
                    onClick = def.onClick?.takeIf { it.isNotBlank() }?.let { act -> { callbacks.onAction(act, itemCtx) } }
                ) { lb ->
                    common()
                    compileChildrenOrForEach(lb, def, ctx, callbacks)
                }
            }
            "app_header", "header" -> {
                val titleStr = DatapackExpr.resolveTemplate(def.title ?: def.label ?: def.text ?: "", ctx)
                val subtitleStr = def.subtitle?.let { DatapackExpr.resolveTemplate(it, ctx) }
                builder.appHeader(
                    title = titleStr,
                    subtitle = subtitleStr,
                    icon = parseIcon(def.icon),
                    iconColor = parseColor(def.color, ctx) ?: HdColorSpec.Token("primary")
                ) { tb ->
                    compileChildrenOrForEach(tb, def, ctx, callbacks)
                }
            }
            "card" -> {
                val clickAction = def.onClick?.takeIf { it.isNotBlank() }
                builder.card(
                    gap = def.gap,
                    padding = parsePadding(def.padding) ?: HdPadding.all(10f),
                    radius = def.radius ?: 10f,
                    shadowBlur = def.shadowBlur ?: 6f,
                    hoverHighlight = def.hoverHighlight || clickAction != null,
                    onClick = clickAction?.let { act -> { callbacks.onAction(act, itemCtx) } }
                ) { cb ->
                    common()
                    compileChildrenOrForEach(cb, def, ctx, callbacks)
                }
            }
            "scroll" -> {
                val scrollId = DatapackExpr.resolveTemplate(def.scrollId ?: def.id ?: "dp_scroll", ctx)
                val node = builder.scroll(
                    scrollId = scrollId,
                    gap = def.gap,
                    padding = parsePadding(def.padding) ?: HdPadding.ZERO
                ) { sb ->
                    compileChildrenOrForEach(sb, def, ctx, callbacks)
                }
                node.anchorBottom = def.anchorBottom
                node.apply {
                    if (def.width != null) parseSize(def.width, ctx)?.let { width = it }
                    if (def.height != null) parseSize(def.height, ctx)?.let { height = it }
                    parseBackground(def, ctx)?.let { background = it }
                }
            }
            "spacer" -> {
                val wSpec = parseSize(def.width, ctx)
                val weightVal = (wSpec as? HdSizeSpec.Weight)?.weight
                builder.spacer(w = def.w, h = def.h, weight = weightVal).apply {
                    common()
                }
            }
            "divider" -> {
                val col = parseColor(def.color, ctx) ?: HdColorSpec.Token("primary_border", 0.35f)
                builder.divider(thickness = def.thickness ?: 0.8f, color = col, vertical = def.vertical).apply {
                    common()
                }
            }
            "text" -> {
                val content = DatapackExpr.resolveTemplate(def.text.orEmpty(), ctx)
                builder.text(
                    text = content,
                    size = def.size ?: 10f,
                    weight = parseWeight(def.weight),
                    color = parseColor(def.color, ctx) ?: HdColorSpec.Token("text_dark"),
                    wrap = def.wrap,
                    align = parseAlign(def.align, HdAlign.START)
                ) {
                    def.maxLines?.let { maxLines = it.coerceAtLeast(1) }
                    common()
                }
            }
            "icon" -> {
                val ic = parseIcon(def.icon) ?: HdIconAtlas.Icon.SPARKLES
                builder.icon(
                    icon = ic,
                    size = def.size ?: 14f,
                    color = parseColor(def.color, ctx) ?: HdColorSpec.Token("text_dark")
                ) {
                    common()
                }
            }
            "image" -> {
                val tex = def.texture?.let { DatapackExpr.resolveTemplate(it, ctx) }
                    ?.let(ResourceLocation::tryParse) ?: return
                val imgW = def.imageWidth ?: def.w.takeIf { it > 0f } ?: 32f
                val imgH = def.imageHeight ?: def.h.takeIf { it > 0f } ?: 32f
                val filterMode = if (def.filter.equals("nearest", ignoreCase = true)) {
                    HdCanvas.TextureFilter.NEAREST
                } else {
                    HdCanvas.TextureFilter.LINEAR
                }
                builder.image(
                    texture = tex,
                    w = imgW,
                    h = imgH,
                    radius = def.radius ?: 0f,
                    filter = filterMode
                ) {
                    u0 = def.u0
                    v0 = def.v0
                    u1 = def.u1 ?: 1f
                    v1 = def.v1 ?: 1f
                    common()
                }
            }
            "avatar" -> {
                val name = DatapackExpr.resolveTemplate(def.playerName ?: "{player.name}", ctx)
                val uuidStr = DatapackExpr.resolveTemplate(def.uuid ?: "{player.uuid}", ctx)
                val parsedUuid = runCatching { UUID.fromString(uuidStr) }
                    .getOrElse { UUID.nameUUIDFromBytes(name.toByteArray()) }
                val skinUrl = def.skinUrl?.let { DatapackExpr.resolveTemplate(it, ctx) }
                builder.avatar(
                    uuid = parsedUuid,
                    name = name,
                    skinUrl = skinUrl,
                    size = def.size ?: 26f
                ) {
                    common()
                }
            }
            "item_icon" -> {
                val itemStr = DatapackExpr.resolveTemplate(def.item.orEmpty(), ctx)
                val loc = ResourceLocation.tryParse(itemStr) ?: return
                val item = BuiltInRegistries.ITEM.getOptional(loc).orElse(null) ?: return
                val cnt = (def.count ?: 1).coerceIn(1, 64)
                builder.itemIcon(
                    stack = ItemStack(item, cnt),
                    size = def.size ?: 22f,
                    showCount = def.showCount != false
                ) {
                    common()
                }
            }
            "pokemon_view" -> {
                val rawSpecies = DatapackExpr.resolveTemplate(def.species ?: "cobblemon:pikachu", ctx)
                val sp = PokeInfoDataProvider.findSpecies(rawSpecies)
                val resolvedSpecies = sp?.resourceIdentifier?.toString()
                    ?: if (':' in rawSpecies) rawSpecies else "cobblemon:$rawSpecies"
                val aspectSet = DatapackExpr.resolveTemplate(def.aspects.orEmpty(), ctx)
                    .split(',')
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                builder.pokemonView(
                    speciesId = resolvedSpecies,
                    aspects = aspectSet,
                    size = def.size ?: 48f,
                    floatingState = callbacks.getFloatingState(),
                    poseType = PoseType.PROFILE,
                    showShinyBadge = def.showShinyBadge != false
                ) {
                    common()
                }
            }
            "progress_bar" -> {
                val progRaw = def.progress?.let { DatapackExpr.resolveTemplate(it, ctx) }?.toFloatOrNull() ?: 0f
                val labelStr = def.label?.let { DatapackExpr.resolveTemplate(it, ctx) }
                builder.progressBar(
                    progress = progRaw.coerceIn(0f, 1f),
                    height = def.h.takeIf { it > 0f } ?: 8f,
                    label = labelStr
                ) {
                    def.radius?.let { radius = it }
                    parseColor(def.trackColor, ctx)?.let { trackColor = it }
                    parseColor(def.fillColor, ctx)?.let { fillColor = it }
                    parseColor(def.borderColor, ctx)?.let { borderColor = it }
                    common()
                }
            }
            "badge", "chip" -> {
                val lbl = DatapackExpr.resolveTemplate(def.label ?: def.text ?: "", ctx)
                builder.badge(
                    label = lbl,
                    icon = parseIcon(def.icon),
                    variant = parseBadgeVariant(def.variant),
                    fontSize = def.size ?: 7.8f,
                    radius = def.radius ?: 9f
                ) {
                    parseColor(def.background, ctx)?.let { customBg = it }
                    parseColor(def.color, ctx)?.let { customTextColor = it }
                    common()
                }
            }
            "stat_bar" -> {
                val lbl = DatapackExpr.resolveTemplate(def.label ?: def.text ?: "", ctx)
                val valNum = def.value?.let { DatapackExpr.resolveTemplate(it, ctx) }?.toFloatOrNull() ?: 0f
                val maxVal = (def.maxValue ?: def.max ?: 255f).coerceAtLeast(1f)
                val valTxt = def.valueText?.let { DatapackExpr.resolveTemplate(it, ctx) }
                builder.statBar(
                    label = lbl,
                    value = valNum,
                    maxValue = maxVal,
                    valueText = valTxt,
                    fillColor = parseColor(def.fillColor ?: def.color, ctx) ?: HdColorSpec.Token("primary")
                ) {
                    parseColor(def.trackColor, ctx)?.let { trackColor = it }
                    def.size?.let { fontSizeDp = it }
                    common()
                }
            }
            "empty_state" -> {
                val titleStr = DatapackExpr.resolveTemplate(def.title ?: def.label ?: def.text ?: "", ctx)
                val descStr = def.description?.let { DatapackExpr.resolveTemplate(it, ctx) }
                    ?: def.subtitle?.let { DatapackExpr.resolveTemplate(it, ctx) }
                val actionLbl = def.actionLabel?.let { DatapackExpr.resolveTemplate(it, ctx) }
                val clickAct = def.onClick?.takeIf { it.isNotBlank() }
                builder.emptyState(
                    title = titleStr,
                    description = descStr,
                    icon = parseIcon(def.icon) ?: HdIconAtlas.Icon.INFO,
                    iconColor = parseColor(def.color, ctx) ?: HdColorSpec.Token("primary"),
                    actionLabel = actionLbl,
                    actionIcon = parseIcon(def.actionIcon),
                    onAction = clickAct?.let { act -> { callbacks.onAction(act, itemCtx) } }
                ) {
                    common()
                }
            }
            "button" -> {
                val lbl = DatapackExpr.resolveTemplate(def.label ?: def.text ?: "", ctx)
                val enabledVal = if (def.enabled != null) DatapackExpr.evaluateCondition(def.enabled, ctx) else true
                builder.button(
                    label = lbl,
                    icon = parseIcon(def.icon),
                    variant = parseButtonVariant(def.variant),
                    fontSize = def.size ?: 9.2f,
                    radius = def.radius ?: 8f,
                    enabled = enabledVal,
                    onClick = def.onClick?.let { act -> { callbacks.onAction(act, itemCtx) } }
                ) {
                    common()
                }
            }
            "pill" -> {
                val lbl = DatapackExpr.resolveTemplate(def.label ?: def.text ?: "", ctx)
                val isActive = if (def.active != null) DatapackExpr.evaluateCondition(def.active, ctx) else false
                builder.pill(
                    label = lbl,
                    icon = parseIcon(def.icon),
                    active = isActive,
                    onClick = def.onClick?.let { act -> { callbacks.onAction(act, itemCtx) } }
                ) {
                    common()
                }
            }
            "tabs" -> {
                val tabItems = def.tabs.orEmpty().map {
                    TabsNode.TabItem(
                        id = DatapackExpr.resolveTemplate(it.id.orEmpty(), ctx),
                        label = DatapackExpr.resolveTemplate(it.label.orEmpty(), ctx),
                        icon = parseIcon(it.icon)
                    )
                }
                val stateKey = def.stateKey ?: "tab"
                val currentActive = if (!def.activeId.isNullOrBlank()) {
                    DatapackExpr.resolveTemplate(def.activeId, ctx)
                } else {
                    callbacks.getState(stateKey).ifEmpty { tabItems.firstOrNull()?.id.orEmpty() }
                }
                val style = when (def.style?.trim()?.lowercase()) {
                    "bottom_nav" -> TabsNode.Style.BOTTOM_NAV
                    "floating_dock", "dock" -> TabsNode.Style.FLOATING_DOCK
                    else -> TabsNode.Style.PILLS
                }
                builder.tabs(
                    items = tabItems,
                    activeId = currentActive,
                    style = style,
                    onSelect = { selected ->
                        callbacks.setState(stateKey, selected)
                        def.onChange?.takeIf { it.isNotBlank() }?.let { callbacks.onAction(it, itemCtx) }
                    }
                ) {
                    common()
                }
            }
            "checkbox" -> {
                val lbl = DatapackExpr.resolveTemplate(def.label ?: def.text ?: "", ctx)
                val stateKey = def.stateKey ?: def.id
                val checkedVal = when {
                    def.checked != null -> DatapackExpr.evaluateCondition(def.checked, ctx)
                    !stateKey.isNullOrBlank() -> DatapackExpr.asTrue(callbacks.getState(stateKey))
                    else -> false
                }
                val enabledVal = if (def.enabled != null) DatapackExpr.evaluateCondition(def.enabled, ctx) else true
                builder.checkbox(
                    label = lbl,
                    checked = checkedVal,
                    enabled = enabledVal,
                    fontSize = def.size ?: 8.8f,
                    onToggle = { next ->
                        if (!stateKey.isNullOrBlank()) callbacks.setState(stateKey, next.toString())
                        def.onChange?.takeIf { it.isNotBlank() }?.let { callbacks.onAction(it, itemCtx) }
                    }
                ) {
                    common()
                }
            }
            "switch" -> {
                val lbl = DatapackExpr.resolveTemplate(def.label ?: def.text ?: "", ctx)
                val stateKey = def.stateKey ?: def.id
                val checkedVal = when {
                    def.checked != null -> DatapackExpr.evaluateCondition(def.checked, ctx)
                    !stateKey.isNullOrBlank() -> DatapackExpr.asTrue(callbacks.getState(stateKey))
                    else -> false
                }
                val enabledVal = if (def.enabled != null) DatapackExpr.evaluateCondition(def.enabled, ctx) else true
                builder.switch(
                    label = lbl,
                    checked = checkedVal,
                    enabled = enabledVal,
                    fontSize = def.size ?: 9.0f,
                    onToggle = { next ->
                        if (!stateKey.isNullOrBlank()) callbacks.setState(stateKey, next.toString())
                        def.onChange?.takeIf { it.isNotBlank() }?.let { callbacks.onAction(it, itemCtx) }
                    }
                ) {
                    common()
                }
            }
            "slider" -> {
                val lbl = DatapackExpr.resolveTemplate(def.label ?: def.text ?: "", ctx)
                val stateKey = def.stateKey ?: def.id
                val minVal = def.min ?: 0f
                val maxVal = def.max ?: 100f
                val stepVal = def.step ?: 1f
                val rawVal = when {
                    !def.value.isNullOrBlank() -> DatapackExpr.resolveTemplate(def.value, ctx).toFloatOrNull()
                    !stateKey.isNullOrBlank() -> callbacks.getState(stateKey).toFloatOrNull()
                    else -> null
                } ?: minVal
                val enabledVal = if (def.enabled != null) DatapackExpr.evaluateCondition(def.enabled, ctx) else true
                builder.slider(
                    value = rawVal.coerceIn(minVal, maxOf(minVal, maxVal)),
                    min = minVal,
                    max = maxVal,
                    step = stepVal,
                    label = lbl,
                    showValue = def.showValue != false,
                    enabled = enabledVal,
                    onChange = { next ->
                        if (!stateKey.isNullOrBlank()) {
                            callbacks.setState(stateKey, SliderNode.formatDefault(next, stepVal))
                        }
                        def.onChange?.takeIf { it.isNotBlank() }?.let { callbacks.onAction(it, itemCtx) }
                    }
                ) {
                    def.size?.let { fontSizeDp = it }
                    common()
                }
            }
            "number_stepper", "stepper" -> {
                val lbl = DatapackExpr.resolveTemplate(def.label ?: def.text ?: "", ctx)
                val stateKey = def.stateKey ?: def.id
                val minVal = def.min ?: 0f
                val maxVal = def.max ?: 100f
                val stepVal = def.step ?: 1f
                val rawVal = when {
                    !def.value.isNullOrBlank() -> DatapackExpr.resolveTemplate(def.value, ctx).toFloatOrNull()
                    !stateKey.isNullOrBlank() -> callbacks.getState(stateKey).toFloatOrNull()
                    else -> null
                } ?: minVal
                val enabledVal = if (def.enabled != null) DatapackExpr.evaluateCondition(def.enabled, ctx) else true
                builder.numberStepper(
                    value = rawVal.coerceIn(minVal, maxOf(minVal, maxVal)),
                    min = minVal,
                    max = maxVal,
                    step = stepVal,
                    label = lbl,
                    enabled = enabledVal,
                    onChange = { next ->
                        if (!stateKey.isNullOrBlank()) {
                            callbacks.setState(stateKey, SliderNode.formatDefault(next, stepVal))
                        }
                        def.onChange?.takeIf { it.isNotBlank() }?.let { callbacks.onAction(it, itemCtx) }
                    }
                ) {
                    def.size?.let { fontSizeDp = it }
                    common()
                }
            }
            "segmented_control", "segmented" -> {
                val rawItems = (def.options.orEmpty().ifEmpty { def.tabs.orEmpty() })
                val opts = rawItems.map {
                    SegmentedControlNode.Option(
                        id = DatapackExpr.resolveTemplate(it.id.orEmpty(), ctx),
                        label = DatapackExpr.resolveTemplate(it.label.orEmpty(), ctx),
                        icon = parseIcon(it.icon)
                    )
                }
                val stateKey = def.stateKey ?: def.id ?: "segment"
                val currentSelected = when {
                    !def.selectedId.isNullOrBlank() -> DatapackExpr.resolveTemplate(def.selectedId, ctx)
                    !def.activeId.isNullOrBlank() -> DatapackExpr.resolveTemplate(def.activeId, ctx)
                    else -> callbacks.getState(stateKey).ifEmpty { opts.firstOrNull()?.id.orEmpty() }
                }
                val enabledVal = if (def.enabled != null) DatapackExpr.evaluateCondition(def.enabled, ctx) else true
                builder.segmentedControl(
                    options = opts,
                    selectedId = currentSelected,
                    enabled = enabledVal,
                    onSelect = { selected ->
                        callbacks.setState(stateKey, selected)
                        def.onChange?.takeIf { it.isNotBlank() }?.let { callbacks.onAction(it, itemCtx) }
                    }
                ) {
                    def.size?.let { fontSizeDp = it }
                    def.radius?.let { radius = it }
                    common()
                }
            }
            "text_input" -> {
                val inputId = DatapackExpr.resolveTemplate(def.inputId ?: def.id ?: "input", ctx)
                val placeholder = DatapackExpr.resolveTemplate(def.placeholder.orEmpty(), ctx)
                val initial = DatapackExpr.resolveTemplate(def.initialText.orEmpty(), ctx)
                builder.textInput(
                    inputId = inputId,
                    placeholder = placeholder,
                    initialText = initial,
                    maxLength = (def.maxLength ?: 280).coerceIn(1, 2048),
                    multiline = def.multiline,
                    showCounter = def.showCounter,
                    onChange = { text ->
                        callbacks.onInputChanged(inputId, text)
                        def.onChange?.takeIf { it.isNotBlank() }?.let { callbacks.onAction(it, itemCtx) }
                    },
                    onSubmit = def.onSubmit?.takeIf { it.isNotBlank() }?.let { act ->
                        { _ -> callbacks.onAction(act, itemCtx) }
                    }
                ) {
                    common()
                }
            }
        }
    }

    private fun compileChildrenOrForEach(
        builder: HdBuilder,
        def: DatapackNodeDef,
        ctx: DatapackExpr.EvalContext,
        callbacks: HostCallbacks
    ) {
        if (!def.forEach.isNullOrBlank()) {
            buildForEachChildren(builder, def, ctx, callbacks)
        } else {
            buildNodes(builder, def.children.orEmpty(), ctx, callbacks)
        }
    }

    private fun buildForEachChildren(
        builder: HdBuilder,
        def: DatapackNodeDef,
        ctx: DatapackExpr.EvalContext,
        callbacks: HostCallbacks
    ) {
        val rawSource = def.forEach?.trim().orEmpty()
        val listKey = rawSource.removePrefix("lists.").removePrefix("list.")
        val items = ctx.lists[listKey].orEmpty()
        if (items.isEmpty()) {
            val emptyMsg = def.emptyText?.let { DatapackExpr.resolveTemplate(it, ctx) }
            if (!emptyMsg.isNullOrBlank()) {
                builder.text(
                    text = emptyMsg,
                    size = 9.2f,
                    color = HdColorSpec.Token("text_secondary"),
                    align = HdAlign.CENTER,
                    wrap = true
                ) {
                    width = HdSizeSpec.Fill
                }
            }
            return
        }

        val alias = (def.forEachAs ?: "item").trim().ifEmpty { "item" }
        items.forEachIndexed { idx, row ->
            val enrichedRow = LinkedHashMap(row).apply {
                putIfAbsent("_index", idx.toString())
                putIfAbsent("_number", (idx + 1).toString())
            }
            val mergedPlaceholders = if (alias != "item") {
                LinkedHashMap(ctx.placeholders).apply {
                    for ((k, v) in enrichedRow) put("$alias.$k", v)
                }
            } else {
                ctx.placeholders
            }
            val childCtx = ctx.copy(placeholders = mergedPlaceholders, item = enrichedRow)
            buildNodes(builder, def.children.orEmpty(), childCtx, callbacks)
        }
    }

    private fun DatapackExpr.asTrue(value: String): Boolean =
        value.equals("true", ignoreCase = true) || value == "1"

    private fun parseSize(raw: String?, ctx: DatapackExpr.EvalContext): HdSizeSpec? {
        if (raw.isNullOrBlank()) return null
        val s = DatapackExpr.resolveTemplate(raw, ctx).trim().lowercase()
        return when {
            s == "fill" -> HdSizeSpec.Fill
            s == "wrap" -> HdSizeSpec.Wrap
            s.startsWith("weight(") && s.endsWith(")") ->
                HdSizeSpec.Weight(s.removePrefix("weight(").removeSuffix(")").toFloatOrNull() ?: 1f)
            s.startsWith("weight:") ->
                HdSizeSpec.Weight(s.removePrefix("weight:").toFloatOrNull() ?: 1f)
            s.endsWith("*") ->
                HdSizeSpec.Weight(s.removeSuffix("*").toFloatOrNull() ?: 1f)
            else -> s.removeSuffix("dp").toFloatOrNull()?.let { HdSizeSpec.Fixed(it) }
        }
    }

    private fun parsePadding(el: JsonElement?): HdPadding? {
        if (el == null || el.isJsonNull) return null
        if (el.isJsonPrimitive) {
            val all = el.asFloat
            return HdPadding.all(all)
        }
        if (el.isJsonArray) {
            val arr = el.asJsonArray
            return when (arr.size()) {
                2 -> HdPadding.symmetric(arr[0].asFloat, arr[1].asFloat)
                4 -> HdPadding(arr[0].asFloat, arr[1].asFloat, arr[2].asFloat, arr[3].asFloat)
                else -> null
            }
        }
        if (el.isJsonObject) {
            val obj = el.asJsonObject
            val h = obj.get("horizontal")?.asFloat
            val v = obj.get("vertical")?.asFloat
            return HdPadding(
                left = obj.get("left")?.asFloat ?: h ?: 0f,
                top = obj.get("top")?.asFloat ?: v ?: 0f,
                right = obj.get("right")?.asFloat ?: h ?: 0f,
                bottom = obj.get("bottom")?.asFloat ?: v ?: 0f
            )
        }
        return null
    }

    private fun parseAlign(raw: String?, default: HdAlign): HdAlign = when (raw?.trim()?.lowercase()) {
        "start", "left", "top" -> HdAlign.START
        "center" -> HdAlign.CENTER
        "end", "right", "bottom" -> HdAlign.END
        "stretch" -> HdAlign.STRETCH
        else -> default
    }

    private fun parseWeight(raw: String?): HdTextLayout.Weight = when (raw?.trim()?.lowercase()) {
        "bold" -> HdTextLayout.Weight.BOLD
        "medium" -> HdTextLayout.Weight.MEDIUM
        else -> HdTextLayout.Weight.REGULAR
    }

    private fun parseButtonVariant(raw: String?): ButtonNode.Variant = when (raw?.trim()?.lowercase()) {
        "secondary" -> ButtonNode.Variant.SECONDARY
        "surface" -> ButtonNode.Variant.SURFACE
        "danger" -> ButtonNode.Variant.DANGER
        "ghost" -> ButtonNode.Variant.GHOST
        else -> ButtonNode.Variant.PRIMARY
    }

    private fun parseBadgeVariant(raw: String?): BadgeNode.Variant = when (raw?.trim()?.lowercase()) {
        "success", "green" -> BadgeNode.Variant.SUCCESS
        "warning", "orange", "amber" -> BadgeNode.Variant.WARNING
        "danger", "error", "red" -> BadgeNode.Variant.DANGER
        "neutral", "muted", "surface" -> BadgeNode.Variant.NEUTRAL
        "gold", "yellow" -> BadgeNode.Variant.GOLD
        else -> BadgeNode.Variant.PRIMARY
    }

    private fun parseColor(raw: String?, ctx: DatapackExpr.EvalContext): HdColorSpec? {
        if (raw.isNullOrBlank()) return null
        val resolved = DatapackExpr.resolveTemplate(raw, ctx)
        return HdColorSpec.parse(resolved)
    }

    private fun parseBackground(def: DatapackNodeDef, ctx: DatapackExpr.EvalContext): HdBackground? {
        val bgColor = parseColor(def.background, ctx)
        val borderColor = parseColor(def.borderColor, ctx)
        if (bgColor == null && borderColor == null && def.radius == null && def.shadowBlur == null) return null
        return HdBackground(
            color = bgColor ?: HdColorSpec.TRANSPARENT,
            radius = def.radius ?: 0f,
            borderWidth = def.borderWidth ?: (if (borderColor != null) 0.75f else 0f),
            borderColor = borderColor ?: HdColorSpec.TRANSPARENT,
            shadowBlur = def.shadowBlur ?: 0f,
            shadowOffsetY = if ((def.shadowBlur ?: 0f) > 0f) 1.5f else 0f,
            shadowColor = if ((def.shadowBlur ?: 0f) > 0f) HdColorSpec.Fixed(0x28000000) else HdColorSpec.TRANSPARENT
        )
    }

    private fun parseHoverBackground(def: DatapackNodeDef, ctx: DatapackExpr.EvalContext): HdBackground? {
        val hoverCol = parseColor(def.hoverBackground, ctx) ?: return null
        val borderColor = parseColor(def.borderColor, ctx) ?: HdColorSpec.TRANSPARENT
        return HdBackground(
            color = hoverCol,
            radius = def.radius ?: 0f,
            borderWidth = def.borderWidth ?: (if (def.borderColor != null) 0.75f else 0f),
            borderColor = borderColor,
            shadowBlur = def.shadowBlur ?: 0f
        )
    }

    private fun parseIcon(raw: String?): HdIconAtlas.Icon? {
        if (raw.isNullOrBlank()) return null
        return HdIconAtlas.Icon.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}
