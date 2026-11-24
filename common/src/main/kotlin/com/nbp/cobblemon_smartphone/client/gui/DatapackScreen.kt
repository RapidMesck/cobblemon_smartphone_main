package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.datapack.DatapackUiCompiler
import com.nbp.cobblemon_smartphone.energy.getEnergy
import com.nbp.cobblemon_smartphone.energy.getMaxEnergy
import com.nbp.cobblemon_smartphone.energy.isInfiniteEnergy
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.ExecuteDatapackScreenActionPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestDatapackScreenDataPacket
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackExpr
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenClientState
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenDefinition
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

/**
 * Runtime host screen for any datapack-defined HD UI screen (`data/<ns>/smartphone_screens/<id>.json`).
 */
class DatapackScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    private val screenId: String,
    scaleOverride: Float? = null,
    styleOverride: com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle? = null
) : HdPhoneScreen(color, smartphoneStack, Component.literal(screenId), scaleOverride, styleOverride) {

    private val localState = LinkedHashMap<String, String>()
    private val floatingState = FloatingState()

    private fun definition(): DatapackScreenDefinition? = DatapackScreenClientState.getScreen(screenId)

    override val viewportMode: ViewportMode
        get() {
            val def = definition() ?: return ViewportMode.Large
            return when ((def.viewport ?: "large").lowercase()) {
                "compact" -> ViewportMode.Compact
                "free" -> ViewportMode.Free(
                    widthDp = (def.widthDp ?: 300f).coerceIn(140f, 520f),
                    heightDp = (def.heightDp ?: 260f).coerceIn(120f, 460f),
                    cornerRadius = def.cornerRadius ?: 16f,
                    backdropColor = def.backdrop?.let { HdColorSpec.parse(it) } ?: HdColorSpec.Token("surface")
                )
                else -> ViewportMode.Large
            }
        }

    override val showBezelBackButton: Boolean
        get() = definition()?.showBezelBackButton != false

    override val showBezelBattery: Boolean
        get() = definition()?.showBezelBattery != false

    override val showBezelHdBadge: Boolean
        get() = definition()?.showBezelHdBadge != false

    override val screenBackdropOverride: HdColorSpec?
        get() = definition()?.backdrop?.let { HdColorSpec.parse(it) }

    override val frameOverride: ResourceLocation?
        get() = definition()?.frameTexture?.let(ResourceLocation::tryParse)

    override val wallpaperOverride: ResourceLocation?
        get() = definition()?.wallpaperTexture?.let(ResourceLocation::tryParse)

    override fun init() {
        super.init()
        val def = definition()
        if (def != null && localState.isEmpty()) {
            for ((k, el) in def.defaultState.orEmpty()) {
                localState[k] = when {
                    el.isJsonPrimitive -> el.asString
                    else -> el.toString()
                }
            }
        }
        if (Minecraft.getInstance().connection != null) {
            RequestDatapackScreenDataPacket(screenId).sendToServer()
        }
    }

    override fun buildModal(theme: SmartphoneTheme): ModalSpec? {
        val def = definition() ?: return null
        val modalDef = def.modal ?: return null
        val ctx = currentEvalContext(def)
        if (!DatapackExpr.evaluateCondition(modalDef.visibleIf, ctx)) return null

        val modalTitle = DatapackExpr.resolveTemplate(modalDef.title.orEmpty(), ctx)
        val closeKey = modalDef.closeStateKey ?: "modal_open"
        return ModalSpec(
            title = modalTitle,
            widthDp = modalDef.widthDp ?: 250f,
            onClose = { localState[closeKey] = "false" }
        ) { body ->
            DatapackUiCompiler.buildNodes(body, modalDef.body.orEmpty(), ctx, callbacks)
        }
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        val def = definition()
        if (def == null) {
            builder.column(
                crossAlign = HdAlign.CENTER,
                mainAlign = HdAlign.CENTER,
                width = HdSizeSpec.Fill,
                height = HdSizeSpec.Weight(1f)
            ) { empty ->
                empty.text(
                    text = "Unknown screen: $screenId",
                    size = 10.5f,
                    color = HdColorSpec.Token("text_secondary"),
                    align = HdAlign.CENTER,
                    wrap = true
                ) {
                    width = HdSizeSpec.Fill
                }
            }
            return
        }

        val ctx = currentEvalContext(def)
        DatapackUiCompiler.buildNodes(builder, def.body.orEmpty(), ctx, callbacks)
    }

    private fun currentEvalContext(def: DatapackScreenDefinition): DatapackExpr.EvalContext {
        val snapshot = DatapackScreenClientState.getData(def.id)
        val mergedPlaceholders = LinkedHashMap<String, String>()
        populateClientFallbackPlaceholders(mergedPlaceholders)
        if (snapshot != null) {
            mergedPlaceholders.putAll(snapshot.placeholders)
        }

        val mergedLists = LinkedHashMap<String, List<Map<String, String>>>()
        for ((listKey, listDef) in def.lists.orEmpty()) {
            if (listDef.type.equals("static", ignoreCase = true)) {
                mergedLists[listKey] = listDef.items.orEmpty()
            }
        }
        if (snapshot != null) {
            mergedLists.putAll(snapshot.lists)
        }

        val inputsMap = LinkedHashMap<String, String>()
        def.body.orEmpty().forEach { collectInputIds(it, inputsMap) }
        def.modal?.body.orEmpty().forEach { collectInputIds(it, inputsMap) }

        return DatapackExpr.EvalContext(
            placeholders = mergedPlaceholders,
            lists = mergedLists,
            state = localState,
            inputs = inputsMap
        )
    }

    private fun collectInputIds(node: com.nbp.cobblemon_smartphone.ui.datapack.DatapackNodeDef, out: MutableMap<String, String>) {
        if (node.type.equals("text_input", ignoreCase = true)) {
            val id = node.inputId ?: node.id ?: "input"
            out[id] = stateStore.getInputText(id)
        }
        node.children.orEmpty().forEach { collectInputIds(it, out) }
    }

    private fun populateClientFallbackPlaceholders(out: MutableMap<String, String>) {
        val mc = Minecraft.getInstance()
        val player = mc.player
        out["player.name"] = player?.gameProfile?.name ?: mc.user?.name ?: "Player"
        out["player.uuid"] = player?.uuid?.toString() ?: "00000000-0000-0000-0000-000000000001"
        out["player.level"] = (player?.experienceLevel ?: 0).toString()

        val dayTime = mc.level?.dayTime ?: 6000L
        val ticksToday = Math.floorMod(dayTime, 24000L)
        val totalMinutes = ((ticksToday * 1440 / 24000L) + 360) % 1440
        out["world.time"] = "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
        out["world.dimension"] = mc.level?.dimension()?.location()?.toString() ?: "minecraft:overworld"

        val phone = SmartphoneBatteryRenderer.resolveSmartphone(smartphoneStack)
        if (phone != null) {
            val cur = phone.getEnergy()
            val max = phone.getMaxEnergy()
            val isInf = phone.isInfiniteEnergy()
            out["phone.battery"] = if (isInf) "∞" else cur.toString()
            out["phone.battery_max"] = max.toString()
            out["phone.battery_pct"] = when {
                isInf -> "100"
                max <= 0 -> "0"
                else -> ((cur.toFloat() / max) * 100f).roundToInt().coerceIn(0, 100).toString()
            }
        }
    }

    private val callbacks = object : DatapackUiCompiler.HostCallbacks {
        override fun onAction(actionKey: String, itemContext: Map<String, String>) {
            playClickSound()
            val def = definition() ?: return

            // Inline shortcut syntax: "state:tab=party", "modal:open", "modal:close", "screen:gps"
            if (handleInlineShortcut(actionKey, def)) return

            val actionDef = def.actions.orEmpty()[actionKey] ?: return
            val ctx = currentEvalContext(def).copy(item = itemContext)

            var hasServerEffects = false
            for (effect in actionDef.effects.orEmpty()) {
                when (effect.type?.lowercase()) {
                    "set_state" -> {
                        val k = effect.key ?: continue
                        val v = effect.value?.let { DatapackExpr.resolveTemplate(it, ctx) } ?: ""
                        localState[k] = v
                    }
                    "toggle_state" -> {
                        val k = effect.key ?: continue
                        val cur = localState[k].equals("true", ignoreCase = true)
                        localState[k] = (!cur).toString()
                    }
                    "increment_state" -> {
                        val k = effect.key ?: continue
                        val cur = localState[k]?.toIntOrNull() ?: 0
                        localState[k] = (cur + (effect.delta ?: 1)).toString()
                    }
                    "open_modal" -> {
                        val k = def.modal?.closeStateKey ?: "modal_open"
                        localState[k] = "true"
                    }
                    "close_modal" -> {
                        val k = def.modal?.closeStateKey ?: "modal_open"
                        localState[k] = "false"
                    }
                    "close_screen" -> {
                        Minecraft.getInstance().setScreen(null)
                        return
                    }
                    "open_screen" -> {
                        val target = effect.screen?.let { DatapackExpr.resolveTemplate(it, ctx) } ?: continue
                        openTargetScreen(color, smartphoneStack, target)
                        return
                    }
                    "toast" -> {
                        val msg = effect.text?.let { DatapackExpr.resolveTemplate(it, ctx) }?.takeIf { it.isNotBlank() } ?: continue
                        val toastKind = when (effect.kind?.trim()?.lowercase()) {
                            "success" -> com.nbp.cobblemon_smartphone.client.ui.hd.HdStateStore.ToastKind.SUCCESS
                            "warning", "warn" -> com.nbp.cobblemon_smartphone.client.ui.hd.HdStateStore.ToastKind.WARNING
                            "error", "danger" -> com.nbp.cobblemon_smartphone.client.ui.hd.HdStateStore.ToastKind.ERROR
                            else -> com.nbp.cobblemon_smartphone.client.ui.hd.HdStateStore.ToastKind.INFO
                        }
                        val toastIcon = effect.icon?.let { raw ->
                            com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas.Icon.entries
                                .firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
                        }
                        showToast(msg, toastKind, toastIcon, effect.durationMs ?: 2600L)
                    }
                    "message", "sound", "function", "give_item", "consume_item", "command", "refresh_data" -> {
                        hasServerEffects = true
                    }
                }
            }

            if (hasServerEffects) {
                val inputValues = LinkedHashMap<String, String>()
                def.body.orEmpty().forEach { collectInputIds(it, inputValues) }
                def.modal?.body.orEmpty().forEach { collectInputIds(it, inputValues) }
                ExecuteDatapackScreenActionPacket(def.id, actionKey, inputValues, itemContext).sendToServer()
            }
        }

        override fun getState(key: String): String = localState[key].orEmpty()

        override fun setState(key: String, value: String) {
            localState[key] = value
        }

        override fun toggleState(key: String) {
            val cur = localState[key].equals("true", ignoreCase = true)
            localState[key] = (!cur).toString()
        }

        override fun onInputChanged(inputId: String, value: String) {
            // Live state mirror if templates read `{inputs.x}`
        }

        override fun getFloatingState(): FloatingState = floatingState
    }

    private fun handleInlineShortcut(raw: String, def: DatapackScreenDefinition): Boolean {
        val s = raw.trim()
        return when {
            s.startsWith("state:") -> {
                val assign = s.removePrefix("state:")
                val eq = assign.indexOf('=')
                if (eq > 0) {
                    localState[assign.substring(0, eq).trim()] = assign.substring(eq + 1).trim()
                    true
                } else {
                    false
                }
            }
            s == "modal:open" -> {
                localState[def.modal?.closeStateKey ?: "modal_open"] = "true"
                true
            }
            s == "modal:close" -> {
                localState[def.modal?.closeStateKey ?: "modal_open"] = "false"
                true
            }
            s == "close" -> {
                Minecraft.getInstance().setScreen(null)
                true
            }
            s.startsWith("screen:") -> {
                openTargetScreen(color, smartphoneStack, s.removePrefix("screen:").trim())
                true
            }
            s.startsWith("toast:") -> {
                val msg = s.removePrefix("toast:").trim()
                if (msg.isNotEmpty()) showToast(msg)
                true
            }
            else -> false
        }
    }

    companion object {
        fun openTargetScreen(color: SmartphoneColor, smartphoneStack: ItemStack?, targetId: String) {
            val mc = Minecraft.getInstance()
            val normalized = targetId.trim().lowercase()
            val next = when (normalized) {
                "smartphone", "home" -> SmartphoneScreen(color, smartphoneStack)
                "settings" -> SmartphoneSettingsScreen(color, smartphoneStack)
                "gps" -> GpsScreen(color, smartphoneStack)
                "structure_compass", "compass" -> StructureCompassScreen(color, smartphoneStack)
                "pokeinfo" -> PokeInfoScreen(color, smartphoneStack)
                "rotom" -> RotomAppScreen(color, smartphoneStack)
                "social" -> SocialScreen(color, smartphoneStack)
                else -> DatapackScreen(color, smartphoneStack, targetId)
            }
            mc.setScreen(next)
        }
    }
}
