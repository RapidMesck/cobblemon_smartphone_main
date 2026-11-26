package com.nbp.cobblemon_smartphone.client.ui.hd

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.UUID

@DslMarker
annotation class HdDslMarker

@HdDslMarker
class HdBuilder(
    private val container: HdContainerNode,
    val stateStore: HdStateStore
) {
    fun <T : HdNode> add(node: T, configure: T.() -> Unit = {}): T {
        node.configure()
        container.add(node)
        return node
    }

    fun column(
        gap: Float = 0f,
        crossAlign: HdAlign = HdAlign.START,
        mainAlign: HdAlign = HdAlign.START,
        padding: HdPadding = HdPadding.ZERO,
        width: HdSizeSpec = HdSizeSpec.Wrap,
        height: HdSizeSpec = HdSizeSpec.Wrap,
        background: HdBackground? = null,
        configure: ColumnNode.(HdBuilder) -> Unit
    ): ColumnNode {
        val node = ColumnNode(gap, crossAlign, mainAlign).apply {
            this.padding = padding
            this.width = width
            this.height = height
            this.background = background
        }
        node.configure(HdBuilder(node, stateStore))
        container.add(node)
        return node
    }

    fun row(
        gap: Float = 0f,
        crossAlign: HdAlign = HdAlign.START,
        mainAlign: HdAlign = HdAlign.START,
        padding: HdPadding = HdPadding.ZERO,
        width: HdSizeSpec = HdSizeSpec.Wrap,
        height: HdSizeSpec = HdSizeSpec.Wrap,
        background: HdBackground? = null,
        configure: RowNode.(HdBuilder) -> Unit
    ): RowNode {
        val node = RowNode(gap, crossAlign, mainAlign).apply {
            this.padding = padding
            this.width = width
            this.height = height
            this.background = background
        }
        node.configure(HdBuilder(node, stateStore))
        container.add(node)
        return node
    }

    fun stack(
        hAlign: HdAlign = HdAlign.START,
        vAlign: HdAlign = HdAlign.START,
        padding: HdPadding = HdPadding.ZERO,
        width: HdSizeSpec = HdSizeSpec.Wrap,
        height: HdSizeSpec = HdSizeSpec.Wrap,
        configure: StackNode.(HdBuilder) -> Unit
    ): StackNode {
        val node = StackNode(hAlign, vAlign).apply {
            this.padding = padding
            this.width = width
            this.height = height
        }
        node.configure(HdBuilder(node, stateStore))
        container.add(node)
        return node
    }

    fun grid(
        columns: Int = 2,
        colGap: Float = 8f,
        rowGap: Float = 8f,
        padding: HdPadding = HdPadding.ZERO,
        width: HdSizeSpec = HdSizeSpec.Fill,
        configure: GridNode.(HdBuilder) -> Unit
    ): GridNode {
        val node = GridNode(columns, colGap, rowGap).apply {
            this.padding = padding
            this.width = width
        }
        node.configure(HdBuilder(node, stateStore))
        container.add(node)
        return node
    }

    fun flowRow(
        hGap: Float = 6f,
        vGap: Float = 6f,
        lineAlign: HdAlign = HdAlign.START,
        crossAlign: HdAlign = HdAlign.CENTER,
        padding: HdPadding = HdPadding.ZERO,
        width: HdSizeSpec = HdSizeSpec.Fill,
        configure: FlowRowNode.(HdBuilder) -> Unit
    ): FlowRowNode {
        val node = FlowRowNode(hGap, vGap, lineAlign, crossAlign).apply {
            this.padding = padding
            this.width = width
        }
        node.configure(HdBuilder(node, stateStore))
        container.add(node)
        return node
    }

    fun accordion(
        title: String,
        expanded: Boolean = false,
        subtitle: String? = null,
        icon: HdIconAtlas.Icon? = null,
        gap: Float = 6f,
        radius: Float = 9f,
        onToggle: ((Boolean) -> Unit)? = null,
        configure: AccordionNode.(HdBuilder) -> Unit
    ): AccordionNode {
        val node = AccordionNode(
            title = title,
            expanded = expanded,
            subtitle = subtitle,
            icon = icon,
            gap = gap,
            radius = radius,
            onToggle = onToggle
        )
        node.configure(HdBuilder(node, stateStore))
        container.add(node)
        return node
    }

    fun listTile(
        title: String,
        subtitle: String? = null,
        leadingIcon: HdIconAtlas.Icon? = null,
        leadingIconColor: HdColorSpec = HdColorSpec.Token("primary"),
        trailingText: String? = null,
        showChevron: Boolean = false,
        enabled: Boolean = true,
        radius: Float = 8f,
        showBackground: Boolean = true,
        onClick: (() -> Unit)? = null,
        configure: ListTileNode.(HdBuilder) -> Unit = {}
    ): ListTileNode {
        val node = ListTileNode(
            title = title,
            subtitle = subtitle,
            leadingIcon = leadingIcon,
            leadingIconColor = leadingIconColor,
            trailingText = trailingText,
            showChevron = showChevron,
            enabled = enabled,
            radius = radius,
            showBackground = showBackground
        ).apply {
            this.onClick = onClick
        }
        node.configure(HdBuilder(node, stateStore))
        container.add(node)
        return node
    }

    fun card(
        gap: Float = 7f,
        padding: HdPadding = HdPadding.all(10f),
        radius: Float = 10f,
        shadowBlur: Float = 6f,
        hoverHighlight: Boolean = false,
        onClick: (() -> Unit)? = null,
        configure: ColumnNode.(HdBuilder) -> Unit
    ): ColumnNode {
        val normalBg = HdBackground(
            color = HdColorSpec.Token("surface"),
            radius = radius,
            borderWidth = 0.85f,
            borderColor = HdColorSpec.Token("primary_border"),
            shadowBlur = shadowBlur,
            shadowOffsetY = 1.5f,
            shadowColor = HdColorSpec.Fixed(0x26000000)
        )
        val hoverBg = if (hoverHighlight) normalBg.copy(color = HdColorSpec.Token("surface_hover")) else null
        val node = ColumnNode(gap = gap, crossAlign = HdAlign.STRETCH).apply {
            this.width = HdSizeSpec.Fill
            this.padding = padding
            this.background = normalBg
            this.hoverBackground = hoverBg
            this.onClick = onClick
        }
        node.configure(HdBuilder(node, stateStore))
        container.add(node)
        return node
    }

    fun scroll(
        scrollId: String,
        gap: Float = 8f,
        padding: HdPadding = HdPadding.ZERO,
        onNearBottom: (() -> Unit)? = null,
        configure: ColumnNode.(HdBuilder) -> Unit
    ): ScrollViewNode {
        val sv = ScrollViewNode(scrollId, stateStore, onNearBottom = onNearBottom)
        sv.padding = padding
        val inner = ColumnNode(gap = gap, crossAlign = HdAlign.STRETCH).apply {
            width = HdSizeSpec.Fill
        }
        inner.configure(HdBuilder(inner, stateStore))
        sv.add(inner)
        container.add(sv)
        return sv
    }

    fun text(
        text: String,
        size: Float = 10f,
        weight: HdTextLayout.Weight = HdTextLayout.Weight.REGULAR,
        color: HdColorSpec = HdColorSpec.Token("text_dark"),
        wrap: Boolean = false,
        align: HdAlign = HdAlign.START,
        fontId: String = HdTextLayout.DEFAULT_FONT_ID,
        socialMarkup: Boolean = false,
        onHashtagClick: ((String) -> Unit)? = null,
        configure: TextNode.() -> Unit = {}
    ): TextNode = add(
        TextNode(
            text = text,
            fontSizeDp = size,
            weight = weight,
            fontId = fontId,
            color = color,
            align = align,
            wrap = wrap,
            socialMarkup = socialMarkup,
            onHashtagClick = onHashtagClick
        ),
        configure
    )

    fun icon(
        icon: HdIconAtlas.Icon,
        size: Float = 14f,
        color: HdColorSpec = HdColorSpec.Token("text_dark"),
        configure: IconNode.() -> Unit = {}
    ): IconNode = add(IconNode(icon, size, color), configure)

    fun image(
        texture: ResourceLocation,
        w: Float,
        h: Float,
        radius: Float = 0f,
        filter: HdCanvas.TextureFilter = HdCanvas.TextureFilter.LINEAR,
        configure: ImageNode.() -> Unit = {}
    ): ImageNode = add(
        ImageNode(texture, w, h, radius = radius, filter = filter),
        configure
    )

    fun appIcon(
        theme: com.nbp.cobblemon_smartphone.theme.SmartphoneTheme,
        actionId: String?,
        defaultTexture: ResourceLocation,
        size: Float = 30f,
        alphaScale: Float = 1f,
        configure: AppIconNode.() -> Unit = {}
    ): AppIconNode = add(
        AppIconNode(theme, actionId, defaultTexture, size, alphaScale),
        configure
    )

    fun avatar(
        uuid: UUID,
        name: String,
        skinUrl: String? = null,
        size: Float = 26f,
        configure: PlayerAvatarNode.() -> Unit = {}
    ): PlayerAvatarNode = add(PlayerAvatarNode(uuid, name, skinUrl, size), configure)

    fun itemIcon(
        stack: ItemStack,
        size: Float = 24f,
        showCount: Boolean = true,
        configure: ItemIconNode.() -> Unit = {}
    ): ItemIconNode = add(ItemIconNode(stack, size, showCount), configure)

    fun model3d(
        modelLocation: ResourceLocation,
        fallbackStack: ItemStack = ItemStack.EMPTY,
        size: Float = 44f,
        tiltXDegrees: Float = 14f,
        spinDegreesPerSec: Float = 65f,
        initialAngleY: Float = 25f,
        configure: Model3DNode.() -> Unit = {}
    ): Model3DNode = add(
        Model3DNode(modelLocation, fallbackStack, size, tiltXDegrees, spinDegreesPerSec, initialAngleY),
        configure
    )

    fun pokemonView(
        speciesId: String,
        aspects: Set<String> = emptySet(),
        size: Float = 56f,
        floatingState: FloatingState = FloatingState(),
        poseType: PoseType = PoseType.PROFILE,
        showShinyBadge: Boolean = true,
        configure: PokemonViewNode.() -> Unit = {}
    ): PokemonViewNode = add(
        PokemonViewNode(speciesId, aspects, size, floatingState = floatingState, poseType = poseType, showShinyBadge = showShinyBadge),
        configure
    )

    fun progressBar(
        progress: Float,
        height: Float = 8f,
        label: String? = null,
        configure: ProgressBarNode.() -> Unit = {}
    ): ProgressBarNode = add(ProgressBarNode(progress, height, label = label), configure)

    fun badge(
        label: String,
        icon: HdIconAtlas.Icon? = null,
        variant: BadgeNode.Variant = BadgeNode.Variant.PRIMARY,
        fontSize: Float = 7.8f,
        radius: Float = 9f,
        configure: BadgeNode.() -> Unit = {}
    ): BadgeNode = add(
        BadgeNode(label, icon, variant, fontSizeDp = fontSize, radius = radius),
        configure
    )

    fun statBar(
        label: String,
        value: Float,
        maxValue: Float = 255f,
        valueText: String? = null,
        fillColor: HdColorSpec = HdColorSpec.Token("primary"),
        configure: StatBarNode.() -> Unit = {}
    ): StatBarNode = add(
        StatBarNode(
            label = label,
            value = value,
            maxValue = maxValue,
            valueText = valueText,
            fillColor = fillColor
        ),
        configure
    )

    fun emptyState(
        title: String,
        description: String? = null,
        icon: HdIconAtlas.Icon? = HdIconAtlas.Icon.INFO,
        iconColor: HdColorSpec = HdColorSpec.Token("primary"),
        actionLabel: String? = null,
        actionIcon: HdIconAtlas.Icon? = null,
        onAction: (() -> Unit)? = null,
        configure: EmptyStateNode.() -> Unit = {}
    ): EmptyStateNode = add(
        EmptyStateNode(
            title = title,
            description = description,
            icon = icon,
            iconColor = iconColor,
            actionLabel = actionLabel,
            actionIcon = actionIcon,
            onAction = onAction
        ),
        configure
    )

    fun button(
        label: String = "",
        icon: HdIconAtlas.Icon? = null,
        variant: ButtonNode.Variant = ButtonNode.Variant.PRIMARY,
        fontSize: Float = 9.5f,
        radius: Float = 9f,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
        configure: ButtonNode.() -> Unit = {}
    ): ButtonNode = add(
        ButtonNode(label, icon, variant, fontSize, radius = radius, enabled = enabled).apply {
            this.onClick = onClick
        },
        configure
    )

    fun pill(
        label: String,
        icon: HdIconAtlas.Icon? = null,
        active: Boolean = false,
        onClick: (() -> Unit)? = null,
        configure: PillNode.() -> Unit = {}
    ): PillNode = add(
        PillNode(label, icon, active).apply { this.onClick = onClick },
        configure
    )

    fun tabs(
        items: List<TabsNode.TabItem>,
        activeId: String,
        style: TabsNode.Style = TabsNode.Style.BOTTOM_NAV,
        onSelect: (String) -> Unit,
        configure: TabsNode.() -> Unit = {}
    ): TabsNode = add(TabsNode(items, activeId, style, onSelect), configure)

    fun checkbox(
        label: String,
        checked: Boolean,
        enabled: Boolean = true,
        fontSize: Float = 8.8f,
        onToggle: (Boolean) -> Unit,
        configure: CheckboxNode.() -> Unit = {}
    ): CheckboxNode = add(CheckboxNode(label, checked, enabled, fontSize, onToggle), configure)

    fun switch(
        label: String = "",
        checked: Boolean = false,
        enabled: Boolean = true,
        fontSize: Float = 9.0f,
        labelOnLeft: Boolean = true,
        onToggle: ((Boolean) -> Unit)? = null,
        configure: SwitchNode.() -> Unit = {}
    ): SwitchNode = add(
        SwitchNode(
            label = label,
            checked = checked,
            enabled = enabled,
            fontSizeDp = fontSize,
            labelOnLeft = labelOnLeft,
            onToggle = onToggle
        ),
        configure
    )

    fun slider(
        value: Float,
        min: Float = 0f,
        max: Float = 1f,
        step: Float = 0f,
        label: String = "",
        showValue: Boolean = true,
        valueFormat: ((Float) -> String)? = null,
        enabled: Boolean = true,
        onChange: ((Float) -> Unit)? = null,
        configure: SliderNode.() -> Unit = {}
    ): SliderNode = add(
        SliderNode(
            value = value,
            min = min,
            max = max,
            step = step,
            label = label,
            showValue = showValue,
            valueFormat = valueFormat,
            enabled = enabled,
            onChange = onChange
        ),
        configure
    )

    fun numberStepper(
        value: Float,
        min: Float = 0f,
        max: Float = 100f,
        step: Float = 1f,
        label: String = "",
        enabled: Boolean = true,
        valueFormat: ((Float) -> String)? = null,
        onChange: ((Float) -> Unit)? = null,
        configure: NumberStepperNode.() -> Unit = {}
    ): NumberStepperNode = add(
        NumberStepperNode(
            value = value,
            min = min,
            max = max,
            step = step,
            label = label,
            enabled = enabled,
            valueFormat = valueFormat,
            onChange = onChange
        ),
        configure
    )

    fun segmentedControl(
        options: List<SegmentedControlNode.Option>,
        selectedId: String,
        enabled: Boolean = true,
        onSelect: ((String) -> Unit)? = null,
        configure: SegmentedControlNode.() -> Unit = {}
    ): SegmentedControlNode = add(
        SegmentedControlNode(
            options = options,
            selectedId = selectedId,
            enabled = enabled,
            onSelect = onSelect
        ),
        configure
    )

    fun textInput(
        inputId: String,
        placeholder: String = "",
        initialText: String = "",
        maxLength: Int = 280,
        multiline: Boolean = false,
        showCounter: Boolean = false,
        onChange: ((String) -> Unit)? = null,
        onSubmit: ((String) -> Unit)? = null,
        configure: TextInputNode.() -> Unit = {}
    ): TextInputNode = add(
        TextInputNode(
            inputId = inputId,
            stateStore = stateStore,
            initialText = initialText,
            placeholder = placeholder,
            maxLength = maxLength,
            multiline = multiline,
            showCounter = showCounter,
            onChange = onChange,
            onSubmit = onSubmit
        ),
        configure
    )

    fun spacer(w: Float = 0f, h: Float = 0f, weight: Float? = null): SpacerNode = add(
        SpacerNode(w, h).apply {
            if (weight != null) {
                when (container) {
                    is RowNode -> this.width = HdSizeSpec.Weight(weight)
                    is ColumnNode -> this.height = HdSizeSpec.Weight(weight)
                    else -> {
                        this.width = HdSizeSpec.Weight(weight)
                        this.height = HdSizeSpec.Weight(weight)
                    }
                }
            }
        }
    )

    fun divider(
        thickness: Float = 0.8f,
        color: HdColorSpec = HdColorSpec.Token("primary_border", 0.35f),
        vertical: Boolean = false
    ): DividerNode = add(DividerNode(thickness, color, vertical))

    /**
     * Standardized Floating Island app header (Option A style): rounded `surface` card
     * with tinted circular icon badge, bold title, optional muted subtitle, trailing actions,
     * and optional embedded bottom content (e.g. segmented control or filter bar).
     */
    fun appHeader(
        title: String,
        subtitle: String? = null,
        icon: HdIconAtlas.Icon? = null,
        iconColor: HdColorSpec = HdColorSpec.Token("primary"),
        horizontalMargin: Float = 7f,
        bottomContent: (ColumnNode.(HdBuilder) -> Unit)? = null,
        trailing: RowNode.(HdBuilder) -> Unit = {}
    ): ColumnNode = column(
        padding = HdPadding.only(left = horizontalMargin, right = horizontalMargin, top = 6f, bottom = 2f),
        width = HdSizeSpec.Fill
    ) { wrap ->
        wrap.column(
            gap = 5f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 5.5f),
            width = HdSizeSpec.Fill,
            background = HdBackground(
                color = HdColorSpec.Token("surface"),
                radius = 12f,
                borderWidth = 0.85f,
                borderColor = HdColorSpec.Token("primary_border"),
                shadowBlur = 6f,
                shadowOffsetY = 2f,
                shadowColor = HdColorSpec.Fixed(0x28000000)
            )
        ) { card ->
            card.row(gap = 6.5f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { hb ->
                icon?.let { ic ->
                    val badgeBg = when (iconColor) {
                        is HdColorSpec.Token -> HdColorSpec.Token(iconColor.name, 0.16f)
                        is HdColorSpec.Fixed -> HdColorSpec.Fixed(HdColors.multiplyAlpha(iconColor.argb, 0.16f))
                    }
                    hb.stack(
                        hAlign = HdAlign.CENTER,
                        vAlign = HdAlign.CENTER,
                        width = HdSizeSpec.Fixed(22f),
                        height = HdSizeSpec.Fixed(22f)
                    ) { icBox ->
                        background = HdBackground(color = badgeBg, radius = 11f)
                        icBox.icon(ic, size = 12f, color = iconColor)
                    }
                }
                hb.column(gap = 0.5f, width = HdSizeSpec.Weight(1f)) { tb ->
                    tb.text(title, size = 10.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
                    if (!subtitle.isNullOrBlank()) {
                        tb.text(subtitle, size = 7.4f, color = HdColorSpec.Token("text_dark_muted"))
                    }
                }
                trailing(hb)
            }
            bottomContent?.invoke(this, card)
        }
    }
}

fun hdColumn(
    stateStore: HdStateStore,
    gap: Float = 0f,
    crossAlign: HdAlign = HdAlign.STRETCH,
    mainAlign: HdAlign = HdAlign.START,
    padding: HdPadding = HdPadding.ZERO,
    width: HdSizeSpec = HdSizeSpec.Fill,
    height: HdSizeSpec = HdSizeSpec.Fill,
    configure: ColumnNode.(HdBuilder) -> Unit
): ColumnNode = ColumnNode(gap, crossAlign, mainAlign).apply {
    this.padding = padding
    this.width = width
    this.height = height
    configure(HdBuilder(this, stateStore))
}
