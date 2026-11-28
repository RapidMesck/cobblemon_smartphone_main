import { useMemo, useState } from 'react'
import {
  Box,
  Code2,
  Copy,
  Download,
  FileUp,
  Hammer,
  Palette,
  Plus,
  Trash2,
  Upload,
} from 'lucide-react'
import { useActiveProject, useActiveProjectAssets, useStudioStore } from '@/store/studioStore'
import type { StudioSkin } from '@/types/schemas'
import { resolveModelTextureZipPath, serializeSkinJson } from '@/export/packExporter'
import { BlockbenchModelViewer } from '@/modules/skins/BlockbenchModelViewer'
import { createId, SMARTPHONE_COLORS, slugify, triggerDownload, type SmartphoneColorId } from '@/lib/utils'

type SkinTab = 'models' | 'colors' | 'unlock' | 'json'

function decodeDataUrlText(dataUrl: string | undefined): string | null {
  if (!dataUrl) return null
  try {
    const idx = dataUrl.indexOf(',')
    if (idx < 0) return null
    const bin = atob(dataUrl.slice(idx + 1))
    const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0))
    return new TextDecoder('utf-8').decode(bytes).replace(/^\uFEFF/, '')
  } catch {
    return null
  }
}

export function SkinStudioView() {
  const project = useActiveProject()
  const assets = useActiveProjectAssets()
  const updateActiveProject = useStudioStore((s) => s.updateActiveProject)
  const addAsset = useStudioStore((s) => s.addAsset)
  const updateTextAsset = useStudioStore((s) => s.updateTextAsset)
  const pushToast = useStudioStore((s) => s.pushToast)

  const [selectedUid, setSelectedUid] = useState<string | null>(null)
  const [tab, setTab] = useState<SkinTab>('models')
  const [previewVariant, setPreviewVariant] = useState<'hand' | 'rotom'>('hand')
  const [casingColorId, setCasingColorId] = useState<SmartphoneColorId>('red')
  const casingColorHex = SMARTPHONE_COLORS.find((c) => c.id === casingColorId)?.hex ?? '#dc2626'

  const skins = project?.skins ?? []
  const activeSkin = useMemo(
    () => skins.find((s) => s.uid === selectedUid) ?? skins[0] ?? null,
    [skins, selectedUid]
  )
  const assetMap = useMemo(() => new Map(assets.map((a) => [a.id, a])), [assets])

  const activeModelJson = useMemo(() => {
    if (!activeSkin) return null
    const id = previewVariant === 'hand' ? activeSkin.handModelAssetId : activeSkin.rotomModelAssetId
    return id ? decodeDataUrlText(assetMap.get(id)?.dataUrl) : null
  }, [activeSkin, previewVariant, assetMap])

  // Detect all unique texture refs declared inside uploaded Hand & Rotom Blockbench JSON models
  const detectedModelTextures = useMemo(() => {
    if (!activeSkin) return [] as Array<{ key: string; ref: string }>
    const refs = new Map<string, string>()
    for (const assetId of [activeSkin.handModelAssetId, activeSkin.rotomModelAssetId]) {
      if (!assetId) continue
      const raw = decodeDataUrlText(assetMap.get(assetId)?.dataUrl)
      if (!raw) continue
      try {
        const parsed = JSON.parse(raw)
        if (parsed.textures && typeof parsed.textures === 'object') {
          for (const [k, v] of Object.entries(parsed.textures)) {
            if (k === 'particle' || typeof v !== 'string' || v.startsWith('#')) continue
            if (!refs.has(v)) refs.set(v, k)
          }
        }
      } catch {
        // ignore invalid JSON
      }
    }
    for (const existingRef of Object.keys(activeSkin.modelTextures ?? {})) {
      if (!refs.has(existingRef)) refs.set(existingRef, existingRef)
    }
    return Array.from(refs.entries()).map(([ref, key]) => ({ key, ref }))
  }, [activeSkin, assetMap])

  const customTextureDataUrls = useMemo(() => {
    const out: Record<string, string> = {}
    if (!activeSkin?.modelTextures) return out
    for (const [ref, assetId] of Object.entries(activeSkin.modelTextures)) {
      const url = assetMap.get(assetId)?.dataUrl
      if (url) out[ref] = url
    }
    return out
  }, [activeSkin?.modelTextures, assetMap])

  if (!project) return null

  const updateSkin = (patch: Partial<StudioSkin>) => {
    if (!activeSkin) return
    updateActiveProject((p) => ({
      ...p,
      skins: p.skins.map((s) =>
        s.uid === activeSkin.uid ? { ...s, ...patch, updatedAt: Date.now() } : s
      ),
    }))
  }

  const createSkin = () => {
    const s: StudioSkin = {
      uid: createId('skin'),
      id: `custom_case_${skins.length + 1}`,
      name: `Custom Case ${skins.length + 1}`,
      hand_model: `${project.namespace}:item/smartphone_skins/custom_case_${skins.length + 1}_hand`,
      rotom_model: `${project.namespace}:item/smartphone_skins/custom_case_${skins.length + 1}_rotom`,
      colors: ['*'],
      locked: false,
      updatedAt: Date.now(),
    }
    updateActiveProject((p) => ({ ...p, skins: [...p.skins, s] }))
    setSelectedUid(s.uid)
  }

  const handleImportJson = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    try {
      const parsed = JSON.parse(await file.text())
      const rawId = typeof parsed.id === 'string' ? parsed.id : file.name.replace(/\.json$/i, '')
      const cleanId = slugify(rawId.includes(':') ? rawId.split(':')[1] : rawId)
      const imported: StudioSkin = {
        uid: createId('skin'),
        id: cleanId,
        name: parsed.name || cleanId,
        hand_model: parsed.hand_model,
        rotom_model: parsed.rotom_model,
        inventory_model: parsed.inventory_model,
        colors: Array.isArray(parsed.colors) ? parsed.colors : ['*'],
        locked: Boolean(parsed.locked),
        require_upgrade: parsed.require_upgrade,
        updatedAt: Date.now(),
      }
      updateActiveProject((p) => ({ ...p, skins: [...p.skins, imported] }))
      setSelectedUid(imported.uid)
      pushToast(`Imported skin "${imported.name}".`, 'success')
    } catch {
      pushToast('Failed to parse skin JSON.', 'error')
    }
  }

  const uploadBlockbenchModel = async (file: File | undefined, slot: 'hand' | 'rotom') => {
    if (!file || !activeSkin) return
    // Validate JSON & warn if missing both elements and parent (e.g. wrong file format like .bbmodel)
    try {
      const raw = (await file.text()).replace(/^\uFEFF/, '')
      const parsed = JSON.parse(raw)
      const hasElements = Array.isArray(parsed.elements) && parsed.elements.length > 0
      const hasParent = typeof parsed.parent === 'string' && parsed.parent.trim().length > 0
      if (!hasElements && !hasParent) {
        pushToast(
          'This JSON has no "elements" or "parent". In Blockbench, use File → Export → Export Block/Item Model (.json).',
          'error'
        )
        return
      }
    } catch {
      pushToast('Invalid JSON file. Export as Minecraft Java Block/Item Model (.json).', 'error')
      return
    }

    const asset = await addAsset(file, 'model_json')
    if (!asset) return
    if (slot === 'hand') {
      updateSkin({
        handModelAssetId: asset.id,
        hand_model: `${project.namespace}:item/smartphone_skins/${slugify(activeSkin.id)}_hand`,
      })
      setPreviewVariant('hand')
    } else {
      updateSkin({
        rotomModelAssetId: asset.id,
        rotom_model: `${project.namespace}:item/smartphone_skins/${slugify(activeSkin.id)}_rotom`,
      })
      setPreviewVariant('rotom')
    }
    pushToast(`Loaded Minecraft 3D model "${file.name}".`, 'success')
  }

  const attachModelTexture = async (file: File | undefined, key: string, ref: string) => {
    if (!file || !activeSkin) return
    const uploaded = await addAsset(file, 'png')
    if (!uploaded) return

    // If the Blockbench JSON used an un-namespaced texture ref (e.g. "0": "texture"),
    // rewrite it to a valid Minecraft ResourceLocation so it works in-game automatically!
    let finalRef = ref
    if (!ref.includes(':')) {
      finalRef = `${project.namespace}:item/smartphone_skins/${slugify(activeSkin.id)}_${slugify(key)}`
      for (const modelAssetId of [activeSkin.handModelAssetId, activeSkin.rotomModelAssetId]) {
        if (!modelAssetId) continue
        const raw = decodeDataUrlText(assetMap.get(modelAssetId)?.dataUrl)
        if (!raw) continue
        try {
          const parsed = JSON.parse(raw)
          if (parsed.textures && typeof parsed.textures === 'object') {
            let changed = false
            for (const [tk, tv] of Object.entries(parsed.textures)) {
              if (tv === ref) {
                parsed.textures[tk] = finalRef
                changed = true
              }
            }
            if (changed) {
              await updateTextAsset(modelAssetId, JSON.stringify(parsed, null, 2))
            }
          }
        } catch {
          // ignore
        }
      }
    }

    updateSkin({
      modelTextures: {
        ...(activeSkin.modelTextures ?? {}),
        [finalRef]: uploaded.id,
      },
    })
    pushToast(`Linked texture → ${finalRef}`, 'success')
  }

  const uploadFrame = async (file: File | undefined, slot: 'frameAssetId' | 'largeFrameAssetId') => {
    if (!file || !activeSkin) return
    const asset = await addAsset(file, 'png')
    if (asset) {
      updateSkin({ [slot]: asset.id })
      pushToast(`Uploaded ${file.name} (${asset.width}×${asset.height}).`, 'success')
    }
  }

  const serializedJson = activeSkin
    ? JSON.stringify(serializeSkinJson(project.namespace, activeSkin), null, 2)
    : ''

  const giveCommand = activeSkin
    ? `/give @p cobblemon_smartphone:yellow_smartphone[minecraft:custom_data={"cobblemon_smartphone:skin":"${project.namespace}:${activeSkin.id}"}]`
    : ''

  const allowsAllColors = activeSkin?.colors.includes('*') || activeSkin?.colors.length === 0

  return (
    <div className="flex h-full min-h-0">
      {/* Left Skin List */}
      <div className="flex w-60 shrink-0 flex-col border-r border-zinc-800/80 bg-zinc-950 p-3">
        <div className="mb-2.5 flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
            3D Skins ({skins.length})
          </span>
          <div className="flex items-center gap-1">
            <label
              title="Import Skin JSON"
              className="cursor-pointer rounded-md border border-zinc-800 bg-zinc-900 p-1 text-zinc-300 hover:bg-zinc-800"
            >
              <FileUp className="h-3.5 w-3.5" />
              <input type="file" accept="application/json,.json" onChange={handleImportJson} className="hidden" />
            </label>
            <button
              onClick={createSkin}
              className="flex items-center gap-1 rounded-md bg-emerald-500/20 px-2 py-1 text-xs font-medium text-emerald-300 hover:bg-emerald-500/30"
            >
              <Plus className="h-3.5 w-3.5" /> New
            </button>
          </div>
        </div>

        <div className="flex-1 space-y-1 overflow-auto">
          {skins.map((s) => (
            <div
              key={s.uid}
              onClick={() => setSelectedUid(s.uid)}
              className={`group flex cursor-pointer items-center justify-between rounded-lg px-2.5 py-2 text-xs transition ${
                activeSkin?.uid === s.uid
                  ? 'bg-zinc-800 text-zinc-50 ring-1 ring-zinc-700'
                  : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
              }`}
            >
              <div className="min-w-0">
                <div className="truncate font-medium">{s.name}</div>
                <div className="truncate font-mono text-[10px] text-zinc-500">
                  {project.namespace}:{s.id}
                </div>
              </div>
              <button
                onClick={(e) => {
                  e.stopPropagation()
                  updateActiveProject((p) => ({
                    ...p,
                    skins: p.skins.filter((x) => x.uid !== s.uid),
                  }))
                  if (selectedUid === s.uid) setSelectedUid(null)
                }}
                className="opacity-0 hover:text-red-400 group-hover:opacity-100"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </div>
          ))}
        </div>
      </div>

      {/* Center 3D Viewport */}
      {activeSkin ? (
        <>
          <div className="flex flex-1 flex-col items-center justify-center gap-4 overflow-auto bg-[radial-gradient(#27272a_1px,transparent_1px)] [background-size:18px_18px] p-6">
            <div className="flex items-center gap-2 rounded-xl border border-zinc-800 bg-zinc-900/90 p-1 text-xs">
              <button
                onClick={() => setPreviewVariant('hand')}
                className={`rounded-lg px-3 py-1 font-medium transition ${
                  previewVariant === 'hand' ? 'bg-emerald-500/20 text-emerald-300' : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                Hand Smartphone 3D
              </button>
              <button
                onClick={() => setPreviewVariant('rotom')}
                className={`rounded-lg px-3 py-1 font-medium transition ${
                  previewVariant === 'rotom' ? 'bg-orange-500/20 text-orange-300' : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                Rotom Phone 3D
              </button>
            </div>

            <BlockbenchModelViewer
              modelJsonText={activeModelJson}
              customTextures={customTextureDataUrls}
              variant={previewVariant}
              casingColor={casingColorId}
              casingHex={casingColorHex}
            />

            {/* 16 Casing Preview Swatches */}
            <div className="flex flex-wrap items-center justify-center gap-1.5 rounded-xl border border-zinc-800 bg-zinc-900/90 px-3 py-2">
              {SMARTPHONE_COLORS.map((c) => (
                <button
                  key={c.id}
                  title={c.label}
                  onClick={() => setCasingColorId(c.id)}
                  className={`h-4.5 w-4.5 rounded-full border-2 transition ${
                    casingColorId === c.id ? 'scale-115 border-emerald-400' : 'border-zinc-700 opacity-80'
                  }`}
                  style={{ backgroundColor: c.hex }}
                />
              ))}
            </div>

            {/* Quick /give command */}
            <div className="flex w-full max-w-lg items-center gap-2 rounded-xl border border-zinc-800 bg-zinc-900/80 px-3 py-2">
              <code className="min-w-0 flex-1 truncate font-mono text-[10px] text-emerald-300">
                {giveCommand}
              </code>
              <button
                onClick={() => {
                  navigator.clipboard.writeText(giveCommand)
                  pushToast('/give command copied!', 'success')
                }}
                className="shrink-0 rounded bg-zinc-800 px-2 py-1 text-[10px] font-medium text-zinc-200 hover:bg-zinc-700"
              >
                Copy /give
              </button>
            </div>
          </div>

          {/* Right Inspector */}
          <div className="flex w-[380px] shrink-0 flex-col border-l border-zinc-800/80 bg-zinc-950">
            <div className="grid grid-cols-4 border-b border-zinc-800 p-1.5 text-[11px]">
              {(
                [
                  { id: 'models', label: 'Models', icon: Box },
                  { id: 'colors', label: 'Colors', icon: Palette },
                  { id: 'unlock', label: 'Unlock', icon: Hammer },
                  { id: 'json', label: 'JSON', icon: Code2 },
                ] as const
              ).map((t) => {
                const Icon = t.icon
                return (
                  <button
                    key={t.id}
                    onClick={() => setTab(t.id)}
                    className={`flex items-center justify-center gap-1 rounded-lg py-1.5 font-medium transition ${
                      tab === t.id
                        ? 'bg-emerald-500/20 text-emerald-300'
                        : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
                    }`}
                  >
                    <Icon className="h-3.5 w-3.5" />
                    {t.label}
                  </button>
                )
              })}
            </div>

            <div className="flex-1 overflow-auto p-4 text-xs">
              {tab === 'models' && (
                <div className="space-y-3.5">
                  <div>
                    <label className="mb-1 block text-[11px] text-zinc-400">Skin Name</label>
                    <input
                      value={activeSkin.name}
                      onChange={(e) => updateSkin({ name: e.target.value })}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none focus:border-emerald-500"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-[11px] text-zinc-400">Skin ID</label>
                    <input
                      value={activeSkin.id}
                      onChange={(e) => updateSkin({ id: slugify(e.target.value) })}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono outline-none"
                    />
                  </div>

                  {/* Blockbench JSON uploaders */}
                  <div className="space-y-2.5 rounded-xl border border-zinc-800 bg-zinc-900/50 p-3">
                    <div className="text-[11px] font-semibold text-zinc-200">
                      3D Blockbench Models (.json)
                    </div>
                    <div>
                      <label className="mb-1 block text-[10px] text-zinc-400">Hand Model Path</label>
                      <input
                        value={activeSkin.hand_model ?? ''}
                        onChange={(e) => updateSkin({ hand_model: e.target.value || undefined })}
                        className="mb-1.5 w-full rounded-lg border border-zinc-800 bg-zinc-950 px-2 py-1 font-mono text-[11px] outline-none"
                      />
                      <label className="flex cursor-pointer items-center justify-center gap-1.5 rounded-lg border border-dashed border-zinc-700 bg-zinc-950 py-1.5 text-[11px] text-zinc-300 hover:border-emerald-400">
                        <Upload className="h-3.5 w-3.5" />
                        {activeSkin.handModelAssetId ? 'Replace Hand .json' : 'Upload Hand Model (.json)'}
                        <input
                          type="file"
                          accept=".json,application/json"
                          onChange={(e) => uploadBlockbenchModel(e.target.files?.[0], 'hand')}
                          className="hidden"
                        />
                      </label>
                    </div>

                    <div className="pt-1">
                      <label className="mb-1 block text-[10px] text-zinc-400">Rotom Model Path</label>
                      <input
                        value={activeSkin.rotom_model ?? ''}
                        onChange={(e) => updateSkin({ rotom_model: e.target.value || undefined })}
                        className="mb-1.5 w-full rounded-lg border border-zinc-800 bg-zinc-950 px-2 py-1 font-mono text-[11px] outline-none"
                      />
                      <label className="flex cursor-pointer items-center justify-center gap-1.5 rounded-lg border border-dashed border-zinc-700 bg-zinc-950 py-1.5 text-[11px] text-zinc-300 hover:border-emerald-400">
                        <Upload className="h-3.5 w-3.5" />
                        {activeSkin.rotomModelAssetId ? 'Replace Rotom .json' : 'Upload Rotom Model (.json)'}
                        <input
                          type="file"
                          accept=".json,application/json"
                          onChange={(e) => uploadBlockbenchModel(e.target.files?.[0], 'rotom')}
                          className="hidden"
                        />
                      </label>
                    </div>
                  </div>

                  {/* Model Textures (.png) auto-detected from uploaded Blockbench JSON */}
                  {detectedModelTextures.length > 0 && (
                    <div className="space-y-2 rounded-xl border border-zinc-800 bg-zinc-900/50 p-3">
                      <div className="flex items-center justify-between">
                        <div className="text-[11px] font-semibold text-zinc-200">
                          Model Textures ({detectedModelTextures.length})
                        </div>
                        <span className="text-[9px] text-zinc-500">
                          Only &quot;In Pack&quot; PNGs go into Code &amp; .zip
                        </span>
                      </div>
                      {detectedModelTextures.map(({ key, ref }) => {
                        const assetId = activeSkin.modelTextures?.[ref]
                        const asset = assetId ? assetMap.get(assetId) : undefined
                        const isModBuiltin = !asset && ref.startsWith('cobblemon_smartphone:')
                        const builtinPreviewUrl = isModBuiltin
                          ? `/mod-assets/textures/${ref.slice('cobblemon_smartphone:'.length)}.png`
                          : null
                        const zipPath = resolveModelTextureZipPath(project.namespace, activeSkin.id, ref)
                        return (
                          <div
                            key={ref}
                            className="flex items-center justify-between gap-2 rounded-lg border border-zinc-800 bg-zinc-950 p-2"
                          >
                            <div className="flex min-w-0 items-center gap-2">
                              {asset ? (
                                <img
                                  src={asset.dataUrl}
                                  alt={ref}
                                  className="pixelated h-8 w-8 shrink-0 rounded border border-emerald-500/40 object-contain"
                                />
                              ) : builtinPreviewUrl ? (
                                <img
                                  src={builtinPreviewUrl}
                                  alt={ref}
                                  className="pixelated h-8 w-8 shrink-0 rounded border border-red-500/30 object-contain opacity-85"
                                />
                              ) : (
                                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-dashed border-red-500/50 bg-fuchsia-950/40 font-mono text-[10px] text-red-300">
                                  #{key}
                                </div>
                              )}
                              <div className="min-w-0">
                                <div className="flex items-center gap-1.5">
                                  <span className="truncate font-mono text-[10px] text-zinc-200">
                                    #{key} {ref}
                                  </span>
                                  {asset ? (
                                    <span className="shrink-0 rounded bg-emerald-500/20 px-1 py-0.2 text-[8px] font-semibold text-emerald-300">
                                      In Pack
                                    </span>
                                  ) : isModBuiltin ? (
                                    <span className="shrink-0 rounded bg-red-500/15 px-1 py-0.2 text-[8px] font-semibold text-red-300">
                                      Mod Built-in
                                    </span>
                                  ) : (
                                    <span className="shrink-0 rounded bg-red-500/20 px-1 py-0.2 text-[8px] font-semibold text-red-300">
                                      Missing PNG
                                    </span>
                                  )}
                                </div>
                                <div className="truncate font-mono text-[9px] text-zinc-500">
                                  {asset
                                    ? zipPath
                                    : isModBuiltin
                                      ? 'Loaded from mod jar (not in your pack)'
                                      : `Target: ${zipPath}`}
                                </div>
                              </div>
                            </div>
                            <div className="flex shrink-0 items-center gap-1">
                              <label className="cursor-pointer rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-[10px] text-zinc-200 hover:border-emerald-400">
                                {asset ? 'Replace' : isModBuiltin ? 'Override' : 'Upload PNG'}
                                <input
                                  type="file"
                                  accept="image/png"
                                  onChange={(e) => attachModelTexture(e.target.files?.[0], key, ref)}
                                  className="hidden"
                                />
                              </label>
                              {asset && (
                                <button
                                  title="Remove from pack"
                                  onClick={() => {
                                    const next = { ...(activeSkin.modelTextures ?? {}) }
                                    delete next[ref]
                                    updateSkin({ modelTextures: next })
                                  }}
                                  className="rounded p-1 text-zinc-500 hover:text-red-400"
                                >
                                  <Trash2 className="h-3 w-3" />
                                </button>
                              )}
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  )}

                  {/* Optional GUI Frame Overrides */}
                  <div className="space-y-2 rounded-xl border border-zinc-800 bg-zinc-900/50 p-3">
                    <div className="text-[11px] font-semibold text-zinc-200">
                      Optional GUI Frame Overrides (PNG)
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      <label className="flex cursor-pointer items-center justify-center gap-1 rounded-lg border border-dashed border-zinc-700 bg-zinc-950 py-2 text-[11px] text-zinc-300 hover:border-emerald-400">
                        <Upload className="h-3 w-3" />
                        {activeSkin.frameAssetId ? 'Frame ✓' : 'Frame 131×207'}
                        <input
                          type="file"
                          accept="image/png"
                          onChange={(e) => uploadFrame(e.target.files?.[0], 'frameAssetId')}
                          className="hidden"
                        />
                      </label>
                      <label className="flex cursor-pointer items-center justify-center gap-1 rounded-lg border border-dashed border-zinc-700 bg-zinc-950 py-2 text-[11px] text-zinc-300 hover:border-emerald-400">
                        <Upload className="h-3 w-3" />
                        {activeSkin.largeFrameAssetId ? 'Large ✓' : 'Large 211×207'}
                        <input
                          type="file"
                          accept="image/png"
                          onChange={(e) => uploadFrame(e.target.files?.[0], 'largeFrameAssetId')}
                          className="hidden"
                        />
                      </label>
                    </div>
                  </div>
                </div>
              )}

              {tab === 'colors' && (
                <div className="space-y-3">
                  <label className="flex cursor-pointer items-center justify-between rounded-xl border border-zinc-800 bg-zinc-900/60 p-3 font-medium">
                    <span>Compatible with All 16 Colors (*)</span>
                    <input
                      type="checkbox"
                      checked={allowsAllColors}
                      onChange={(e) =>
                        updateSkin({ colors: e.target.checked ? ['*'] : ['red', 'yellow'] })
                      }
                      className="accent-emerald-400"
                    />
                  </label>

                  {!allowsAllColors && (
                    <div className="grid grid-cols-2 gap-1.5">
                      {SMARTPHONE_COLORS.map((c) => {
                        const selected = activeSkin.colors.includes(c.id)
                        return (
                          <button
                            key={c.id}
                            onClick={() =>
                              updateSkin({
                                colors: selected
                                  ? activeSkin.colors.filter((x) => x !== c.id)
                                  : [...activeSkin.colors.filter((x) => x !== '*'), c.id],
                              })
                            }
                            className={`flex items-center gap-2 rounded-lg border px-2.5 py-1.5 text-xs ${
                              selected
                                ? 'border-emerald-500/50 bg-emerald-500/15 text-emerald-200'
                                : 'border-zinc-800 bg-zinc-900/50 text-zinc-400'
                            }`}
                          >
                            <span className="h-3 w-3 rounded-full" style={{ backgroundColor: c.hex }} />
                            {c.label}
                          </button>
                        )
                      })}
                    </div>
                  )}
                </div>
              )}

              {tab === 'unlock' && (
                <div className="space-y-4">
                  <label className="flex cursor-pointer items-center justify-between rounded-xl border border-zinc-800 bg-zinc-900/60 p-3 font-medium">
                    <span>Start Locked (require unlock)</span>
                    <input
                      type="checkbox"
                      checked={activeSkin.locked}
                      onChange={(e) => updateSkin({ locked: e.target.checked })}
                      className="accent-emerald-400"
                    />
                  </label>

                  <div>
                    <label className="mb-1 block text-[11px] text-zinc-400">
                      Link to Upgrade Key (optional)
                    </label>
                    <input
                      value={activeSkin.require_upgrade ?? ''}
                      onChange={(e) =>
                        updateSkin({ require_upgrade: e.target.value ? slugify(e.target.value) : undefined })
                      }
                      placeholder="e.g. upgrade_pikachu"
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono outline-none"
                    />
                  </div>

                  <div className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-3 text-[11px] text-zinc-400">
                    To create a Smithing Table unlock recipe or SD Card for this skin, use the dedicated{' '}
                    <span className="font-semibold text-red-300">Recipes &amp; SD Cards</span> tab.
                  </div>
                </div>
              )}

              {tab === 'json' && (
                <div className="flex h-full flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <span className="truncate font-mono text-[10px] text-zinc-400">
                      assets/{project.namespace}/smartphone_skins/{activeSkin.id}.json
                    </span>
                    <div className="flex gap-1">
                      <button
                        onClick={() => {
                          navigator.clipboard.writeText(serializedJson)
                          pushToast('Skin JSON copied!', 'success')
                        }}
                        className="rounded border border-zinc-800 bg-zinc-900 p-1 text-zinc-300 hover:bg-zinc-800"
                      >
                        <Copy className="h-3.5 w-3.5" />
                      </button>
                      <button
                        onClick={() =>
                          triggerDownload(
                            new Blob([serializedJson], { type: 'application/json' }),
                            `${slugify(activeSkin.id)}.json`
                          )
                        }
                        className="rounded bg-emerald-400 p-1 text-zinc-950 hover:bg-emerald-300"
                      >
                        <Download className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>
                  <pre className="flex-1 overflow-auto rounded-xl border border-zinc-800 bg-zinc-900/60 p-3 font-mono text-[11px] leading-relaxed text-emerald-100">
                    {serializedJson}
                  </pre>
                </div>
              )}
            </div>
          </div>
        </>
      ) : (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 text-sm text-zinc-400">
          <p>No 3D skins in this project yet.</p>
          <button
            onClick={createSkin}
            className="rounded-lg bg-emerald-400 px-4 py-2 text-xs font-semibold text-zinc-950 hover:bg-emerald-300"
          >
            Create First 3D Skin
          </button>
        </div>
      )}
    </div>
  )
}
