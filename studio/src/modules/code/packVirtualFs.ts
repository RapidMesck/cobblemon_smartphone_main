import {
  DEFAULT_THEME_COLORS,
  type StudioAsset,
  type StudioProject,
  type ThemeColors,
} from '@/types/schemas'
import {
  resolveModelTextureZipPath,
  serializeActionJson,
  serializeRecipeJson,
  serializeScreenJson,
  serializeSkinJson,
  serializeThemeJson,
} from '@/export/packExporter'
import { slugify } from '@/lib/utils'

export type VirtualFileKind =
  | 'pack_mcmeta'
  | 'theme'
  | 'screen'
  | 'action'
  | 'skin'
  | 'recipe'
  | 'png'
  | 'model_json'

export interface VirtualFile {
  path: string
  name: string
  kind: VirtualFileKind
  editable: boolean
  entityUid?: string
  assetId?: string
  content?: string
  dataUrl?: string
  width?: number
  height?: number
  sizeBytes?: number
}

function decodeBase64DataUrl(dataUrl: string): string {
  try {
    const idx = dataUrl.indexOf(',')
    if (idx < 0) return ''
    const bin = atob(dataUrl.slice(idx + 1))
    const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0))
    return new TextDecoder().decode(bytes)
  } catch {
    return ''
  }
}

export interface VirtualTreeNode {
  name: string
  path: string
  isDir: boolean
  children: VirtualTreeNode[]
  file?: VirtualFile
}

export function buildVirtualFiles(
  project: StudioProject,
  assets: StudioAsset[]
): VirtualFile[] {
  const ns = slugify(project.namespace)
  const assetMap = new Map(assets.map((a) => [a.id, a]))
  const files: VirtualFile[] = []

  // pack.mcmeta
  files.push({
    path: 'pack.mcmeta',
    name: 'pack.mcmeta',
    kind: 'pack_mcmeta',
    editable: true,
    content: JSON.stringify(
      {
        pack: {
          pack_format: project.packFormatResource,
          description: project.description || `${project.name} (Cobblemon Smartphone Studio)`,
        },
      },
      null,
      2
    ),
  })

  const pushPng = (assetId: string | undefined, targetPath: string) => {
    if (!assetId) return
    const asset = assetMap.get(assetId)
    if (!asset) return
    files.push({
      path: targetPath,
      name: targetPath.split('/').pop()!,
      kind: 'png',
      editable: false,
      dataUrl: asset.dataUrl,
      width: asset.width,
      height: asset.height,
      sizeBytes: asset.sizeBytes,
    })
  }

  // Themes
  for (const theme of project.themes) {
    const id = slugify(theme.id)
    files.push({
      path: `assets/${ns}/themes/${id}.json`,
      name: `${id}.json`,
      kind: 'theme',
      editable: true,
      entityUid: theme.uid,
      content: JSON.stringify(serializeThemeJson(ns, theme), null, 2),
    })
    pushPng(theme.wallpaperAssetId, `assets/${ns}/textures/gui/wallpapers/${id}.png`)
    pushPng(theme.largeWallpaperAssetId, `assets/${ns}/textures/gui/wallpapers/${id}_large.png`)
    pushPng(theme.frameAssetId, `assets/${ns}/textures/gui/frames/${id}.png`)
    pushPng(theme.largeFrameAssetId, `assets/${ns}/textures/gui/frames/${id}_large.png`)
    for (const [k, assetId] of Object.entries(theme.elements)) {
      pushPng(assetId, `assets/${ns}/textures/gui/elements/${id}_${k}.png`)
    }
    for (const [appId, override] of Object.entries(theme.appIcons)) {
      const iconSlug = slugify(appId.includes(':') ? appId.split(':')[1] : appId)
      pushPng(override.textureAssetId, `assets/${ns}/textures/gui/apps/${id}_${iconSlug}.png`)
      pushPng(override.hoverAssetId, `assets/${ns}/textures/gui/apps/${id}_${iconSlug}_hover.png`)
    }
  }

  // Skins
  for (const skin of project.skins) {
    const id = slugify(skin.id)
    files.push({
      path: `assets/${ns}/smartphone_skins/${id}.json`,
      name: `${id}.json`,
      kind: 'skin',
      editable: true,
      entityUid: skin.uid,
      content: JSON.stringify(serializeSkinJson(ns, skin), null, 2),
    })
    pushPng(skin.frameAssetId, `assets/${ns}/textures/gui/skin_frames/${id}.png`)
    pushPng(skin.largeFrameAssetId, `assets/${ns}/textures/gui/skin_frames/${id}_large.png`)

    const pushModel = (assetId: string | undefined, targetPath: string) => {
      if (!assetId) return
      const asset = assetMap.get(assetId)
      if (!asset) return
      files.push({
        path: targetPath,
        name: targetPath.split('/').pop()!,
        kind: 'model_json',
        editable: true,
        assetId: asset.id,
        content: decodeBase64DataUrl(asset.dataUrl),
      })
    }
    pushModel(skin.handModelAssetId, `assets/${ns}/models/item/smartphone_skins/${id}_hand.json`)
    pushModel(skin.rotomModelAssetId, `assets/${ns}/models/item/smartphone_skins/${id}_rotom.json`)

    for (const [texRef, assetId] of Object.entries(skin.modelTextures ?? {})) {
      pushPng(assetId, resolveModelTextureZipPath(ns, id, texRef))
    }
  }

  // Screens
  for (const screen of project.screens) {
    const id = slugify(screen.id)
    files.push({
      path: `data/${ns}/smartphone_screens/${id}.json`,
      name: `${id}.json`,
      kind: 'screen',
      editable: true,
      entityUid: screen.uid,
      content: JSON.stringify(serializeScreenJson(ns, screen), null, 2),
    })
  }

  // Actions
  for (const action of project.actions) {
    const id = slugify(action.id)
    files.push({
      path: `data/${ns}/smartphone_actions/${id}.json`,
      name: `${id}.json`,
      kind: 'action',
      editable: true,
      entityUid: action.uid,
      content: JSON.stringify(serializeActionJson(ns, action), null, 2),
    })
    pushPng(action.textureAssetId, `assets/${ns}/textures/gui/buttons/${id}.png`)
    pushPng(action.hoverTextureAssetId, `assets/${ns}/textures/gui/buttons/${id}_hover.png`)
  }

  // Recipes & SD Cards
  for (const recipe of project.recipes ?? []) {
    const id = slugify(recipe.id)
    files.push({
      path: `data/${ns}/recipe/${id}.json`,
      name: `${id}.json`,
      kind: 'recipe',
      editable: false,
      entityUid: recipe.uid,
      content: JSON.stringify(serializeRecipeJson(ns, recipe), null, 2),
    })
  }

  return files
}

export function buildFileTree(files: VirtualFile[]): VirtualTreeNode[] {
  const root: VirtualTreeNode = { name: '', path: '', isDir: true, children: [] }

  for (const file of files) {
    const parts = file.path.split('/')
    let current = root
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      const isLast = i === parts.length - 1
      const subPath = parts.slice(0, i + 1).join('/')
      let existing = current.children.find((c) => c.name === part && c.isDir === !isLast)
      if (!existing) {
        existing = {
          name: part,
          path: subPath,
          isDir: !isLast,
          children: [],
          ...(isLast ? { file } : {}),
        }
        current.children.push(existing)
      }
      current = existing
    }
  }

  const sortNodes = (nodes: VirtualTreeNode[]) => {
    nodes.sort((a, b) => {
      if (a.isDir !== b.isDir) return a.isDir ? -1 : 1
      return a.name.localeCompare(b.name)
    })
    nodes.forEach((n) => sortNodes(n.children))
  }
  sortNodes(root.children)
  return root.children
}

export function applyVirtualFileSave(
  project: StudioProject,
  file: VirtualFile,
  rawText: string
): StudioProject {
  const parsed = JSON.parse(rawText)

  if (file.kind === 'pack_mcmeta') {
    return {
      ...project,
      description: typeof parsed?.pack?.description === 'string' ? parsed.pack.description : project.description,
      packFormatResource:
        typeof parsed?.pack?.pack_format === 'number'
          ? parsed.pack.pack_format
          : project.packFormatResource,
    }
  }

  if (file.kind === 'theme' && file.entityUid) {
    return {
      ...project,
      themes: project.themes.map((t) => {
        if (t.uid !== file.entityUid) return t
        const nextColors: ThemeColors = { ...DEFAULT_THEME_COLORS, ...t.colors }
        if (parsed.colors && typeof parsed.colors === 'object') {
          for (const k of Object.keys(nextColors) as Array<keyof ThemeColors>) {
            if (typeof parsed.colors[k] === 'string') {
              nextColors[k] = parsed.colors[k]
            }
          }
        }
        const rawId = typeof parsed.id === 'string' ? parsed.id : t.id
        const cleanId = slugify(rawId.includes(':') ? rawId.split(':')[1] : rawId)
        return {
          ...t,
          id: cleanId,
          name: typeof parsed.name === 'string' ? parsed.name : t.name,
          order: typeof parsed.order === 'number' ? parsed.order : t.order,
          colors: nextColors,
          updatedAt: Date.now(),
        }
      }),
    }
  }

  if (file.kind === 'screen' && file.entityUid) {
    return {
      ...project,
      screens: project.screens.map((s) => {
        if (s.uid !== file.entityUid) return s
        const rawId = typeof parsed.id === 'string' ? parsed.id : s.id
        const cleanId = slugify(rawId.includes(':') ? rawId.split(':')[1] : rawId)
        return {
          ...s,
          id: cleanId,
          title: typeof parsed.title === 'string' ? parsed.title : s.title,
          viewport: parsed.viewport ?? s.viewport,
          width_dp: parsed.width_dp ?? s.width_dp,
          height_dp: parsed.height_dp ?? s.height_dp,
          corner_radius: typeof parsed.corner_radius === 'number' ? parsed.corner_radius : s.corner_radius,
          show_bezel_back: parsed.show_bezel_back ?? s.show_bezel_back,
          show_bezel_battery: parsed.show_bezel_battery ?? s.show_bezel_battery,
          show_bezel_hd_badge: parsed.show_bezel_hd_badge ?? s.show_bezel_hd_badge,
          require_mod: parsed.require_mod,
          require_upgrade: parsed.require_upgrade,
          default_state: parsed.default_state ?? s.default_state,
          actions: parsed.actions ?? s.actions,
          body: Array.isArray(parsed.body) ? parsed.body : s.body,
          updatedAt: Date.now(),
        }
      }),
    }
  }

  if (file.kind === 'action' && file.entityUid) {
    return {
      ...project,
      actions: project.actions.map((a) => {
        if (a.uid !== file.entityUid) return a
        const rawId = typeof parsed.id === 'string' ? parsed.id : a.id
        const cleanId = slugify(rawId.includes(':') ? rawId.split(':')[1] : rawId)
        return {
          ...a,
          id: cleanId,
          open_screen: parsed.open_screen,
          functions: Array.isArray(parsed.functions) ? parsed.functions : [],
          commands: Array.isArray(parsed.commands) ? parsed.commands : [],
          use_items: Array.isArray(parsed.use_items) ? parsed.use_items : [],
          patchouli_book: parsed.patchouli_book,
          order: typeof parsed.order === 'number' ? parsed.order : a.order,
          cooldown_seconds:
            typeof parsed.cooldown_seconds === 'number'
              ? parsed.cooldown_seconds
              : a.cooldown_seconds,
          fe_cost: parsed.fe_cost,
          require_mod: parsed.require_mod,
          require_upgrade: parsed.require_upgrade,
          updatedAt: Date.now(),
        }
      }),
    }
  }

  if (file.kind === 'skin' && file.entityUid) {
    return {
      ...project,
      skins: project.skins.map((sk) => {
        if (sk.uid !== file.entityUid) return sk
        const rawId = typeof parsed.id === 'string' ? parsed.id : sk.id
        const cleanId = slugify(rawId.includes(':') ? rawId.split(':')[1] : rawId)
        return {
          ...sk,
          id: cleanId,
          name: typeof parsed.name === 'string' ? parsed.name : sk.name,
          hand_model: parsed.hand_model,
          rotom_model: parsed.rotom_model,
          inventory_model: parsed.inventory_model,
          colors: Array.isArray(parsed.colors) ? parsed.colors : sk.colors,
          locked: Boolean(parsed.locked),
          require_upgrade: parsed.require_upgrade,
          updatedAt: Date.now(),
        }
      }),
    }
  }

  return project
}
