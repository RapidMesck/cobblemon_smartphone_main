import type { ScreenNodeDef, StudioScreen, ThemeColors } from '@/types/schemas'
import { HdCanvasWebGL } from '@/engine/hd/HdCanvasWebGL'
import { multiplyAlpha, parseColorArgb } from '@/engine/hd/HdBatchWebGL'
import { evalVisibleIf, interpolateText } from '@/modules/screens/screenCatalog'
import type { SmartphoneColorId } from '@/lib/utils'

export interface HitBox {
  id: string
  path: number[]
  x: number
  y: number
  w: number
  h: number
  onClick?: () => void
}

export interface RenderScreenOptions {
  screen: StudioScreen
  colors: ThemeColors
  casingColor: SmartphoneColorId
  state: Record<string, string | number | boolean>
  isPixelStyle: boolean
  interactionMode: 'play' | 'select'
  selectedPath: number[] | null
  hoveredId: string | null
  phoneToast: { text: string; kind: string } | null
  onSetState: (key: string, value: string | number | boolean) => void
  onRunAction: (actionKey?: string) => void
  onSelectPath: (path: number[]) => void
}

function samePath(a: number[] | null, b: number[]) {
  return Boolean(a && a.length === b.length && a.every((v, i) => v === b[i]))
}

export function getScreenDesignSize(screen: StudioScreen): { w: number; h: number } {
  if (screen.viewport === 'free') {
    return {
      w: Math.min(480, Math.max(200, screen.width_dp ?? 300)),
      h: Math.min(460, Math.max(180, screen.height_dp ?? 260)),
    }
  }
  if (screen.viewport === 'compact') return { w: 262, h: 414 }
  return { w: 422, h: 414 }
}

/**
 * Measures the height (in dp) of a ScreenNodeDef tree for exact top-down layout.
 */
function measureNodeHeight(
  node: ScreenNodeDef,
  availW: number,
  state: Record<string, string | number | boolean>,
  showHidden: boolean
): number {
  if (!showHidden && !evalVisibleIf(node.visible_if, state)) return 0

  switch (node.type) {
    case 'app_header':
      return node.subtitle ? 40 : 28
    case 'tabs':
      return 28
    case 'button':
      return 28
    case 'pill':
      return 22
    case 'switch':
    case 'checkbox':
      return 24
    case 'slider':
      return 34
    case 'number_stepper':
      return 26
    case 'segmented_control':
      return 26
    case 'text_input':
      return 26
    case 'text':
      return Math.ceil((node.size ?? 11) * 1.45)
    case 'badge':
      return 20
    case 'stat_bar':
      return 26
    case 'progress_bar':
      return 10
    case 'pokemon_view':
      return 48
    case 'item_icon':
    case 'avatar':
      return 36
    case 'icon':
      return node.size ?? 16
    case 'image':
      return 56
    case 'empty_state':
      return node.label ? 92 : 68
    case 'list_tile':
      return 36
    case 'divider':
      return 6
    case 'spacer':
      return Number(node.height) || 8
    case 'flow_row':
      return 22
    case 'row': {
      const children = node.children ?? []
      if (children.length === 0) return 24
      const gap = node.gap ?? 6
      const colW = (availW - gap * (children.length - 1)) / children.length
      let maxH = 0
      for (const c of children) {
        maxH = Math.max(maxH, measureNodeHeight(c, colW, state, showHidden))
      }
      return Math.max(20, maxH)
    }
    case 'grid': {
      const children = node.children ?? []
      const cols = Math.max(1, node.columns ?? 2)
      const gap = node.gap ?? 6
      const colW = (availW - gap * (cols - 1)) / cols
      let total = 0
      for (let i = 0; i < children.length; i += cols) {
        let rowH = 0
        for (let j = 0; j < cols && i + j < children.length; j++) {
          rowH = Math.max(rowH, measureNodeHeight(children[i + j], colW, state, showHidden))
        }
        total += rowH + (i + cols < children.length ? gap : 0)
      }
      return Math.max(20, total)
    }
    case 'accordion': {
      const key = node.state_key || 'acc_open'
      const open = Boolean(state[key] ?? true)
      if (!open) return 32
      const innerH = measureColumnChildren(node.children ?? [], availW - 20, 6, state, showHidden)
      return 32 + 12 + innerH
    }
    case 'card': {
      const pad = typeof node.padding === 'number' ? node.padding : 10
      const gap = node.gap ?? 6
      const innerH = measureColumnChildren(node.children ?? [], availW - pad * 2, gap, state, showHidden)
      return pad * 2 + innerH
    }
    case 'column':
    case 'scroll':
    case 'stack': {
      const gap = node.gap ?? 6
      return measureColumnChildren(node.children ?? [], availW, gap, state, showHidden)
    }
  }
}

function measureColumnChildren(
  children: ScreenNodeDef[],
  availW: number,
  gap: number,
  state: Record<string, string | number | boolean>,
  showHidden: boolean
): number {
  let total = 0
  let visibleCount = 0
  for (const c of children) {
    const h = measureNodeHeight(c, availW, state, showHidden)
    if (h > 0) {
      if (visibleCount > 0) total += gap
      total += h
      visibleCount++
    }
  }
  return total
}

/**
 * Renders the full HdPhoneScreen onto WebGL2 using the verbatim HdShader.kt SDF shader
 * and returns exact dp-space HitBoxes for interactive controls & node selection.
 */
export function renderHdScreen(
  canvas: HdCanvasWebGL,
  opts: RenderScreenOptions
): HitBox[] {
  const {
    screen,
    colors,
    casingColor,
    state,
    interactionMode,
    selectedPath,
    hoveredId,
    phoneToast,
    onSetState,
    onRunAction,
    onSelectPath,
  } = opts

  const hitBoxes: HitBox[] = []
  const { w: designW, h: designH } = getScreenDesignSize(screen)
  const isCompact = screen.viewport === 'compact'
  const isFree = screen.viewport === 'free'
  const showHidden = interactionMode === 'select'

  const col = {
    primary: parseColorArgb(colors.primary),
    primaryHover: parseColorArgb(colors.primary_hover),
    primaryBorder: parseColorArgb(colors.primary_border),
    surface: parseColorArgb(colors.surface),
    surfaceAlt: parseColorArgb(colors.surface_alt),
    surfaceHover: parseColorArgb(colors.surface_hover),
    shadow: parseColorArgb(colors.shadow, 0x35000000),
    headerNavy: parseColorArgb(colors.header_navy),
    headerNavyLight: parseColorArgb(colors.header_navy_light),
    textPrimary: parseColorArgb(colors.text_primary),
    textSecondary: parseColorArgb(colors.text_secondary),
    textMuted: parseColorArgb(colors.text_muted),
    textDark: parseColorArgb(colors.text_dark),
    textDarkMuted: parseColorArgb(colors.text_dark_muted),
    gold: parseColorArgb(colors.gold),
    danger: parseColorArgb(colors.danger),
    success: parseColorArgb(colors.success),
    batteryBorder: parseColorArgb(colors.battery_border),
    batteryEmpty: parseColorArgb(colors.battery_empty),
    batteryHigh: parseColorArgb(colors.battery_high),
  }

  // 1. Phone Bezel / Backdrop (exact HdPhoneScreen.kt)
  if (isFree) {
    canvas.shadow(0, 0, designW, designH, screen.corner_radius, 10, 3, 0x55000000)
    canvas.rect(0, 0, designW, designH, col.surface, screen.corner_radius, 1, col.primaryBorder)
  } else {
    const wallpaperUrl = isCompact
      ? '/mod-assets/textures/gui/home_screen.png'
      : '/mod-assets/textures/gui/large_screen.png'
    const frameUrl = isCompact
      ? `/mod-assets/textures/gui/smartphone_${casingColor}.png`
      : `/mod-assets/textures/gui/large_smartphone_${casingColor}.png`

    canvas.image(wallpaperUrl, 0, 0, designW, designH, 0, true)
    canvas.image(frameUrl, 0, 0, designW, designH, 0, true)

    // Bezel chrome (exact 2x coordinates)
    if (screen.show_bezel_back) {
      canvas.text('←', 40, 29, 10, col.textSecondary, 'bold')
    }
    if (screen.show_bezel_battery) {
      const bx = isCompact ? 202 : 362
      const by = isCompact ? 38 : 34
      // 9x5 scaled 2x -> 18x10 + 2x6 tip
      canvas.rect(bx, by, 18, 10, col.batteryEmpty, 0, 2, col.batteryBorder)
      canvas.rect(bx + 18, by + 2, 2, 6, col.batteryBorder, 0)
      canvas.rect(bx + 2, by + 2, 11, 6, col.batteryHigh, 0)
    }
  }

  // 2. Content inside exact screenRectDp: Compact (40,48,182,340), Large (40,48,342,340), Free (10,10,W-20,H-20)
  const contentX = isFree ? 10 : 44
  const contentY = isFree ? 10 : 52
  const contentW = isFree ? designW - 20 : isCompact ? 174 : 334
  const contentH = isFree ? designH - 20 : 332

  canvas.pushClip(contentX - 4, contentY - 4, contentW + 8, contentH + 8)

  const drawNode = (node: ScreenNodeDef, path: number[], x: number, y: number, w: number): number => {
    const visible = evalVisibleIf(node.visible_if, state)
    if (!visible && !showHidden) return 0

    const h = measureNodeHeight(node, w, state, showHidden)
    const hitId = `node_${path.join('_')}`
    const isSelected = samePath(selectedPath, path)
    const isHovered = hoveredId === hitId

    if (interactionMode === 'select') {
      hitBoxes.push({ id: hitId, path, x, y, w, h, onClick: () => onSelectPath(path) })
    }

    switch (node.type) {
      case 'app_header': {
        canvas.shadow(x, y, w, h, 10, 5, 1.5, 0x35000000)
        canvas.gradientRect(x, y, w, h, col.headerNavyLight, col.headerNavy, 10, 0.8, col.primaryBorder)
        canvas.circle(x + 14, y + h / 2, 5, col.primary)
        canvas.text(interpolateText(node.title || 'Header', state), x + 26, y + (node.subtitle ? 6 : 8), 11, col.textPrimary, 'bold', w - 34)
        if (node.subtitle) {
          canvas.text(interpolateText(node.subtitle, state), x + 26, y + 21, 8.5, col.textSecondary, 'normal', w - 34)
        }
        break
      }

      case 'tabs': {
        const tabs = node.tabs ?? []
        const key = node.state_key || 'active_tab'
        const current = String(state[key] ?? tabs[0]?.id ?? '')
        canvas.rect(x, y, w, h, col.surfaceAlt, 10, 0.8, col.primaryBorder)
        if (tabs.length > 0) {
          const pad = 3
          const tabW = (w - pad * 2 - (tabs.length - 1) * 3) / tabs.length
          tabs.forEach((t, i) => {
            const tx = x + pad + i * (tabW + 3)
            const ty = y + pad
            const th = h - pad * 2
            const active = current === t.id
            const tabHitId = `${hitId}_tab_${t.id}`
            if (interactionMode === 'play') {
              hitBoxes.push({
                id: tabHitId,
                path,
                x: tx,
                y: ty,
                w: tabW,
                h: th,
                onClick: () => onSetState(key, t.id),
              })
            }
            if (active) {
              canvas.rect(tx, ty, tabW, th, col.primary, 7)
            } else if (hoveredId === tabHitId) {
              canvas.rect(tx, ty, tabW, th, col.surfaceHover, 7)
            }
            canvas.text(
              t.label,
              tx + Math.max(6, (tabW - t.label.length * 5.2) / 2),
              ty + 4,
              9.5,
              active ? col.headerNavy : col.textDarkMuted,
              'bold',
              tabW - 8
            )
          })
        }
        break
      }

      case 'card': {
        const pad = typeof node.padding === 'number' ? node.padding : 10
        const gap = node.gap ?? 6
        canvas.shadow(x, y, w, h, 10, 6, 2, col.shadow)
        canvas.rect(x, y, w, h, col.surfaceAlt, 10, 0.9, col.primaryBorder)
        let cy = y + pad
        ;(node.children ?? []).forEach((child, idx) => {
          const ch = drawNode(child, [...path, idx], x + pad, cy, w - pad * 2)
          if (ch > 0) cy += ch + gap
        })
        break
      }

      case 'column':
      case 'scroll':
      case 'stack': {
        const gap = node.gap ?? 6
        let cy = y
        ;(node.children ?? []).forEach((child, idx) => {
          const ch = drawNode(child, [...path, idx], x, cy, w)
          if (ch > 0) cy += ch + gap
        })
        break
      }

      case 'row': {
        const children = node.children ?? []
        const gap = node.gap ?? 6
        if (children.length > 0) {
          const colW = (w - gap * (children.length - 1)) / children.length
          children.forEach((child, idx) => {
            drawNode(child, [...path, idx], x + idx * (colW + gap), y, colW)
          })
        }
        break
      }

      case 'grid': {
        const children = node.children ?? []
        const cols = Math.max(1, node.columns ?? 2)
        const gap = node.gap ?? 6
        const colW = (w - gap * (cols - 1)) / cols
        let cy = y
        for (let i = 0; i < children.length; i += cols) {
          let rowH = 0
          for (let j = 0; j < cols && i + j < children.length; j++) {
            const ch = drawNode(children[i + j], [...path, i + j], x + j * (colW + gap), cy, colW)
            rowH = Math.max(rowH, ch)
          }
          cy += rowH + gap
        }
        break
      }

      case 'flow_row': {
        const gap = node.gap ?? 4
        let cx = x
        ;(node.children ?? []).forEach((child, idx) => {
          const cw = Math.min(72, Math.max(40, ((child.label || child.text || 'Tag').length + 2) * 6))
          drawNode(child, [...path, idx], cx, y, cw)
          cx += cw + gap
        })
        break
      }

      case 'accordion': {
        const key = node.state_key || 'acc_open'
        const open = Boolean(state[key] ?? true)
        canvas.rect(x, y, w, h, col.surfaceAlt, 9, 0.8, col.primaryBorder)
        if (interactionMode === 'play') {
          hitBoxes.push({
            id: `${hitId}_acc`,
            path,
            x,
            y,
            w,
            h: 32,
            onClick: () => onSetState(key, !open),
          })
        }
        canvas.text(interpolateText(node.title || 'Accordion', state), x + 10, y + 6, 10, col.textDark, 'bold', w - 30)
        if (node.subtitle) {
          canvas.text(interpolateText(node.subtitle, state), x + 10, y + 18, 8, col.textDarkMuted, 'normal', w - 30)
        }
        canvas.text(open ? '▲' : '▼', x + w - 18, y + 10, 8, col.textDarkMuted, 'bold')
        if (open) {
          canvas.rect(x + 8, y + 32, w - 16, 0.8, multiplyAlpha(col.primaryBorder, 0.5))
          let cy = y + 38
          ;(node.children ?? []).forEach((child, idx) => {
            const ch = drawNode(child, [...path, idx], x + 10, cy, w - 20)
            if (ch > 0) cy += ch + 6
          })
        }
        break
      }

      case 'list_tile': {
        if (interactionMode === 'play') {
          hitBoxes.push({ id: hitId, path, x, y, w, h, onClick: () => onRunAction(node.on_click) })
        }
        canvas.rect(x, y, w, h, isHovered ? col.surfaceHover : col.surface, 8, 0.8, col.primaryBorder)
        canvas.circle(x + 14, y + h / 2, 5, col.primary)
        canvas.text(interpolateText(node.title || 'List Item', state), x + 26, y + 5, 10, col.textDark, 'bold', w - 70)
        if (node.subtitle) {
          canvas.text(interpolateText(node.subtitle, state), x + 26, y + 18, 8.2, col.textDarkMuted, 'normal', w - 70)
        }
        if (node.trailing_text) {
          canvas.text(interpolateText(node.trailing_text, state), x + w - 42, y + 11, 9, col.primary, 'bold', 38)
        }
        break
      }

      case 'button': {
        if (interactionMode === 'play') {
          hitBoxes.push({ id: hitId, path, x, y, w, h, onClick: () => onRunAction(node.on_click) })
        }
        const bgTop =
          node.variant === 'danger'
            ? col.danger
            : node.variant === 'secondary'
              ? col.headerNavyLight
              : isHovered
                ? col.primaryHover
                : col.primary
        const fg = node.variant === 'primary' || !node.variant ? col.headerNavy : col.textPrimary
        canvas.shadow(x, y, w, h, 8, 4, 1.5, 0x30000000)
        canvas.rect(x, y, w, h, bgTop, 8)
        const label = interpolateText(node.label || 'Button', state)
        canvas.text(label, x + Math.max(8, (w - label.length * 5.6) / 2), y + 7, 10, fg, 'bold', w - 16)
        break
      }

      case 'pill': {
        if (interactionMode === 'play') {
          hitBoxes.push({ id: hitId, path, x, y, w, h, onClick: () => onRunAction(node.on_click) })
        }
        canvas.rect(x, y, w, h, isHovered ? col.surfaceHover : col.headerNavyLight, 11, 0.8, col.primaryBorder)
        canvas.text(interpolateText(node.label || 'Pill', state), x + 10, y + 4, 9, col.textSecondary, 'bold', w - 20)
        break
      }

      case 'switch': {
        const key = node.state_key || 'toggle'
        const checked = Boolean(state[key])
        if (interactionMode === 'play') {
          hitBoxes.push({ id: hitId, path, x, y, w, h, onClick: () => onSetState(key, !checked) })
        }
        canvas.text(interpolateText(node.label || 'Switch', state), x + 2, y + 5, 10, col.textDark, 'normal', w - 44)
        const sx = x + w - 34
        const sy = y + 3
        canvas.rect(sx, sy, 32, 16, checked ? col.primary : col.headerNavyLight, 8, 0.8, col.primaryBorder)
        canvas.circle(checked ? sx + 24 : sx + 8, sy + 8, 5.5, 0xffffffff)
        break
      }

      case 'checkbox': {
        const key = node.state_key || 'checked'
        const checked = Boolean(state[key])
        if (interactionMode === 'play') {
          hitBoxes.push({ id: hitId, path, x, y, w, h, onClick: () => onSetState(key, !checked) })
        }
        canvas.rect(x + 2, y + 4, 15, 15, checked ? col.primary : col.headerNavy, 4, 1, col.primaryBorder)
        if (checked) canvas.text('✓', x + 5, y + 4, 10, col.headerNavy, 'bold')
        canvas.text(interpolateText(node.label || 'Checkbox', state), x + 24, y + 5, 10, col.textDark, 'normal', w - 28)
        break
      }

      case 'slider': {
        const key = node.state_key || 'slider'
        const min = node.min ?? 0
        const max = node.max ?? 100
        const step = node.step ?? 5
        const val = Number(state[key] ?? min)
        const ratio = max > min ? Math.max(0, Math.min(1, (val - min) / (max - min))) : 0
        if (interactionMode === 'play') {
          hitBoxes.push({
            id: hitId,
            path,
            x,
            y,
            w,
            h,
            onClick: () => {
              const next = val + step > max ? min : val + step
              onSetState(key, next)
            },
          })
        }
        canvas.text(interpolateText(node.label || 'Slider', state), x + 2, y + 2, 9.5, col.textDark, 'normal', w - 34)
        canvas.text(String(val), x + w - 24, y + 2, 9.5, col.primary, 'bold', 24)
        canvas.rect(x + 2, y + 20, w - 4, 6, col.headerNavy, 3)
        canvas.rect(x + 2, y + 20, Math.max(6, (w - 4) * ratio), 6, col.primary, 3)
        canvas.circle(x + 2 + (w - 4) * ratio, y + 23, 5.5, 0xffffffff, 1.2, col.primary)
        break
      }

      case 'number_stepper': {
        const key = node.state_key || 'qty'
        const min = node.min ?? 0
        const max = node.max ?? 99
        const step = node.step ?? 1
        const val = Number(state[key] ?? min)
        canvas.text(interpolateText(node.label || 'Quantity', state), x + 2, y + 6, 10, col.textDark, 'normal', w - 80)
        const bx = x + w - 70
        if (interactionMode === 'play') {
          hitBoxes.push({ id: `${hitId}_dec`, path, x: bx, y: y + 2, w: 20, h: 20, onClick: () => onSetState(key, Math.max(min, val - step)) })
          hitBoxes.push({ id: `${hitId}_inc`, path, x: bx + 48, y: y + 2, w: 20, h: 20, onClick: () => onSetState(key, Math.min(max, val + step)) })
        }
        canvas.rect(bx, y + 2, 20, 20, col.headerNavyLight, 6, 0.8, col.primaryBorder)
        canvas.text('-', bx + 7, y + 4, 11, col.textPrimary, 'bold')
        canvas.text(String(val), bx + 28, y + 6, 10, col.primary, 'bold', 18)
        canvas.rect(bx + 48, y + 2, 20, 20, col.headerNavyLight, 6, 0.8, col.primaryBorder)
        canvas.text('+', bx + 54, y + 4, 11, col.textPrimary, 'bold')
        break
      }

      case 'segmented_control': {
        const options = node.options ?? []
        const key = node.state_key || 'mode'
        const current = String(state[key] ?? options[0]?.id ?? '')
        canvas.rect(x, y, w, h, col.headerNavy, 8, 0.8, col.primaryBorder)
        if (options.length > 0) {
          const optW = (w - 6) / options.length
          options.forEach((opt, i) => {
            const ox = x + 3 + i * optW
            const active = current === opt.id
            if (interactionMode === 'play') {
              hitBoxes.push({ id: `${hitId}_${opt.id}`, path, x: ox, y: y + 3, w: optW, h: h - 6, onClick: () => onSetState(key, opt.id) })
            }
            if (active) canvas.rect(ox, y + 3, optW, h - 6, col.primary, 6)
            canvas.text(opt.label, ox + 8, y + 6, 9, active ? col.headerNavy : col.textSecondary, 'bold', optW - 10)
          })
        }
        break
      }

      case 'text_input': {
        canvas.rect(x, y, w, h, col.headerNavy, 7, 0.9, col.primaryBorder)
        const key = node.state_key || 'input'
        const textVal = String(state[key] ?? '')
        canvas.text(textVal || node.placeholder || 'Type here...', x + 8, y + 6, 9.5, textVal ? col.textPrimary : col.textMuted, 'normal', w - 16)
        break
      }

      case 'text': {
        canvas.text(
          interpolateText(node.text || 'Text', state),
          x,
          y,
          node.size ?? 11,
          node.color ? parseColorArgb(node.color, col.textDark) : col.textDark,
          node.weight ?? 'normal',
          w
        )
        break
      }

      case 'badge': {
        const bg =
          node.variant === 'success'
            ? col.success
            : node.variant === 'danger'
              ? col.danger
              : node.variant === 'gold'
                ? col.gold
                : col.primary
        const lbl = interpolateText(node.label || 'Badge', state)
        const bw = Math.min(w, Math.max(38, lbl.length * 6.2 + 14))
        canvas.rect(x, y, bw, h, bg, 10)
        canvas.text(lbl, x + 7, y + 4, 8.5, 0xff09090b, 'bold', bw - 10)
        break
      }

      case 'stat_bar': {
        const val = Number(interpolateText(node.value ?? '50', state)) || 0
        const max = node.max_value ?? 100
        const pct = Math.max(0, Math.min(1, val / max))
        canvas.text(interpolateText(node.label || 'Stat', state), x, y, 9, col.textDarkMuted, 'normal', w - 42)
        canvas.text(interpolateText(node.value_text ?? `${Math.round(pct * 100)}%`, state), x + w - 36, y, 9, col.primary, 'bold', 36)
        canvas.rect(x, y + 15, w, 7, col.headerNavy, 3.5)
        canvas.rect(x, y + 15, Math.max(4, w * pct), 7, col.primary, 3.5)
        break
      }

      case 'progress_bar': {
        const raw = Number(interpolateText(node.progress ?? '0.5', state)) || 0
        const pct = Math.max(0, Math.min(1, raw <= 1 ? raw : raw / 100))
        canvas.rect(x, y + 1, w, 7, col.headerNavy, 3.5)
        canvas.rect(x, y + 1, Math.max(4, w * pct), 7, col.primary, 3.5)
        break
      }

      case 'pokemon_view':
      case 'item_icon':
      case 'avatar': {
        const size = node.type === 'pokemon_view' ? 44 : 34
        canvas.rect(x, y, size, size, col.headerNavy, node.type === 'avatar' ? size / 2 : 8, 1, col.primaryBorder)
        const tag =
          node.type === 'pokemon_view'
            ? (node.species || 'pikachu').split(':').pop()!.slice(0, 6)
            : node.type === 'item_icon'
              ? (node.item || 'item').split(':').pop()!.slice(0, 6)
              : 'YOU'
        canvas.text(tag, x + 5, y + size / 2 - 5, 8, col.gold, 'bold', size - 8)
        break
      }

      case 'icon':
        canvas.circle(x + 8, y + 8, 6, col.primary)
        break

      case 'image':
        canvas.rect(x, y, w, h, col.headerNavy, 8, 0.8, col.primaryBorder)
        canvas.text('Texture Image', x + 10, y + h / 2 - 6, 9, col.textMuted, 'normal', w - 20)
        break

      case 'empty_state':
        canvas.text(interpolateText(node.title || 'Empty', state), x + 12, y + 12, 11, col.textDark, 'bold', w - 24)
        if (node.description) {
          canvas.text(interpolateText(node.description, state), x + 12, y + 30, 8.8, col.textDarkMuted, 'normal', w - 24)
        }
        if (node.label) {
          if (interactionMode === 'play') {
            hitBoxes.push({ id: hitId, path, x: x + 12, y: y + 50, w: 80, h: 24, onClick: () => onRunAction(node.on_click) })
          }
          canvas.rect(x + 12, y + 50, 80, 24, col.primary, 7)
          canvas.text(node.label, x + 26, y + 56, 9.5, col.headerNavy, 'bold', 60)
        }
        break

      case 'divider':
        canvas.rect(x, y + 2, w, 0.8, multiplyAlpha(col.primaryBorder, 0.45))
        break

      case 'spacer':
        break
    }

    // Selection outline in design mode
    if (isSelected) {
      canvas.rect(x - 1.5, y - 1.5, w + 3, h + 3, 0x00000000, 9, 1.6, 0xff22d3ee)
    } else if (interactionMode === 'select' && isHovered) {
      canvas.rect(x - 1, y - 1, w + 2, h + 2, 0x00000000, 9, 1.0, 0x8822d3ee)
    }

    return h
  }

  let curY = contentY
  screen.body.forEach((node, idx) => {
    const h = drawNode(node, [idx], contentX, curY, contentW)
    if (h > 0) curY += h + 8
  })

  canvas.popClip()

  // 3. In-Phone Toast (rendered via SDF shadow + rounded rect!)
  if (phoneToast) {
    const tw = contentW - 8
    const th = 28
    const tx = contentX + 4
    const ty = contentY + contentH - th - 6
    canvas.shadow(tx, ty, tw, th, 10, 8, 2, 0x66000000)
    canvas.rect(tx, ty, tw, th, 0xf209090b, 10, 1, col.success)
    canvas.text(phoneToast.text, tx + 10, ty + 7, 9.5, col.success, 'bold', tw - 20)
  }

  return hitBoxes
}
