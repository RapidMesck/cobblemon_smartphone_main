import { useEffect, useRef, useState } from 'react'
import * as THREE from 'three'
import { Grid3x3, RotateCw } from 'lucide-react'
import type { SmartphoneColorId } from '@/lib/utils'

interface BlockbenchFace {
  uv?: [number, number, number, number]
  texture?: string
  rotation?: number
}

interface BlockbenchElement {
  from?: [number, number, number]
  to?: [number, number, number]
  rotation?: {
    origin?: [number, number, number]
    axis?: 'x' | 'y' | 'z'
    angle?: number
    rescale?: boolean
  }
  faces?: Partial<Record<'north' | 'south' | 'east' | 'west' | 'up' | 'down', BlockbenchFace>>
}

interface BlockbenchModelJson {
  parent?: string
  texture_size?: [number, number]
  textures?: Record<string, string>
  elements?: BlockbenchElement[]
}

/**
 * Resolves Minecraft's `parent` inheritance chain (e.g. `cobblemon_smartphone:item/red_smartphone_3d`)
 * so child models that only override `textures` inherit `elements` and `texture_size`.
 */
async function resolveModelParentChain(
  model: BlockbenchModelJson,
  depth = 0
): Promise<{ resolved: BlockbenchModelJson; parentUsed?: string }> {
  if (!model.parent || depth > 6) return { resolved: model }

  const parentRef = model.parent.trim()
  let parentUrl: string | null = null
  if (parentRef.startsWith('cobblemon_smartphone:')) {
    const p = parentRef.slice('cobblemon_smartphone:'.length).replace(/\.json$/i, '')
    parentUrl = `/mod-assets/models/${p}.json`
  }
  if (!parentUrl) return { resolved: model }

  try {
    const res = await fetch(parentUrl)
    if (!res.ok) return { resolved: model }
    const parentJson = (await res.json()) as BlockbenchModelJson
    const { resolved: ancestor } = await resolveModelParentChain(parentJson, depth + 1)
    return {
      parentUsed: parentRef,
      resolved: {
        ...ancestor,
        ...model,
        texture_size: model.texture_size ?? ancestor.texture_size,
        textures: { ...(ancestor.textures ?? {}), ...(model.textures ?? {}) },
        elements: model.elements && model.elements.length > 0 ? model.elements : ancestor.elements,
      },
    }
  } catch {
    return { resolved: model }
  }
}

/**
 * Computes default Minecraft UVs when `face.uv` is omitted (Minecraft ModelBakery behavior).
 */
function computeDefaultMinecraftUv(
  dir: 'east' | 'west' | 'up' | 'down' | 'south' | 'north',
  from: [number, number, number],
  to: [number, number, number]
): [number, number, number, number] {
  const [x1, y1, z1] = from
  const [x2, y2, z2] = to
  switch (dir) {
    case 'north': return [16 - x2, 16 - y2, 16 - x1, 16 - y1]
    case 'south': return [x1, 16 - y2, x2, 16 - y1]
    case 'west': return [z1, 16 - y2, z2, 16 - y1]
    case 'east': return [16 - z2, 16 - y2, 16 - z1, 16 - y1]
    case 'up': return [x1, z1, x2, z2]
    case 'down': return [x1, 16 - z2, x2, 16 - z1]
  }
}

interface Props {
  modelJsonText?: string | null
  customTextures?: Record<string, string>
  variant: 'hand' | 'rotom'
  casingColor: SmartphoneColorId
  casingHex: string
}

const textureLoader = new THREE.TextureLoader()
const texCache = new Map<string, THREE.Texture>()
let missingTexSingleton: THREE.DataTexture | null = null

function getMissingCheckerTexture(): THREE.DataTexture {
  if (missingTexSingleton) return missingTexSingleton
  // Classic 2x2 Minecraft purple/black missing texture
  const data = new Uint8Array([
    248, 0, 248, 255, 0, 0, 0, 255,
    0, 0, 0, 255, 248, 0, 248, 255,
  ])
  const tex = new THREE.DataTexture(data, 2, 2, THREE.RGBAFormat)
  tex.magFilter = THREE.NearestFilter
  tex.minFilter = THREE.NearestFilter
  tex.wrapS = THREE.RepeatWrapping
  tex.wrapT = THREE.RepeatWrapping
  tex.colorSpace = THREE.SRGBColorSpace
  tex.needsUpdate = true
  missingTexSingleton = tex
  return tex
}

function resolveModTextureUrl(ref: string): string | null {
  if (!ref) return null
  if (ref.startsWith('cobblemon_smartphone:')) {
    const p = ref.slice('cobblemon_smartphone:'.length)
    return `/mod-assets/textures/${p}.png`
  }
  return null
}

function getPixelTexture(url: string): THREE.Texture {
  const existing = texCache.get(url)
  if (existing) return existing
  const tex = textureLoader.load(url)
  tex.magFilter = THREE.NearestFilter
  tex.minFilter = THREE.NearestFilter
  tex.generateMipmaps = false
  tex.colorSpace = THREE.SRGBColorSpace
  texCache.set(url, tex)
  return tex
}

/**
 * Sets per-face Minecraft UV coordinates on a Three.js BoxGeometry.
 * Three.js BoxGeometry face order: 0:+x (east), 1:-x (west), 2:+y (up), 3:-y (down), 4:+z (south), 5:-z (north)
 */
const BOX_FACE_ORDER: Array<'east' | 'west' | 'up' | 'down' | 'south' | 'north'> = [
  'east',
  'west',
  'up',
  'down',
  'south',
  'north',
]

function applyFaceUvs(
  geom: THREE.BoxGeometry,
  faceIdx: number,
  uv: [number, number, number, number],
  rotationDeg = 0
) {
  // Minecraft model UVs are always in 0..16 space regardless of texture resolution
  const u1 = uv[0] / 16
  const v1 = 1 - uv[1] / 16
  const u2 = uv[2] / 16
  const v2 = 1 - uv[3] / 16

  // Default quad vertex order in Three.js BoxGeometry per face:
  // 0: top-left, 1: top-right, 2: bottom-left, 3: bottom-right
  let corners: Array<[number, number]> = [
    [u1, v1],
    [u2, v1],
    [u1, v2],
    [u2, v2],
  ]

  const steps = ((rotationDeg / 90) % 4 + 4) % 4
  for (let s = 0; s < steps; s++) {
    // 90 deg clockwise rotation on [TL, TR, BL, BR] -> [BL, TL, BR, TR]
    corners = [corners[2], corners[0], corners[3], corners[1]]
  }

  const uvAttr = geom.attributes.uv as THREE.BufferAttribute
  const base = faceIdx * 4
  for (let i = 0; i < 4; i++) {
    uvAttr.setXY(base + i, corners[i][0], corners[i][1])
  }
  uvAttr.needsUpdate = true
}

function resolveTextureKey(
  rawRef: string | undefined,
  textures: Record<string, string>
): string | null {
  if (!rawRef) return null
  let cur = rawRef
  for (let depth = 0; depth < 6; depth++) {
    if (!cur.startsWith('#')) return cur
    const next = textures[cur.slice(1)]
    if (!next) return null
    cur = next
  }
  return null
}

// Exact vanilla Minecraft item lighting from assets/minecraft/shaders/include/light.glsl
const MC_VERTEX_SHADER = `
varying vec2 vUv;
varying vec3 vNormal;

void main() {
  vUv = uv;
  vNormal = normalize(normalMatrix * normal);
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`

const MC_FRAGMENT_SHADER = `
uniform sampler2D uMap;
uniform bool uHasMap;
uniform vec3 uColor;

varying vec2 vUv;
varying vec3 vNormal;

void main() {
  vec4 base = uHasMap ? texture2D(uMap, vUv) : vec4(uColor, 1.0);
  if (base.a < 0.1) discard;

  // Minecraft Lighting.setupFor3DItems() two-light diffuse formula
  vec3 n = normalize(gl_FrontFacing ? vNormal : -vNormal);
  vec3 lightDir0 = normalize(vec3(0.2, 1.0, -0.7));
  vec3 lightDir1 = normalize(vec3(-0.2, 1.0, 0.7));
  float light0 = max(0.0, dot(lightDir0, n));
  float light1 = max(0.0, dot(lightDir1, n));
  float shade = min(1.0, (light0 + light1) * 0.6 + 0.4);

  gl_FragColor = vec4(base.rgb * shade, base.a);
  #include <colorspace_fragment>
}
`

function createMinecraftMaterial(
  tex: THREE.Texture | null,
  fallbackHex: string,
  wireframe: boolean
): THREE.ShaderMaterial {
  return new THREE.ShaderMaterial({
    vertexShader: MC_VERTEX_SHADER,
    fragmentShader: MC_FRAGMENT_SHADER,
    uniforms: {
      uMap: { value: tex },
      uHasMap: { value: tex !== null },
      uColor: { value: new THREE.Color(fallbackHex) },
    },
    side: THREE.DoubleSide,
    transparent: true,
    wireframe,
  })
}

function buildMinecraftModelGroup(
  model: BlockbenchModelJson,
  customTextures: Record<string, string>,
  fallbackHex: string,
  wireframe: boolean
): { group: THREE.Group; elementCount: number } {
  const group = new THREE.Group()
  const elements = model.elements ?? []
  const textures = model.textures ?? {}
  const materialMap = new Map<string, THREE.ShaderMaterial>()

  const getFaceMaterial = (face: BlockbenchFace | undefined): THREE.Material => {
    if (!face || !face.texture) {
      return new THREE.MeshBasicMaterial({ visible: false })
    }
    const rawKey = face.texture.replace(/^#/, '')
    const resolved = resolveTextureKey(face.texture, textures)
    const customUrl =
      (resolved ? customTextures[resolved] : undefined) ??
      customTextures[rawKey] ??
      null
    const url = customUrl ?? (resolved ? resolveModTextureUrl(resolved) : null)
    if (!url) {
      const key = `missing_${wireframe}`
      if (!materialMap.has(key)) {
        materialMap.set(key, createMinecraftMaterial(getMissingCheckerTexture(), fallbackHex, wireframe))
      }
      return materialMap.get(key)!
    }

    const key = `${url}_${wireframe}`
    if (!materialMap.has(key)) {
      const tex = getPixelTexture(url)
      materialMap.set(key, createMinecraftMaterial(tex, fallbackHex, wireframe))
    }
    return materialMap.get(key)!
  }

  const scale = 1 / 4 // 16 MC units = 4 world units

  for (const el of elements) {
    if (!el.from || !el.to) continue
    const [fx, fy, fz] = el.from
    const [tx, ty, tz] = el.to
    const w = Math.max(0.002, Math.abs(tx - fx) * scale)
    const h = Math.max(0.002, Math.abs(ty - fy) * scale)
    const d = Math.max(0.002, Math.abs(tz - fz) * scale)

    const geom = new THREE.BoxGeometry(w, h, d)
    const materials: THREE.Material[] = BOX_FACE_ORDER.map((dir, faceIdx) => {
      const face = el.faces?.[dir]
      if (!face) return new THREE.MeshBasicMaterial({ visible: false })
      const uv = face.uv ?? computeDefaultMinecraftUv(dir, [fx, fy, fz], [tx, ty, tz])
      if (uv[0] === uv[2] || uv[1] === uv[3]) {
        return new THREE.MeshBasicMaterial({ visible: false })
      }
      applyFaceUvs(geom, faceIdx, uv, face.rotation ?? 0)
      return getFaceMaterial(face)
    })

    const mesh = new THREE.Mesh(geom, materials)
    const cx = ((fx + tx) / 2) * scale
    const cy = ((fy + ty) / 2) * scale
    const cz = ((fz + tz) / 2) * scale

    if (el.rotation?.angle && el.rotation.origin) {
      const pivot = new THREE.Group()
      const [ox, oy, oz] = el.rotation.origin
      const px = ox * scale
      const py = oy * scale
      const pz = oz * scale
      pivot.position.set(px, py, pz)
      mesh.position.set(cx - px, cy - py, cz - pz)
      const rad = THREE.MathUtils.degToRad(el.rotation.angle)
      const rescaleFactor = el.rotation.rescale ? 1 / Math.max(0.01, Math.cos(rad)) : 1
      if (el.rotation.axis === 'x') {
        pivot.rotation.x = rad
        if (el.rotation.rescale) pivot.scale.set(1, rescaleFactor, rescaleFactor)
      }
      if (el.rotation.axis === 'y') {
        pivot.rotation.y = rad
        if (el.rotation.rescale) pivot.scale.set(rescaleFactor, 1, rescaleFactor)
      }
      if (el.rotation.axis === 'z') {
        pivot.rotation.z = rad
        if (el.rotation.rescale) pivot.scale.set(rescaleFactor, rescaleFactor, 1)
      }
      pivot.add(mesh)
      group.add(pivot)
    } else {
      mesh.position.set(cx, cy, cz)
      group.add(mesh)
    }
  }

  // Center the model cleanly at the origin and normalize scale
  const box = new THREE.Box3().setFromObject(group)
  if (!box.isEmpty()) {
    const center = box.getCenter(new THREE.Vector3())
    const size = box.getSize(new THREE.Vector3())
    const maxDim = Math.max(size.x, size.y, size.z, 0.1)
    const targetSize = 2.2
    const norm = targetSize / maxDim

    const wrapper = new THREE.Group()
    group.position.set(-center.x, -center.y, -center.z)
    wrapper.add(group)
    wrapper.scale.setScalar(norm)
    return { group: wrapper, elementCount: elements.length }
  }

  return { group, elementCount: elements.length }
}

export function BlockbenchModelViewer({
  modelJsonText,
  customTextures = {},
  variant,
  casingColor,
  casingHex,
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const [autoRotate, setAutoRotate] = useState(true)
  const [wireframe, setWireframe] = useState(false)
  const [modelMeta, setModelMeta] = useState<{ label: string; cubes: number }>({
    label: 'Loading 3D model...',
    cubes: 0,
  })

  const rotRef = useRef({ x: 0.18, y: Math.PI + 0.45, zoom: 4.3, dragging: false, lastX: 0, lastY: 0 })
  const autoRotateRef = useRef(autoRotate)
  autoRotateRef.current = autoRotate

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    let cancelled = false
    let rafId = 0
    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(38, container.clientWidth / container.clientHeight, 0.1, 100)
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.setSize(container.clientWidth, container.clientHeight)
    renderer.toneMapping = THREE.NoToneMapping
    renderer.outputColorSpace = THREE.SRGBColorSpace
    container.innerHTML = ''
    container.appendChild(renderer.domElement)

    const grid = new THREE.GridHelper(6, 12, 0x3f3f46, 0x27272a)
    grid.position.y = -1.3
    scene.add(grid)

    const loadModel = async (): Promise<{ json: BlockbenchModelJson; label: string } | null> => {
      if (modelJsonText) {
        try {
          const parsed = JSON.parse(modelJsonText.replace(/^\uFEFF/, '')) as BlockbenchModelJson
          const { resolved, parentUsed } = await resolveModelParentChain(parsed)
          return {
            json: resolved,
            label: parentUsed ? `Custom (parent: ${parentUsed.split('/').pop()})` : 'Custom Blockbench JSON',
          }
        } catch {
          // Fall through to built-in model
        }
      }
      const modModelUrl =
        variant === 'rotom'
          ? `/mod-assets/models/item/rotom_${casingColor}.json`
          : `/mod-assets/models/item/${casingColor}_smartphone_3d.json`
      try {
        const res = await fetch(modModelUrl)
        if (!res.ok) return null
        const json = (await res.json()) as BlockbenchModelJson
        const { resolved } = await resolveModelParentChain(json)
        return {
          json: resolved,
          label: variant === 'rotom' ? `In-Game rotom_${casingColor}.json` : `In-Game ${casingColor}_smartphone_3d.json`,
        }
      } catch {
        return null
      }
    }

    let rootModel: THREE.Group | null = null

    loadModel().then((loaded) => {
      if (cancelled || !loaded) return
      const built = buildMinecraftModelGroup(loaded.json, customTextures, casingHex, wireframe)
      rootModel = built.group
      scene.add(rootModel)
      setModelMeta({ label: loaded.label, cubes: built.elementCount })
    })

    const animate = () => {
      rafId = requestAnimationFrame(animate)
      if (autoRotateRef.current && !rotRef.current.dragging) {
        rotRef.current.y += 0.008
      }
      if (rootModel) {
        rootModel.rotation.x = rotRef.current.x
        rootModel.rotation.y = rotRef.current.y
      }
      camera.position.set(0, 0.2, rotRef.current.zoom)
      camera.lookAt(0, 0, 0)
      renderer.render(scene, camera)
    }
    animate()

    const handleResize = () => {
      if (!container) return
      camera.aspect = container.clientWidth / container.clientHeight
      camera.updateProjectionMatrix()
      renderer.setSize(container.clientWidth, container.clientHeight)
    }
    window.addEventListener('resize', handleResize)

    return () => {
      cancelled = true
      cancelAnimationFrame(rafId)
      window.removeEventListener('resize', handleResize)
      renderer.dispose()
    }
  }, [modelJsonText, customTextures, variant, casingColor, casingHex, wireframe])

  return (
    <div className="relative flex h-[390px] w-full max-w-lg flex-col overflow-hidden rounded-3xl border border-zinc-800 bg-zinc-900/70 shadow-2xl">
      <div className="absolute top-3 left-3 z-10 flex items-center gap-1.5 rounded-lg border border-zinc-800 bg-zinc-950/85 px-2.5 py-1 text-[10px] font-medium text-zinc-300 backdrop-blur">
        <span>
          {modelMeta.label} • {modelMeta.cubes} elements
        </span>
      </div>

      <div className="absolute top-3 right-3 z-10 flex items-center gap-1">
        <button
          onClick={() => setAutoRotate((v) => !v)}
          title="Auto Rotate"
          className={`rounded-lg border p-1.5 transition ${
            autoRotate
              ? 'border-emerald-500/40 bg-emerald-500/20 text-emerald-300'
              : 'border-zinc-800 bg-zinc-950/85 text-zinc-400'
          }`}
        >
          <RotateCw className="h-3.5 w-3.5" />
        </button>
        <button
          onClick={() => setWireframe((v) => !v)}
          title="Wireframe"
          className={`rounded-lg border p-1.5 transition ${
            wireframe
              ? 'border-red-500/40 bg-red-500/20 text-red-300'
              : 'border-zinc-800 bg-zinc-950/85 text-zinc-400'
          }`}
        >
          <Grid3x3 className="h-3.5 w-3.5" />
        </button>
      </div>

      <div
        ref={containerRef}
        onMouseDown={(e) => {
          rotRef.current.dragging = true
          rotRef.current.lastX = e.clientX
          rotRef.current.lastY = e.clientY
        }}
        onMouseMove={(e) => {
          if (!rotRef.current.dragging) return
          const dx = e.clientX - rotRef.current.lastX
          const dy = e.clientY - rotRef.current.lastY
          rotRef.current.y += dx * 0.012
          rotRef.current.x = Math.max(-1.2, Math.min(1.2, rotRef.current.x + dy * 0.012))
          rotRef.current.lastX = e.clientX
          rotRef.current.lastY = e.clientY
        }}
        onMouseUp={() => {
          rotRef.current.dragging = false
        }}
        onMouseLeave={() => {
          rotRef.current.dragging = false
        }}
        onWheel={(e) => {
          rotRef.current.zoom = Math.max(2.0, Math.min(7.0, rotRef.current.zoom + e.deltaY * 0.002))
        }}
        className="flex-1 cursor-grab active:cursor-grabbing"
      />

      <div className="border-t border-zinc-800/80 bg-zinc-950/70 px-3 py-1.5 text-center text-[10px] text-zinc-500">
        Real in-game Minecraft JSON geometry + UV pixel textures • Drag to orbit 360° • Scroll to zoom
      </div>
    </div>
  )
}
