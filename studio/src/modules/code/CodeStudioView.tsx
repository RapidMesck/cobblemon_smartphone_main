import { useEffect, useMemo, useState } from 'react'
import {
  ChevronDown,
  ChevronRight,
  Copy,
  Download,
  FileCode2,
  Folder,
  FolderOpen,
  Image as ImageIcon,
  Save,
  Search,
  Sparkles,
} from 'lucide-react'
import { useActiveProject, useActiveProjectAssets, useStudioStore } from '@/store/studioStore'
import {
  applyVirtualFileSave,
  buildFileTree,
  buildVirtualFiles,
  type VirtualTreeNode,
} from '@/modules/code/packVirtualFs'
import { formatBytes, triggerDownload } from '@/lib/utils'

export function CodeStudioView() {
  const project = useActiveProject()
  const assets = useActiveProjectAssets()
  const updateActiveProject = useStudioStore((s) => s.updateActiveProject)
  const updateTextAsset = useStudioStore((s) => s.updateTextAsset)
  const pushToast = useStudioStore((s) => s.pushToast)

  const [selectedPath, setSelectedPath] = useState<string>('pack.mcmeta')
  const [collapsedDirs, setCollapsedDirs] = useState<Record<string, boolean>>({})
  const [filter, setFilter] = useState('')
  const [draft, setDraft] = useState('')
  const [dirty, setDirty] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const files = useMemo(
    () => (project ? buildVirtualFiles(project, assets) : []),
    [project, assets]
  )

  const filteredFiles = useMemo(() => {
    const q = filter.trim().toLowerCase()
    return q ? files.filter((f) => f.path.toLowerCase().includes(q)) : files
  }, [files, filter])

  const tree = useMemo(() => buildFileTree(filteredFiles), [filteredFiles])

  const activeFile = useMemo(
    () => files.find((f) => f.path === selectedPath) ?? files[0] ?? null,
    [files, selectedPath]
  )

  // Sync draft when switching selected file or when external state changes cleanly
  useEffect(() => {
    if (!activeFile) return
    setDraft(activeFile.content ?? '')
    setDirty(false)
    setError(null)
  }, [activeFile?.path, activeFile?.content])

  if (!project) return null

  const handleSave = () => {
    if (!activeFile || !activeFile.editable) return
    try {
      JSON.parse(draft)
      setError(null)
      if (activeFile.kind === 'model_json' && activeFile.assetId) {
        void updateTextAsset(activeFile.assetId, draft)
      } else {
        updateActiveProject((p) => applyVirtualFileSave(p, activeFile, draft))
      }
      setDirty(false)
      pushToast(`Saved ${activeFile.name}`, 'success')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid JSON syntax')
    }
  }

  const handleFormat = () => {
    try {
      const formatted = JSON.stringify(JSON.parse(draft), null, 2)
      setDraft(formatted)
      setDirty(true)
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot format invalid JSON')
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
      e.preventDefault()
      handleSave()
      return
    }
    if (e.key === 'Tab') {
      e.preventDefault()
      const el = e.currentTarget
      const start = el.selectionStart
      const end = el.selectionEnd
      const next = draft.slice(0, start) + '  ' + draft.slice(end)
      setDraft(next)
      setDirty(true)
      requestAnimationFrame(() => {
        el.selectionStart = el.selectionEnd = start + 2
      })
    }
  }

  const renderTree = (nodes: VirtualTreeNode[], depth = 0) => {
    return nodes.map((node) => {
      if (node.isDir) {
        const isCollapsed = Boolean(collapsedDirs[node.path])
        return (
          <div key={node.path}>
            <button
              type="button"
              onClick={() =>
                setCollapsedDirs((prev) => ({ ...prev, [node.path]: !isCollapsed }))
              }
              style={{ paddingLeft: `${depth * 12 + 8}px` }}
              className="flex w-full items-center gap-1.5 rounded-md py-1 pr-2 text-left text-xs text-zinc-300 hover:bg-zinc-900"
            >
              {isCollapsed ? (
                <ChevronRight className="h-3.5 w-3.5 shrink-0 text-zinc-500" />
              ) : (
                <ChevronDown className="h-3.5 w-3.5 shrink-0 text-zinc-500" />
              )}
              {isCollapsed ? (
                <Folder className="h-3.5 w-3.5 shrink-0 text-amber-400/80" />
              ) : (
                <FolderOpen className="h-3.5 w-3.5 shrink-0 text-amber-400/80" />
              )}
              <span className="truncate font-medium">{node.name}</span>
            </button>
            {!isCollapsed && renderTree(node.children, depth + 1)}
          </div>
        )
      }

      const file = node.file!
      const isSelected = activeFile?.path === file.path
      const isPng = file.kind === 'png'
      return (
        <button
          key={file.path}
          type="button"
          onClick={() => setSelectedPath(file.path)}
          style={{ paddingLeft: `${depth * 12 + 22}px` }}
          className={`flex w-full items-center gap-1.5 rounded-md py-1 pr-2 text-left text-xs transition ${
            isSelected
              ? 'bg-red-500/15 font-medium text-red-300 ring-1 ring-red-500/30'
              : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
          }`}
        >
          {isPng ? (
            <ImageIcon className="h-3.5 w-3.5 shrink-0 text-emerald-400" />
          ) : (
            <FileCode2 className="h-3.5 w-3.5 shrink-0 text-red-400" />
          )}
          <span className="truncate font-mono text-[11px]">{node.name}</span>
        </button>
      )
    })
  }

  const lineCount = draft.split('\n').length

  return (
    <div className="flex h-full min-h-0">
      {/* Left Pack Explorer */}
      <div className="flex w-72 shrink-0 flex-col border-r border-zinc-800/80 bg-zinc-950">
        <div className="border-b border-zinc-800 p-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
              Pack Explorer ({files.length})
            </span>
            <span className="font-mono text-[10px] text-zinc-500">{project.namespace}/</span>
          </div>
          <div className="relative">
            <Search className="pointer-events-none absolute top-2 left-2.5 h-3.5 w-3.5 text-zinc-500" />
            <input
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              placeholder="Filter files..."
              className="w-full rounded-lg border border-zinc-800 bg-zinc-900 py-1.5 pr-2.5 pl-8 text-xs outline-none focus:border-red-500"
            />
          </div>
        </div>

        <div className="flex-1 space-y-0.5 overflow-auto p-2">{renderTree(tree)}</div>
      </div>

      {/* Right Code / Asset Editor */}
      {activeFile ? (
        <div className="flex min-w-0 flex-1 flex-col bg-zinc-950">
          {/* Top Breadcrumb & Actions Bar */}
          <div className="flex items-center justify-between gap-3 border-b border-zinc-800 bg-zinc-900/40 px-4 py-2">
            <div className="flex min-w-0 items-center gap-2">
              <span className="truncate font-mono text-xs text-zinc-200">{activeFile.path}</span>
              {dirty && (
                <span className="rounded bg-amber-500/20 px-1.5 py-0.5 text-[10px] font-medium text-amber-300">
                  Unsaved
                </span>
              )}
              {!activeFile.editable && activeFile.kind !== 'png' && (
                <span className="rounded bg-zinc-800 px-1.5 py-0.5 text-[10px] text-zinc-400">
                  Auto-generated
                </span>
              )}
            </div>

            <div className="flex shrink-0 items-center gap-1.5">
              {activeFile.kind !== 'png' && (
                <>
                  {activeFile.editable && (
                    <>
                      <button
                        onClick={handleFormat}
                        className="flex items-center gap-1 rounded-md border border-zinc-800 bg-zinc-900 px-2.5 py-1 text-[11px] text-zinc-300 hover:bg-zinc-800"
                      >
                        <Sparkles className="h-3 w-3" /> Format
                      </button>
                      <button
                        onClick={handleSave}
                        disabled={!dirty}
                        className="flex items-center gap-1 rounded-md bg-red-600text-whitehover:bg-red-500 disabled:opacity-40"
                      >
                        <Save className="h-3 w-3" /> Save (Ctrl+S)
                      </button>
                    </>
                  )}
                  <button
                    onClick={() => {
                      navigator.clipboard.writeText(draft)
                      pushToast(`Copied ${activeFile.name}`, 'success')
                    }}
                    title="Copy contents"
                    className="rounded-md border border-zinc-800 bg-zinc-900 p-1.5 text-zinc-300 hover:bg-zinc-800"
                  >
                    <Copy className="h-3.5 w-3.5" />
                  </button>
                  <button
                    onClick={() =>
                      triggerDownload(new Blob([draft], { type: 'application/json' }), activeFile.name)
                    }
                    title="Download file"
                    className="rounded-md border border-zinc-800 bg-zinc-900 p-1.5 text-zinc-300 hover:bg-zinc-800"
                  >
                    <Download className="h-3.5 w-3.5" />
                  </button>
                </>
              )}
            </div>
          </div>

          {/* Content Area */}
          {activeFile.kind === 'png' && activeFile.dataUrl ? (
            <div className="flex flex-1 flex-col items-center justify-center gap-4 bg-[radial-gradient(#27272a_1px,transparent_1px)] [background-size:16px_16px] p-6">
              <div className="rounded-2xl border border-zinc-800 bg-zinc-900/80 p-6 shadow-xl">
                <img
                  src={activeFile.dataUrl}
                  alt={activeFile.name}
                  className="pixelated max-h-96 max-w-md object-contain"
                />
              </div>
              <div className="flex items-center gap-3 font-mono text-xs text-zinc-400">
                <span>
                  {activeFile.width ?? '?'} × {activeFile.height ?? '?'} px
                </span>
                {activeFile.sizeBytes !== undefined && (
                  <span>• {formatBytes(activeFile.sizeBytes)}</span>
                )}
              </div>
            </div>
          ) : (
            <div className="flex min-h-0 flex-1 overflow-auto bg-zinc-950 font-mono text-xs leading-6">
              {/* Line Numbers Gutter */}
              <div
                aria-hidden="true"
                className="select-none border-r border-zinc-900 bg-zinc-950 px-3 py-4 text-right text-zinc-600"
              >
                {Array.from({ length: lineCount }, (_, i) => (
                  <div key={i}>{i + 1}</div>
                ))}
              </div>

              {/* Textarea Editor */}
              <textarea
                value={draft}
                readOnly={!activeFile.editable}
                onChange={(e) => {
                  setDraft(e.target.value)
                  setDirty(true)
                  setError(null)
                }}
                onKeyDown={handleKeyDown}
                spellCheck={false}
                className="min-h-full flex-1 resize-none bg-transparent p-4 text-red-200 outline-none"
                style={{ tabSize: 2 }}
              />
            </div>
          )}

          {error && (
            <div className="border-t border-red-500/30 bg-red-950/60 px-4 py-2 font-mono text-xs text-red-300">
              JSON Error: {error}
            </div>
          )}
        </div>
      ) : (
        <div className="flex flex-1 items-center justify-center text-sm text-zinc-500">
          Select a file in the Pack Explorer.
        </div>
      )}
    </div>
  )
}
