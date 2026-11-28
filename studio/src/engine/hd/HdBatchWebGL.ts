/**
 * Direct WebGL2 (`#version 300 es`) port of `HdShader.kt` (`HdBatch`) from Cobblemon Smartphone.
 * Runs the EXACT same SDF fragment shader (rounded rects, vertical gradients, anti-aliased
 * borders, soft drop shadows, rounded textures, and alpha-atlas text/icons) on the browser GPU.
 */

export const MODE_ROUNDED_RECT = 0
export const MODE_DROP_SHADOW = 1
export const MODE_TEXTURED_ROUNDED = 2
export const MODE_ALPHA_ATLAS = 3

const MAX_QUADS = 2048
const FLOATS_PER_VERTEX = 28
const VERTICES_PER_QUAD = 4
const INDICES_PER_QUAD = 6
const STRIDE_BYTES = FLOATS_PER_VERTEX * 4

const VERTEX_SHADER = `#version 300 es
precision highp float;

uniform mat4 uProjection;

layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aLocalNorm;
layout(location = 2) in vec2 aTexUv;
layout(location = 3) in vec2 aRectSize;
layout(location = 4) in vec4 aRadii;
layout(location = 5) in vec4 aParams;
layout(location = 6) in vec4 aColorTop;
layout(location = 7) in vec4 aColorBot;
layout(location = 8) in vec4 aBorderCol;

out vec2 vLocalNorm;
out vec2 vTexUv;
out vec2 vRectSize;
out vec4 vRadii;
out vec4 vParams;
out vec4 vColorTop;
out vec4 vColorBot;
out vec4 vBorderCol;

void main() {
    gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
    vLocalNorm = aLocalNorm;
    vTexUv = aTexUv;
    vRectSize = aRectSize;
    vRadii = aRadii;
    vParams = aParams;
    vColorTop = aColorTop;
    vColorBot = aColorBot;
    vBorderCol = aBorderCol;
}
`

// Verbatim fragment shader from HdShader.kt (only #version 150 core -> #version 300 es + precision)
const FRAGMENT_SHADER = `#version 300 es
precision highp float;

uniform sampler2D uTexture;

in vec2 vLocalNorm;
in vec2 vTexUv;
in vec2 vRectSize;
in vec4 vRadii;
in vec4 vParams;
in vec4 vColorTop;
in vec4 vColorBot;
in vec4 vBorderCol;

out vec4 fragColor;

float sdRoundedBox(vec2 p, vec2 b, vec4 r) {
    vec2 topOrBot = (p.x > 0.0) ? vec2(r.y, r.z) : vec2(r.x, r.w);
    float rad = (p.y > 0.0) ? topOrBot.y : topOrBot.x;
    vec2 q = abs(p) - b + rad;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rad;
}

void main() {
    float borderW = vParams.x;
    float softness = max(vParams.y, 0.001);
    float mode = vParams.z;

    if (mode > 2.5) {
        float coverage = texture(uTexture, vTexUv).a;
        if (coverage <= 0.002) discard;
        fragColor = vec4(vColorTop.rgb, vColorTop.a * coverage);
        return;
    }

    vec2 halfSize = vRectSize * 0.5;
    vec2 p = (vLocalNorm - 0.5) * vRectSize;
    float maxRad = min(halfSize.x, halfSize.y);
    vec4 radii = clamp(vRadii, vec4(0.0), vec4(maxRad));

    if (mode > 1.5) {
        vec4 tex = texture(uTexture, vTexUv) * vColorTop;
        if (max(max(radii.x, radii.y), max(radii.z, radii.w)) > 0.0 || borderW > 0.0) {
            float d = sdRoundedBox(p, halfSize, radii);
            float outerAlpha = 1.0 - smoothstep(-0.65, 0.65, d);
            if (outerAlpha <= 0.001) discard;
            if (borderW > 0.0) {
                float borderMask = smoothstep(-borderW - 0.65, -borderW + 0.65, d);
                tex = mix(tex, vBorderCol, borderMask);
            }
            fragColor = vec4(tex.rgb, tex.a * outerAlpha);
        } else {
            fragColor = tex;
        }
        return;
    }

    if (mode > 0.5) {
        vec2 innerHalf = max(halfSize - vec2(softness), vec2(1.0));
        vec4 shadowRadii = clamp(vRadii, vec4(0.0), vec4(min(innerHalf.x, innerHalf.y)));
        float d = sdRoundedBox(p, innerHalf, shadowRadii);
        float t = clamp(1.0 - smoothstep(-softness * 0.35, softness, d), 0.0, 1.0);
        float alpha = vColorTop.a * (t * t);
        if (alpha <= 0.002) discard;
        fragColor = vec4(vColorTop.rgb, alpha);
        return;
    }

    float d = sdRoundedBox(p, halfSize, radii);
    float outerAlpha = 1.0 - smoothstep(-0.65, 0.65, d);
    if (outerAlpha <= 0.001) discard;

    vec4 fillCol = mix(vColorTop, vColorBot, clamp(vLocalNorm.y, 0.0, 1.0));
    if (borderW > 0.0) {
        float borderMask = smoothstep(-borderW - 0.65, -borderW + 0.65, d);
        if (fillCol.a <= 0.001) {
            fillCol.rgb = vBorderCol.rgb;
        }
        vec4 col = mix(fillCol, vBorderCol, borderMask);
        fragColor = vec4(col.rgb, col.a * outerAlpha);
    } else {
        fragColor = vec4(fillCol.rgb, fillCol.a * outerAlpha);
    }
}
`

export interface PushQuadArgs {
  x: number
  y: number
  w: number
  h: number
  u0?: number
  v0?: number
  u1?: number
  v1?: number
  rTL?: number
  rTR?: number
  rBR?: number
  rBL?: number
  borderW?: number
  softness?: number
  mode?: number
  colorTop: number
  colorBottom?: number
  borderColor?: number
  texture?: WebGLTexture | null
}

export function parseColorArgb(hex: string | undefined, fallback = 0xffffffff): number {
  if (!hex) return fallback
  const clean = hex.replace('#', '').trim()
  if (clean.length === 6) {
    const rgb = parseInt(clean, 16)
    return Number.isNaN(rgb) ? fallback : ((0xff000000 | rgb) >>> 0)
  }
  if (clean.length === 8) {
    const argb = parseInt(clean, 16)
    return Number.isNaN(argb) ? fallback : (argb >>> 0)
  }
  return fallback
}

export function multiplyAlpha(argb: number, factor: number): number {
  const a = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xff) * factor)))
  return (((a << 24) | (argb & 0x00ffffff)) >>> 0)
}

export class HdBatchWebGL {
  readonly gl: WebGL2RenderingContext
  private program: WebGLProgram
  private vao: WebGLVertexArrayObject
  private vbo: WebGLBuffer
  private ebo: WebGLBuffer
  private uProjectionLoc: WebGLUniformLocation | null
  private uTextureLoc: WebGLUniformLocation | null
  private dummyTex: WebGLTexture

  private vertices = new Float32Array(MAX_QUADS * VERTICES_PER_QUAD * FLOATS_PER_VERTEX)
  private vertexCursor = 0
  private quadCount = 0
  private currentTexture: WebGLTexture | null = null
  private activeW = 1
  private activeH = 1

  constructor(gl: WebGL2RenderingContext) {
    this.gl = gl
    const vert = this.compile(gl.VERTEX_SHADER, VERTEX_SHADER)
    const frag = this.compile(gl.FRAGMENT_SHADER, FRAGMENT_SHADER)
    const prog = gl.createProgram()!
    gl.attachShader(prog, vert)
    gl.attachShader(prog, frag)
    gl.linkProgram(prog)
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
      const log = gl.getProgramInfoLog(prog)
      throw new Error(`HdBatchWebGL link error: ${log}`)
    }
    gl.deleteShader(vert)
    gl.deleteShader(frag)

    this.program = prog
    this.uProjectionLoc = gl.getUniformLocation(prog, 'uProjection')
    this.uTextureLoc = gl.getUniformLocation(prog, 'uTexture')

    this.vao = gl.createVertexArray()!
    this.vbo = gl.createBuffer()!
    this.ebo = gl.createBuffer()!

    gl.bindVertexArray(this.vao)
    gl.bindBuffer(gl.ARRAY_BUFFER, this.vbo)
    gl.bufferData(gl.ARRAY_BUFFER, this.vertices.byteLength, gl.STREAM_DRAW)

    const indices = new Uint32Array(MAX_QUADS * INDICES_PER_QUAD)
    for (let q = 0; q < MAX_QUADS; q++) {
      const base = q * 4
      const off = q * 6
      indices[off + 0] = base + 0
      indices[off + 1] = base + 1
      indices[off + 2] = base + 2
      indices[off + 3] = base + 2
      indices[off + 4] = base + 3
      indices[off + 5] = base + 0
    }
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this.ebo)
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indices, gl.STATIC_DRAW)

    const attrib = (index: number, size: number, offsetFloats: number) => {
      gl.enableVertexAttribArray(index)
      gl.vertexAttribPointer(index, size, gl.FLOAT, false, STRIDE_BYTES, offsetFloats * 4)
    }
    attrib(0, 2, 0)
    attrib(1, 2, 2)
    attrib(2, 2, 4)
    attrib(3, 2, 6)
    attrib(4, 4, 8)
    attrib(5, 4, 12)
    attrib(6, 4, 16)
    attrib(7, 4, 20)
    attrib(8, 4, 24)

    gl.bindVertexArray(null)

    // 1x1 white dummy texture when no texture is bound
    this.dummyTex = gl.createTexture()!
    gl.bindTexture(gl.TEXTURE_2D, this.dummyTex)
    gl.texImage2D(
      gl.TEXTURE_2D,
      0,
      gl.RGBA,
      1,
      1,
      0,
      gl.RGBA,
      gl.UNSIGNED_BYTE,
      new Uint8Array([255, 255, 255, 255])
    )
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST)
  }

  private compile(type: number, src: string): WebGLShader {
    const gl = this.gl
    const shader = gl.createShader(type)!
    gl.shaderSource(shader, src)
    gl.compileShader(shader)
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      const log = gl.getShaderInfoLog(shader)
      gl.deleteShader(shader)
      throw new Error(`HdBatchWebGL shader compile error: ${log}`)
    }
    return shader
  }

  beginFrame(width: number, height: number) {
    this.activeW = Math.max(1, width)
    this.activeH = Math.max(1, height)
    this.vertexCursor = 0
    this.quadCount = 0
    this.currentTexture = null

    const gl = this.gl
    gl.viewport(0, 0, this.activeW, this.activeH)
    gl.disable(gl.SCISSOR_TEST)
    gl.clearColor(0, 0, 0, 0)
    gl.clear(gl.COLOR_BUFFER_BIT)
  }

  pushQuad(args: PushQuadArgs) {
    const {
      x,
      y,
      w,
      h,
      u0 = 0,
      v0 = 0,
      u1 = 1,
      v1 = 1,
      rTL = 0,
      rTR = 0,
      rBR = 0,
      rBL = 0,
      borderW = 0,
      softness = 0,
      mode = MODE_ROUNDED_RECT,
      colorTop,
      colorBottom = colorTop,
      borderColor = 0,
      texture = null,
    } = args

    if (w <= 0 || h <= 0) return
    if (texture !== this.currentTexture && this.quadCount > 0) {
      this.flush()
    }
    if (this.quadCount >= MAX_QUADS) {
      this.flush()
    }
    this.currentTexture = texture

    const topR = ((colorTop >>> 16) & 0xff) / 255
    const topG = ((colorTop >>> 8) & 0xff) / 255
    const topB = (colorTop & 0xff) / 255
    const topA = ((colorTop >>> 24) & 0xff) / 255

    const botR = ((colorBottom >>> 16) & 0xff) / 255
    const botG = ((colorBottom >>> 8) & 0xff) / 255
    const botB = (colorBottom & 0xff) / 255
    const botA = ((colorBottom >>> 24) & 0xff) / 255

    const brdR = ((borderColor >>> 16) & 0xff) / 255
    const brdG = ((borderColor >>> 8) & 0xff) / 255
    const brdB = (borderColor & 0xff) / 255
    const brdA = ((borderColor >>> 24) & 0xff) / 255

    const buf = this.vertices
    let c = this.vertexCursor
    const put = (vx: number, vy: number, lx: number, ly: number, u: number, v: number) => {
      buf[c++] = vx
      buf[c++] = vy
      buf[c++] = lx
      buf[c++] = ly
      buf[c++] = u
      buf[c++] = v
      buf[c++] = w
      buf[c++] = h
      buf[c++] = rTL
      buf[c++] = rTR
      buf[c++] = rBR
      buf[c++] = rBL
      buf[c++] = borderW
      buf[c++] = softness
      buf[c++] = mode
      buf[c++] = 0
      buf[c++] = topR
      buf[c++] = topG
      buf[c++] = topB
      buf[c++] = topA
      buf[c++] = botR
      buf[c++] = botG
      buf[c++] = botB
      buf[c++] = botA
      buf[c++] = brdR
      buf[c++] = brdG
      buf[c++] = brdB
      buf[c++] = brdA
    }

    put(x, y, 0, 0, u0, v0)
    put(x + w, y, 1, 0, u1, v0)
    put(x + w, y + h, 1, 1, u1, v1)
    put(x, y + h, 0, 1, u0, v1)
    this.vertexCursor = c
    this.quadCount++
  }

  flush() {
    if (this.quadCount === 0) return
    const gl = this.gl

    gl.enable(gl.BLEND)
    gl.blendFuncSeparate(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA, gl.ONE, gl.ONE_MINUS_SRC_ALPHA)
    gl.disable(gl.DEPTH_TEST)
    gl.disable(gl.CULL_FACE)

    gl.useProgram(this.program)

    // Ortho(0, W, H, 0, -1, 1) in column-major order
    const W = this.activeW
    const H = this.activeH
    const ortho = new Float32Array([
      2 / W, 0, 0, 0,
      0, -2 / H, 0, 0,
      0, 0, -1, 0,
      -1, 1, 0, 1,
    ])
    gl.uniformMatrix4fv(this.uProjectionLoc, false, ortho)
    gl.uniform1i(this.uTextureLoc, 0)

    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, this.currentTexture ?? this.dummyTex)

    gl.bindVertexArray(this.vao)
    gl.bindBuffer(gl.ARRAY_BUFFER, this.vbo)
    gl.bufferSubData(gl.ARRAY_BUFFER, 0, this.vertices.subarray(0, this.vertexCursor))
    gl.drawElements(gl.TRIANGLES, this.quadCount * INDICES_PER_QUAD, gl.UNSIGNED_INT, 0)

    gl.bindVertexArray(null)
    this.vertexCursor = 0
    this.quadCount = 0
  }

  dispose() {
    const gl = this.gl
    gl.deleteProgram(this.program)
    gl.deleteVertexArray(this.vao)
    gl.deleteBuffer(this.vbo)
    gl.deleteBuffer(this.ebo)
    gl.deleteTexture(this.dummyTex)
  }
}
