import Dexie, { type EntityTable } from 'dexie'
import type { StudioAsset, StudioProject } from '@/types/schemas'
import { createId } from '@/lib/utils'

class CobblemonSmartphoneStudioDB extends Dexie {
  projects!: EntityTable<StudioProject, 'id'>
  assets!: EntityTable<StudioAsset, 'id'>

  constructor() {
    super('CobblemonSmartphoneStudioDB')
    this.version(1).stores({
      projects: 'id, namespace, updatedAt',
      assets: 'id, projectId, kind, createdAt',
    })
  }
}

export const db = new CobblemonSmartphoneStudioDB()

export function createStarterProject(): StudioProject {
  const now = Date.now()
  return {
    id: createId('proj'),
    name: 'Untitled Pack',
    namespace: 'mypack',
    description: '',
    packFormatResource: 34, // Minecraft 1.21.1 resource pack format
    packFormatData: 48, // Minecraft 1.21.1 data pack format
    themes: [],
    screens: [],
    actions: [],
    skins: [],
    recipes: [],
    createdAt: now,
    updatedAt: now,
  }
}

export async function ensureInitialProject(): Promise<StudioProject[]> {
  const existing = await db.projects.orderBy('updatedAt').reverse().toArray()
  if (existing.length > 0) return existing
  const starter = createStarterProject()
  await db.projects.put(starter)
  return [starter]
}
