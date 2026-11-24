package com.nbp.cobblemon_smartphone.client.ui.hd

import com.nbp.cobblemon_smartphone.util.PreferencesSaver
import net.minecraft.client.Minecraft
import kotlin.math.roundToInt

/**
 * Visual rendering style for the unified smartphone UI engine.
 * [PIXEL] (default) renders with Minecraft's crisp pixel font, sharp corners, hard shadows, and
 * nearest-neighbor textures/icons. [HD] renders with anti-aliased vector typography, rounded
 * surfaces, soft drop shadows, and mipmapped vector icons.
 */
enum class HdUiStyle {
    PIXEL,
    HD
}

/**
 * Client-side preference state for the unified smartphone UI system.
 * Persists across sessions via [PreferencesSaver] when a player is loaded.
 */
object HdUiPreferences {
    private const val PREF_SOCIAL_HD = "cobblemonsmartphone_social_hd"
    private const val PREF_HD_SCALE = "cobblemonsmartphone_hd_scale"
    private const val PREF_UI_STYLE = "cobblemonsmartphone_ui_style"

    private var socialHdOverride: Boolean? = null
    private var scaleOverride: Float? = null
    private var styleOverride: HdUiStyle? = null

    fun getUiStyle(): HdUiStyle {
        styleOverride?.let { return it }
        val player = runCatching { Minecraft.getInstance()?.player }.getOrNull() as? PreferencesSaver
            ?: return HdUiStyle.PIXEL
        val tag = player.`cobblemonsmartphone$getSavedPreferences`()
        val raw = if (tag.contains(PREF_UI_STYLE)) tag.getString(PREF_UI_STYLE) else ""
        return if (raw.equals("hd", ignoreCase = true)) HdUiStyle.HD else HdUiStyle.PIXEL
    }

    fun setUiStyle(style: HdUiStyle) {
        styleOverride = style
        val player = runCatching { Minecraft.getInstance()?.player }.getOrNull() as? PreferencesSaver ?: return
        player.`cobblemonsmartphone$getSavedPreferences`().putString(PREF_UI_STYLE, style.name.lowercase())
    }

    fun toggleUiStyle(): HdUiStyle {
        val next = if (getUiStyle() == HdUiStyle.PIXEL) HdUiStyle.HD else HdUiStyle.PIXEL
        setUiStyle(next)
        return next
    }

    fun isHdStyle(): Boolean = getUiStyle() == HdUiStyle.HD

    /** Resets in-memory overrides (useful in unit tests). */
    internal fun resetOverridesForTest() {
        socialHdOverride = null
        scaleOverride = null
        styleOverride = null
    }

    fun isSocialHdEnabled(): Boolean {
        socialHdOverride?.let { return it }
        val player = runCatching { Minecraft.getInstance()?.player }.getOrNull() as? PreferencesSaver ?: return false
        val tag = player.`cobblemonsmartphone$getSavedPreferences`()
        return if (tag.contains(PREF_SOCIAL_HD)) tag.getBoolean(PREF_SOCIAL_HD) else false
    }

    fun setSocialHdEnabled(enabled: Boolean) {
        socialHdOverride = enabled
        val player = runCatching { Minecraft.getInstance()?.player }.getOrNull() as? PreferencesSaver ?: return
        player.`cobblemonsmartphone$getSavedPreferences`().putBoolean(PREF_SOCIAL_HD, enabled)
    }

    fun getUserScale(): Float {
        scaleOverride?.let { return it }
        val player = runCatching { Minecraft.getInstance()?.player }.getOrNull() as? PreferencesSaver ?: return 1.0f
        val tag = player.`cobblemonsmartphone$getSavedPreferences`()
        val raw = if (tag.contains(PREF_HD_SCALE)) tag.getFloat(PREF_HD_SCALE) else 1.0f
        return raw.coerceIn(HdViewport.MIN_USER_SCALE, HdViewport.MAX_USER_SCALE)
    }

    fun setUserScale(scale: Float) {
        val clamped = ((scale.coerceIn(HdViewport.MIN_USER_SCALE, HdViewport.MAX_USER_SCALE) * 20f).roundToInt() / 20f)
        scaleOverride = clamped
        val player = runCatching { Minecraft.getInstance()?.player }.getOrNull() as? PreferencesSaver ?: return
        player.`cobblemonsmartphone$getSavedPreferences`().putFloat(PREF_HD_SCALE, clamped)
    }

    fun adjustUserScale(delta: Float): Float {
        val next = getUserScale() + delta
        setUserScale(next)
        return getUserScale()
    }
}
