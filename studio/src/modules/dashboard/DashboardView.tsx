import {
  ArrowRight,
  Box,
  CreditCard,
  Download,
  LayoutTemplate,
  Palette,
  Trash2,
  Zap,
} from 'lucide-react'
import { useActiveProject, useActiveProjectAssets, useStudioStore } from '@/store/studioStore'
import { exportProjectZip } from '@/export/packExporter'
import { formatBytes, slugify } from '@/lib/utils'
import type { StudioModule } from '@/types/schemas'

export function DashboardView() {
  const project = useActiveProject()
  const assets = useActiveProjectAssets()
  const setActiveModule = useStudioStore((s) => s.setActiveModule)
  const updateActiveProject = useStudioStore((s) => s.updateActiveProject)
  const deleteProject = useStudioStore((s) => s.deleteProject)
  const pushToast = useStudioStore((s) => s.pushToast)

  if (!project) return null

  const totalAssetBytes = assets.reduce((acc, a) => acc + a.sizeBytes, 0)

  const modules: Array<{
    id: StudioModule
    title: string
    desc: string
    count: number
    accent: string
    icon: typeof Palette
  }> = [
    {
      id: 'themes',
      title: 'Theme Studio',
      desc: '22 semantic GUI colors, wallpapers, frames, custom app icons, and live 16-color casing preview.',
      count: project.themes.length,
      accent: 'from-red-500/20 to-rose-500/10 border-red-500/30 text-red-300',
      icon: Palette,
    },
    {
      id: 'screens',
      title: 'UI Screen Studio (HD / Pixel)',
      desc: 'Build interactive smartphone_screens/ layouts with live preview, state, tabs, modals, and toasts.',
      count: project.screens.length,
      accent: 'from-violet-500/20 to-fuchsia-500/10 border-violet-500/30 text-violet-300',
      icon: LayoutTemplate,
    },
    {
      id: 'actions',
      title: 'Action & App Studio',
      desc: 'Custom smartphone apps, commands, built-in functions, simulated item use, and upgrade recipes.',
      count: project.actions.length,
      accent: 'from-amber-500/20 to-orange-500/10 border-amber-500/30 text-amber-300',
      icon: Zap,
    },
    {
      id: 'skins',
      title: '3D Skin & Case Studio',
      desc: 'Custom 3D Smartphone & Rotom Phone models, textures, and dedicated GUI frames.',
      count: project.skins.length,
      accent: 'from-emerald-500/20 to-green-500/10 border-emerald-500/30 text-emerald-300',
      icon: Box,
    },
    {
      id: 'recipes',
      title: 'Recipes & SD Cards',
      desc: 'Craftable SD Cards, Smartphone Smithing upgrades, and 3D Skin unlock recipes.',
      count: (project.recipes ?? []).length,
      accent: 'from-sky-500/20 to-blue-500/10 border-sky-500/30 text-sky-300',
      icon: CreditCard,
    },
  ]

  const handleExport = async (mode: 'combined' | 'resourcepack' | 'datapack') => {
    await exportProjectZip(project, assets, mode)
    pushToast(`Exported ${project.namespace}_${mode}.zip`, 'success')
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      {/* Hero Banner */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-red-900/40 bg-gradient-to-br from-zinc-900 via-zinc-900 to-red-950/35 p-6">
        <div className="flex items-center gap-4">
          <img
            src="/mod-assets/icon.png"
            alt="Cobblemon Smartphone"
            className="pixelated hidden h-14 w-14 shrink-0 object-contain sm:block"
          />
          <div className="space-y-1.5">
            <div className="inline-flex items-center gap-2 rounded-full bg-red-500/15 px-2.5 py-0.5 text-[11px] font-medium text-red-300 ring-1 ring-red-500/25">
              Namespace: <span className="font-mono">{project.namespace}</span>
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-zinc-50">{project.name}</h1>
            <p className="max-w-xl text-sm text-zinc-400">
              {project.description ||
                'Design themes, HD UI screens, custom apps, and 3D skins, then export ready-to-load Minecraft packs.'}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => handleExport('resourcepack')}
            className="flex items-center gap-1.5 rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs font-medium text-zinc-200 transition hover:bg-zinc-800"
          >
            <Download className="h-3.5 w-3.5" />
            Resource Pack
          </button>
          <button
            onClick={() => handleExport('datapack')}
            className="flex items-center gap-1.5 rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs font-medium text-zinc-200 transition hover:bg-zinc-800"
          >
            <Download className="h-3.5 w-3.5" />
            Data Pack
          </button>
          <button
            onClick={() => handleExport('combined')}
            className="flex items-center gap-1.5 rounded-lg bg-red-600 px-3.5 py-2 text-xs font-semibold text-white shadow-sm shadow-red-600/25 transition hover:bg-red-500"
          >
            <Download className="h-3.5 w-3.5" />
            Combined Pack (.zip)
          </button>
        </div>
      </div>

      {/* Studio Modules Grid */}
      <div className="grid gap-4 md:grid-cols-2">
        {modules.map((m) => {
          const Icon = m.icon
          return (
            <button
              key={m.id}
              onClick={() => setActiveModule(m.id)}
              className="group flex flex-col justify-between rounded-2xl border border-zinc-800/90 bg-zinc-900/50 p-5 text-left transition hover:border-zinc-700 hover:bg-zinc-900"
            >
              <div>
                <div className="mb-3 flex items-center justify-between">
                  <div
                    className={`flex h-10 w-10 items-center justify-center rounded-xl border bg-gradient-to-br ${m.accent}`}
                  >
                    <Icon className="h-5 w-5" />
                  </div>
                  <span className="rounded-full bg-zinc-800 px-2.5 py-0.5 text-xs font-semibold text-zinc-300">
                    {m.count}
                  </span>
                </div>
                <h2 className="text-sm font-semibold text-zinc-100">{m.title}</h2>
                <p className="mt-1 text-xs leading-relaxed text-zinc-400">{m.desc}</p>
              </div>
              <div className="mt-4 flex items-center gap-1 text-xs font-medium text-red-400 opacity-80 transition group-hover:translate-x-0.5 group-hover:opacity-100">
                Open editor <ArrowRight className="h-3.5 w-3.5" />
              </div>
            </button>
          )
        })}
      </div>

      {/* Project Settings Card */}
      <div className="rounded-2xl border border-zinc-800 bg-zinc-900/40 p-5">
        <div className="mb-4 flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold text-zinc-100">Project Settings</h3>
            <p className="text-xs text-zinc-400">
              {assets.length} asset(s) stored locally ({formatBytes(totalAssetBytes)})
            </p>
          </div>
          <button
            onClick={() => {
              if (confirm(`Delete project "${project.name}"?`)) {
                deleteProject(project.id)
              }
            }}
            className="flex items-center gap-1.5 rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-1.5 text-xs font-medium text-red-300 transition hover:bg-red-500/20"
          >
            <Trash2 className="h-3.5 w-3.5" />
            Delete Project
          </button>
        </div>

        <div className="grid gap-4 md:grid-cols-3">
          <div>
            <label className="mb-1 block text-xs text-zinc-400">Project Name</label>
            <input
              value={project.name}
              onChange={(e) =>
                updateActiveProject((p) => ({ ...p, name: e.target.value }))
              }
              className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-3 py-2 text-xs outline-none focus:border-red-500"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-zinc-400">Namespace</label>
            <input
              value={project.namespace}
              onChange={(e) =>
                updateActiveProject((p) => ({ ...p, namespace: slugify(e.target.value) }))
              }
              className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-3 py-2 font-mono text-xs outline-none focus:border-red-500"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-zinc-400">pack.mcmeta Description</label>
            <input
              value={project.description}
              onChange={(e) =>
                updateActiveProject((p) => ({ ...p, description: e.target.value }))
              }
              className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-3 py-2 text-xs outline-none focus:border-red-500"
            />
          </div>
        </div>
      </div>
    </div>
  )
}
