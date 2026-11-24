package com.nbp.cobblemon_smartphone.client.ui.hd

import net.minecraft.server.packs.resources.ResourceManager
import java.awt.Font
import java.awt.FontFormatException
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.LineMetrics
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Pure Java2D-backed typography measurement, font fallback, word wrapping, and styled span
 * parser (`@mentions`, `#hashtags`). Supports custom TTF/OTF fonts loaded from resource packs
 * (`assets/<namespace>/fonts/<name>.ttf`) and has zero OpenGL dependencies so layout math
 * can be unit-tested headlessly.
 */
object HdTextLayout {

    const val DEFAULT_FONT_ID = "default"
    const val PIXEL_FONT_BASE_PX = 9.2f

    /**
     * Active typography backend synchronized by [HdPhoneScreen] before layout/render.
     * Defaults to [HdUiStyle.HD] so headless unit tests exercise the Java2D font layout path.
     */
    @Volatile
    var activeStyle: HdUiStyle = HdUiStyle.HD

    enum class Weight { REGULAR, MEDIUM, BOLD }

    data class StyledSpan(
        val text: String,
        val kind: Kind = Kind.PLAIN
    ) {
        enum class Kind { PLAIN, MENTION, HASHTAG }
    }

    data class WrappedLine(
        val spans: List<StyledSpan>,
        val widthDp: Float
    ) {
        val plainText: String get() = spans.joinToString("") { it.text }
    }

    private data class FontKey(val fontId: String, val weight: Weight, val pixelSize: Int)

    private val PREFERRED_FAMILIES = listOf(
        "Inter",
        "SF Pro Text",
        "SF Pro Display",
        "Segoe UI",
        "Roboto",
        "Noto Sans",
        "Ubuntu",
        "Liberation Sans",
        "DejaVu Sans"
    )

    val fontRenderContext: FontRenderContext by lazy {
        val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        val frc = g.fontRenderContext
        g.dispose()
        frc ?: FontRenderContext(AffineTransform(), true, true)
    }

    private val availableFamilies: Set<String> by lazy {
        runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        }.getOrElse { emptySet() }
    }

    private val resolvedFamily: String by lazy {
        PREFERRED_FAMILIES.firstOrNull { candidate ->
            availableFamilies.any { it.equals(candidate, ignoreCase = true) }
        } ?: Font.SANS_SERIF
    }

    private val customFonts = ConcurrentHashMap<Pair<String, Weight>, Font>()
    private val derivedFontCache = ConcurrentHashMap<FontKey, Font>()
    private val fallbackFontCache = ConcurrentHashMap<Pair<Weight, Int>, List<Font>>()
    private val lineMetricsCache = ConcurrentHashMap<FontKey, LineMetrics>()
    private var loadedFromResources = false

    private val TOKEN_REGEX = Regex("""(?<=^|\s)([@#])([a-zA-Z0-9_]{1,24})\b""")

    /** Registers a TTF/OTF font under [fontId] and [weight] (defaults to [DEFAULT_FONT_ID]). */
    fun registerCustomFont(
        weight: Weight,
        stream: InputStream,
        fontId: String = DEFAULT_FONT_ID
    ): Boolean {
        return try {
            val loaded = Font.createFont(Font.TRUETYPE_FONT, stream)
            customFonts[fontId to weight] = loaded
            clearDerivedCaches()
            true
        } catch (_: FontFormatException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Discovers `.ttf` and `.otf` files in resource packs under `assets/<namespace>/fonts/`.
     * `cobblemon_smartphone:fonts/default.ttf` and `default_bold.ttf` override the default UI font.
     */
    fun reloadFromResources(resourceManager: ResourceManager) {
        customFonts.clear()
        clearDerivedCaches()
        val resources = resourceManager.listResources("fonts") { loc ->
            val p = loc.path.lowercase()
            p.endsWith(".ttf") || p.endsWith(".otf")
        }
        resources.forEach { (loc, res) ->
            val fileName = loc.path.substringAfterLast('/')
            val stem = fileName.substringBeforeLast('.')
            val isBold = stem.endsWith("_bold") || stem.endsWith("-bold")
            val baseName = stem.removeSuffix("_bold").removeSuffix("-bold")
            val fontId = if (loc.namespace == "cobblemon_smartphone" && baseName == "default") {
                DEFAULT_FONT_ID
            } else {
                "${loc.namespace}:$baseName"
            }
            val weight = if (isBold) Weight.BOLD else Weight.REGULAR
            runCatching {
                res.open().use { stream -> registerCustomFont(weight, stream, fontId) }
            }
        }
        loadedFromResources = true
    }

    fun ensureResourcesLoaded(resourceManager: ResourceManager) {
        if (!loadedFromResources) {
            reloadFromResources(resourceManager)
        }
    }

    private fun clearDerivedCaches() {
        derivedFontCache.clear()
        fallbackFontCache.clear()
        lineMetricsCache.clear()
    }

    fun resolvePixelSize(fontSizeDp: Float, viewportScale: Float): Int =
        (fontSizeDp * viewportScale).roundToInt().coerceIn(8, 128)

    /**
     * Computes the `dp` scale per Minecraft bitmap font texel so that every font texel maps
     * to an exact integer number of physical monitor pixels (`k >= 1`), preventing fractional
     * bitmap scaling artifacts (dropped 1px strokes) in [HdUiStyle.PIXEL] mode.
     */
    fun pixelFontDpScale(fontSizeDp: Float, viewportScale: Float): Float {
        val safeScale = viewportScale.coerceAtLeast(0.01f)
        val rawPhysTexel = (fontSizeDp / PIXEL_FONT_BASE_PX) * safeScale
        val snappedPhysTexel = kotlin.math.max(1, rawPhysTexel.roundToInt())
        return snappedPhysTexel.toFloat() / safeScale
    }

    fun primaryFont(weight: Weight, pixelSize: Int, fontId: String = DEFAULT_FONT_ID): Font {
        val clampedPx = pixelSize.coerceIn(8, 128)
        val key = FontKey(fontId, weight, clampedPx)
        return derivedFontCache.getOrPut(key) {
            val exactCustom = customFonts[fontId to weight]
            val baseCustom = exactCustom
                ?: (if (weight == Weight.BOLD) customFonts[fontId to Weight.REGULAR] else null)
                ?: (if (fontId != DEFAULT_FONT_ID) {
                    customFonts[DEFAULT_FONT_ID to weight] ?: customFonts[DEFAULT_FONT_ID to Weight.REGULAR]
                } else null)

            if (baseCustom != null) {
                val style = if (weight == Weight.BOLD && exactCustom == null) Font.BOLD else Font.PLAIN
                baseCustom.deriveFont(style, clampedPx.toFloat())
            } else {
                val awtStyle = if (weight == Weight.BOLD) Font.BOLD else Font.PLAIN
                Font(resolvedFamily, awtStyle, clampedPx)
            }
        }
    }

    fun fontForCodePoint(
        codePoint: Int,
        weight: Weight,
        pixelSize: Int,
        fontId: String = DEFAULT_FONT_ID
    ): Font {
        val primary = primaryFont(weight, pixelSize, fontId)
        if (primary.canDisplay(codePoint)) return primary
        val clampedPx = pixelSize.coerceIn(8, 128)
        val awtStyle = if (weight == Weight.BOLD) Font.BOLD else Font.PLAIN
        val fallbacks = fallbackFontCache.getOrPut(weight to clampedPx) {
            listOf(
                Font("DejaVu Sans", awtStyle, clampedPx),
                Font(Font.SANS_SERIF, awtStyle, clampedPx),
                Font(Font.DIALOG, awtStyle, clampedPx)
            )
        }
        return fallbacks.firstOrNull { it.canDisplay(codePoint) } ?: primary
    }

    fun metrics(weight: Weight, pixelSize: Int, fontId: String = DEFAULT_FONT_ID): LineMetrics {
        val clampedPx = pixelSize.coerceIn(8, 128)
        val key = FontKey(fontId, weight, clampedPx)
        return lineMetricsCache.getOrPut(key) {
            primaryFont(weight, clampedPx, fontId).getLineMetrics("AgjpqyÁÇ", fontRenderContext)
        }
    }

    fun lineHeightDp(
        fontSizeDp: Float,
        weight: Weight,
        viewportScale: Float,
        fontId: String = DEFAULT_FONT_ID
    ): Float {
        if (activeStyle == HdUiStyle.PIXEL) {
            val dpScale = pixelFontDpScale(fontSizeDp, viewportScale)
            return maxOf(fontSizeDp * 1.12f, 9f * dpScale + 1.5f)
        }
        val px = resolvePixelSize(fontSizeDp, viewportScale)
        val m = metrics(weight, px, fontId)
        return ceil((m.ascent + m.descent + m.leading).toDouble()).toFloat() / viewportScale.coerceAtLeast(0.01f)
    }

    fun glyphAdvancePx(
        codePoint: Int,
        weight: Weight,
        pixelSize: Int,
        fontId: String = DEFAULT_FONT_ID
    ): Float {
        val font = fontForCodePoint(codePoint, weight, pixelSize, fontId)
        val str = String(Character.toChars(codePoint))
        val gv = font.createGlyphVector(fontRenderContext, str)
        return gv.getGlyphMetrics(0).advanceX
    }

    fun measureWidthPx(
        text: String,
        weight: Weight,
        pixelSize: Int,
        fontId: String = DEFAULT_FONT_ID
    ): Float {
        if (text.isEmpty()) return 0f
        var width = 0f
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            width += glyphAdvancePx(cp, weight, pixelSize, fontId)
            i += Character.charCount(cp)
        }
        return width
    }

    fun measureWidthDp(
        text: String,
        fontSizeDp: Float,
        weight: Weight,
        viewportScale: Float,
        fontId: String = DEFAULT_FONT_ID
    ): Float {
        if (text.isEmpty()) return 0f
        if (activeStyle == HdUiStyle.PIXEL) {
            val dpScale = pixelFontDpScale(fontSizeDp, viewportScale)
            val mcFont = runCatching { net.minecraft.client.Minecraft.getInstance()?.font }.getOrNull()
            if (mcFont != null) {
                return mcFont.width(text) * dpScale
            }
            return text.length * 6f * dpScale
        }
        val px = resolvePixelSize(fontSizeDp, viewportScale)
        return measureWidthPx(text, weight, px, fontId) / viewportScale.coerceAtLeast(0.01f)
    }

    fun truncateDp(
        text: String,
        maxWidthDp: Float,
        fontSizeDp: Float,
        weight: Weight,
        viewportScale: Float,
        ellipsis: String = "…",
        fontId: String = DEFAULT_FONT_ID
    ): String {
        if (text.isEmpty() || maxWidthDp <= 0f) return ""
        if (activeStyle == HdUiStyle.PIXEL) {
            if (measureWidthDp(text, fontSizeDp, weight, viewportScale, fontId) <= maxWidthDp + 0.5f) return text
            val ellW = measureWidthDp(ellipsis, fontSizeDp, weight, viewportScale, fontId)
            val targetW = (maxWidthDp - ellW).coerceAtLeast(0f)
            var end = text.length
            while (end > 0 && measureWidthDp(text.substring(0, end), fontSizeDp, weight, viewportScale, fontId) > targetW) {
                end--
            }
            return if (end <= 0) ellipsis else text.substring(0, end) + ellipsis
        }
        val px = resolvePixelSize(fontSizeDp, viewportScale)
        val maxPx = maxWidthDp * viewportScale
        if (measureWidthPx(text, weight, px, fontId) <= maxPx + 0.75f) return text

        val ellipsisW = measureWidthPx(ellipsis, weight, px, fontId)
        if (ellipsisW >= maxPx) return ""

        val sb = StringBuilder()
        var acc = 0f
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val adv = glyphAdvancePx(cp, weight, px, fontId)
            if (acc + adv + ellipsisW > maxPx) break
            sb.appendCodePoint(cp)
            acc += adv
            i += Character.charCount(cp)
        }
        return sb.toString().trimEnd() + ellipsis
    }

    fun parseSocialSpans(text: String): List<StyledSpan> {
        if (text.isEmpty()) return emptyList()
        if (!text.contains('@') && !text.contains('#')) {
            return listOf(StyledSpan(text, StyledSpan.Kind.PLAIN))
        }
        val out = mutableListOf<StyledSpan>()
        var lastIdx = 0
        TOKEN_REGEX.findAll(text).forEach { match ->
            val start = match.range.first
            if (start > lastIdx) {
                out.add(StyledSpan(text.substring(lastIdx, start), StyledSpan.Kind.PLAIN))
            }
            val prefix = match.groupValues[1]
            val kind = if (prefix == "@") StyledSpan.Kind.MENTION else StyledSpan.Kind.HASHTAG
            out.add(StyledSpan(match.value, kind))
            lastIdx = match.range.last + 1
        }
        if (lastIdx < text.length) {
            out.add(StyledSpan(text.substring(lastIdx), StyledSpan.Kind.PLAIN))
        }
        return out
    }

    fun wrapPlainText(
        text: String,
        maxWidthDp: Float,
        fontSizeDp: Float,
        weight: Weight,
        viewportScale: Float,
        fontId: String = DEFAULT_FONT_ID
    ): List<String> = wrapInternal(
        spans = listOf(StyledSpan(text, StyledSpan.Kind.PLAIN)),
        maxWidthDp = maxWidthDp,
        fontSizeDp = fontSizeDp,
        baseWeight = weight,
        viewportScale = viewportScale,
        fontId = fontId
    ).map { it.plainText }

    fun wrapSocialText(
        text: String,
        maxWidthDp: Float,
        fontSizeDp: Float,
        viewportScale: Float,
        fontId: String = DEFAULT_FONT_ID
    ): List<WrappedLine> {
        if (text.isEmpty() || maxWidthDp <= 0f) return emptyList()
        return wrapInternal(
            spans = parseSocialSpans(text),
            maxWidthDp = maxWidthDp,
            fontSizeDp = fontSizeDp,
            baseWeight = Weight.REGULAR,
            viewportScale = viewportScale,
            fontId = fontId
        )
    }

    private fun wrapInternal(
        spans: List<StyledSpan>,
        maxWidthDp: Float,
        fontSizeDp: Float,
        baseWeight: Weight,
        viewportScale: Float,
        fontId: String
    ): List<WrappedLine> {
        if (spans.isEmpty() || maxWidthDp <= 0f) return emptyList()
        val isPixel = activeStyle == HdUiStyle.PIXEL
        val px = resolvePixelSize(fontSizeDp, viewportScale)
        val maxPx = maxWidthDp * viewportScale

        fun measureSegmentPx(segment: String, weight: Weight): Float =
            if (isPixel) measureWidthDp(segment, fontSizeDp, weight, viewportScale, fontId) * viewportScale
            else measureWidthPx(segment, weight, px, fontId)

        fun measureCodePointPx(cp: Int, weight: Weight): Float =
            if (isPixel) measureWidthDp(String(Character.toChars(cp)), fontSizeDp, weight, viewportScale, fontId) * viewportScale
            else glyphAdvancePx(cp, weight, px, fontId)

        data class Token(val text: String, val kind: StyledSpan.Kind, val isNewline: Boolean = false)
        val tokens = mutableListOf<Token>()
        for (span in spans) {
            val raw = span.text
            var pos = 0
            while (pos < raw.length) {
                val nl = raw.indexOf('\n', pos)
                val segEnd = if (nl == -1) raw.length else nl
                val segment = raw.substring(pos, segEnd)
                var i = 0
                while (i < segment.length) {
                    val isSpace = segment[i] == ' '
                    var j = i + 1
                    while (j < segment.length && (segment[j] == ' ') == isSpace) {
                        j++
                    }
                    tokens.add(Token(segment.substring(i, j), span.kind, false))
                    i = j
                }
                if (nl != -1) {
                    tokens.add(Token("\n", span.kind, true))
                    pos = nl + 1
                } else {
                    break
                }
            }
        }

        val lines = mutableListOf<WrappedLine>()
        var currentSpans = mutableListOf<StyledSpan>()
        var currentWidthPx = 0f

        fun flushLine() {
            val merged = mutableListOf<StyledSpan>()
            for (s in currentSpans) {
                if (merged.isNotEmpty() && merged.last().kind == s.kind) {
                    val prev = merged.removeAt(merged.lastIndex)
                    merged.add(StyledSpan(prev.text + s.text, s.kind))
                } else {
                    merged.add(s)
                }
            }
            lines.add(WrappedLine(merged, currentWidthPx / viewportScale.coerceAtLeast(0.01f)))
            currentSpans = mutableListOf()
            currentWidthPx = 0f
        }

        for (tok in tokens) {
            if (tok.isNewline) {
                flushLine()
                continue
            }
            val weight = if (tok.kind == StyledSpan.Kind.PLAIN) baseWeight else Weight.BOLD
            val tokW = measureSegmentPx(tok.text, weight)

            if (currentSpans.isEmpty() && tok.text.isBlank()) {
                continue
            }

            if (currentWidthPx + tokW <= maxPx) {
                currentSpans.add(StyledSpan(tok.text, tok.kind))
                currentWidthPx += tokW
            } else if (tok.text.isBlank()) {
                flushLine()
            } else {
                if (currentSpans.isNotEmpty()) {
                    flushLine()
                }
                if (tokW > maxPx) {
                    val part = StringBuilder()
                    var partW = 0f
                    var i = 0
                    while (i < tok.text.length) {
                        val cp = tok.text.codePointAt(i)
                        val cw = measureCodePointPx(cp, weight)
                        if (part.isNotEmpty() && partW + cw > maxPx) {
                            currentSpans.add(StyledSpan(part.toString(), tok.kind))
                            currentWidthPx = partW
                            flushLine()
                            part.clear()
                            partW = 0f
                        }
                        part.appendCodePoint(cp)
                        partW += cw
                        i += Character.charCount(cp)
                    }
                    if (part.isNotEmpty()) {
                        currentSpans.add(StyledSpan(part.toString(), tok.kind))
                        currentWidthPx = partW
                    }
                } else {
                    currentSpans.add(StyledSpan(tok.text, tok.kind))
                    currentWidthPx = tokW
                }
            }
        }
        if (currentSpans.isNotEmpty() || lines.isEmpty()) {
            flushLine()
        }
        return lines
    }
}
