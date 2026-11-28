import { useMemo, useState } from 'react'
import {
  Code2,
  Copy,
  Download,
  FileUp,
  Image as ImageIcon,
  Layers,
  Palette,
  Plus,
  RotateCcw,
  Search,
  Sliders,
  Sparkles,
  Trash2,
  Upload,
  X,
} from 'lucide-react'
import { useActiveProject, useActiveProjectAssets, useStudioStore } from '@/store/studioStore'
import {
  DARK_THEME_COLORS,
  DEFAULT_THEME_COLORS,
  ROTOM_THEME_COLORS,
  type StudioTheme,
  type ThemeColors,
} from '@/types/schemas'
import { exportProjectZip, serializeThemeJson } from '@/export/packExporter'
import {
  BUILTIN_APPS,
  THEME_COLOR_GROUPS,
  THEME_ELEMENTS,
  type ThemeElementKey,
} from '@/modules/themes/themeConstants'
import { ThemeCanvasPreview } from '@/modules/themes/ThemeCanvasPreview'
import { createId, SMARTPHONE_COLORS, slugify, triggerDownload } from '@/lib/utils'

type InspectorTab = 'general' | 'colors' | 'assets' | 'elements' | 'icons' | 'json'

function isDarkSurface(hex: string): boolean {
  const clean = hex.replace('#', '')
  const rgb = clean.length === 8 ? clean.slice(2) : clean.padEnd(6, '0').slice(0, 6)
  const r = parseInt(rgb.slice(0, 2), 16) || 0
  const g = parseInt(rgb.slice(2, 4), 16) || 0
  const b = parseInt(rgb.slice(4, 6), 16) || 0
  return 0.299 * r + 0.587 * g + 0.114 * b < 128
}

export function ThemeStudioView() {
  const project = useActiveProject()
  const assets = useActiveProjectAssets()
  const updateActiveProject = useStudioStore((s) => s.updateActiveProject)
  const addAsset = useStudioStore((s) => s.addAsset)
  const pushToast = useStudioStore((s) => s.pushToast)

  const [selectedUid, setSelectedUid] = useState<string | null>(null)
  const [tab, setTab] = useState<InspectorTab>('colors')
  const [iconSearch, setIconSearch] = useState('')
  const [customAppId, setCustomAppId] = useState('')

  const themes = project?.themes ?? []
  const activeTheme = useMemo(
    () => themes.find((t) => t.uid === selectedUid) ?? themes[0] ?? null,
    [themes, selectedUid]
  )
  const assetMap = useMemo(() => new Map(assets.map((a) => [a.id, a])), [assets])

  if (!project) return null

  const updateTheme = (patch: Partial<StudioTheme>) => {
    if (!activeTheme) return
    updateActiveProject((p) => ({
      ...p,
      themes: p.themes.map((t) =>
        t.uid === activeTheme.uid ? { ...t, ...patch, updatedAt: Date.now() } : t
      ),
    }))
  }

  const createTheme = () => {
    const newTheme: StudioTheme = {
      uid: createId('theme'),
      id: `theme_${themes.length + 1}`,
      name: `New Theme ${themes.length + 1}`,
      order: 50,
      elements: {},
      appIcons: {},
      colors: { ...DARK_THEME_COLORS },
      updatedAt: Date.now(),
    }
    updateActiveProject((p) => ({ ...p, themes: [...p.themes, newTheme] }))
    setSelectedUid(newTheme.uid)
  }

  const duplicateTheme = (source: StudioTheme) => {
    const copy: StudioTheme = {
      ...structuredClone(source),
      uid: createId('theme'),
      id: `${source.id}_copy`,
      name: `${source.name} (Copy)`,
      updatedAt: Date.now(),
    }
    updateActiveProject((p) => ({ ...p, themes: [...p.themes, copy] }))
    setSelectedUid(copy.uid)
    pushToast(`Duplicated "${source.name}".`, 'success')
  }

  const removeTheme = (uid: string) => {
    updateActiveProject((p) => ({ ...p, themes: p.themes.filter((t) => t.uid !== uid) }))
    if (selectedUid === uid) setSelectedUid(null)
  }

  const handleImportJson = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    try {
      const text = await file.text()
      const parsed = JSON.parse(text)
      const rawId: string = typeof parsed.id === 'string' ? parsed.id : file.name.replace(/\.json$/i, '')
      const cleanId = slugify(rawId.includes(':') ? rawId.split(':')[1] : rawId)
      const mergedColors: ThemeColors = { ...DEFAULT_THEME_COLORS }
      if (parsed.colors && typeof parsed.colors === 'object') {
        for (const k of Object.keys(mergedColors) as Array<keyof ThemeColors>) {
          if (typeof parsed.colors[k] === 'string' && parsed.colors[k].startsWith('#')) {
            mergedColors[k] = parsed.colors[k].toUpperCase()
          }
        }
      }
      const imported: StudioTheme = {
        uid: createId('theme'),
        id: cleanId,
        name: typeof parsed.name === 'string' ? parsed.name : cleanId,
        order: typeof parsed.order === 'number' ? parsed.order : 50,
        elements: {},
        appIcons: {},
        colors: mergedColors,
        updatedAt: Date.now(),
      }
      updateActiveProject((p) => ({ ...p, themes: [...p.themes, imported] }))
      setSelectedUid(imported.uid)
      pushToast(`Imported theme "${imported.name}" from JSON.`, 'success')
    } catch {
      pushToast('Failed to parse theme JSON file.', 'error')
    }
  }

  const handleCloneBuiltinColorTheme = async (colorId: string) => {
    try {
      const res = await fetch(`/mod-assets/themes/${colorId}.json`)
      if (!res.ok) throw new Error('Not found')
      const parsed = await res.json()
      const mergedColors: ThemeColors = { ...DEFAULT_THEME_COLORS }
      if (parsed.colors) {
        for (const k of Object.keys(mergedColors) as Array<keyof ThemeColors>) {
          if (typeof parsed.colors[k] === 'string') {
            mergedColors[k] = parsed.colors[k].toUpperCase()
          }
        }
      }
      if (activeTheme) {
        updateTheme({ colors: mergedColors })
        pushToast(`Applied built-in "${colorId}" palette.`, 'success')
      }
    } catch {
      pushToast(`Could not load built-in theme "${colorId}".`, 'error')
    }
  }

  const uploadThemeImage = async (
    file: File | undefined,
    field: 'wallpaperAssetId' | 'largeWallpaperAssetId' | 'frameAssetId' | 'largeFrameAssetId',
    expectedW: number,
    expectedH: number
  ) => {
    if (!file || !activeTheme) return
    const asset = await addAsset(file, 'png')
    if (!asset) return
    updateTheme({ [field]: asset.id })
    const dimsOk = asset.width === expectedW && asset.height === expectedH
    pushToast(
      dimsOk
        ? `Uploaded ${file.name} (${asset.width}×${asset.height}).`
        : `Uploaded ${file.name} (${asset.width}×${asset.height} — recommended ${expectedW}×${expectedH}).`,
      dimsOk ? 'success' : 'info'
    )
  }

  const uploadElementImage = async (file: File | undefined, key: ThemeElementKey) => {
    if (!file || !activeTheme) return
    const asset = await addAsset(file, 'png')
    if (!asset) return
    updateTheme({ elements: { ...activeTheme.elements, [key]: asset.id } })
    pushToast(`Updated ${key} (${asset.width}×${asset.height}).`, 'success')
  }

  const uploadAppIcon = async (
    file: File | undefined,
    appId: string,
    slot: 'textureAssetId' | 'hoverAssetId'
  ) => {
    if (!file || !activeTheme) return
    const asset = await addAsset(file, 'png')
    if (!asset) return
    const prev = activeTheme.appIcons[appId] ?? {}
    updateTheme({
      appIcons: {
        ...activeTheme.appIcons,
        [appId]: { ...prev, [slot]: asset.id },
      },
    })
    pushToast(`Updated icon for ${appId}.`, 'success')
  }

  const allAppEntries = useMemo(() => {
    const known = new Set(BUILTIN_APPS.map((a) => a.id))
    const customIds = Object.keys(activeTheme?.appIcons ?? {}).filter((id) => !known.has(id))
    const merged = [
      ...BUILTIN_APPS,
      ...customIds.map((id) => ({ id, stem: '', label: id })),
    ]
    const q = iconSearch.trim().toLowerCase()
    return q
      ? merged.filter(
          (a) => a.label.toLowerCase().includes(q) || a.id.toLowerCase().includes(q)
        )
      : merged
  }, [activeTheme?.appIcons, iconSearch])

  const serializedJson = activeTheme
    ? JSON.stringify(serializeThemeJson(project.namespace, activeTheme), null, 2)
    : ''

  return (
    <div className="flex h-full min-h-0">
      {/* Left Theme List Sidebar */}
      <div className="flex w-60 shrink-0 flex-col border-r border-zinc-800/80 bg-zinc-950 p-3">
        <div className="mb-2.5 flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
            Themes ({themes.length})
          </span>
          <div className="flex items-center gap-1">
            <label
              title="Import Theme JSON"
              className="cursor-pointer rounded-md border border-zinc-800 bg-zinc-900 p-1 text-zinc-300 hover:bg-zinc-800 hover:text-zinc-100"
            >
              <FileUp className="h-3.5 w-3.5" />
              <input type="file" accept="application/json,.json" onChange={handleImportJson} className="hidden" />
            </label>
            <button
              onClick={createTheme}
              className="flex items-center gap-1 rounded-md bg-red-500/15 px-2 py-1 text-xs font-medium text-red-300 hover:bg-red-500/25"
            >
              <Plus className="h-3.5 w-3.5" /> New
            </button>
          </div>
        </div>

        <div className="flex-1 space-y-1 overflow-auto">
          {themes.map((t) => (
            <div
              key={t.uid}
              onClick={() => setSelectedUid(t.uid)}
              className={`group flex cursor-pointer items-center justify-between rounded-lg px-2.5 py-2 text-xs transition ${
                activeTheme?.uid === t.uid
                  ? 'bg-zinc-800 text-zinc-50 ring-1 ring-zinc-700'
                  : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
              }`}
            >
              <div className="flex min-w-0 items-center gap-2">
                <span
                  className="h-3 w-3 shrink-0 rounded-full ring-1 ring-white/25"
                  style={{ background: t.colors.primary }}
                />
                <div className="min-w-0">
                  <div className="truncate font-medium">{t.name}</div>
                  <div className="truncate font-mono text-[10px] text-zinc-500">
                    {project.namespace}:{t.id}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-1 opacity-0 transition group-hover:opacity-100">
                <button
                  title="Duplicate"
                  onClick={(e) => {
                    e.stopPropagation()
                    duplicateTheme(t)
                  }}
                  className="rounded p-0.5 text-zinc-400 hover:text-zinc-100"
                >
                  <Copy className="h-3 w-3" />
                </button>
                <button
                  title="Delete"
                  onClick={(e) => {
                    e.stopPropagation()
                    removeTheme(t.uid)
                  }}
                  className="rounded p-0.5 text-zinc-400 hover:text-red-400"
                >
                  <Trash2 className="h-3 w-3" />
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Center + Right */}
      {activeTheme ? (
        <>
          <ThemeCanvasPreview theme={activeTheme} assets={assets} />

          {/* Right Tabbed Inspector */}
          <div className="flex w-[380px] shrink-0 flex-col border-l border-zinc-800/80 bg-zinc-950">
            {/* Inspector Tab Bar */}
            <div className="grid grid-cols-6 border-b border-zinc-800 bg-zinc-950 p-1.5 text-[10px]">
              {(
                [
                  { id: 'general', label: 'Info', icon: Sliders },
                  { id: 'colors', label: 'Colors', icon: Palette },
                  { id: 'assets', label: 'Frames', icon: ImageIcon },
                  { id: 'elements', label: 'UI', icon: Layers },
                  { id: 'icons', label: 'Icons', icon: Sparkles },
                  { id: 'json', label: 'JSON', icon: Code2 },
                ] as const
              ).map((item) => {
                const Icon = item.icon
                return (
                  <button
                    key={item.id}
                    onClick={() => setTab(item.id)}
                    className={`flex flex-col items-center gap-1 rounded-lg py-1.5 font-medium transition ${
                      tab === item.id
                        ? 'bg-red-500/15 text-red-300'
                        : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
                    }`}
                  >
                    <Icon className="h-3.5 w-3.5" />
                    {item.label}
                  </button>
                )
              })}
            </div>

            {/* Tab Content */}
            <div className="flex-1 overflow-auto p-4">
              {tab === 'general' && (
                <div className="space-y-5">
                  <div className="space-y-3">
                    <div>
                      <label className="mb-1 block text-[11px] text-zinc-400">Display Name</label>
                      <input
                        value={activeTheme.name}
                        onChange={(e) => updateTheme({ name: e.target.value })}
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-3 py-1.5 text-xs outline-none focus:border-red-500"
                      />
                      <p className="mt-1 text-[10px] text-zinc-500">
                        Use a dot (e.g. <span className="font-mono">theme.{project.namespace}.{activeTheme.id}</span>) for a translation key, or plain text.
                      </p>
                    </div>
                    <div className="grid grid-cols-2 gap-2.5">
                      <div>
                        <label className="mb-1 block text-[11px] text-zinc-400">Theme ID</label>
                        <input
                          value={activeTheme.id}
                          onChange={(e) => updateTheme({ id: slugify(e.target.value) })}
                          className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-3 py-1.5 font-mono text-xs outline-none focus:border-red-500"
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-[11px] text-zinc-400">Sort Order</label>
                        <input
                          type="number"
                          value={activeTheme.order}
                          onChange={(e) => updateTheme({ order: Number(e.target.value) || 0 })}
                          className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-3 py-1.5 text-xs outline-none focus:border-red-500"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Built-in Presets */}
                  <div className="space-y-2">
                    <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                      Core Presets
                    </div>
                    <div className="grid grid-cols-3 gap-2">
                      {[
                        { label: 'Default', colors: DEFAULT_THEME_COLORS },
                        { label: 'Dark', colors: DARK_THEME_COLORS },
                        { label: 'Rotom', colors: ROTOM_THEME_COLORS },
                      ].map((preset) => (
                        <button
                          key={preset.label}
                          onClick={() => {
                            updateTheme({ colors: { ...preset.colors } })
                            pushToast(`Applied ${preset.label} palette.`, 'success')
                          }}
                          className="rounded-lg border border-zinc-800 bg-zinc-900 py-2 text-xs font-medium text-zinc-200 hover:border-red-500/50 hover:bg-zinc-800"
                        >
                          {preset.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* 16 Built-in Mod Color Themes */}
                  <div className="space-y-2">
                    <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                      Clone Built-in Mod Palette (16)
                    </div>
                    <div className="grid grid-cols-4 gap-1.5">
                      {SMARTPHONE_COLORS.map((c) => (
                        <button
                          key={c.id}
                          onClick={() => handleCloneBuiltinColorTheme(c.id)}
                          className="flex items-center gap-1.5 rounded-lg border border-zinc-800 bg-zinc-900/70 px-2 py-1.5 text-[11px] text-zinc-300 hover:border-zinc-700 hover:bg-zinc-800"
                        >
                          <span
                            className="h-2.5 w-2.5 shrink-0 rounded-full"
                            style={{ backgroundColor: c.hex }}
                          />
                          <span className="truncate">{c.label}</span>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Quick Export */}
                  <div className="space-y-2 border-t border-zinc-800 pt-4">
                    <button
                      onClick={() =>
                        triggerDownload(
                          new Blob([serializedJson], { type: 'application/json' }),
                          `${slugify(activeTheme.id)}.json`
                        )
                      }
                      className="flex w-full items-center justify-center gap-2 rounded-lg border border-zinc-700 bg-zinc-900 py-2 text-xs font-medium text-zinc-100 hover:bg-zinc-800"
                    >
                      <Download className="h-3.5 w-3.5" /> Download Theme JSON
                    </button>
                    <button
                      onClick={async () => {
                        await exportProjectZip(project, assets, 'resourcepack')
                        pushToast('Resource Pack (.zip) exported!', 'success')
                      }}
                      className="flex w-full items-center justify-center gap-2 rounded-lg bg-red-600text-whitehover:bg-red-500"
                    >
                      <Download className="h-3.5 w-3.5" /> Export Resource Pack (.zip)
                    </button>
                  </div>
                </div>
              )}

              {tab === 'colors' && (
                <div className="space-y-4">
                  <div className="flex items-center justify-between rounded-xl border border-zinc-800 bg-zinc-900/60 px-3 py-2 text-[11px]">
                    <span className="text-zinc-400">Detected Surface Mode</span>
                    <span className="rounded bg-zinc-800 px-2 py-0.5 font-semibold text-red-300">
                      {isDarkSurface(activeTheme.colors.surface) ? 'Dark Mode' : 'Light Mode'}
                    </span>
                  </div>

                  {THEME_COLOR_GROUPS.map((group) => (
                    <div key={group.title} className="space-y-2">
                      <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                        {group.title}
                      </div>
                      <div className="space-y-1.5">
                        {group.keys.map(({ key, label, hint }) => (
                          <div
                            key={key}
                            title={hint}
                            className="flex items-center justify-between gap-2 rounded-lg border border-zinc-800/80 bg-zinc-900/50 px-2.5 py-1.5"
                          >
                            <div className="min-w-0">
                              <div className="truncate text-xs font-medium text-zinc-200">{label}</div>
                              <div className="truncate text-[10px] text-zinc-500">{hint}</div>
                            </div>
                            <div className="flex shrink-0 items-center gap-1.5">
                              <input
                                type="text"
                                value={activeTheme.colors[key]}
                                onChange={(e) =>
                                  updateTheme({
                                    colors: { ...activeTheme.colors, [key]: e.target.value },
                                  })
                                }
                                className="w-20 rounded border border-zinc-800 bg-zinc-950 px-1.5 py-1 font-mono text-[10px] text-zinc-200 outline-none focus:border-red-500"
                              />
                              <input
                                type="color"
                                value={
                                  activeTheme.colors[key].length === 9
                                    ? `#${activeTheme.colors[key].slice(3)}`
                                    : activeTheme.colors[key].slice(0, 7)
                                }
                                onChange={(e) =>
                                  updateTheme({
                                    colors: {
                                      ...activeTheme.colors,
                                      [key]: e.target.value.toUpperCase(),
                                    },
                                  })
                                }
                                className="h-6 w-6 cursor-pointer rounded border-0 bg-transparent"
                              />
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {tab === 'assets' && (
                <div className="space-y-3">
                  {(
                    [
                      {
                        field: 'wallpaperAssetId',
                        label: 'Standard Wallpaper',
                        dims: '131×207 px',
                        w: 131,
                        h: 207,
                      },
                      {
                        field: 'largeWallpaperAssetId',
                        label: 'Large Wallpaper',
                        dims: '211×207 px',
                        w: 211,
                        h: 207,
                      },
                      {
                        field: 'frameAssetId',
                        label: 'Custom Phone Frame',
                        dims: '131×207 px',
                        w: 131,
                        h: 207,
                      },
                      {
                        field: 'largeFrameAssetId',
                        label: 'Custom Large Frame',
                        dims: '211×207 px',
                        w: 211,
                        h: 207,
                      },
                    ] as const
                  ).map((slot) => {
                    const assetId = activeTheme[slot.field]
                    const asset = assetId ? assetMap.get(assetId) : undefined
                    return (
                      <div
                        key={slot.field}
                        className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-3"
                      >
                        <div className="mb-2 flex items-center justify-between">
                          <div>
                            <div className="text-xs font-semibold text-zinc-100">{slot.label}</div>
                            <div className="text-[10px] text-zinc-500">Recommended: {slot.dims}</div>
                          </div>
                          {asset && (
                            <button
                              onClick={() => updateTheme({ [slot.field]: undefined })}
                              title="Reset to default"
                              className="rounded p-1 text-zinc-400 hover:bg-zinc-800 hover:text-red-400"
                            >
                              <X className="h-3.5 w-3.5" />
                            </button>
                          )}
                        </div>
                        <div className="flex items-center gap-3">
                          {asset && (
                            <img
                              src={asset.dataUrl}
                              alt={slot.label}
                              className="pixelated h-16 w-12 rounded border border-zinc-700 bg-zinc-950 object-contain"
                            />
                          )}
                          <label className="flex flex-1 cursor-pointer items-center justify-center gap-2 rounded-lg border border-dashed border-zinc-700 bg-zinc-950 px-3 py-2.5 text-xs text-zinc-300 transition hover:border-red-500/60 hover:text-red-300">
                            <Upload className="h-3.5 w-3.5" />
                            {asset ? `${asset.name} (${asset.width}×${asset.height})` : 'Upload PNG'}
                            <input
                              type="file"
                              accept="image/png"
                              onChange={(e) =>
                                uploadThemeImage(e.target.files?.[0], slot.field, slot.w, slot.h)
                              }
                              className="hidden"
                            />
                          </label>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}

              {tab === 'elements' && (
                <div className="space-y-2.5">
                  <p className="text-[11px] text-zinc-400">
                    Override individual UI sprites. Omitted elements automatically use the mod defaults.
                  </p>
                  {THEME_ELEMENTS.map((el) => {
                    const assetId = activeTheme.elements[el.key]
                    const asset = assetId ? assetMap.get(assetId) : undefined
                    return (
                      <div
                        key={el.key}
                        className="flex items-center justify-between gap-2.5 rounded-xl border border-zinc-800 bg-zinc-900/50 p-2.5"
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          <img
                            src={asset?.dataUrl ?? el.defaultPath}
                            alt={el.label}
                            className="pixelated h-9 w-9 shrink-0 rounded-lg border border-zinc-800 bg-zinc-950 object-contain p-1"
                          />
                          <div className="min-w-0">
                            <div className="truncate text-xs font-medium text-zinc-100">{el.label}</div>
                            <div className="truncate text-[10px] text-zinc-500">
                              {el.dimensions} • {el.layout}
                            </div>
                          </div>
                        </div>
                        <div className="flex shrink-0 items-center gap-1">
                          <label className="cursor-pointer rounded-lg border border-zinc-700 bg-zinc-950 px-2 py-1 text-[11px] text-zinc-200 hover:border-red-500">
                            Upload
                            <input
                              type="file"
                              accept="image/png"
                              onChange={(e) => uploadElementImage(e.target.files?.[0], el.key)}
                              className="hidden"
                            />
                          </label>
                          {asset && (
                            <button
                              onClick={() => {
                                const next = { ...activeTheme.elements }
                                delete next[el.key]
                                updateTheme({ elements: next })
                              }}
                              title="Reset"
                              className="rounded p-1 text-zinc-400 hover:text-red-400"
                            >
                              <RotateCcw className="h-3.5 w-3.5" />
                            </button>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}

              {tab === 'icons' && (
                <div className="space-y-3">
                  <div className="relative">
                    <Search className="pointer-events-none absolute top-2 left-2.5 h-3.5 w-3.5 text-zinc-500" />
                    <input
                      value={iconSearch}
                      onChange={(e) => setIconSearch(e.target.value)}
                      placeholder="Filter 23 built-in apps..."
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 py-1.5 pr-3 pl-8 text-xs outline-none focus:border-red-500"
                    />
                  </div>

                  {/* Add Custom Datapack App ID */}
                  <div className="flex gap-1.5">
                    <input
                      value={customAppId}
                      onChange={(e) => setCustomAppId(e.target.value)}
                      placeholder="Custom app ID (e.g. mypack:warp)"
                      className="flex-1 rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono text-[11px] outline-none focus:border-red-500"
                    />
                    <button
                      onClick={() => {
                        const clean = customAppId.trim()
                        if (!clean) return
                        updateTheme({
                          appIcons: { ...activeTheme.appIcons, [clean]: {} },
                        })
                        setCustomAppId('')
                      }}
                      className="rounded-lg bg-zinc-800 px-2.5 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-700"
                    >
                      Add
                    </button>
                  </div>

                  <div className="space-y-2">
                    {allAppEntries.map((app) => {
                      const override = activeTheme.appIcons[app.id]
                      const normalAsset = override?.textureAssetId
                        ? assetMap.get(override.textureAssetId)
                        : undefined
                      const hoverAsset = override?.hoverAssetId
                        ? assetMap.get(override.hoverAssetId)
                        : undefined
                      const hasOverride = Boolean(normalAsset || hoverAsset)

                      return (
                        <div
                          key={app.id}
                          className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-2.5"
                        >
                          <div className="mb-2 flex items-center justify-between gap-2">
                            <div className="min-w-0">
                              <div className="flex items-center gap-1.5 text-xs font-medium text-zinc-100">
                                <span className="truncate">{app.label}</span>
                                <span
                                  className={`rounded px-1.5 py-0.2 text-[9px] font-semibold ${
                                    hasOverride
                                      ? 'bg-amber-500/20 text-amber-300'
                                      : 'bg-red-500/15 text-red-300'
                                  }`}
                                >
                                  {hasOverride ? 'Custom' : 'Style B'}
                                </span>
                              </div>
                              <div className="truncate font-mono text-[10px] text-zinc-500">
                                {app.id}
                              </div>
                            </div>
                            {hasOverride && (
                              <button
                                onClick={() => {
                                  const next = { ...activeTheme.appIcons }
                                  delete next[app.id]
                                  updateTheme({ appIcons: next })
                                }}
                                title="Reset to Style B"
                                className="rounded p-1 text-zinc-400 hover:text-red-400"
                              >
                                <RotateCcw className="h-3.5 w-3.5" />
                              </button>
                            )}
                          </div>

                          <div className="grid grid-cols-2 gap-1.5">
                            <label className="flex cursor-pointer items-center gap-1.5 rounded-lg border border-zinc-800 bg-zinc-950 px-2 py-1.5 text-[11px] text-zinc-300 hover:border-red-500">
                              {normalAsset ? (
                                <img src={normalAsset.dataUrl} className="pixelated h-5 w-5" alt="" />
                              ) : (
                                <Upload className="h-3 w-3 text-zinc-500" />
                              )}
                              <span className="truncate">Normal 36×36</span>
                              <input
                                type="file"
                                accept="image/png"
                                onChange={(e) =>
                                  uploadAppIcon(e.target.files?.[0], app.id, 'textureAssetId')
                                }
                                className="hidden"
                              />
                            </label>
                            <label className="flex cursor-pointer items-center gap-1.5 rounded-lg border border-zinc-800 bg-zinc-950 px-2 py-1.5 text-[11px] text-zinc-300 hover:border-red-500">
                              {hoverAsset ? (
                                <img src={hoverAsset.dataUrl} className="pixelated h-5 w-5" alt="" />
                              ) : (
                                <Upload className="h-3 w-3 text-zinc-500" />
                              )}
                              <span className="truncate">Hover 36×36</span>
                              <input
                                type="file"
                                accept="image/png"
                                onChange={(e) =>
                                  uploadAppIcon(e.target.files?.[0], app.id, 'hoverAssetId')
                                }
                                className="hidden"
                              />
                            </label>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}

              {tab === 'json' && (
                <div className="flex h-full flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-[11px] text-zinc-400">
                      assets/{project.namespace}/themes/{activeTheme.id}.json
                    </span>
                    <div className="flex gap-1.5">
                      <button
                        onClick={() => {
                          navigator.clipboard.writeText(serializedJson)
                          pushToast('Theme JSON copied!', 'success')
                        }}
                        className="flex items-center gap-1 rounded-md border border-zinc-800 bg-zinc-900 px-2 py-1 text-[11px] text-zinc-200 hover:bg-zinc-800"
                      >
                        <Copy className="h-3 w-3" /> Copy
                      </button>
                      <button
                        onClick={() =>
                          triggerDownload(
                            new Blob([serializedJson], { type: 'application/json' }),
                            `${slugify(activeTheme.id)}.json`
                          )
                        }
                        className="flex items-center gap-1 rounded-md bg-red-600text-whitehover:bg-red-500"
                      >
                        <Download className="h-3 w-3" /> Save
                      </button>
                    </div>
                  </div>
                  <pre className="flex-1 overflow-auto rounded-xl border border-zinc-800 bg-zinc-900/60 p-3 font-mono text-[11px] leading-relaxed text-red-200">
                    {serializedJson}
                  </pre>
                </div>
              )}
            </div>
          </div>
        </>
      ) : (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 text-sm text-zinc-400">
          <p>No themes in this project yet.</p>
          <button
            onClick={createTheme}
            className="rounded-lg bg-red-600text-whitehover:bg-red-500"
          >
            Create First Theme
          </button>
        </div>
      )}
    </div>
  )
}
