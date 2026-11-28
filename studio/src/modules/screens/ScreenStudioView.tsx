import { useMemo, useState } from 'react'
import {
  ArrowDown,
  ArrowUp,
  FileUp,
  Layers,
  Palette,
  Plus,
  Trash2,
} from 'lucide-react'
import { useActiveProject, useStudioStore } from '@/store/studioStore'
import {
  DARK_THEME_COLORS,
  type ScreenNodeDef,
  type StudioScreen,
} from '@/types/schemas'
import {
  COMPONENT_CATALOG,
  CONTAINER_TYPES,
  createScreenFromTemplate,
  deleteNodeAtPath,
  getNodeAtPath,
  insertChildAtPath,
  moveNodeAtPath,
  updateNodeAtPath,
  type ComponentCategory,
} from '@/modules/screens/screenCatalog'
import { ScreenLivePreview } from '@/modules/screens/ScreenLivePreview'
import {
  ScreenNodeInspector,
  type ScreenInspectorTab,
} from '@/modules/screens/ScreenNodeInspector'
import { createId, slugify } from '@/lib/utils'

const CATEGORIES: ComponentCategory[] = ['Layout', 'Navigation', 'Controls', 'Feedback & Data']

export function ScreenStudioView() {
  const project = useActiveProject()
  const updateActiveProject = useStudioStore((s) => s.updateActiveProject)
  const pushToast = useStudioStore((s) => s.pushToast)

  const [selectedUid, setSelectedUid] = useState<string | null>(null)
  const [selectedPath, setSelectedPath] = useState<number[] | null>([0])
  const [inspectorTab, setInspectorTab] = useState<ScreenInspectorTab>('node')
  const [leftTab, setLeftTab] = useState<'tree' | 'palette'>('tree')
  const [previewThemeUid, setPreviewThemeUid] = useState<string>('first')

  const screens = project?.screens ?? []
  const activeScreen = useMemo(
    () => screens.find((s) => s.uid === selectedUid) ?? screens[0] ?? null,
    [screens, selectedUid]
  )

  const previewColors = useMemo(() => {
    if (!project || project.themes.length === 0) return DARK_THEME_COLORS
    const found = project.themes.find((t) => t.uid === previewThemeUid)
    return (found ?? project.themes[0]).colors
  }, [project, previewThemeUid])

  const selectedNode = useMemo(
    () => (activeScreen && selectedPath ? getNodeAtPath(activeScreen.body, selectedPath) : null),
    [activeScreen, selectedPath]
  )

  if (!project) return null

  const updateScreen = (patch: Partial<StudioScreen>) => {
    if (!activeScreen) return
    updateActiveProject((p) => ({
      ...p,
      screens: p.screens.map((s) =>
        s.uid === activeScreen.uid ? { ...s, ...patch, updatedAt: Date.now() } : s
      ),
    }))
  }

  const addFromTemplate = (tpl: 'blank' | 'dashboard' | 'settings' | 'shop') => {
    const s = createScreenFromTemplate(tpl, screens.length + 1)
    updateActiveProject((p) => ({ ...p, screens: [...p.screens, s] }))
    setSelectedUid(s.uid)
    setSelectedPath([0])
    pushToast(`Created "${s.title}" from template.`, 'success')
  }

  const handleImportJson = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    try {
      const parsed = JSON.parse(await file.text())
      const rawId = typeof parsed.id === 'string' ? parsed.id : file.name.replace(/\.json$/i, '')
      const cleanId = slugify(rawId.includes(':') ? rawId.split(':')[1] : rawId)
      const imported: StudioScreen = {
        uid: createId('screen'),
        id: cleanId,
        title: parsed.title || cleanId,
        viewport: parsed.viewport || 'large',
        width_dp: parsed.width_dp,
        height_dp: parsed.height_dp,
        corner_radius: parsed.corner_radius ?? 16,
        show_bezel_back: parsed.show_bezel_back ?? true,
        show_bezel_battery: parsed.show_bezel_battery ?? true,
        show_bezel_hd_badge: parsed.show_bezel_hd_badge ?? true,
        default_state: parsed.default_state ?? {},
        actions: parsed.actions ?? {},
        body: Array.isArray(parsed.body) ? parsed.body : [],
        updatedAt: Date.now(),
      }
      updateActiveProject((p) => ({ ...p, screens: [...p.screens, imported] }))
      setSelectedUid(imported.uid)
      setSelectedPath(imported.body.length > 0 ? [0] : null)
      pushToast(`Imported screen "${imported.title}".`, 'success')
    } catch {
      pushToast('Failed to parse screen JSON.', 'error')
    }
  }

  const handleInsertComponent = (node: ScreenNodeDef) => {
    if (!activeScreen) return
    const targetParent =
      selectedPath && selectedNode && CONTAINER_TYPES.has(selectedNode.type)
        ? selectedPath
        : null
    const { body, insertedPath } = insertChildAtPath(activeScreen.body, targetParent, node)
    updateScreen({ body })
    setSelectedPath(insertedPath)
    setInspectorTab('node')
  }

  const renderTreeNodes = (nodes: ScreenNodeDef[], basePath: number[] = []) => {
    return nodes.map((node, idx) => {
      const path = [...basePath, idx]
      const key = path.join('-')
      const isSelected =
        Boolean(selectedPath) &&
        selectedPath!.length === path.length &&
        selectedPath!.every((v, i) => v === path[i])
      const label = node.title || node.label || node.text?.slice(0, 18) || node.state_key || ''

      return (
        <div key={key}>
          <div
            onClick={() => {
              setSelectedPath(path)
              setInspectorTab('node')
            }}
            style={{ paddingLeft: `${basePath.length * 12 + 8}px` }}
            className={`group flex cursor-pointer items-center justify-between rounded-md py-1 pr-1.5 text-[11px] transition ${
              isSelected
                ? 'bg-violet-500/20 text-violet-200 ring-1 ring-violet-500/40'
                : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
            }`}
          >
            <div className="flex min-w-0 items-center gap-1.5">
              <span className="font-mono text-[10px] text-red-300">{node.type}</span>
              {label && <span className="truncate text-zinc-400">{label}</span>}
            </div>
            <div className="flex shrink-0 items-center gap-0.5 opacity-0 group-hover:opacity-100">
              <button
                title="Move Up"
                onClick={(e) => {
                  e.stopPropagation()
                  if (!activeScreen) return
                  const { body, nextPath } = moveNodeAtPath(activeScreen.body, path, -1)
                  updateScreen({ body })
                  setSelectedPath(nextPath)
                }}
                className="rounded p-0.5 hover:bg-zinc-800 hover:text-zinc-100"
              >
                <ArrowUp className="h-3 w-3" />
              </button>
              <button
                title="Move Down"
                onClick={(e) => {
                  e.stopPropagation()
                  if (!activeScreen) return
                  const { body, nextPath } = moveNodeAtPath(activeScreen.body, path, 1)
                  updateScreen({ body })
                  setSelectedPath(nextPath)
                }}
                className="rounded p-0.5 hover:bg-zinc-800 hover:text-zinc-100"
              >
                <ArrowDown className="h-3 w-3" />
              </button>
              <button
                title="Delete"
                onClick={(e) => {
                  e.stopPropagation()
                  if (!activeScreen) return
                  updateScreen({ body: deleteNodeAtPath(activeScreen.body, path) })
                  setSelectedPath(null)
                }}
                className="rounded p-0.5 hover:bg-zinc-800 hover:text-red-400"
              >
                <Trash2 className="h-3 w-3" />
              </button>
            </div>
          </div>
          {node.children && node.children.length > 0 && renderTreeNodes(node.children, path)}
        </div>
      )
    })
  }

  return (
    <div className="flex h-full min-h-0">
      {/* Left Pane: Screen Selector + Tree / Component Palette */}
      <div className="flex w-72 shrink-0 flex-col border-r border-zinc-800/80 bg-zinc-950">
        {/* Top Screen Switcher */}
        <div className="space-y-2 border-b border-zinc-800 p-3">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
              UI Screens ({screens.length})
            </span>
            <div className="flex items-center gap-1">
              <label
                title="Import Screen JSON"
                className="cursor-pointer rounded-md border border-zinc-800 bg-zinc-900 p-1 text-zinc-300 hover:bg-zinc-800"
              >
                <FileUp className="h-3.5 w-3.5" />
                <input type="file" accept="application/json,.json" onChange={handleImportJson} className="hidden" />
              </label>
              <button
                onClick={() => addFromTemplate('blank')}
                className="flex items-center gap-0.5 rounded-md bg-violet-500/20 px-2 py-1 text-[11px] font-medium text-violet-300 hover:bg-violet-500/30"
              >
                <Plus className="h-3 w-3" /> Blank
              </button>
            </div>
          </div>

          {/* Quick Templates */}
          <div className="grid grid-cols-3 gap-1 text-[10px]">
            {(
              [
                ['dashboard', '+ Dashboard'],
                ['settings', '+ Settings'],
                ['shop', '+ Shop'],
              ] as const
            ).map(([tpl, label]) => (
              <button
                key={tpl}
                onClick={() => addFromTemplate(tpl)}
                className="rounded border border-zinc-800 bg-zinc-900/70 py-1 text-zinc-300 hover:border-violet-500/40 hover:text-violet-200"
              >
                {label}
              </button>
            ))}
          </div>

          <div className="max-h-28 space-y-1 overflow-auto pt-1">
            {screens.map((s) => (
              <div
                key={s.uid}
                onClick={() => {
                  setSelectedUid(s.uid)
                  setSelectedPath(s.body.length > 0 ? [0] : null)
                }}
                className={`group flex cursor-pointer items-center justify-between rounded-lg px-2 py-1.5 text-xs ${
                  activeScreen?.uid === s.uid
                    ? 'bg-zinc-800 text-zinc-100'
                    : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
                }`}
              >
                <span className="truncate font-medium">{s.title}</span>
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    updateActiveProject((p) => ({
                      ...p,
                      screens: p.screens.filter((x) => x.uid !== s.uid),
                    }))
                    pushToast('Screen deleted.', 'info')
                  }}
                  className="opacity-0 hover:text-red-400 group-hover:opacity-100"
                >
                  <Trash2 className="h-3 w-3" />
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* Tree vs Palette Toggle */}
        <div className="grid grid-cols-2 border-b border-zinc-800 p-1.5 text-xs">
          <button
            onClick={() => setLeftTab('tree')}
            className={`flex items-center justify-center gap-1.5 rounded-lg py-1.5 font-medium transition ${
              leftTab === 'tree' ? 'bg-zinc-800 text-zinc-100' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            <Layers className="h-3.5 w-3.5" /> Tree
          </button>
          <button
            onClick={() => setLeftTab('palette')}
            className={`flex items-center justify-center gap-1.5 rounded-lg py-1.5 font-medium transition ${
              leftTab === 'palette' ? 'bg-violet-500/20 text-violet-300' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            <Plus className="h-3.5 w-3.5" /> Components
          </button>
        </div>

        {/* Tree / Palette Content */}
        <div className="flex-1 overflow-auto p-2.5">
          {leftTab === 'tree' ? (
            activeScreen ? (
              <div className="space-y-0.5">
                {renderTreeNodes(activeScreen.body)}
                {activeScreen.body.length === 0 && (
                  <p className="py-6 text-center text-xs text-zinc-500">
                    Empty screen. Switch to Components to add nodes.
                  </p>
                )}
              </div>
            ) : null
          ) : (
            <div className="space-y-4">
              {selectedNode && CONTAINER_TYPES.has(selectedNode.type) && (
                <div className="rounded-lg border border-red-500/30 bg-red-500/10 px-2.5 py-1.5 text-[10px] text-red-200">
                  Inserting inside selected <span className="font-mono font-bold">&lt;{selectedNode.type}&gt;</span>
                </div>
              )}
              {CATEGORIES.map((cat) => (
                <div key={cat} className="space-y-1.5">
                  <div className="text-[10px] font-semibold uppercase tracking-wider text-zinc-500">
                    {cat}
                  </div>
                  <div className="grid grid-cols-2 gap-1.5">
                    {COMPONENT_CATALOG.filter((c) => c.category === cat).map((item) => (
                      <button
                        key={item.type}
                        title={item.description}
                        onClick={() => handleInsertComponent(item.createDefault())}
                        className="rounded-lg border border-zinc-800 bg-zinc-900/60 px-2 py-1.5 text-left text-[11px] font-medium text-zinc-200 transition hover:border-violet-500/50 hover:bg-zinc-800"
                      >
                        + {item.label}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Preview Theme Picker Footer */}
        {project.themes.length > 0 && (
          <div className="flex items-center gap-2 border-t border-zinc-800 px-3 py-2 text-[11px] text-zinc-400">
            <Palette className="h-3.5 w-3.5 text-red-400" />
            <span className="shrink-0">Preview Theme:</span>
            <select
              value={previewThemeUid}
              onChange={(e) => setPreviewThemeUid(e.target.value)}
              className="min-w-0 flex-1 bg-transparent font-medium text-zinc-200 outline-none"
            >
              {project.themes.map((t) => (
                <option key={t.uid} value={t.uid} className="bg-zinc-900 text-zinc-100">
                  {t.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Center Live Interactive Preview + Right Inspector */}
      {activeScreen ? (
        <>
          <ScreenLivePreview
            screen={activeScreen}
            colors={previewColors}
            selectedPath={selectedPath}
            onSelectPath={(path) => {
              setSelectedPath(path)
              setInspectorTab('node')
            }}
          />
          <ScreenNodeInspector
            namespace={project.namespace}
            screen={activeScreen}
            selectedNode={selectedNode}
            tab={inspectorTab}
            onTabChange={setInspectorTab}
            onUpdateNode={(patch) => {
              if (!selectedPath) return
              updateScreen({
                body: updateNodeAtPath(activeScreen.body, selectedPath, (n) => ({ ...n, ...patch })),
              })
            }}
            onUpdateScreen={updateScreen}
            onToast={pushToast}
          />
        </>
      ) : (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 text-sm text-zinc-400">
          <p>No UI screens in this project yet.</p>
          <button
            onClick={() => addFromTemplate('dashboard')}
            className="rounded-lg bg-violet-500 px-4 py-2 text-xs font-semibold text-zinc-950 hover:bg-violet-400"
          >
            Create Trainer Dashboard Screen
          </button>
        </div>
      )}
    </div>
  )
}
