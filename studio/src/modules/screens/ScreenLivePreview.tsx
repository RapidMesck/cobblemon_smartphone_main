import { useEffect, useRef, useState } from 'react'
import { Cpu, MousePointerClick, Play } from 'lucide-react'
import type { StudioScreen, ThemeColors } from '@/types/schemas'
import { interpolateText } from '@/modules/screens/screenCatalog'
import { HdCanvasWebGL } from '@/engine/hd/HdCanvasWebGL'
import {
  getScreenDesignSize,
  renderHdScreen,
  type HitBox,
} from '@/engine/hd/HdScreenRenderer'
import { SMARTPHONE_COLORS, type SmartphoneColorId } from '@/lib/utils'

interface Props {
  screen: StudioScreen
  colors: ThemeColors
  selectedPath: number[] | null
  onSelectPath: (path: number[]) => void
}

export function ScreenLivePreview({ screen, colors, selectedPath, onSelectPath }: Props) {
  const [renderStyle, setRenderStyle] = useState<'hd' | 'pixel'>('pixel')
  const [interactionMode, setInteractionMode] = useState<'play' | 'select'>('play')
  const [casingColor, setCasingColor] = useState<SmartphoneColorId>('red')
  const [state, setState] = useState<Record<string, string | number | boolean>>(() => ({
    ...screen.default_state,
  }))
  const [phoneToast, setPhoneToast] = useState<{ text: string; kind: string } | null>(null)
  const [effectLog, setEffectLog] = useState<string[]>([])
  const [hoveredId, setHoveredId] = useState<string | null>(null)
  const [texVersion, setTexVersion] = useState(0)

  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const hdCanvasRef = useRef<HdCanvasWebGL | null>(null)
  const hitBoxesRef = useRef<HitBox[]>([])

  // Reset state when switching screen or default_state changes
  useEffect(() => {
    setState({ ...screen.default_state })
    setEffectLog([])
  }, [screen.uid, screen.default_state])

  // Initialize WebGL2 HdCanvasWebGL once
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

  const showToast = (text: string, kind = 'info', duration = 2600) => {
    setPhoneToast({ text: interpolateText(text, state), kind })
    setTimeout(() => setPhoneToast(null), duration)
  }

  const runAction = (actionKey?: string) => {
    if (!actionKey) return
    if (actionKey.startsWith('toast:')) {
      showToast(actionKey.slice(6), 'info')
      return
    }
    const action = screen.actions[actionKey]
    if (!action) {
      setEffectLog((prev) => [`Action "${actionKey}" triggered`, ...prev.slice(0, 4)])
      return
    }
    const nextState = { ...state }
    const logs: string[] = []
    for (const eff of action.effects) {
      if (eff.type === 'toast' && eff.text) {
        showToast(eff.text, eff.kind ?? 'info', eff.duration_ms ?? 2500)
      } else if (eff.type === 'set_state' && eff.key) {
        nextState[eff.key] = eff.value ?? ''
        logs.push(`set_state: ${eff.key} = ${eff.value}`)
      } else if (eff.type === 'toggle_state' && eff.key) {
        nextState[eff.key] = !nextState[eff.key]
        logs.push(`toggle_state: ${eff.key} → ${String(nextState[eff.key])}`)
      } else if (eff.type === 'inc_state' && eff.key) {
        nextState[eff.key] = Number(nextState[eff.key] ?? 0) + (eff.delta ?? 1)
        logs.push(`inc_state: ${eff.key} → ${nextState[eff.key]}`)
      } else if (eff.type === 'function' && eff.function) {
        logs.push(`server function: ${eff.function}()`)
      } else if (eff.type === 'command' && eff.command) {
        logs.push(`command: /${interpolateText(eff.command, nextState)}`)
      } else if (eff.type === 'give_item' && eff.item) {
        logs.push(`give_item: ${eff.count ?? 1}× ${eff.item}`)
      } else if (eff.type === 'message' && eff.text) {
        logs.push(`chat: ${interpolateText(eff.text, nextState)}`)
      }
    }
    setState(nextState)
    if (logs.length > 0) {
      setEffectLog((prev) => [...logs, ...prev].slice(0, 5))
    }
  }

  const { w: designW, h: designH } = getScreenDesignSize(screen)
  const dpr = typeof window !== 'undefined' ? Math.min(window.devicePixelRatio || 1, 2) : 2

  // Render frame onto WebGL2 using exact HdShader.kt SDF shader
  useEffect(() => {
    const el = canvasRef.current
    const hdCanvas = hdCanvasRef.current
    if (!el || !hdCanvas) return

    const physW = Math.round(designW * dpr)
    const physH = Math.round(designH * dpr)
    if (el.width !== physW || el.height !== physH) {
      el.width = physW
      el.height = physH
    }

    hdCanvas.begin(physW, physH, dpr, renderStyle === 'pixel')
    hitBoxesRef.current = renderHdScreen(hdCanvas, {
      screen,
      colors,
      casingColor,
      state,
      isPixelStyle: renderStyle === 'pixel',
      interactionMode,
      selectedPath,
      hoveredId,
      phoneToast,
      onSetState: (k, v) => setState((prev) => ({ ...prev, [k]: v })),
      onRunAction: runAction,
      onSelectPath,
    })
    hdCanvas.end()
  })

  const getDpCoords = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    return {
      x: ((e.clientX - rect.left) / rect.width) * designW,
      y: ((e.clientY - rect.top) / rect.height) * designH,
    }
  }

  const findTopHit = (x: number, y: number): HitBox | undefined => {
    const boxes = hitBoxesRef.current
    for (let i = boxes.length - 1; i >= 0; i--) {
      const b = boxes[i]
      if (x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h) {
        return b
      }
    }
    return undefined
  }

  const isFree = screen.viewport === 'free'

  return (
    <div className="flex flex-1 flex-col items-center justify-between gap-3 overflow-auto bg-[radial-gradient(#27272a_1px,transparent_1px)] [background-size:18px_18px] p-5">
      {/* Top Preview Controls */}
      <div className="flex flex-wrap items-center justify-center gap-2">
        <div className="flex items-center gap-1.5 rounded-xl border border-emerald-500/30 bg-emerald-950/40 px-2.5 py-1.5 text-[11px] font-medium text-emerald-300">
          <Cpu className="h-3.5 w-3.5" />
          WebGL2 SDF Engine
        </div>

        <div className="flex rounded-xl border border-zinc-800 bg-zinc-900/90 p-1 text-xs">
          <button
            onClick={() => setRenderStyle('hd')}
            className={`rounded-lg px-2.5 py-1 font-medium transition ${
              renderStyle === 'hd' ? 'bg-violet-500/20 text-violet-300' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            HD Style
          </button>
          <button
            onClick={() => setRenderStyle('pixel')}
            className={`rounded-lg px-2.5 py-1 font-medium transition ${
              renderStyle === 'pixel' ? 'bg-red-500/20 text-red-300' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Pixel Style
          </button>
        </div>

        <div className="flex rounded-xl border border-zinc-800 bg-zinc-900/90 p-1 text-xs">
          <button
            onClick={() => setInteractionMode('play')}
            className={`flex items-center gap-1 rounded-lg px-2.5 py-1 font-medium transition ${
              interactionMode === 'play' ? 'bg-emerald-500/20 text-emerald-300' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            <Play className="h-3 w-3" /> Interactive
          </button>
          <button
            onClick={() => setInteractionMode('select')}
            className={`flex items-center gap-1 rounded-lg px-2.5 py-1 font-medium transition ${
              interactionMode === 'select' ? 'bg-red-500/20 text-red-300' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            <MousePointerClick className="h-3 w-3" /> Click to Select
          </button>
        </div>

        {!isFree && (
          <div className="flex items-center gap-1 rounded-xl border border-zinc-800 bg-zinc-900/90 px-2.5 py-1.5">
            {SMARTPHONE_COLORS.slice(0, 8).map((c) => (
              <button
                key={c.id}
                title={`Casing: ${c.label}`}
                onClick={() => setCasingColor(c.id)}
                className={`h-3.5 w-3.5 rounded-full border transition ${
                  casingColor === c.id ? 'scale-115 border-red-400' : 'border-zinc-700 opacity-75'
                }`}
                style={{ backgroundColor: c.hex }}
              />
            ))}
          </div>
        )}
      </div>

      {/* WebGL2 Canvas running the verbatim HdShader.kt SDF shader */}
      <div className="flex flex-col items-center gap-1.5">
        <canvas
          ref={canvasRef}
          data-tex-version={texVersion}
          onMouseMove={(e) => {
            const { x, y } = getDpCoords(e)
            const hit = findTopHit(x, y)
            const nextId = hit?.id ?? null
            if (nextId !== hoveredId) setHoveredId(nextId)
          }}
          onMouseLeave={() => setHoveredId(null)}
          onClick={(e) => {
            const { x, y } = getDpCoords(e)
            findTopHit(x, y)?.onClick?.()
          }}
          className={`block ${hoveredId ? 'cursor-pointer' : 'cursor-default'}`}
          style={{ width: designW, height: designH }}
        />
      </div>

      {/* Bottom Live State & Simulated Server Log */}
      <div className="flex w-full max-w-xl flex-wrap items-center justify-between gap-2 rounded-xl border border-zinc-800 bg-zinc-900/80 px-3 py-2 text-[11px]">
        <div className="flex flex-wrap items-center gap-1.5 font-mono text-zinc-400">
          <span className="text-zinc-500">state:</span>
          {Object.keys(state).length === 0 ? (
            <span className="text-zinc-600">{'{}'}</span>
          ) : (
            Object.entries(state).map(([k, v]) => (
              <span key={k} className="rounded bg-zinc-800 px-1.5 py-0.5 text-[10px] text-red-300">
                {k}={String(v)}
              </span>
            ))
          )}
        </div>
        {effectLog.length > 0 && (
          <span className="truncate font-mono text-[10px] text-amber-300">
            ⚡ {effectLog[0]}
          </span>
        )}
      </div>
    </div>
  )
}
