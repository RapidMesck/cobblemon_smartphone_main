import { useMemo, useState } from 'react'
import {
  Code2,
  Copy,
  Download,
  FileUp,
  Lock,
  Plus,
  Sliders,
  Trash2,
  Upload,
  Zap,
} from 'lucide-react'
import { useActiveProject, useActiveProjectAssets, useStudioStore } from '@/store/studioStore'
import { BUILTIN_ACTION_FUNCTIONS, type StudioAction } from '@/types/schemas'
import { serializeActionJson } from '@/export/packExporter'
import { createId, slugify, triggerDownload } from '@/lib/utils'

type ActionTab = 'general' | 'behavior' | 'json'

export function ActionStudioView() {
  const project = useActiveProject()
  const assets = useActiveProjectAssets()
  const updateActiveProject = useStudioStore((s) => s.updateActiveProject)
  const addAsset = useStudioStore((s) => s.addAsset)
  const pushToast = useStudioStore((s) => s.pushToast)

  const [selectedUid, setSelectedUid] = useState<string | null>(null)
  const [tab, setTab] = useState<ActionTab>('behavior')
  const [hoverPreview, setHoverPreview] = useState(false)
  const [newCmd, setNewCmd] = useState('')
  const [newItemId, setNewItemId] = useState('')
  const [newItemMode, setNewItemMode] = useState<'use' | 'finish_using'>('use')

  const actions = project?.actions ?? []
  const activeAction = useMemo(
    () => actions.find((a) => a.uid === selectedUid) ?? actions[0] ?? null,
    [actions, selectedUid]
  )
  const assetMap = useMemo(() => new Map(assets.map((a) => [a.id, a])), [assets])

  if (!project) return null

  const updateAction = (patch: Partial<StudioAction>) => {
    if (!activeAction) return
    updateActiveProject((p) => ({
      ...p,
      actions: p.actions.map((a) =>
        a.uid === activeAction.uid ? { ...a, ...patch, updatedAt: Date.now() } : a
      ),
    }))
  }

  const createAction = () => {
    const a: StudioAction = {
      uid: createId('action'),
      id: `custom_app_${actions.length + 1}`,
      displayName: `Custom App ${actions.length + 1}`,
      functions: ['open_pc'],
      commands: [],
      use_items: [],
      order: 50,
      cooldown_seconds: 5,
      updatedAt: Date.now(),
    }
    updateActiveProject((p) => ({ ...p, actions: [...p.actions, a] }))
    setSelectedUid(a.uid)
  }

  const handleImportJson = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    try {
      const parsed = JSON.parse(await file.text())
      const rawId = typeof parsed.id === 'string' ? parsed.id : file.name.replace(/\.json$/i, '')
      const cleanId = slugify(rawId.includes(':') ? rawId.split(':')[1] : rawId)
      const imported: StudioAction = {
        uid: createId('action'),
        id: cleanId,
        displayName: cleanId,
        open_screen: parsed.open_screen,
        functions: Array.isArray(parsed.functions) ? parsed.functions : [],
        commands: Array.isArray(parsed.commands) ? parsed.commands : [],
        use_items: Array.isArray(parsed.use_items) ? parsed.use_items : [],
        patchouli_book: parsed.patchouli_book,
        order: parsed.order ?? 50,
        cooldown_seconds: parsed.cooldown_seconds ?? 0,
        fe_cost: parsed.fe_cost,
        require_mod: parsed.require_mod,
        require_upgrade: parsed.require_upgrade,
        updatedAt: Date.now(),
      }
      updateActiveProject((p) => ({ ...p, actions: [...p.actions, imported] }))
      setSelectedUid(imported.uid)
      pushToast(`Imported action "${imported.id}".`, 'success')
    } catch {
      pushToast('Failed to parse action JSON.', 'error')
    }
  }

  const uploadIcon = async (file: File | undefined, slot: 'textureAssetId' | 'hoverTextureAssetId') => {
    if (!file || !activeAction) return
    const asset = await addAsset(file, 'png')
    if (asset) {
      updateAction({ [slot]: asset.id })
      pushToast(`Uploaded ${file.name} (${asset.width}×${asset.height}).`, 'success')
    }
  }

  const normalAsset = activeAction?.textureAssetId ? assetMap.get(activeAction.textureAssetId) : undefined
  const hoverAsset = activeAction?.hoverTextureAssetId ? assetMap.get(activeAction.hoverTextureAssetId) : undefined
  const serializedJson = activeAction
    ? JSON.stringify(serializeActionJson(project.namespace, activeAction), null, 2)
    : ''

  return (
    <div className="flex h-full min-h-0">
      {/* Left Action List */}
      <div className="flex w-60 shrink-0 flex-col border-r border-zinc-800/80 bg-zinc-950 p-3">
        <div className="mb-2.5 flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
            Actions ({actions.length})
          </span>
          <div className="flex items-center gap-1">
            <label
              title="Import Action JSON"
              className="cursor-pointer rounded-md border border-zinc-800 bg-zinc-900 p-1 text-zinc-300 hover:bg-zinc-800"
            >
              <FileUp className="h-3.5 w-3.5" />
              <input type="file" accept="application/json,.json" onChange={handleImportJson} className="hidden" />
            </label>
            <button
              onClick={createAction}
              className="flex items-center gap-1 rounded-md bg-amber-500/20 px-2 py-1 text-xs font-medium text-amber-300 hover:bg-amber-500/30"
            >
              <Plus className="h-3.5 w-3.5" /> New
            </button>
          </div>
        </div>

        <div className="flex-1 space-y-1 overflow-auto">
          {actions.map((a) => (
            <div
              key={a.uid}
              onClick={() => setSelectedUid(a.uid)}
              className={`group flex cursor-pointer items-center justify-between rounded-lg px-2.5 py-2 text-xs transition ${
                activeAction?.uid === a.uid
                  ? 'bg-zinc-800 text-zinc-50 ring-1 ring-zinc-700'
                  : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
              }`}
            >
              <div className="min-w-0">
                <div className="truncate font-medium">{a.displayName}</div>
                <div className="truncate font-mono text-[10px] text-zinc-500">
                  {project.namespace}:{a.id}
                </div>
              </div>
              <button
                onClick={(e) => {
                  e.stopPropagation()
                  updateActiveProject((p) => ({
                    ...p,
                    actions: p.actions.filter((x) => x.uid !== a.uid),
                  }))
                  if (selectedUid === a.uid) setSelectedUid(null)
                }}
                className="opacity-0 hover:text-red-400 group-hover:opacity-100"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </div>
          ))}
        </div>
      </div>

      {/* Center Interactive App Preview & Pipeline */}
      {activeAction ? (
        <>
          <div className="flex flex-1 flex-col items-center justify-center gap-6 overflow-auto bg-[radial-gradient(#27272a_1px,transparent_1px)] [background-size:18px_18px] p-6">
            {/* App Icon Card Preview */}
            <div className="flex flex-col items-center gap-3 rounded-3xl border border-zinc-800 bg-zinc-900/80 p-6 shadow-2xl">
              <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                Home Screen Button Preview (Hover to test)
              </div>
              <button
                type="button"
                onMouseEnter={() => setHoverPreview(true)}
                onMouseLeave={() => setHoverPreview(false)}
                className="relative flex h-24 w-24 items-center justify-center rounded-2xl border-2 border-red-500/40 bg-zinc-950 transition hover:scale-105 hover:border-red-400"
              >
                {hoverPreview && hoverAsset ? (
                  <img src={hoverAsset.dataUrl} className="pixelated h-20 w-20" alt="Hover" />
                ) : normalAsset ? (
                  <img src={normalAsset.dataUrl} className="pixelated h-20 w-20" alt="Normal" />
                ) : (
                  <Zap className={`h-10 w-10 transition ${hoverPreview ? 'text-amber-300' : 'text-red-400'}`} />
                )}
                {activeAction.require_upgrade && (
                  <span className="absolute -top-2 -right-2 rounded-full bg-amber-500 p-1 text-zinc-950 shadow">
                    <Lock className="h-3 w-3" />
                  </span>
                )}
              </button>
              <div className="text-center">
                <div className="text-sm font-bold text-zinc-100">{activeAction.displayName}</div>
                <div className="font-mono text-[11px] text-zinc-400">
                  {project.namespace}:{activeAction.id}
                </div>
              </div>
              <div className="flex flex-wrap justify-center gap-1.5 text-[10px]">
                {activeAction.cooldown_seconds > 0 && (
                  <span className="rounded-full bg-zinc-800 px-2.5 py-0.5 text-zinc-300">
                    Cooldown: {activeAction.cooldown_seconds}s
                  </span>
                )}
                {activeAction.fe_cost ? (
                  <span className="rounded-full bg-amber-500/20 px-2.5 py-0.5 text-amber-300">
                    {activeAction.fe_cost} FE
                  </span>
                ) : null}
                {activeAction.require_mod && (
                  <span className="rounded-full bg-violet-500/20 px-2.5 py-0.5 text-violet-300">
                    Mod: {activeAction.require_mod}
                  </span>
                )}
              </div>
            </div>

            {/* Execution Pipeline */}
            <div className="w-full max-w-md space-y-2 rounded-2xl border border-zinc-800 bg-zinc-900/70 p-4 text-xs">
              <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                Execution Pipeline
              </div>
              <div className="space-y-1.5 font-mono text-[11px]">
                {activeAction.open_screen && (
                  <div className="rounded-lg bg-violet-500/15 px-3 py-1.5 text-violet-200">
                    1. open_screen → {activeAction.open_screen}
                  </div>
                )}
                {activeAction.functions.map((fn) => (
                  <div key={fn} className="rounded-lg bg-red-500/15 px-3 py-1.5 text-red-300">
                    function → {fn}()
                  </div>
                ))}
                {activeAction.use_items.map((u, i) => (
                  <div key={i} className="rounded-lg bg-emerald-500/15 px-3 py-1.5 text-emerald-200">
                    use_item ({u.mode}) → {u.id}
                  </div>
                ))}
                {activeAction.commands.map((cmd, i) => (
                  <div key={i} className="rounded-lg bg-amber-500/15 px-3 py-1.5 text-amber-200">
                    command → /{cmd}
                  </div>
                ))}
                {activeAction.patchouli_book && (
                  <div className="rounded-lg bg-pink-500/15 px-3 py-1.5 text-pink-200">
                    patchouli_book → {activeAction.patchouli_book}
                  </div>
                )}
                {!activeAction.open_screen &&
                  activeAction.functions.length === 0 &&
                  activeAction.use_items.length === 0 &&
                  activeAction.commands.length === 0 &&
                  !activeAction.patchouli_book && (
                    <div className="rounded-lg border border-dashed border-red-500/40 px-3 py-2 text-red-300">
                      Add at least one screen, function, use_item, command, or Patchouli book.
                    </div>
                  )}
              </div>
            </div>
          </div>

          {/* Right Tabbed Inspector */}
          <div className="flex w-[380px] shrink-0 flex-col border-l border-zinc-800/80 bg-zinc-950">
            <div className="grid grid-cols-3 border-b border-zinc-800 p-1.5 text-[11px]">
              {(
                [
                  { id: 'general', label: 'Info', icon: Sliders },
                  { id: 'behavior', label: 'Behavior', icon: Zap },
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
                        ? 'bg-amber-500/20 text-amber-300'
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
              {tab === 'general' && (
                <div className="space-y-3.5">
                  <div>
                    <label className="mb-1 block text-[11px] text-zinc-400">Display Name (Studio label)</label>
                    <input
                      value={activeAction.displayName}
                      onChange={(e) => updateAction({ displayName: e.target.value })}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none focus:border-amber-500"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="mb-1 block text-[11px] text-zinc-400">Action ID</label>
                      <input
                        value={activeAction.id}
                        onChange={(e) => updateAction({ id: slugify(e.target.value) })}
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono outline-none"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-[11px] text-zinc-400">Sort Order</label>
                      <input
                        type="number"
                        value={activeAction.order}
                        onChange={(e) => updateAction({ order: Number(e.target.value) || 0 })}
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none"
                      />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="mb-1 block text-[11px] text-zinc-400">Cooldown (sec)</label>
                      <input
                        type="number"
                        min={0}
                        value={activeAction.cooldown_seconds}
                        onChange={(e) => updateAction({ cooldown_seconds: Number(e.target.value) || 0 })}
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-[11px] text-zinc-400">FE Energy Cost</label>
                      <input
                        type="number"
                        min={0}
                        value={activeAction.fe_cost ?? 0}
                        onChange={(e) =>
                          updateAction({ fe_cost: Number(e.target.value) || undefined })
                        }
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none"
                      />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="mb-1 block text-[11px] text-zinc-400">Require Mod ID</label>
                      <input
                        value={activeAction.require_mod ?? ''}
                        onChange={(e) => updateAction({ require_mod: e.target.value || undefined })}
                        placeholder="e.g. waystones"
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono outline-none"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-[11px] text-zinc-400">Require Upgrade Key</label>
                      <input
                        value={activeAction.require_upgrade ?? ''}
                        onChange={(e) =>
                          updateAction({ require_upgrade: e.target.value ? slugify(e.target.value) : undefined })
                        }
                        placeholder="upgrade_portal"
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono outline-none"
                      />
                    </div>
                  </div>

                  {/* 36x36 Icon Uploaders */}
                  <div className="space-y-2 rounded-xl border border-zinc-800 bg-zinc-900/50 p-3">
                    <div className="text-[11px] font-semibold text-zinc-200">Button Textures (36×36 PNG)</div>
                    <div className="grid grid-cols-2 gap-2">
                      <label className="flex cursor-pointer items-center justify-center gap-1.5 rounded-lg border border-dashed border-zinc-700 bg-zinc-950 py-2 text-[11px] text-zinc-300 hover:border-amber-400">
                        <Upload className="h-3.5 w-3.5" />
                        {normalAsset ? 'Replace Normal' : 'Normal PNG'}
                        <input
                          type="file"
                          accept="image/png"
                          onChange={(e) => uploadIcon(e.target.files?.[0], 'textureAssetId')}
                          className="hidden"
                        />
                      </label>
                      <label className="flex cursor-pointer items-center justify-center gap-1.5 rounded-lg border border-dashed border-zinc-700 bg-zinc-950 py-2 text-[11px] text-zinc-300 hover:border-amber-400">
                        <Upload className="h-3.5 w-3.5" />
                        {hoverAsset ? 'Replace Hover' : 'Hover PNG'}
                        <input
                          type="file"
                          accept="image/png"
                          onChange={(e) => uploadIcon(e.target.files?.[0], 'hoverTextureAssetId')}
                          className="hidden"
                        />
                      </label>
                    </div>
                  </div>
                </div>
              )}

              {tab === 'behavior' && (
                <div className="space-y-4">
                  {/* Link to UI Screen */}
                  <div>
                    <label className="mb-1 block text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                      Open Custom UI Screen (open_screen)
                    </label>
                    <select
                      value={activeAction.open_screen ?? ''}
                      onChange={(e) => updateAction({ open_screen: e.target.value || undefined })}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono text-[11px] outline-none"
                    >
                      <option value="">None</option>
                      {project.screens.map((s) => (
                        <option key={s.uid} value={`${project.namespace}:${s.id}`}>
                          {project.namespace}:{s.id} ({s.title})
                        </option>
                      ))}
                    </select>
                  </div>

                  {/* 12 Built-in Functions */}
                  <div className="space-y-1.5">
                    <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                      Built-in Functions ({BUILTIN_ACTION_FUNCTIONS.length})
                    </div>
                    <div className="grid grid-cols-2 gap-1.5">
                      {BUILTIN_ACTION_FUNCTIONS.map((fn) => {
                        const checked = activeAction.functions.includes(fn.id)
                        return (
                          <button
                            key={fn.id}
                            title={fn.description}
                            onClick={() =>
                              updateAction({
                                functions: checked
                                  ? activeAction.functions.filter((x) => x !== fn.id)
                                  : [...activeAction.functions, fn.id],
                              })
                            }
                            className={`rounded-lg border px-2 py-1.5 text-left text-[11px] transition ${
                              checked
                                ? 'border-red-500/50 bg-red-500/15 font-semibold text-red-300'
                                : 'border-zinc-800 bg-zinc-900/50 text-zinc-400 hover:text-zinc-200'
                            }`}
                          >
                            {fn.label}
                          </button>
                        )
                      })}
                    </div>
                  </div>

                  {/* Simulated Item Use */}
                  <div className="space-y-2 border-t border-zinc-800 pt-3">
                    <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                      Simulated Item Use (use_items)
                    </div>
                    {activeAction.use_items.map((u, idx) => (
                      <div key={idx} className="flex items-center justify-between rounded-lg border border-zinc-800 bg-zinc-900/60 px-2.5 py-1.5 font-mono text-[11px]">
                        <span>
                          {u.id} <span className="text-zinc-500">({u.mode})</span>
                        </span>
                        <button
                          onClick={() =>
                            updateAction({
                              use_items: activeAction.use_items.filter((_, i) => i !== idx),
                            })
                          }
                          className="text-zinc-500 hover:text-red-400"
                        >
                          <Trash2 className="h-3 w-3" />
                        </button>
                      </div>
                    ))}
                    <div className="flex gap-1">
                      <input
                        value={newItemId}
                        onChange={(e) => setNewItemId(e.target.value)}
                        placeholder="cobblemon:poke_flute"
                        className="min-w-0 flex-1 rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1 font-mono text-[11px] outline-none"
                      />
                      <select
                        value={newItemMode}
                        onChange={(e) => setNewItemMode(e.target.value as 'use' | 'finish_using')}
                        className="rounded-lg border border-zinc-800 bg-zinc-900 px-1.5 py-1 text-[10px] outline-none"
                      >
                        <option value="use">use</option>
                        <option value="finish_using">finish</option>
                      </select>
                      <button
                        onClick={() => {
                          if (!newItemId.trim()) return
                          updateAction({
                            use_items: [...activeAction.use_items, { id: newItemId.trim(), mode: newItemMode }],
                          })
                          setNewItemId('')
                        }}
                        className="rounded-lg bg-zinc-800 px-2 text-zinc-200 hover:bg-zinc-700"
                      >
                        <Plus className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>

                  {/* Commands */}
                  <div className="space-y-2 border-t border-zinc-800 pt-3">
                    <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                      Commands
                    </div>
                    {activeAction.commands.map((cmd, idx) => (
                      <div key={idx} className="flex items-center justify-between gap-2 rounded-lg border border-zinc-800 bg-zinc-900/60 px-2.5 py-1.5 font-mono text-[11px]">
                        <span className="truncate">/{cmd}</span>
                        <button
                          onClick={() =>
                            updateAction({
                              commands: activeAction.commands.filter((_, i) => i !== idx),
                            })
                          }
                          className="text-zinc-500 hover:text-red-400"
                        >
                          <Trash2 className="h-3 w-3" />
                        </button>
                      </div>
                    ))}
                    <div className="flex gap-1">
                      <input
                        value={newCmd}
                        onChange={(e) => setNewCmd(e.target.value)}
                        placeholder="say Hello from smartphone!"
                        className="min-w-0 flex-1 rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1 font-mono text-[11px] outline-none"
                      />
                      <button
                        onClick={() => {
                          if (!newCmd.trim()) return
                          updateAction({ commands: [...activeAction.commands, newCmd.trim()] })
                          setNewCmd('')
                        }}
                        className="rounded-lg bg-zinc-800 px-2 text-zinc-200 hover:bg-zinc-700"
                      >
                        <Plus className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>

                  {/* Patchouli Book */}
                  <div className="border-t border-zinc-800 pt-3">
                    <label className="mb-1 block text-[11px] text-zinc-400">Patchouli Book ID (optional)</label>
                    <input
                      value={activeAction.patchouli_book ?? ''}
                      onChange={(e) => updateAction({ patchouli_book: e.target.value || undefined })}
                      placeholder="my_mod:guidebook_id"
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono text-[11px] outline-none"
                    />
                  </div>
                </div>
              )}

              {tab === 'json' && (
                <div className="flex h-full flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <span className="truncate font-mono text-[10px] text-zinc-400">
                      data/{project.namespace}/smartphone_actions/{activeAction.id}.json
                    </span>
                    <div className="flex gap-1">
                      <button
                        onClick={() => {
                          navigator.clipboard.writeText(serializedJson)
                          pushToast('Action JSON copied!', 'success')
                        }}
                        className="rounded border border-zinc-800 bg-zinc-900 p-1 text-zinc-300 hover:bg-zinc-800"
                      >
                        <Copy className="h-3.5 w-3.5" />
                      </button>
                      <button
                        onClick={() =>
                          triggerDownload(
                            new Blob([serializedJson], { type: 'application/json' }),
                            `${slugify(activeAction.id)}.json`
                          )
                        }
                        className="rounded bg-amber-400 p-1 text-zinc-950 hover:bg-amber-300"
                      >
                        <Download className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>
                  <pre className="flex-1 overflow-auto rounded-xl border border-zinc-800 bg-zinc-900/60 p-3 font-mono text-[11px] leading-relaxed text-amber-100">
                    {serializedJson}
                  </pre>
                </div>
              )}
            </div>
          </div>
        </>
      ) : (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 text-sm text-zinc-400">
          <p>No custom actions in this project yet.</p>
          <button
            onClick={createAction}
            className="rounded-lg bg-amber-400 px-4 py-2 text-xs font-semibold text-zinc-950 hover:bg-amber-300"
          >
            Create First Action
          </button>
        </div>
      )}
    </div>
  )
}
