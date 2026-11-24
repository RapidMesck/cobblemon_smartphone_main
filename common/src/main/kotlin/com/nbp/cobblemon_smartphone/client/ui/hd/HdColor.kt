package com.nbp.cobblemon_smartphone.client.ui.hd

import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeColors
import kotlin.math.roundToInt

/**
 * Color specification that resolves either to a fixed ARGB integer or dynamically to a
 * semantic token from the active [SmartphoneThemeColors].
 */
sealed interface HdColorSpec {
    fun resolve(colors: SmartphoneThemeColors): Int

    data class Fixed(val argb: Int) : HdColorSpec {
        override fun resolve(colors: SmartphoneThemeColors): Int = argb
    }

    data class Token(val name: String, val alpha: Float = 1f) : HdColorSpec {
        override fun resolve(colors: SmartphoneThemeColors): Int {
            val base = when (name.lowercase().removePrefix("@theme.").removePrefix("theme.")) {
                "primary" -> colors.primary
                "primary_hover" -> colors.primaryHover
                "primary_border", "border" -> colors.primaryBorder
                "surface" -> colors.surface
                "surface_alt" -> colors.surfaceAlt
                "surface_hover" -> colors.surfaceHover
                "shadow" -> colors.shadow
                "header", "header_navy" -> colors.headerNavy
                "header_light", "header_navy_light" -> colors.headerNavyLight
                "text_primary" -> colors.textPrimary
                "text_secondary" -> colors.textSecondary
                "text_muted" -> colors.textMuted
                "text_dark" -> colors.textDark
                "text_dark_muted" -> colors.textDarkMuted
                "gold" -> colors.gold
                "content_gold" -> colors.contentGold
                "danger" -> colors.danger
                "success" -> colors.success
                "time" -> colors.timeColor
                "transparent" -> 0x00000000
                else -> colors.textDark
            }
            return if (alpha >= 0.999f) base else HdColors.multiplyAlpha(base, alpha)
        }
    }

    companion object {
        val TRANSPARENT: HdColorSpec = Fixed(0x00000000)

        fun fixed(argb: Int): HdColorSpec = Fixed(argb)
        fun token(name: String, alpha: Float = 1f): HdColorSpec = Token(name, alpha)

        /**
         * Parses `"#RRGGBB"`, `"#AARRGGBB"`, `"@theme.primary"`, `"primary"`, or `"primary@0.5"`.
         */
        fun parse(raw: String?): HdColorSpec? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            if (trimmed.startsWith("#")) {
                return SmartphoneThemeColors.parseHex(trimmed)?.let { Fixed(it) }
            }
            val atIdx = trimmed.lastIndexOf('@')
            if (atIdx > 0 && !trimmed.startsWith("@theme.")) {
                val tokenPart = trimmed.substring(0, atIdx)
                val alphaPart = trimmed.substring(atIdx + 1).toFloatOrNull()
                if (alphaPart != null) {
                    return Token(tokenPart, alphaPart.coerceIn(0f, 1f))
                }
            } else if (trimmed.startsWith("@theme.") && trimmed.indexOf('@', 1) > 0) {
                val secondAt = trimmed.indexOf('@', 1)
                val tokenPart = trimmed.substring(0, secondAt)
                val alphaPart = trimmed.substring(secondAt + 1).toFloatOrNull()
                if (alphaPart != null) {
                    return Token(tokenPart, alphaPart.coerceIn(0f, 1f))
                }
            }
            return Token(trimmed)
        }
    }
}

data class HdBackground(
    val color: HdColorSpec,
    val colorBottom: HdColorSpec? = null,
    val radius: Float = 0f,
    val borderWidth: Float = 0f,
    val borderColor: HdColorSpec = HdColorSpec.TRANSPARENT,
    val shadowBlur: Float = 0f,
    val shadowOffsetY: Float = 1.5f,
    val shadowColor: HdColorSpec = HdColorSpec.Fixed(0x28000000)
)

object HdColors {
    fun mix(c1: Int, c2: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val a = (((c1 ushr 24) and 0xFF) * (1f - k) + ((c2 ushr 24) and 0xFF) * k).roundToInt()
        val r = (((c1 ushr 16) and 0xFF) * (1f - k) + ((c2 ushr 16) and 0xFF) * k).roundToInt()
        val g = (((c1 ushr 8) and 0xFF) * (1f - k) + ((c2 ushr 8) and 0xFF) * k).roundToInt()
        val b = ((c1 and 0xFF) * (1f - k) + (c2 and 0xFF) * k).roundToInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun multiplyAlpha(argb: Int, factor: Float): Int {
        val origA = (argb ushr 24) and 0xFF
        val newA = (origA * factor.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return (argb and 0x00FFFFFF) or (newA shl 24)
    }

    fun withAlpha(argb: Int, alphaByte: Int): Int =
        (argb and 0x00FFFFFF) or ((alphaByte.coerceIn(0, 255)) shl 24)
}
