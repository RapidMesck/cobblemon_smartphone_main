import { useState, type ReactNode } from 'react'
import {
  Box,
  Code2,
  CreditCard,
  Download,
  FolderPlus,
  HardDrive,
  LayoutDashboard,
  LayoutTemplate,
  Palette,
  Sparkles,
  X,
  Zap,
} from 'lucide-react'
import { useActiveProject, useActiveProjectAssets, useStudioStore } from '@/store/studioStore'
import { exportProjectZip } from '@/export/packExporter'
import type { StudioModule } from '@/types/schemas'
import { cn } from '@/lib/utils'

const NAV_ITEMS: Array<{
  id: StudioModule
  label: string
  badge?: string
  icon: typeof LayoutDashboard
}> = [
  { id: 'dashboard', label: 'Overview', icon: LayoutDashboard },
  { id: 'themes', label: 'Theme Studio', icon: Palette },
  { id: 'screens', label: 'UI Screens', icon: LayoutTemplate },
  { id: 'actions', label: 'Actions & Apps', icon: Zap },
  { id: 'skins', label: '3D Skins', icon: Box },
  { id: 'recipes', label: 'Recipes & SD Cards', icon: CreditCard },
  { id: 'code', label: 'Code', icon: Code2 },
]

export function StudioShell({ children }: { children: ReactNode }) {
  const projects = useStudioStore((s) => s.projects)
  const activeProject = useActiveProject()
  const assets = useActiveProjectAssets()
  const activeModule = useStudioStore((s) => s.activeModule)
  const setActiveModule = useStudioStore((s) => s.setActiveModule)
  const selectProject = useStudioStore((s) => s.selectProject)
  const createProject = useStudioStore((s) => s.createProject)
  const pushToast = useStudioStore((s) => s.pushToast)
  const toasts = useStudioStore((s) => s.toasts)
  const dismissToast = useStudioStore((s) => s.dismissToast)

  const [newProjOpen, setNewProjOpen] = useState(false)
  const [newName, setNewName] = useState('')
  const [newNs, setNewNs] = useState('')

  const handleExport = async (mode: 'combined' | 'resourcepack' | 'datapack') => {
    if (!activeProject) return
    await exportProjectZip(activeProject, assets, mode)
    pushToast(`Exported ${mode} pack successfully!`, 'success')
  }

  const handleCreateProject = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newName.trim()) return
    await createProject(newName, newNs || newName)
    setNewName('')
    setNewNs('')
    setNewProjOpen(false)
  }

  return (
    <div className="flex h-screen flex-col bg-zinc-950 text-zinc-100">
      {/* Top Header */}
      <header className="flex h-13 shrink-0 items-center justify-between border-b border-zinc-800/80 bg-zinc-950/90 px-4 backdrop-blur">
        <div className="flex items-center gap-3">
          <img
            src="/mod-assets/icon.png"
            alt="Cobblemon Smartphone"
            className="pixelated h-9 w-9 shrink-0 object-contain"
          />
          <div className="leading-tight">
            <div className="flex items-center gap-2 text-sm font-semibold tracking-tight">
              Cobblemon Smartphone Studio
              <span className="rounded bg-red-500/15 px-1.5 py-0.5 text-[10px] font-medium text-red-300 ring-1 ring-red-500/30">
                ALPHA
              </span>
            </div>
            <div className="text-[11px] text-zinc-400">
              Local Creator Hub • Minecraft 1.21.1
            </div>
          </div>
        </div>

        {/* Project Switcher & Export */}
        <div className="flex items-center gap-2.5">
          <div className="flex items-center gap-1.5 rounded-lg border border-zinc-800 bg-zinc-900/80 px-2.5 py-1">
            <HardDrive className="h-3.5 w-3.5 text-emerald-400" />
            <select
              value={activeProject?.id ?? ''}
              onChange={(e) => selectProject(e.target.value)}
              className="bg-transparent text-xs font-medium text-zinc-100 outline-none"
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id} className="bg-zinc-900 text-zinc-100">
                  {p.name} ({p.namespace})
                </option>
              ))}
            </select>
            <button
              onClick={() => setNewProjOpen(true)}
              title="New Project"
              className="ml-1 rounded p-1 text-zinc-400 transition hover:bg-zinc-800 hover:text-zinc-100"
            >
              <FolderPlus className="h-3.5 w-3.5" />
            </button>
          </div>

          <button
            onClick={() => handleExport('combined')}
            className="flex items-center gap-1.5 rounded-lg bg-red-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm shadow-red-600/25 transition hover:bg-red-500"
          >
            <Download className="h-3.5 w-3.5" />
            Export Pack (.zip)
          </button>
        </div>
      </header>

      {/* Main Body */}
      <div className="flex min-h-0 flex-1">
        {/* Left Navigation Rail */}
        <aside className="flex w-56 shrink-0 flex-col justify-between border-r border-zinc-800/80 bg-zinc-950 p-3">
          <nav className="space-y-1">
            {NAV_ITEMS.map((item) => {
              const Icon = item.icon
              const active = activeModule === item.id
              return (
                <button
                  key={item.id}
                  onClick={() => setActiveModule(item.id)}
                  className={cn(
                    'flex w-full items-center justify-between rounded-lg px-3 py-2 text-xs font-medium transition',
                    active
                      ? 'bg-red-500/15 text-red-300 ring-1 ring-red-500/35'
                      : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-100'
                  )}
                >
                  <span className="flex items-center gap-2.5">
                    <Icon className="h-4 w-4" />
                    {item.label}
                  </span>
                  {item.badge && (
                    <span className="rounded bg-violet-500/20 px-1.5 py-0.5 text-[10px] font-semibold text-violet-300">
                      {item.badge}
                    </span>
                  )}
                </button>
              )
            })}
          </nav>

          <div className="rounded-xl border border-zinc-800/80 bg-zinc-900/50 p-3 text-[11px] text-zinc-400">
            <div className="mb-1 flex items-center gap-1.5 font-medium text-zinc-200">
              <Sparkles className="h-3.5 w-3.5 text-red-400" />
              100% Local Storage
            </div>
            <p className="leading-relaxed">
              Projects, PNG textures, and 3D models are automatically saved in your browser IndexedDB.
            </p>
          </div>
        </aside>

        {/* Active Workspace */}
        <main className="min-h-0 min-w-0 flex-1 overflow-auto bg-zinc-950">
          {children}
        </main>
      </div>

      {/* New Project Modal */}
      {newProjOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-xs">
          <form
            onSubmit={handleCreateProject}
            className="w-full max-w-md rounded-2xl border border-zinc-800 bg-zinc-900 p-5 shadow-2xl"
          >
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-sm font-semibold text-zinc-100">Create New Project</h2>
              <button
                type="button"
                onClick={() => setNewProjOpen(false)}
                className="rounded p-1 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="mb-1 block text-xs text-zinc-400">Project Name</label>
                <input
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  placeholder="e.g. Neon Kanto Pack"
                  className="w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm outline-none focus:border-red-500"
                  required
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-zinc-400">
                  Namespace (lowercase, no spaces)
                </label>
                <input
                  value={newNs}
                  onChange={(e) => setNewNs(e.target.value)}
                  placeholder="e.g. neon_kanto"
                  className="w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 font-mono text-sm outline-none focus:border-red-500"
                />
              </div>
            </div>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setNewProjOpen(false)}
                className="rounded-lg border border-zinc-700 px-3 py-1.5 text-xs text-zinc-300 hover:bg-zinc-800"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-red-600 px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-red-500"
              >
                Create Project
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Toasts */}
      <div className="pointer-events-none fixed right-4 bottom-4 z-50 flex flex-col gap-2">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={cn(
              'pointer-events-auto flex items-center gap-2 rounded-xl border px-3.5 py-2.5 text-xs font-medium shadow-xl backdrop-blur',
              t.kind === 'success' && 'border-emerald-500/30 bg-emerald-950/90 text-emerald-200',
              t.kind === 'error' && 'border-red-500/30 bg-red-950/90 text-red-200',
              t.kind === 'info' && 'border-zinc-700 bg-zinc-900/95 text-zinc-100'
            )}
          >
            <span>{t.text}</span>
            <button onClick={() => dismissToast(t.id)} className="opacity-60 hover:opacity-100">
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}
