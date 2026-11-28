import { useEffect, useMemo, useRef, useState } from 'react'
import { Cpu, Monitor, Settings2, Smartphone, Zap, ZoomIn } from 'lucide-react'
import type { StudioAsset, StudioTheme, ThemeColors } from '@/types/schemas'
import { BUILTIN_APPS } from '@/modules/themes/themeConstants'
import { HdCanvasWebGL } from '@/engine/hd/HdCanvasWebGL'
import { multiplyAlpha, parseColorArgb } from '@/engine/hd/HdBatchWebGL'
import { SMARTPHONE_COLORS, type SmartphoneColorId } from '@/lib/utils'

export type PreviewScreenMode = 'home' | 'settings' | 'large'
type SettingsTabId = 'APPS' | 'THEMES' | 'SKINS' | 'DISPLAY'
type SocialTabId = 'feed' | 'dms' | 'alerts'

interface Props {
  theme: StudioTheme
  assets: StudioAsset[]
}

interface HitRegion {
  id: string
  x: number
  y: number
  w: number
  h: number
  onClick?: () => void
}

// Exact 9x9 alpha matrices from PageDotRenderer.kt
const DOT_ACTIVE_ALPHA = [
  10, 17, 24, 29, 32, 29, 24, 17, 10,
  17, 28, 40, 50, 53, 50, 40, 28, 17,
  24, 40, 57, 229, 230, 229, 57, 40, 24,
  29, 50, 229, 242, 252, 242, 229, 50, 29,
  32, 53, 230, 252, 255, 252, 230, 53, 32,
  29, 50, 229, 242, 252, 242, 229, 50, 29,
  24, 40, 57, 229, 230, 229, 57, 40, 24,
  17, 28, 40, 50, 53, 50, 40, 28, 17,
  10, 17, 24, 29, 32, 29, 24, 17, 10,
]

const DOT_INACTIVE_ALPHA = [
  2, 3, 5, 6, 6, 6, 5, 3, 2,
  3, 6, 8, 10, 10, 10, 8, 6, 3,
  5, 8, 11, 116, 116, 116, 11, 8, 5,
  6, 10, 116, 124, 131, 124, 116, 10, 6,
  6, 10, 116, 131, 133, 131, 116, 10, 6,
  6, 10, 116, 124, 131, 124, 116, 10, 6,
  5, 8, 11, 116, 116, 116, 11, 8, 5,
  3, 6, 8, 10, 10, 10, 8, 6, 3,
  2, 3, 5, 6, 6, 6, 5, 3, 2,
]

function mixArgb(a: number, b: number, t: number, alpha255: number): number {
  const ar = (a >>> 16) & 0xff
  const ag = (a >>> 8) & 0xff
  const ab = a & 0xff
  const br = (b >>> 16) & 0xff
  const bg = (b >>> 8) & 0xff
  const bb = b & 0xff
  const r = Math.round(ar + (br - ar) * t)
  const g = Math.round(ag + (bg - ag) * t)
  const bl = Math.round(ab + (bb - ab) * t)
  return (((alpha255 & 0xff) << 24) | (r << 16) | (g << 8) | bl) >>> 0
}

function drawStyleBIconGpu(
  canvas: HdCanvasWebGL,
  colors: ThemeColors,
  glyphUrl: string,
  x: number,
  y: number,
  size: number,
  hovered: boolean,
  alphaScale = 1
) {
  const navy = parseColorArgb(colors.header_navy)
  const primary = parseColorArgb(colors.primary)
  const primaryHover = parseColorArgb(colors.primary_hover)
  const textSecondary = parseColorArgb(colors.text_secondary)
  const white = 0xffffffff

  const bodyArgb = mixArgb(navy, primary, hovered ? 0.28 : 0.14, Math.round((hovered ? 212 : 188) * alphaScale))
  const sheenArgb = ((Math.round((hovered ? 255 : 185) * alphaScale) << 24) | 0xffffff) >>> 0
  const borderArgb = hovered
    ? mixArgb(primaryHover, white, 0.5, Math.round(238 * alphaScale))
    : mixArgb(navy, primary, 0.55, Math.round(238 * alphaScale))
  const glyphArgb = hovered
    ? ((Math.round(255 * alphaScale) << 24) | 0xffffff) >>> 0
    : mixArgb(textSecondary, white, 0.35, Math.round(255 * alphaScale))

  canvas.image('/mod-assets/textures/gui/buttons/base/squircle_body.png', x, y, size, size, 0, true, bodyArgb)
  canvas.image('/mod-assets/textures/gui/buttons/base/squircle_sheen.png', x, y, size, size, 0, true, sheenArgb)
  canvas.image('/mod-assets/textures/gui/buttons/base/squircle_border.png', x, y, size, size, 0, true, borderArgb)
  canvas.image(glyphUrl, x, y, size, size, 0, true, glyphArgb)
}

export function ThemeCanvasPreview({ theme, assets }: Props) {
  const [screenMode, setScreenMode] = useState<PreviewScreenMode>('home')
  const [renderStyle, setRenderStyle] = useState<'hd' | 'pixel'>('pixel')
  const [settingsTab, setSettingsTab] = useState<SettingsTabId>('APPS')
  const [hiddenApps, setHiddenApps] = useState<Record<string, boolean>>({ 'cobblemon_smartphone:gps': true })
  const [socialTab, setSocialTab] = useState<SocialTabId>('feed')
  const [feedFilter, setFeedFilter] = useState<'global' | 'following'>('global')
  const [likedPost, setLikedPost] = useState(true)
  const [casingColor, setCasingColor] = useState<SmartphoneColorId>('red')
  const [battery, setBattery] = useState(78)
  const [rotomInfinite, setRotomInfinite] = useState(false)
  const [zoom, setZoom] = useState<1 | 1.15 | 1.3>(1.15)
  const [page, setPage] = useState(0)
  const [hoveredId, setHoveredId] = useState<string | null>(null)
  const [texVersion, setTexVersion] = useState(0)

  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const hdCanvasRef = useRef<HdCanvasWebGL | null>(null)
  const hitRegionsRef = useRef<HitRegion[]>([])

  const assetMap = useMemo(() => new Map(assets.map((a) => [a.id, a])), [assets])

  const isLarge = screenMode === 'large' || screenMode === 'settings'
  const designW = isLarge ? 422 : 262
  const designH = 414
  const totalPages = Math.ceil(BUILTIN_APPS.length / 6)

  useEffect(() => {
    const el = canvasRef.current
    if (!el) return
    const gl = el.getContext('webgl2', { alpha: true, antialias: true, premultipliedAlpha: false })
    if (!gl) return
    const hdCanvas = new HdCanvasWebGL(gl, () => setTexVersion((v) => v + 1))
    hdCanvasRef.current = hdCanvas
    return () => {
      hdCanvas.dispose()
      hdCanvasRef.current = null
    }
  }, [])

  const dpr = typeof window !== 'undefined' ? Math.max(2, Math.min(window.devicePixelRatio || 2, 3)) : 2

  useEffect(() => {
    const el = canvasRef.current
    const canvas = hdCanvasRef.current
    if (!el || !canvas) return

    const cssW = Math.round(designW * zoom)
    const cssH = Math.round(designH * zoom)
    const effectiveScale = (cssW / designW) * dpr
    const physW = Math.round(cssW * dpr)
    const physH = Math.round(cssH * dpr)
    if (el.width !== physW || el.height !== physH) {
      el.width = physW
      el.height = physH
    }

    const isPixel = screenMode === 'home' ? true : renderStyle === 'pixel'
    canvas.begin(physW, physH, effectiveScale, isPixel)

    const regions: HitRegion[] = []
    const c = theme.colors
    const col = {
      primary: parseColorArgb(c.primary),
      primaryHover: parseColorArgb(c.primary_hover),
      primaryBorder: parseColorArgb(c.primary_border),
      surface: parseColorArgb(c.surface),
      surfaceAlt: parseColorArgb(c.surface_alt),
      surfaceHover: parseColorArgb(c.surface_hover),
      shadow: parseColorArgb(c.shadow, 0x35000000),
      headerNavy: parseColorArgb(c.header_navy),
      headerNavyLight: parseColorArgb(c.header_navy_light),
      textPrimary: parseColorArgb(c.text_primary),
      textSecondary: parseColorArgb(c.text_secondary),
      textMuted: parseColorArgb(c.text_muted),
      textDark: parseColorArgb(c.text_dark),
      textDarkMuted: parseColorArgb(c.text_dark_muted),
      time: parseColorArgb(c.time),
      gold: parseColorArgb(c.gold),
      danger: parseColorArgb(c.danger),
      success: parseColorArgb(c.success),
      batteryBorder: parseColorArgb(c.battery_border),
      batteryEmpty: parseColorArgb(c.battery_empty),
      batteryHigh: parseColorArgb(c.battery_high),
      batteryMed: parseColorArgb(c.battery_med),
      batteryLow: parseColorArgb(c.battery_low),
    }

    const resolveAsset = (assetId: string | undefined, fallback: string) =>
      assetId ? assetMap.get(assetId)?.dataUrl ?? fallback : fallback

    const wallpaperUrl = isLarge
      ? resolveAsset(theme.largeWallpaperAssetId, '/mod-assets/textures/gui/large_screen.png')
      : resolveAsset(theme.wallpaperAssetId, '/mod-assets/textures/gui/home_screen.png')

    const frameUrl = isLarge
      ? resolveAsset(theme.largeFrameAssetId, `/mod-assets/textures/gui/large_smartphone_${casingColor}.png`)
      : resolveAsset(theme.frameAssetId, `/mod-assets/textures/gui/smartphone_${casingColor}.png`)

    const homeBtnUrl = resolveAsset(theme.elements.home_button, '/mod-assets/textures/gui/elements/home_button.png')
    const prevBtnUrl = resolveAsset(theme.elements.prev_button, '/mod-assets/textures/gui/elements/prev_button.png')
    const nextBtnUrl = resolveAsset(theme.elements.next_button, '/mod-assets/textures/gui/elements/next_button.png')
    const settingsBtnUrl = resolveAsset(theme.elements.settings_button, '/mod-assets/textures/gui/elements/settings_button.png')

    // 1. Wallpaper + 2. Frame
    canvas.image(wallpaperUrl, 0, 0, designW, designH, 0, true)
    canvas.image(frameUrl, 0, 0, designW, designH, 0, true)

    const drawBattery = (bx: number, by: number) => {
      const borderCol = rotomInfinite ? 0xff00e5ff : col.batteryBorder
      canvas.rect(bx, by, 18, 10, col.batteryEmpty, 0, 2, borderCol)
      canvas.rect(bx + 18, by + 2, 2, 6, borderCol, 0)
      const ratio = rotomInfinite ? 1 : Math.max(0, Math.min(1, battery / 100))
      const fillUnits = rotomInfinite || battery > 0 ? Math.max(1, Math.min(7, Math.round(7 * ratio))) : 0
      if (fillUnits > 0) {
        const fillCol = rotomInfinite
          ? 0xff00e5ff
          : ratio > 0.5
            ? col.batteryHigh
            : ratio > 0.2
              ? col.batteryMed
              : col.batteryLow
        canvas.rect(bx + 2, by + 2, fillUnits * 2, 6, fillCol, 0)
      }
    }

    const drawSpriteBtn = (
      id: string,
      url: string,
      x: number,
      y: number,
      w: number,
      h: number,
      onClick?: () => void
    ) => {
      regions.push({ id, x, y, w, h, onClick })
      const isHover = hoveredId === id
      canvas.subImage(url, x, y, w, h, 0, isHover ? 0.5 : 0, 1, isHover ? 1 : 0.5, 0, true)
    }

    const drawLargeBezelChrome = () => {
      regions.push({ id: 'bezel_back', x: 36, y: 26, w: 20, h: 18, onClick: () => setScreenMode('home') })
      canvas.text('←', 40, 29, 10, hoveredId === 'bezel_back' ? col.primary : col.textSecondary, 'bold')
      drawBattery(362, 34)
    }

    if (screenMode === 'home') {
      canvas.text('14:32', 40, 39, 10, col.time, 'bold')
      drawSpriteBtn('settings_btn', settingsBtnUrl, 160, 34, 18, 18, () => setScreenMode('settings'))
      drawBattery(202, 38)

      const pageApps = BUILTIN_APPS.slice(page * 6, page * 6 + 6)
      pageApps.forEach((app, idx) => {
        const colIdx = idx % 2
        const rowIdx = Math.floor(idx / 2)
        const x = 52 + colIdx * 86
        const y = 74 + rowIdx * 86
        const hitId = `app_${app.id}`
        const isHover = hoveredId === hitId
        regions.push({ id: hitId, x, y, w: 72, h: 72 })

        const override = theme.appIcons[app.id]
        if (override?.textureAssetId) {
          const texUrl = isHover && override.hoverAssetId
            ? assetMap.get(override.hoverAssetId)?.dataUrl
            : assetMap.get(override.textureAssetId)?.dataUrl
          if (texUrl) canvas.image(texUrl, x, y, 72, 72, 0, true)
        } else {
          drawStyleBIconGpu(
            canvas,
            c,
            `/mod-assets/textures/gui/buttons/glyphs/${app.stem}.png`,
            x,
            y,
            72,
            isHover
          )
        }
      })

      const dotCount = Math.min(3, totalPages)
      const activeDot = totalPages <= 3 ? page : page === 0 ? 0 : page === totalPages - 1 ? 2 : 1
      const startX = Math.floor(131 - (dotCount * 18 + (dotCount - 1) * 4) / 2)
      for (let d = 0; d < dotCount; d++) {
        const active = d === activeDot
        const dx = startX + d * 22
        const dy = 338 + (active ? 0 : 2)
        const matrix = active ? DOT_ACTIVE_ALPHA : DOT_INACTIVE_ALPHA
        const rgb = active ? (col.primary & 0x00ffffff) : 0x000000
        for (let i = 0; i < matrix.length; i++) {
          const a = matrix[i]
          if (a === 0) continue
          canvas.rect(dx + (i % 9) * 2, dy + Math.floor(i / 9) * 2, 2, 2, (((a << 24) | rgb) >>> 0))
        }
      }

      drawSpriteBtn('prev_btn', prevBtnUrl, 72, 374, 14, 14, () =>
        setPage((p) => (p - 1 + totalPages) % totalPages)
      )
      drawSpriteBtn('home_btn', homeBtnUrl, 124, 374, 14, 14, () => setPage(0))
      drawSpriteBtn('next_btn', nextBtnUrl, 176, 374, 14, 14, () =>
        setPage((p) => (p + 1) % totalPages)
      )
    } else if (screenMode === 'settings') {
      // REAL SmartphoneSettingsScreen.kt replica (appHeader + scroll body + FLOATING_DOCK at bottom!)
      drawLargeBezelChrome()
      const sx = 44
      const sy = 52
      const sw = 334

      const settingsApps = BUILTIN_APPS.slice(0, 4)
      const visibleCount = settingsApps.filter((a) => !hiddenApps[a.id]).length
      const subtitle =
        settingsTab === 'APPS'
          ? `${visibleCount}/${settingsApps.length} Apps`
          : settingsTab === 'THEMES'
            ? '3 Themes'
            : settingsTab === 'SKINS'
              ? '3 Skins'
              : `${renderStyle.toUpperCase()} • 100%`

      // 1. appHeader (exact SmartphoneSettingsScreen.kt lines 248-253)
      canvas.shadow(sx, sy, sw, 36, 11, 5, 1.5, 0x2a000000)
      canvas.rect(sx, sy, sw, 36, col.surface, 11, 0.85, col.primaryBorder)
      canvas.circle(sx + 18, sy + 18, 10, multiplyAlpha(col.primary, 0.18))
      canvas.circle(sx + 18, sy + 18, 4.5, col.primary)
      canvas.text('Settings', sx + 34, sy + 6, 10.5, col.textDark, 'bold', 180)
      canvas.text(subtitle, sx + 34, sy + 20, 8, col.textDarkMuted, 'normal', 180)

      // 2. Tab body
      const bodyY = sy + 44
      if (settingsTab === 'APPS') {
        // Real buildAppsList cards: 30x30 Style B icon, Name + Bind Hotkey button, Up/Down buttons, Switch
        settingsApps.forEach((app, idx) => {
          const ry = bodyY + idx * 54
          const isHidden = Boolean(hiddenApps[app.id])
          const boundSlot = idx === 0 ? 'Slot 1' : null
          canvas.shadow(sx, ry, sw, 47, 10, 4, 1.5, col.shadow)
          canvas.rect(sx, ry, sw, 47, col.surface, 10, 0.85, col.primaryBorder)

          // 30x30 Style B App Icon
          drawStyleBIconGpu(
            canvas,
            c,
            `/mod-assets/textures/gui/buttons/glyphs/${app.stem}.png`,
            sx + 8,
            ry + 8.5,
            30,
            false,
            isHidden ? 0.4 : 1
          )

          // Name + Hotkey pill button
          canvas.text(app.label, sx + 46, ry + 7, 9.6, isHidden ? col.textDarkMuted : col.textDark, 'bold', 140)
          const hkW = boundSlot ? 78 : 66
          canvas.rect(
            sx + 46,
            ry + 23,
            hkW,
            16,
            boundSlot ? col.primary : col.surfaceAlt,
            6,
            0.7,
            boundSlot ? col.primary : col.primaryBorder
          )
          canvas.text(
            boundSlot ? `Hotkey: ${boundSlot}` : 'Bind Hotkey',
            sx + 52,
            ry + 25.5,
            7.4,
            boundSlot ? col.headerNavy : col.textDarkMuted,
            'bold',
            hkW - 8
          )

          // Up / Down reorder buttons
          canvas.rect(sx + sw - 92, ry + 13, 20, 20, col.surfaceAlt, 6, 0.7, col.primaryBorder)
          canvas.text('▲', sx + sw - 86, ry + 16, 7.5, idx > 0 ? col.textDark : col.textDarkMuted, 'bold')
          canvas.rect(sx + sw - 69, ry + 13, 20, 20, col.surfaceAlt, 6, 0.7, col.primaryBorder)
          canvas.text('▼', sx + sw - 63, ry + 16, 7.5, idx < settingsApps.length - 1 ? col.textDark : col.textDarkMuted, 'bold')

          // Real Show / Hide Switch (clickable!)
          const swX = sx + sw - 40
          const swY = ry + 15
          regions.push({
            id: `sw_${app.id}`,
            x: swX,
            y: swY,
            w: 32,
            h: 16,
            onClick: () => setHiddenApps((prev) => ({ ...prev, [app.id]: !prev[app.id] })),
          })
          canvas.rect(swX, swY, 32, 16, !isHidden ? col.primary : col.surfaceAlt, 8, 0.8, col.primaryBorder)
          canvas.circle(!isHidden ? swX + 24 : swX + 8, swY + 8, 5.5, 0xffffffff)
        })
      } else if (settingsTab === 'THEMES') {
        // Real buildThemesGrid: 3-column gallery with wallpaper preview + color swatches
        const colW = (sw - 12) / 3
        const gallery = [
          { name: theme.name || 'Active', selected: true, accent: col.primary, bg: col.surfaceAlt, wp: wallpaperUrl },
          { name: 'Default', selected: false, accent: 0xff3a96b6, bg: 0xffeffdff, wp: '/mod-assets/textures/gui/home_screen.png' },
          { name: 'Rotom OS', selected: false, accent: 0xffea580c, bg: 0xff24140e, wp: '/mod-assets/textures/gui/wallpapers/orange.png' },
        ]
        gallery.forEach((item, idx) => {
          const gx = sx + idx * (colW + 6)
          canvas.shadow(gx, bodyY, colW, 134, 10, 4, 1.5, col.shadow)
          canvas.rect(
            gx,
            bodyY,
            colW,
            134,
            col.surface,
            10,
            item.selected ? 1.5 : 0.8,
            item.selected ? col.primary : col.primaryBorder
          )
          // Mini wallpaper preview
          canvas.image(item.wp, gx + 8, bodyY + 8, colW - 16, 76, 6, true)
          canvas.text(item.name.slice(0, 12), gx + 8, bodyY + 90, 8.8, col.textDark, 'bold', colW - 16)
          // 3 color swatches
          canvas.circle(gx + 14, bodyY + 114, 4.5, item.accent)
          canvas.circle(gx + 26, bodyY + 114, 4.5, item.bg, 0.8, col.primaryBorder)
          canvas.circle(gx + 38, bodyY + 114, 4.5, col.headerNavy)
          if (item.selected) {
            canvas.text('★', gx + colW - 18, bodyY + 108, 10, col.gold, 'bold')
          }
        })
      } else if (settingsTab === 'SKINS') {
        const colW = (sw - 12) / 3
        const skinCards = [
          { name: 'Default Case', sub: 'Equipped', active: true },
          { name: 'Gold Collector', sub: 'Unlocked', active: false },
          { name: 'Pikachu Case', sub: 'Locked', active: false },
        ]
        skinCards.forEach((sk, idx) => {
          const gx = sx + idx * (colW + 6)
          canvas.shadow(gx, bodyY, colW, 118, 10, 4, 1.5, col.shadow)
          canvas.rect(gx, bodyY, colW, 118, col.surface, 10, sk.active ? 1.5 : 0.8, sk.active ? col.primary : col.primaryBorder)
          canvas.rect(gx + 14, bodyY + 12, colW - 28, 62, col.surfaceAlt, 8, 0.8, col.primaryBorder)
          canvas.text(sk.name.slice(0, 13), gx + 8, bodyY + 82, 8.5, col.textDark, 'bold', colW - 16)
          canvas.text(sk.sub, gx + 8, bodyY + 96, 7.6, sk.active ? col.primary : col.textDarkMuted, 'normal', colW - 16)
        })
      } else {
        // Real Display tab: UI Style (HD / Pixel) + UI Scale
        canvas.shadow(sx, bodyY, sw, 116, 10, 5, 1.5, col.shadow)
        canvas.rect(sx, bodyY, sw, 116, col.surface, 10, 0.85, col.primaryBorder)
        canvas.text('Interface Style', sx + 12, bodyY + 10, 9.5, col.textDark, 'bold')
        canvas.rect(sx + 12, bodyY + 28, sw - 24, 24, col.surfaceAlt, 8, 0.8, col.primaryBorder)
        const half = (sw - 30) / 2
        regions.push({ id: 'disp_hd', x: sx + 15, y: bodyY + 31, w: half, h: 18, onClick: () => setRenderStyle('hd') })
        regions.push({ id: 'disp_px', x: sx + 15 + half, y: bodyY + 31, w: half, h: 18, onClick: () => setRenderStyle('pixel') })
        canvas.rect(renderStyle === 'hd' ? sx + 15 : sx + 15 + half, bodyY + 31, half, 18, col.primary, 6)
        canvas.text('HD Smooth', sx + 48, bodyY + 34, 8.5, renderStyle === 'hd' ? col.headerNavy : col.textDarkMuted, 'bold')
        canvas.text('Pixel Classic', sx + 15 + half + 36, bodyY + 34, 8.5, renderStyle === 'pixel' ? col.headerNavy : col.textDarkMuted, 'bold')

        canvas.text('UI Scale', sx + 12, bodyY + 66, 9.5, col.textDark, 'bold')
        canvas.text('100%', sx + sw - 38, bodyY + 66, 9.5, col.primary, 'bold')
        canvas.rect(sx + 12, bodyY + 86, sw - 24, 6, col.headerNavy, 3)
        canvas.rect(sx + 12, bodyY + 86, (sw - 24) * 0.5, 6, col.primary, 3)
        canvas.circle(sx + 12 + (sw - 24) * 0.5, bodyY + 89, 5.5, 0xffffffff, 1.2, col.primary)
      }

      // 3. FLOATING_DOCK Bottom Navigation (exact SmartphoneSettingsScreen.kt lines 279-290)
      const dockY = sy + 292
      canvas.shadow(sx + 6, dockY, sw - 12, 34, 14, 7, 2, 0x35000000)
      canvas.rect(sx + 6, dockY, sw - 12, 34, col.surface, 14, 0.9, col.primaryBorder)
      const dockItems: Array<{ id: SettingsTabId; label: string }> = [
        { id: 'APPS', label: 'Apps' },
        { id: 'THEMES', label: 'Themes' },
        { id: 'SKINS', label: 'Skins' },
        { id: 'DISPLAY', label: 'Display' },
      ]
      const itemW = (sw - 24) / dockItems.length
      dockItems.forEach((item, i) => {
        const ix = sx + 12 + i * itemW
        const active = settingsTab === item.id
        const hitId = `dock_${item.id}`
        regions.push({ id: hitId, x: ix, y: dockY + 4, w: itemW, h: 26, onClick: () => setSettingsTab(item.id) })
        if (active) {
          canvas.rect(ix + 2, dockY + 4, itemW - 4, 26, col.primary, 10)
        } else if (hoveredId === hitId) {
          canvas.rect(ix + 2, dockY + 4, itemW - 4, 26, col.surfaceHover, 10)
        }
        canvas.text(
          item.label,
          ix + Math.round((itemW - item.label.length * 5) / 2),
          dockY + 11,
          8.8,
          active ? col.headerNavy : col.textDarkMuted,
          'bold'
        )
      })
    } else {
      // REAL SocialScreen.kt replica (floating pill header + real feed posts/DMs/alerts + bottom nav!)
      drawLargeBezelChrome()
      const sx = 44
      const sy = 52
      const sw = 334

      // 1. Floating Header Card (exact SocialScreen.kt lines 107-253)
      canvas.shadow(sx, sy, sw, 32, 12, 6, 2, 0x28000000)
      canvas.rect(sx, sy, sw, 32, col.surface, 12, 0.85, col.primaryBorder)

      if (socialTab === 'feed') {
        // Global & Following pills
        regions.push({ id: 'pill_global', x: sx + 7, y: sy + 5, w: 60, h: 22, onClick: () => setFeedFilter('global') })
        canvas.rect(sx + 7, sy + 5, 60, 22, feedFilter === 'global' ? col.primary : col.surfaceAlt, 10, 0.7, col.primaryBorder)
        canvas.text('Global', sx + 21, sy + 10, 8.5, feedFilter === 'global' ? col.headerNavy : col.textDarkMuted, 'bold')

        regions.push({ id: 'pill_following', x: sx + 72, y: sy + 5, w: 72, h: 22, onClick: () => setFeedFilter('following') })
        canvas.rect(sx + 72, sy + 5, 72, 22, feedFilter === 'following' ? col.primary : col.surfaceAlt, 10, 0.7, col.primaryBorder)
        canvas.text('Following', sx + 84, sy + 10, 8.5, feedFilter === 'following' ? col.headerNavy : col.textDarkMuted, 'bold')
      } else if (socialTab === 'dms') {
        canvas.circle(sx + 18, sy + 16, 9, multiplyAlpha(col.primary, 0.18))
        canvas.text('Messages', sx + 32, sy + 5, 9.5, col.textDark, 'bold')
        canvas.text('2 threads', sx + 32, sy + 18, 7.2, col.textDarkMuted, 'normal')
      } else {
        canvas.rect(sx + 7, sy + 5, 62, 22, col.primary, 10)
        canvas.text('Alerts', sx + 22, sy + 10, 8.5, col.headerNavy, 'bold')
        canvas.rect(sx + 74, sy + 5, 66, 22, col.surfaceAlt, 10, 0.7, col.primaryBorder)
        canvas.text('Settings', sx + 86, sy + 10, 8.5, col.textDarkMuted, 'bold')
      }

      // My Profile avatar + Compose (+) button
      canvas.circle(sx + sw - 46, sy + 16, 10, col.surfaceAlt, 0.9, col.primaryBorder)
      canvas.circle(sx + sw - 46, sy + 16, 6.5, col.primary)
      if (socialTab !== 'alerts') {
        canvas.rect(sx + sw - 28, sy + 5, 22, 22, col.primary, 9)
        canvas.text('+', sx + sw - 21, sy + 8, 11.5, col.headerNavy, 'bold')
      }

      // 2. Scrollable Feed / DMs / Alerts Body
      const bodyY = sy + 40
      if (socialTab === 'feed') {
        // Post Card 1 (Trainer post with #hashtags + clickable Like!)
        canvas.shadow(sx, bodyY, sw, 116, 12, 5, 1.5, col.shadow)
        canvas.rect(sx, bodyY, sw, 116, col.surface, 12, 0.85, col.primaryBorder)
        canvas.circle(sx + 20, bodyY + 20, 11, col.primary, 1, col.primaryBorder)
        canvas.circle(sx + 28, bodyY + 28, 3.5, col.success, 0.8, col.surface)
        canvas.text('TrainerRed', sx + 38, bodyY + 10, 9.5, col.textDark, 'bold')
        canvas.text('2m ago • Global', sx + 38, bodyY + 22, 7.5, col.textDarkMuted, 'normal')
        canvas.rect(sx + sw - 54, bodyY + 10, 44, 15, col.gold, 7)
        canvas.text('CHAMP', sx + sw - 48, bodyY + 12.5, 7.2, 0xff09090b, 'bold')

        canvas.text('Caught a Shiny Umbreon at Cerulean Cave!', sx + 12, bodyY + 40, 9.2, col.textDark, 'normal', sw - 24)
        canvas.text('#shiny  #cobblemon  #kanto', sx + 12, bodyY + 56, 8.6, col.primary, 'bold', sw - 24)

        // Post action footer (Like, Comments, Battle)
        canvas.rect(sx + 10, bodyY + 78, sw - 20, 0.7, multiplyAlpha(col.primaryBorder, 0.45))
        regions.push({ id: 'like_p1', x: sx + 12, y: bodyY + 85, w: 54, h: 22, onClick: () => setLikedPost((v) => !v) })
        canvas.rect(sx + 12, bodyY + 85, 54, 22, likedPost ? multiplyAlpha(col.danger, 0.16) : col.surfaceAlt, 8)
        canvas.text(`♥ ${likedPost ? 15 : 14}`, sx + 22, bodyY + 90, 8.5, likedPost ? col.danger : col.textDarkMuted, 'bold')

        canvas.rect(sx + 72, bodyY + 85, 52, 22, col.surfaceAlt, 8)
        canvas.text('💬 4', sx + 83, bodyY + 90, 8.5, col.textDarkMuted, 'bold')

        canvas.rect(sx + sw - 74, bodyY + 85, 62, 22, col.primary, 8)
        canvas.text('Battle', sx + sw - 58, bodyY + 90, 8.5, col.headerNavy, 'bold')

        // Post Card 2 (Poll post)
        const p2Y = bodyY + 124
        canvas.shadow(sx, p2Y, sw, 114, 12, 5, 1.5, col.shadow)
        canvas.rect(sx, p2Y, sw, 114, col.surface, 12, 0.85, col.primaryBorder)
        canvas.circle(sx + 20, p2Y + 18, 10, col.headerNavyLight, 1, col.primaryBorder)
        canvas.text('Cynthia', sx + 36, p2Y + 9, 9.5, col.textDark, 'bold')
        canvas.text('14m ago • Poll', sx + 36, p2Y + 20, 7.5, col.textDarkMuted, 'normal')
        canvas.text('Best starter for Sinnoh tournament?', sx + 12, p2Y + 36, 9, col.textDark, 'medium', sw - 24)

        // Poll bar 1
        canvas.rect(sx + 12, p2Y + 54, sw - 24, 22, col.surfaceAlt, 7, 0.8, col.primary)
        canvas.rect(sx + 12, p2Y + 54, (sw - 24) * 0.68, 22, multiplyAlpha(col.primary, 0.28), 7)
        canvas.text('Garchomp / Lucario', sx + 20, p2Y + 59, 8.4, col.textDark, 'bold')
        canvas.text('68%', sx + sw - 40, p2Y + 59, 8.4, col.primary, 'bold')

        // Poll bar 2
        canvas.rect(sx + 12, p2Y + 82, sw - 24, 22, col.surfaceAlt, 7, 0.7, col.primaryBorder)
        canvas.rect(sx + 12, p2Y + 82, (sw - 24) * 0.32, 22, multiplyAlpha(col.primary, 0.14), 7)
        canvas.text('Milotic / Togekiss', sx + 20, p2Y + 87, 8.4, col.textDarkMuted, 'normal')
        canvas.text('32%', sx + sw - 40, p2Y + 87, 8.4, col.textDarkMuted, 'normal')
      } else if (socialTab === 'dms') {
        const threads = [
          { name: 'Cynthia', msg: 'Ready for our rematch at Sinnoh League?', time: '1m', unread: 2, online: true },
          { name: 'Prof. Oak', msg: 'Your Pokédex progress is remarkable!', time: '25m', unread: 0, online: true },
          { name: 'Blue', msg: 'Smell ya later! Trade me that Scizor.', time: '2h', unread: 0, online: false },
        ]
        threads.forEach((t, idx) => {
          const ty = bodyY + idx * 62
          canvas.shadow(sx, ty, sw, 54, 11, 4, 1.5, col.shadow)
          canvas.rect(sx, ty, sw, 54, col.surface, 11, 0.85, col.primaryBorder)
          canvas.circle(sx + 24, ty + 27, 14, col.primary, 1, col.primaryBorder)
          canvas.circle(sx + 34, ty + 37, 4, t.online ? col.success : col.textMuted, 0.9, col.surface)
          canvas.text(t.name, sx + 46, ty + 11, 9.8, col.textDark, 'bold')
          canvas.text(t.msg, sx + 46, ty + 27, 8.2, col.textDarkMuted, 'normal', sw - 96)
          canvas.text(t.time, sx + sw - 32, ty + 11, 7.6, col.textDarkMuted, 'normal')
          if (t.unread > 0) {
            canvas.circle(sx + sw - 22, ty + 34, 8, col.danger)
            canvas.text(String(t.unread), sx + sw - 25, ty + 29, 8, 0xffffffff, 'bold')
          }
        })
      } else {
        const alerts = [
          { title: 'Cynthia liked your post', sub: '2 minutes ago • #shiny', dot: col.danger },
          { title: 'TrainerRed sent a Trade Request', sub: '9 minutes ago • Online', dot: col.success },
          { title: 'New reply on your Poll', sub: '18 minutes ago • 4 comments', dot: col.primary },
        ]
        alerts.forEach((a, idx) => {
          const ay = bodyY + idx * 54
          canvas.shadow(sx, ay, sw, 46, 10, 4, 1.5, col.shadow)
          canvas.rect(sx, ay, sw, 46, col.surface, 10, 0.85, col.primaryBorder)
          canvas.circle(sx + 18, ay + 23, 5, a.dot)
          canvas.text(a.title, sx + 32, ay + 9, 9.2, col.textDark, 'bold', sw - 44)
          canvas.text(a.sub, sx + 32, ay + 24, 7.8, col.textDarkMuted, 'normal', sw - 44)
        })
      }

      // 3. Real Social Bottom Navigation (exact SocialComponents.buildBottomNav: Feed | Discover | DMs | Alerts)
      const navY = sy + 292
      canvas.shadow(sx + 4, navY, sw - 8, 34, 14, 7, 2, 0x35000000)
      canvas.rect(sx + 4, navY, sw - 8, 34, col.surface, 14, 0.9, col.primaryBorder)
      const navItems: Array<{ id: SocialTabId | 'discover'; label: string }> = [
        { id: 'feed', label: 'Feed' },
        { id: 'discover', label: 'Discover' },
        { id: 'dms', label: 'DMs' },
        { id: 'alerts', label: 'Alerts' },
      ]
      const navW = (sw - 20) / navItems.length
      navItems.forEach((item, i) => {
        const nx = sx + 10 + i * navW
        const active = socialTab === item.id
        const hitId = `snav_${item.id}`
        regions.push({
          id: hitId,
          x: nx,
          y: navY + 4,
          w: navW,
          h: 26,
          onClick: () => {
            if (item.id !== 'discover') setSocialTab(item.id)
          },
        })
        if (active) {
          canvas.rect(nx + 2, navY + 4, navW - 4, 26, col.primary, 10)
        } else if (hoveredId === hitId) {
          canvas.rect(nx + 2, navY + 4, navW - 4, 26, col.surfaceHover, 10)
        }
        canvas.text(
          item.label,
          nx + Math.round((navW - item.label.length * 5) / 2),
          navY + 11,
          8.6,
          active ? col.headerNavy : col.textDarkMuted,
          'bold'
        )
      })
    }

    canvas.end()
    hitRegionsRef.current = regions
  })

  const getDpCoords = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    return {
      x: ((e.clientX - rect.left) / rect.width) * designW,
      y: ((e.clientY - rect.top) / rect.height) * designH,
    }
  }

  return (
    <div className="flex flex-1 flex-col items-center justify-between gap-4 overflow-auto bg-[radial-gradient(#27272a_1px,transparent_1px)] [background-size:18px_18px] p-5">
      {/* Top Preview Toolbar */}
      <div className="flex flex-wrap items-center justify-center gap-2">
        <div className="flex items-center gap-1.5 rounded-xl border border-emerald-500/30 bg-emerald-950/40 px-2.5 py-1.5 text-[11px] font-medium text-emerald-300">
          <Cpu className="h-3.5 w-3.5" />
          WebGL2 SDF
        </div>

        <div className="flex items-center rounded-xl border border-zinc-800 bg-zinc-900/90 p-1 text-xs">
          {(
            [
              { id: 'home', label: 'Home (131×207)', icon: Smartphone },
              { id: 'settings', label: 'Settings (Large)', icon: Settings2 },
              { id: 'large', label: 'Social (211×207)', icon: Monitor },
            ] as const
          ).map((mode) => {
            const Icon = mode.icon
            return (
              <button
                key={mode.id}
                onClick={() => setScreenMode(mode.id)}
                className={`flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 font-medium transition ${
                  screenMode === mode.id
                    ? 'bg-red-500/20 text-red-300'
                    : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                <Icon className="h-3.5 w-3.5" />
                {mode.label}
              </button>
            )
          })}
        </div>

        {isLarge && (
          <div className="flex rounded-xl border border-zinc-800 bg-zinc-900/90 p-1 text-xs">
            <button
              onClick={() => setRenderStyle('hd')}
              className={`rounded-lg px-2 py-1 font-medium transition ${
                renderStyle === 'hd' ? 'bg-violet-500/20 text-violet-300' : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              HD
            </button>
            <button
              onClick={() => setRenderStyle('pixel')}
              className={`rounded-lg px-2 py-1 font-medium transition ${
                renderStyle === 'pixel' ? 'bg-red-500/20 text-red-300' : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              Pixel
            </button>
          </div>
        )}

        <div className="flex items-center gap-1 rounded-xl border border-zinc-800 bg-zinc-900/90 p-1 text-xs">
          <ZoomIn className="ml-1.5 h-3.5 w-3.5 text-zinc-500" />
          {([1, 1.15, 1.3] as const).map((z) => (
            <button
              key={z}
              onClick={() => setZoom(z)}
              className={`rounded-lg px-2 py-1 font-mono text-[11px] transition ${
                zoom === z ? 'bg-zinc-800 text-zinc-100' : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              {Math.round(z * 100)}%
            </button>
          ))}
        </div>
      </div>

      {/* Unified WebGL2 Canvas Stage */}
      <div className="flex flex-col items-center gap-2">
        <canvas
          ref={canvasRef}
          data-tex-version={texVersion}
          onMouseMove={(e) => {
            const { x, y } = getDpCoords(e)
            const hit = hitRegionsRef.current.find(
              (r) => x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h
            )
            const nextId = hit?.id ?? null
            if (nextId !== hoveredId) setHoveredId(nextId)
          }}
          onMouseLeave={() => setHoveredId(null)}
          onClick={(e) => {
            const { x, y } = getDpCoords(e)
            const hit = hitRegionsRef.current.find(
              (r) => x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h
            )
            hit?.onClick?.()
          }}
          className={`block ${hoveredId ? 'cursor-pointer' : 'cursor-default'}`}
          style={{ width: Math.round(designW * zoom), height: Math.round(designH * zoom) }}
        />
        <div className="text-[11px] text-zinc-500">
          Interactive in-game screens (Home, Settings Dock & Social Feed) • Click tabs, switches & likes
        </div>
      </div>

      {/* Bottom Controls: 16 Casings + Battery Tester */}
      <div className="flex flex-wrap items-center justify-center gap-3">
        <div className="flex flex-wrap items-center justify-center gap-1.5 rounded-xl border border-zinc-800 bg-zinc-900/90 px-3 py-2">
          {SMARTPHONE_COLORS.map((c) => (
            <button
              key={c.id}
              title={`Casing: ${c.label}`}
              onClick={() => setCasingColor(c.id)}
              className={`h-4.5 w-4.5 rounded-full border-2 transition ${
                casingColor === c.id
                  ? 'scale-115 border-red-400'
                  : 'border-zinc-700 opacity-80 hover:opacity-100'
              }`}
              style={{ backgroundColor: c.hex }}
            />
          ))}
        </div>

        <div className="flex items-center gap-3 rounded-xl border border-zinc-800 bg-zinc-900/90 px-3.5 py-2 text-xs text-zinc-300">
          <span className="w-20 font-mono text-[11px]">
            {rotomInfinite ? 'Rotom ∞' : `Battery ${battery}%`}
          </span>
          <input
            type="range"
            min={0}
            max={100}
            disabled={rotomInfinite}
            value={battery}
            onChange={(e) => setBattery(Number(e.target.value))}
            className="w-28 accent-red-500 disabled:opacity-40"
          />
          <button
            onClick={() => setRotomInfinite((v) => !v)}
            title="Toggle Rotom Infinite Energy (#00E5FF)"
            className={`flex items-center gap-1 rounded-md px-2 py-1 text-[11px] font-medium transition ${
              rotomInfinite
                ? 'bg-red-500/20 text-red-300 ring-1 ring-red-500/40'
                : 'bg-zinc-800 text-zinc-400 hover:text-zinc-200'
            }`}
          >
            <Zap className="h-3.5 w-3.5" />
            Rotom ∞
          </button>
        </div>
      </div>
    </div>
  )
}
