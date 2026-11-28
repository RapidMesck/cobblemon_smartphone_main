import { create } from 'zustand'
import { db, createStarterProject, ensureInitialProject } from '@/storage/db'
import type { StudioAsset, StudioModule, StudioProject } from '@/types/schemas'
import { createId, slugify } from '@/lib/utils'

interface ToastMessage {
  id: string
  text: string
  kind: 'info' | 'success' | 'error'
}

interface StudioState {
  initialized: boolean
  projects: StudioProject[]
  activeProjectId: string | null
  activeModule: StudioModule
  assetsByProject: Record<string, StudioAsset[]>
  toasts: ToastMessage[]

  init: () => Promise<void>
  setActiveModule: (module: StudioModule) => void
  selectProject: (projectId: string) => Promise<void>
  createProject: (name: string, namespace: string, description?: string) => Promise<StudioProject>
  updateActiveProject: (updater: (project: StudioProject) => StudioProject) => Promise<void>
  deleteProject: (projectId: string) => Promise<void>
  addAsset: (file: File, kind?: 'png' | 'model_json') => Promise<StudioAsset | null>
  updateTextAsset: (assetId: string, content: string) => Promise<void>
  deleteAsset: (assetId: string) => Promise<void>
  pushToast: (text: string, kind?: ToastMessage['kind']) => void
  dismissToast: (id: string) => void
}

export const useStudioStore = create<StudioState>((set, get) => ({
  initialized: false,
  projects: [],
  activeProjectId: null,
  activeModule: 'dashboard',
  assetsByProject: {},
  toasts: [],

  init: async () => {
    if (get().initialized) return
    const projects = await ensureInitialProject()
    const savedId = localStorage.getItem('cs_studio_active_project')
    const activeProject = projects.find((p) => p.id === savedId) ?? projects[0]
    const assets = activeProject
      ? await db.assets.where('projectId').equals(activeProject.id).toArray()
      : []

    set({
      initialized: true,
      projects,
      activeProjectId: activeProject?.id ?? null,
      assetsByProject: activeProject ? { [activeProject.id]: assets } : {},
    })
  },

  setActiveModule: (module) => set({ activeModule: module }),

  selectProject: async (projectId) => {
    localStorage.setItem('cs_studio_active_project', projectId)
    const assets = await db.assets.where('projectId').equals(projectId).toArray()
    set((state) => ({
      activeProjectId: projectId,
      assetsByProject: { ...state.assetsByProject, [projectId]: assets },
    }))
  },

  createProject: async (name, namespace, description = '') => {
    const now = Date.now()
    const cleanNs = slugify(namespace || name)
    const project: StudioProject = {
      id: createId('proj'),
      name: name.trim() || 'New Project',
      namespace: cleanNs,
      description: description.trim(),
      packFormatResource: 34,
      packFormatData: 48,
      themes: [],
      screens: [],
      actions: [],
      skins: [],
      recipes: [],
      createdAt: now,
      updatedAt: now,
    }
    await db.projects.put(project)
    localStorage.setItem('cs_studio_active_project', project.id)
    set((state) => ({
      projects: [project, ...state.projects],
      activeProjectId: project.id,
      assetsByProject: { ...state.assetsByProject, [project.id]: [] },
    }))
    get().pushToast(`Project "${project.name}" created locally.`, 'success')
    return project
  },

  updateActiveProject: async (updater) => {
    const { projects, activeProjectId } = get()
    const current = projects.find((p) => p.id === activeProjectId)
    if (!current) return
    const updated: StudioProject = {
      ...updater(current),
      updatedAt: Date.now(),
    }
    await db.projects.put(updated)
    set({
      projects: projects.map((p) => (p.id === updated.id ? updated : p)),
    })
  },

  deleteProject: async (projectId) => {
    await db.projects.delete(projectId)
    await db.assets.where('projectId').equals(projectId).delete()
    let remaining = get().projects.filter((p) => p.id !== projectId)
    if (remaining.length === 0) {
      const starter = createStarterProject()
      await db.projects.put(starter)
      remaining = [starter]
    }
    const nextActive = remaining[0]
    localStorage.setItem('cs_studio_active_project', nextActive.id)
    const assets = await db.assets.where('projectId').equals(nextActive.id).toArray()
    set((state) => ({
      projects: remaining,
      activeProjectId: nextActive.id,
      assetsByProject: { ...state.assetsByProject, [nextActive.id]: assets },
    }))
    get().pushToast('Project deleted from local storage.', 'info')
  },

  addAsset: async (file, kind) => {
    const projectId = get().activeProjectId
    if (!projectId) return null

    const inferredKind: 'png' | 'model_json' =
      kind ?? (file.name.endsWith('.json') ? 'model_json' : 'png')

    const dataUrl = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result))
      reader.onerror = () => reject(reader.error)
      reader.readAsDataURL(file)
    })

    let width: number | undefined
    let height: number | undefined
    if (inferredKind === 'png') {
      await new Promise<void>((resolve) => {
        const img = new Image()
        img.onload = () => {
          width = img.naturalWidth
          height = img.naturalHeight
          resolve()
        }
        img.onerror = () => resolve()
        img.src = dataUrl
      })
    }

    const asset: StudioAsset = {
      id: createId('asset'),
      projectId,
      name: file.name,
      kind: inferredKind,
      mimeType: file.type || (inferredKind === 'png' ? 'image/png' : 'application/json'),
      dataUrl,
      sizeBytes: file.size,
      width,
      height,
      createdAt: Date.now(),
    }

    await db.assets.put(asset)
    set((state) => ({
      assetsByProject: {
        ...state.assetsByProject,
        [projectId]: [asset, ...(state.assetsByProject[projectId] ?? [])],
      },
    }))
    return asset
  },

  updateTextAsset: async (assetId, content) => {
    const projectId = get().activeProjectId
    if (!projectId) return
    const list = get().assetsByProject[projectId] ?? []
    const existing = list.find((a) => a.id === assetId)
    if (!existing) return
    const bytes = new TextEncoder().encode(content)
    let bin = ''
    for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i])
    const dataUrl = `data:application/json;base64,${btoa(bin)}`
    const updated: StudioAsset = { ...existing, dataUrl, sizeBytes: bytes.length }
    await db.assets.put(updated)
    set((state) => ({
      assetsByProject: {
        ...state.assetsByProject,
        [projectId]: (state.assetsByProject[projectId] ?? []).map((a) =>
          a.id === assetId ? updated : a
        ),
      },
    }))
  },

  deleteAsset: async (assetId) => {
    const projectId = get().activeProjectId
    if (!projectId) return
    await db.assets.delete(assetId)
    set((state) => ({
      assetsByProject: {
        ...state.assetsByProject,
        [projectId]: (state.assetsByProject[projectId] ?? []).filter((a) => a.id !== assetId),
      },
    }))
  },

  pushToast: (text, kind = 'info') => {
    const id = createId('toast')
    set((state) => ({ toasts: [...state.toasts, { id, text, kind }] }))
    setTimeout(() => {
      get().dismissToast(id)
    }, 3200)
  },

  dismissToast: (id) => {
    set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) }))
  },
}))

export function useActiveProject(): StudioProject | null {
  return useStudioStore(
    (s) => s.projects.find((p) => p.id === s.activeProjectId) ?? null
  )
}

export function useActiveProjectAssets(): StudioAsset[] {
  return useStudioStore((s) =>
    s.activeProjectId ? (s.assetsByProject[s.activeProjectId] ?? []) : []
  )
}
