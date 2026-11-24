package com.nbp.cobblemon_smartphone.ui.datapack

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * JSON definition for a custom smartphone UI screen loaded from `data/<ns>/smartphone_screens/<id>.json`.
 * Optional fields with non-zero/non-false defaults are nullable so Gson's Unsafe allocator never flips
 * omitted fields into unexpected `false`/`0`/`null` runtime crashes.
 */
data class DatapackScreenDefinition(
    val id: String = "",
    val title: String? = null,
    val viewport: String? = "large",
    @SerializedName("width_dp", alternate = ["widthDp"])
    val widthDp: Float? = null,
    @SerializedName("height_dp", alternate = ["heightDp"])
    val heightDp: Float? = null,
    @SerializedName("corner_radius", alternate = ["cornerRadius"])
    val cornerRadius: Float? = 16f,
    val backdrop: String? = null,
    @SerializedName("frame_texture", alternate = ["frameTexture"])
    val frameTexture: String? = null,
    @SerializedName("wallpaper_texture", alternate = ["wallpaperTexture"])
    val wallpaperTexture: String? = null,
    @SerializedName("show_bezel_back", alternate = ["showBezelBackButton", "show_back"])
    val showBezelBackButton: Boolean? = true,
    @SerializedName("show_bezel_battery", alternate = ["showBezelBattery", "show_battery"])
    val showBezelBattery: Boolean? = true,
    @SerializedName("show_bezel_hd_badge", alternate = ["showBezelHdBadge", "show_hd_badge"])
    val showBezelHdBadge: Boolean? = true,
    @SerializedName("require_mod", alternate = ["requireMod"])
    val requireMod: String? = null,
    @SerializedName("require_upgrade", alternate = ["requireUpgrade"])
    val requireUpgrade: String? = null,
    @SerializedName("default_state", alternate = ["defaultState", "state"])
    val defaultState: Map<String, JsonElement>? = emptyMap(),
    val placeholders: Map<String, String>? = emptyMap(),
    val lists: Map<String, DatapackListDef>? = emptyMap(),
    val actions: Map<String, DatapackScreenActionDef>? = emptyMap(),
    val body: List<DatapackNodeDef>? = emptyList(),
    val modal: DatapackModalDef? = null
) {
    companion object {
        val GSON = Gson()

        fun fromJson(json: String): DatapackScreenDefinition? = try {
            GSON.fromJson(json, DatapackScreenDefinition::class.java)
        } catch (_: Exception) {
            null
        }
    }
}

data class DatapackListDef(
    val type: String? = "static",
    val limit: Int? = 36,
    val items: List<Map<String, String>>? = emptyList()
)

data class DatapackModalDef(
    val title: String? = "",
    @SerializedName("width_dp", alternate = ["widthDp"])
    val widthDp: Float? = 250f,
    @SerializedName("visible_if", alternate = ["visibleIf"])
    val visibleIf: String? = "state.modal_open == true",
    @SerializedName("close_state_key", alternate = ["closeStateKey"])
    val closeStateKey: String? = "modal_open",
    val body: List<DatapackNodeDef>? = emptyList()
)

data class DatapackScreenActionDef(
    @SerializedName("require_upgrade", alternate = ["requireUpgrade"])
    val requireUpgrade: String? = null,
    @SerializedName("cooldown_seconds", alternate = ["cooldownSeconds"])
    val cooldownSeconds: Int = 0,
    @SerializedName("fe_cost", alternate = ["feCost", "energy_cost"])
    val feCost: Int = 0,
    val effects: List<DatapackEffectDef>? = emptyList()
)

/**
 * Typed effect step executed either client-side (`set_state`, `toggle_state`, `open_modal`, etc.)
 * or authoritatively server-side (`message`, `sound`, `function`, `give_item`, `consume_item`, `command`).
 */
data class DatapackEffectDef(
    val type: String? = "",
    val key: String? = null,
    val value: String? = null,
    val delta: Int? = null,
    val screen: String? = null,
    val text: String? = null,
    val actionbar: Boolean = false,
    val sound: String? = null,
    val volume: Float? = 0.7f,
    val pitch: Float? = 1.0f,
    val function: String? = null,
    val item: String? = null,
    val count: Int? = 1,
    val command: String? = null,
    @SerializedName("as_server", alternate = ["asServer"])
    val asServer: Boolean = false,
    val kind: String? = null,
    val icon: String? = null,
    @SerializedName("duration_ms", alternate = ["durationMs"])
    val durationMs: Long? = null
)

data class DatapackTabItemDef(
    val id: String? = "",
    val label: String? = "",
    val icon: String? = null
)

data class DatapackNodeDef(
    val type: String? = "column",
    val id: String? = null,
    val width: String? = null,
    val height: String? = null,
    @SerializedName("min_width", alternate = ["minWidth"])
    val minWidth: Float? = null,
    @SerializedName("min_height", alternate = ["minHeight"])
    val minHeight: Float? = null,
    @SerializedName("max_width", alternate = ["maxWidth"])
    val maxWidth: Float? = null,
    @SerializedName("max_height", alternate = ["maxHeight"])
    val maxHeight: Float? = null,
    val padding: JsonElement? = null,
    val gap: Float = 0f,
    val columns: Int? = 2,
    @SerializedName("col_gap", alternate = ["colGap"])
    val colGap: Float? = 8f,
    @SerializedName("row_gap", alternate = ["rowGap"])
    val rowGap: Float? = 8f,
    @SerializedName("cross_align", alternate = ["crossAlign"])
    val crossAlign: String? = null,
    @SerializedName("main_align", alternate = ["mainAlign"])
    val mainAlign: String? = null,
    @SerializedName("h_align", alternate = ["hAlign"])
    val hAlign: String? = null,
    @SerializedName("v_align", alternate = ["vAlign"])
    val vAlign: String? = null,

    // Background / Styling
    val background: String? = null,
    @SerializedName("hover_background", alternate = ["hoverBackground"])
    val hoverBackground: String? = null,
    val radius: Float? = null,
    @SerializedName("border_width", alternate = ["borderWidth"])
    val borderWidth: Float? = null,
    @SerializedName("border_color", alternate = ["borderColor"])
    val borderColor: String? = null,
    @SerializedName("shadow_blur", alternate = ["shadowBlur"])
    val shadowBlur: Float? = null,
    @SerializedName("hover_highlight", alternate = ["hoverHighlight"])
    val hoverHighlight: Boolean = false,
    @SerializedName("clip_children", alternate = ["clipChildren"])
    val clipChildren: Boolean = false,

    // Logic & Iteration
    @SerializedName("visible_if", alternate = ["visibleIf"])
    val visibleIf: String? = null,
    @SerializedName("for_each", alternate = ["forEach", "items_source"])
    val forEach: String? = null,
    @SerializedName("for_each_as", alternate = ["forEachAs", "as"])
    val forEachAs: String? = "item",
    @SerializedName("empty_text", alternate = ["emptyText"])
    val emptyText: String? = null,

    // Interactions & Tooltip
    val tooltip: String? = null,
    @SerializedName("on_click", alternate = ["onClick", "action"])
    val onClick: String? = null,
    @SerializedName("on_change", alternate = ["onChange"])
    val onChange: String? = null,
    @SerializedName("on_submit", alternate = ["onSubmit"])
    val onSubmit: String? = null,

    // Text
    val text: String? = null,
    val size: Float? = null,
    val weight: String? = null,
    val color: String? = null,
    val wrap: Boolean = false,
    val align: String? = null,
    @SerializedName("max_lines", alternate = ["maxLines"])
    val maxLines: Int? = null,

    // Icon / Image / Avatar / Item / Pokemon
    val icon: String? = null,
    val texture: String? = null,
    val u0: Float = 0f,
    val v0: Float = 0f,
    val u1: Float? = 1f,
    val v1: Float? = 1f,
    @SerializedName("image_width", alternate = ["imageWidth"])
    val imageWidth: Float? = null,
    @SerializedName("image_height", alternate = ["imageHeight"])
    val imageHeight: Float? = null,
    val filter: String? = null,
    val uuid: String? = null,
    @SerializedName("player_name", alternate = ["playerName"])
    val playerName: String? = null,
    @SerializedName("skin_url", alternate = ["skinUrl"])
    val skinUrl: String? = null,
    val item: String? = null,
    val count: Int? = 1,
    @SerializedName("show_count", alternate = ["showCount"])
    val showCount: Boolean? = true,
    val species: String? = null,
    val aspects: String? = null,
    @SerializedName("show_shiny_badge", alternate = ["showShinyBadge"])
    val showShinyBadge: Boolean? = true,

    // ProgressBar / StatBar / Badge / EmptyState
    val progress: String? = null,
    @SerializedName("track_color", alternate = ["trackColor"])
    val trackColor: String? = null,
    @SerializedName("fill_color", alternate = ["fillColor"])
    val fillColor: String? = null,
    @SerializedName("max_value", alternate = ["maxValue"])
    val maxValue: Float? = null,
    @SerializedName("value_text", alternate = ["valueText"])
    val valueText: String? = null,
    val description: String? = null,
    @SerializedName("action_label", alternate = ["actionLabel"])
    val actionLabel: String? = null,
    @SerializedName("action_icon", alternate = ["actionIcon"])
    val actionIcon: String? = null,

    // Button / Pill / Checkbox / Switch / Slider / Stepper / SegmentedControl
    val label: String? = null,
    val variant: String? = null,
    val enabled: String? = null,
    val active: String? = null,
    val checked: String? = null,
    @SerializedName("state_key", alternate = ["stateKey"])
    val stateKey: String? = null,
    val value: String? = null,
    val min: Float? = null,
    val max: Float? = null,
    val step: Float? = null,
    @SerializedName("show_value", alternate = ["showValue"])
    val showValue: Boolean? = true,
    @SerializedName("selected_id", alternate = ["selectedId"])
    val selectedId: String? = null,
    val options: List<DatapackTabItemDef>? = emptyList(),

    // Tabs
    val tabs: List<DatapackTabItemDef>? = emptyList(),
    @SerializedName("active_id", alternate = ["activeId"])
    val activeId: String? = null,
    val style: String? = null,

    // TextInput
    @SerializedName("input_id", alternate = ["inputId"])
    val inputId: String? = null,
    val placeholder: String? = null,
    @SerializedName("initial_text", alternate = ["initialText"])
    val initialText: String? = null,
    @SerializedName("max_length", alternate = ["maxLength"])
    val maxLength: Int? = 280,
    val multiline: Boolean = false,
    @SerializedName("show_counter", alternate = ["showCounter"])
    val showCounter: Boolean = false,

    // ScrollView / Spacer / Divider / FlowRow / Accordion / ListTile
    @SerializedName("scroll_id", alternate = ["scrollId"])
    val scrollId: String? = null,
    @SerializedName("anchor_bottom", alternate = ["anchorBottom"])
    val anchorBottom: Boolean = false,
    val w: Float = 0f,
    val h: Float = 0f,
    val thickness: Float? = 0.8f,
    val vertical: Boolean = false,
    @SerializedName("h_gap", alternate = ["hGap"])
    val hGap: Float? = null,
    @SerializedName("v_gap", alternate = ["vGap"])
    val vGap: Float? = null,
    @SerializedName("line_align", alternate = ["lineAlign"])
    val lineAlign: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val expanded: String? = null,
    @SerializedName("trailing_text", alternate = ["trailingText"])
    val trailingText: String? = null,
    @SerializedName("show_chevron", alternate = ["showChevron"])
    val showChevron: Boolean = false,
    @SerializedName("show_background", alternate = ["showBackground"])
    val showBackground: Boolean? = true,

    // Children
    val children: List<DatapackNodeDef>? = emptyList()
)
