import {
  HdBatchWebGL,
  MODE_ALPHA_ATLAS,
  MODE_DROP_SHADOW,
  MODE_ROUNDED_RECT,
  MODE_TEXTURED_ROUNDED,
  multiplyAlpha,
} from '@/engine/hd/HdBatchWebGL'

export interface TextCacheEntry {
  tex: WebGLTexture
  w: number
  h: number
}

/**
 * Direct dp-space drawing canvas mirroring `HdDraw.kt` (`HdCanvas`) backed by `HdBatchWebGL`.
 */
export class HdCanvasWebGL {
  readonly batch: HdBatchWebGL
  scale = 2
  isPixelStyle = false
  private physH = 1

  private imageTextures = new Map<string, WebGLTexture>()
  private pendingLoads = new Set<string>()
  private textTextures = new Map<string, TextCacheEntry>()
  private clipStack: Array<{ x: number; y: number; w: number; h: number }> = []
  private onTextureLoaded?: () => void

  constructor(gl: WebGL2RenderingContext, onTextureLoaded?: () => void) {
    this.batch = new HdBatchWebGL(gl)
    this.onTextureLoaded = onTextureLoaded
  }

  begin(physW: number, physH: number, scale: number, isPixelStyle: boolean) {
    this.scale = scale
    this.isPixelStyle = isPixelStyle
    this.physH = physH
    this.clipStack = []
    this.batch.beginFrame(physW, physH)
  }

  end() {
    this.batch.flush()
    this.clipStack = []
    this.batch.gl.disable(this.batch.gl.SCISSOR_TEST)
  }

  pushClip(dpX: number, dpY: number, dpW: number, dpH: number) {
    this.batch.flush()
    const s = this.scale
    const x = Math.round(dpX * s)
    const y = Math.round(this.physH - (dpY + dpH) * s)
    const w = Math.max(0, Math.round(dpW * s))
    const h = Math.max(0, Math.round(dpH * s))
    const rect = { x, y, w, h }
    this.clipStack.push(rect)
    const gl = this.batch.gl
    gl.enable(gl.SCISSOR_TEST)
    gl.scissor(rect.x, rect.y, rect.w, rect.h)
  }

  popClip() {
    this.batch.flush()
    this.clipStack.pop()
    const gl = this.batch.gl
    if (this.clipStack.length === 0) {
      gl.disable(gl.SCISSOR_TEST)
    } else {
      const top = this.clipStack[this.clipStack.length - 1]
      gl.scissor(top.x, top.y, top.w, top.h)
    }
  }

  rect(
    x: number,
    y: number,
    w: number,
    h: number,
    color: number,
    radius = 0,
    borderW = 0,
    borderColor = 0
  ) {
    this.gradientRect(x, y, w, h, color, color, radius, borderW, borderColor)
  }

  gradientRect(
    x: number,
    y: number,
    w: number,
    h: number,
    colorTop: number,
    colorBottom: number,
    radius = 0,
    borderW = 0,
    borderColor = 0
  ) {
    const s = this.scale
    const r = this.isPixelStyle ? 0 : radius * s
    const effBorder = this.isPixelStyle && borderW > 0 ? Math.max(1.25, borderW) * s : borderW * s
    this.batch.pushQuad({
      x: x * s,
      y: y * s,
      w: w * s,
      h: h * s,
      rTL: r,
      rTR: r,
      rBR: r,
      rBL: r,
      borderW: effBorder,
      mode: MODE_ROUNDED_RECT,
      colorTop,
      colorBottom,
      borderColor,
    })
  }

  shadow(
    x: number,
    y: number,
    w: number,
    h: number,
    radius: number,
    blur = 8,
    offsetY = 2,
    color = 0x3a000000
  ) {
    if (this.isPixelStyle) {
      const hard = multiplyAlpha(color, 0.7)
      this.rect(x + 2, y + Math.max(1.5, offsetY), w, h, hard, 0)
      return
    }
    const s = this.scale
    const pad = blur
    const r = radius * s
    this.batch.pushQuad({
      x: (x - pad) * s,
      y: (y - pad + offsetY) * s,
      w: (w + pad * 2) * s,
      h: (h + pad * 2) * s,
      rTL: r,
      rTR: r,
      rBR: r,
      rBL: r,
      softness: blur * s,
      mode: MODE_DROP_SHADOW,
      colorTop: color,
    })
  }

  circle(cx: number, cy: number, radius: number, color: number, borderW = 0, borderColor = 0) {
    this.rect(cx - radius, cy - radius, radius * 2, radius * 2, color, radius, borderW, borderColor)
  }

  image(
    url: string,
    x: number,
    y: number,
    w: number,
    h: number,
    radius = 0,
    nearest = true,
    tint = 0xffffffff
  ) {
    this.subImage(url, x, y, w, h, 0, 0, 1, 1, radius, nearest, tint)
  }

  subImage(
    url: string,
    x: number,
    y: number,
    w: number,
    h: number,
    u0 = 0,
    v0 = 0,
    u1 = 1,
    v1 = 1,
    radius = 0,
    nearest = true,
    tint = 0xffffffff
  ) {
    const tex = this.getOrLoadTexture(url, nearest)
    if (!tex) return
    const s = this.scale
    const r = this.isPixelStyle ? 0 : radius * s
    this.batch.pushQuad({
      x: x * s,
      y: y * s,
      w: w * s,
      h: h * s,
      u0,
      v0,
      u1,
      v1,
      rTL: r,
      rTR: r,
      rBR: r,
      rBL: r,
      mode: MODE_TEXTURED_ROUNDED,
      colorTop: tint,
      texture: tex,
    })
  }

  /**
   * Renders crisp text via Canvas2D rasterized into a cached alpha-mask WebGL texture
   * drawn with `MODE_ALPHA_ATLAS` (exact same shader path as `HdFontAtlas`).
   */
  text(
    str: string,
    x: number,
    y: number,
    sizeDp: number,
    color: number,
    weight: 'normal' | 'medium' | 'bold' = 'normal',
    maxWidthDp?: number
  ) {
    if (!str) return
    const fontWeight = weight === 'bold' ? '700' : weight === 'medium' ? '600' : '400'
    const fontFamily = this.isPixelStyle
      ? 'monospace'
      : 'Inter, system-ui, -apple-system, BlinkMacSystemFont, sans-serif'
    const pxSize = Math.max(8, Math.round(sizeDp * this.scale))
    const key = `${fontWeight}|${pxSize}|${fontFamily}|${maxWidthDp ?? 0}|${str}`

    let entry = this.textTextures.get(key)
    if (!entry) {
      if (this.textTextures.size > 400) {
        // Evict oldest to keep GPU memory bounded
        const firstKey = this.textTextures.keys().next().value
        if (firstKey) {
          this.batch.gl.deleteTexture(this.textTextures.get(firstKey)!.tex)
          this.textTextures.delete(firstKey)
        }
      }
      const off = document.createElement('canvas')
      const octx = off.getContext('2d')!
      const fontSpec = `${fontWeight} ${pxSize}px ${fontFamily}`
      octx.font = fontSpec
      const measured = Math.ceil(octx.measureText(str).width)
      const capW = maxWidthDp ? Math.min(measured, Math.ceil(maxWidthDp * this.scale)) : measured
      const cw = Math.max(2, capW + 4)
      const ch = Math.max(2, Math.ceil(pxSize * 1.35))
      off.width = cw
      off.height = ch
      octx.font = fontSpec
      octx.textBaseline = 'top'
      octx.fillStyle = '#ffffff'
      octx.fillText(str, 1, Math.round(pxSize * 0.14))

      const gl = this.batch.gl
      const tex = gl.createTexture()!
      gl.bindTexture(gl.TEXTURE_2D, tex)
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, off)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, this.isPixelStyle ? gl.NEAREST : gl.LINEAR)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, this.isPixelStyle ? gl.NEAREST : gl.LINEAR)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)

      entry = { tex, w: cw, h: ch }
      this.textTextures.set(key, entry)
    }

    this.batch.pushQuad({
      x: x * this.scale,
      y: y * this.scale,
      w: entry.w,
      h: entry.h,
      mode: MODE_ALPHA_ATLAS,
      colorTop: color,
      texture: entry.tex,
    })
  }

  private getOrLoadTexture(url: string, nearest: boolean): WebGLTexture | null {
    const key = `${url}|${nearest ? 'N' : 'L'}`
    const existing = this.imageTextures.get(key)
    if (existing) return existing
    if (this.pendingLoads.has(key)) return null

    this.pendingLoads.add(key)
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => {
      this.pendingLoads.delete(key)
      const gl = this.batch.gl
      const tex = gl.createTexture()
      if (!tex) return
      gl.bindTexture(gl.TEXTURE_2D, tex)
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, nearest ? gl.NEAREST : gl.LINEAR)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, nearest ? gl.NEAREST : gl.LINEAR)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
      this.imageTextures.set(key, tex)
      this.onTextureLoaded?.()
    }
    img.onerror = () => {
      this.pendingLoads.delete(key)
    }
    img.src = url
    return null
  }

  dispose() {
    const gl = this.batch.gl
    for (const t of this.imageTextures.values()) gl.deleteTexture(t)
    for (const e of this.textTextures.values()) gl.deleteTexture(e.tex)
    this.imageTextures.clear()
    this.textTextures.clear()
    this.batch.dispose()
  }
}
