import JSZip from 'jszip'
import type {
  MinecraftRecipeType,
  RecipeResultKind,
  StudioAction,
  StudioAsset,
  StudioProject,
  StudioRecipe,
  StudioScreen,
  StudioSkin,
  StudioTheme,
} from '@/types/schemas'
import { slugify, triggerDownload } from '@/lib/utils'

function dataUrlToBase64(dataUrl: string): string {
  const idx = dataUrl.indexOf(',')
  return idx >= 0 ? dataUrl.slice(idx + 1) : dataUrl
}

export function serializeThemeJson(namespace: string, theme: StudioTheme) {
  const ns = slugify(namespace)
  const id = slugify(theme.id)
  const payload: Record<string, unknown> = {
    id: `${ns}:${id}`,
    name: theme.name || id,
    wallpaper: theme.wallpaperAssetId
      ? `${ns}:textures/gui/wallpapers/${id}.png`
      : 'cobblemon_smartphone:textures/gui/home_screen.png',
    order: theme.order,
    colors: theme.colors,
  }
  if (theme.largeWallpaperAssetId) {
    payload.large_wallpaper = `${ns}:textures/gui/wallpapers/${id}_large.png`
  }
  if (theme.frameAssetId) {
    payload.frame = `${ns}:textures/gui/frames/${id}.png`
  }
  if (theme.largeFrameAssetId) {
    payload.large_frame = `${ns}:textures/gui/frames/${id}_large.png`
  }
  const elements: Record<string, string> = {}
  for (const [k, assetId] of Object.entries(theme.elements)) {
    if (assetId) elements[k] = `${ns}:textures/gui/elements/${id}_${k}.png`
  }
  if (Object.keys(elements).length > 0) payload.elements = elements

  const appIcons: Record<string, string | { texture: string; hover: string }> = {}
  for (const [appId, override] of Object.entries(theme.appIcons)) {
    const iconSlug = slugify(appId.includes(':') ? appId.split(':')[1] : appId)
    if (override.textureAssetId && override.hoverAssetId) {
      appIcons[appId] = {
        texture: `${ns}:textures/gui/apps/${id}_${iconSlug}.png`,
        hover: `${ns}:textures/gui/apps/${id}_${iconSlug}_hover.png`,
      }
    } else if (override.textureAssetId) {
      appIcons[appId] = `${ns}:textures/gui/apps/${id}_${iconSlug}.png`
    }
  }
  if (Object.keys(appIcons).length > 0) payload.app_icons = appIcons
  return payload
}

export function serializeScreenJson(namespace: string, screen: StudioScreen) {
  const ns = slugify(namespace)
  const id = slugify(screen.id)
  return {
    id: `${ns}:${id}`,
    title: screen.title,
    viewport: screen.viewport,
    ...(screen.width_dp ? { width_dp: screen.width_dp } : {}),
    ...(screen.height_dp ? { height_dp: screen.height_dp } : {}),
    corner_radius: screen.corner_radius,
    show_bezel_back: screen.show_bezel_back,
    show_bezel_battery: screen.show_bezel_battery,
    show_bezel_hd_badge: screen.show_bezel_hd_badge,
    ...(screen.require_mod ? { require_mod: screen.require_mod } : {}),
    ...(screen.require_upgrade ? { require_upgrade: screen.require_upgrade } : {}),
    default_state: screen.default_state,
    actions: screen.actions,
    body: screen.body,
  }
}

export function serializeActionJson(namespace: string, action: StudioAction) {
  const ns = slugify(namespace)
  const id = slugify(action.id)
  return {
    id: `${ns}:${id}`,
    texture: `${ns}:textures/gui/buttons/${id}.png`,
    hover_texture: `${ns}:textures/gui/buttons/${id}_hover.png`,
    ...(action.open_screen ? { open_screen: action.open_screen } : {}),
    ...(action.functions.length > 0 ? { functions: action.functions } : {}),
    ...(action.commands.length > 0 ? { commands: action.commands } : {}),
    ...(action.use_items.length > 0 ? { use_items: action.use_items } : {}),
    ...(action.patchouli_book ? { patchouli_book: action.patchouli_book } : {}),
    order: action.order,
    cooldown_seconds: action.cooldown_seconds,
    ...(action.fe_cost ? { fe_cost: action.fe_cost } : {}),
    ...(action.require_mod ? { require_mod: action.require_mod } : {}),
    ...(action.require_upgrade ? { require_upgrade: action.require_upgrade } : {}),
  }
}

export function resolveModelTextureZipPath(
  defaultNs: string,
  skinId: string,
  texKeyOrRef: string
): string {
  const clean = texKeyOrRef.replace(/^#/, '').trim()
  if (clean.includes(':')) {
    const [tns, tpath] = clean.split(':', 2)
    const normalized = tpath.replace(/^textures\//, '').replace(/\.png$/i, '')
    return `assets/${slugify(tns)}/textures/${normalized}.png`
  }
  return `assets/${slugify(defaultNs)}/textures/item/smartphone_skins/${slugify(skinId)}_${slugify(clean)}.png`
}

export function serializeSkinJson(namespace: string, skin: StudioSkin) {
  const ns = slugify(namespace)
  const id = slugify(skin.id)
  return {
    id: `${ns}:${id}`,
    name: skin.name,
    ...(skin.hand_model ? { hand_model: skin.hand_model } : {}),
    ...(skin.rotom_model ? { rotom_model: skin.rotom_model } : {}),
    ...(skin.inventory_model ? { inventory_model: skin.inventory_model } : {}),
    ...(skin.frameAssetId ? { frame: `${ns}:textures/gui/skin_frames/${id}.png` } : {}),
    ...(skin.largeFrameAssetId
      ? { large_frame: `${ns}:textures/gui/skin_frames/${id}_large.png` }
      : {}),
    colors: skin.colors.length > 0 ? skin.colors : ['*'],
    locked: skin.locked,
    ...(skin.require_upgrade ? { require_upgrade: skin.require_upgrade } : {}),
  }
}

export function normalizeRecipe(recipe: StudioRecipe): {
  recipeType: MinecraftRecipeType
  resultKind: RecipeResultKind
} {
  if (recipe.recipeType && recipe.resultKind) {
    return { recipeType: recipe.recipeType, resultKind: recipe.resultKind }
  }
  switch (recipe.kind) {
    case 'sd_card_shaped':
      return { recipeType: 'minecraft:crafting_shaped', resultKind: 'sd_card' }
    case 'smithing_upgrade':
      return { recipeType: 'minecraft:smithing_transform', resultKind: 'smartphone_upgrade' }
    case 'smithing_skin':
      return { recipeType: 'minecraft:smithing_transform', resultKind: 'smartphone_skin' }
    case 'sd_card_shapeless':
    default:
      return {
        recipeType: recipe.recipeType ?? 'minecraft:crafting_shapeless',
        resultKind: recipe.resultKind ?? 'sd_card',
      }
  }
}

function parseIngredientSpec(raw: string): { item?: string; tag?: string } {
  const clean = raw.trim()
  if (clean.startsWith('#')) {
    return { tag: clean.slice(1) }
  }
  return { item: clean }
}

function buildRecipeResult(
  namespace: string,
  recipe: StudioRecipe,
  resultKind: RecipeResultKind,
  includeCount: boolean
): Record<string, unknown> {
  const count = Math.max(1, Math.min(64, recipe.resultCount ?? 1))
  const countField = includeCount ? { count } : {}

  if (resultKind === 'sd_card') {
    return {
      id: 'cobblemon_smartphone:sd_card',
      ...countField,
      components: {
        'minecraft:custom_data': {
          'cobblemon_smartphone:sd_card_upgrade': recipe.upgradeKey || 'upgrade_custom',
        },
      },
    }
  }

  if (resultKind === 'smartphone_upgrade') {
    return {
      id: recipe.resultItem || 'cobblemon_smartphone:red_smartphone',
      ...countField,
      components: {
        'minecraft:custom_data': {
          'cobblemon_smartphone:upgrades': {
            [recipe.upgradeKey || 'upgrade_custom']: true,
          },
        },
      },
    }
  }

  if (resultKind === 'smartphone_skin') {
    const fullSkinId = recipe.skinId?.includes(':')
      ? recipe.skinId
      : `${slugify(namespace)}:${slugify(recipe.skinId || 'custom_skin')}`
    return {
      id: recipe.resultItem || 'cobblemon_smartphone:red_smartphone',
      ...countField,
      components: {
        'minecraft:custom_data': recipe.equipOnCraft
          ? { 'cobblemon_smartphone:skin': fullSkinId }
          : { 'cobblemon_smartphone:unlocked_skins': { [fullSkinId]: true } },
      },
    }
  }

  if (resultKind === 'smartphone') {
    return {
      id: recipe.resultItem || 'cobblemon_smartphone:red_smartphone',
      ...countField,
    }
  }

  // custom_item
  const res: Record<string, unknown> = {
    id: recipe.resultItem || 'minecraft:diamond',
    ...countField,
  }
  if (recipe.customComponentsJson?.trim()) {
    try {
      const parsed = JSON.parse(recipe.customComponentsJson)
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        res.components = parsed
      }
    } catch {
      // Ignore invalid JSON while editing
    }
  }
  return res
}

function trimShapedPattern(rows: string[]): string[] {
  let top = 0
  let bottom = rows.length - 1
  while (top <= bottom && rows[top].trim() === '') top++
  while (bottom >= top && rows[bottom].trim() === '') bottom--
  if (top > bottom) return ['   ', '   ', '   ']
  const sliced = rows.slice(top, bottom + 1)
  let left = 3
  let right = -1
  for (const row of sliced) {
    for (let c = 0; c < row.length; c++) {
      if (row[c] !== ' ') {
        if (c < left) left = c
        if (c > right) right = c
      }
    }
  }
  if (left > right) return ['   ']
  return sliced.map((row) => row.slice(left, right + 1))
}

export function serializeRecipeJson(namespace: string, recipe: StudioRecipe): Record<string, unknown> {
  const { recipeType, resultKind } = normalizeRecipe(recipe)
  const modConditions = recipe.require_mod
    ? {
        'fabric:load_conditions': [
          { condition: 'fabric:all_mods_loaded', values: [recipe.require_mod] },
        ],
        'neoforge:conditions': [
          { type: 'neoforge:mod_loaded', modid: recipe.require_mod },
        ],
      }
    : {}

  const groupField = recipe.group?.trim() ? { group: recipe.group.trim() } : {}

  if (recipeType === 'minecraft:crafting_shapeless') {
    const ingredients = (recipe.ingredients ?? []).map((ing) =>
      ing.type === 'tag' ? { tag: ing.value.replace(/^#/, '') } : { item: ing.value }
    )
    return {
      type: 'minecraft:crafting_shapeless',
      ...modConditions,
      category: recipe.category ?? 'misc',
      ...groupField,
      ingredients,
      result: buildRecipeResult(namespace, recipe, resultKind, true),
    }
  }

  if (recipeType === 'minecraft:crafting_shaped') {
    const grid = recipe.grid ?? Array(9).fill('')
    const symbolMap = new Map<string, string>()
    const keyObj: Record<string, { item?: string; tag?: string }> = {}
    const chars = 'ABCDEFGHI'
    let charIdx = 0
    const rawRows = [0, 1, 2].map((r) =>
      [0, 1, 2]
        .map((c) => {
          const val = (grid[r * 3 + c] ?? '').trim()
          if (!val) return ' '
          let sym = symbolMap.get(val)
          if (!sym) {
            sym = chars[charIdx++] ?? 'X'
            symbolMap.set(val, sym)
            keyObj[sym] = parseIngredientSpec(val)
          }
          return sym
        })
        .join('')
    )
    return {
      type: 'minecraft:crafting_shaped',
      ...modConditions,
      category: recipe.category ?? 'misc',
      ...groupField,
      pattern: trimShapedPattern(rawRows),
      key: keyObj,
      result: buildRecipeResult(namespace, recipe, resultKind, true),
    }
  }

  if (recipeType === 'minecraft:smithing_transform') {
    const tplRaw = (recipe.templateItem ?? '').trim()
    const baseRaw = (recipe.baseItem ?? '#cobblemon_smartphone:smartphones').trim()
    const addRaw = (recipe.additionItem ?? 'minecraft:diamond').trim()
    return {
      type: 'minecraft:smithing_transform',
      ...modConditions,
      template: tplRaw ? parseIngredientSpec(tplRaw) : [],
      base: parseIngredientSpec(baseRaw || '#cobblemon_smartphone:smartphones'),
      addition: parseIngredientSpec(addRaw || 'minecraft:diamond'),
      result: buildRecipeResult(namespace, recipe, resultKind, true),
    }
  }

  if (recipeType === 'minecraft:stonecutting') {
    return {
      type: 'minecraft:stonecutting',
      ...modConditions,
      ...groupField,
      ingredient: parseIngredientSpec(recipe.singleIngredient || 'cobblemon:upgrade'),
      result: buildRecipeResult(namespace, recipe, resultKind, true),
    }
  }

  // Cooking recipes: smelting, blasting, smoking, campfire_cooking
  const defaultCookTime =
    recipeType === 'minecraft:blasting' || recipeType === 'minecraft:smoking'
      ? 100
      : recipeType === 'minecraft:campfire_cooking'
        ? 600
        : 200
  const cookCategory =
    recipe.category === 'food' || recipe.category === 'blocks' ? recipe.category : 'misc'

  return {
    type: recipeType,
    ...modConditions,
    category: cookCategory,
    ...groupField,
    ingredient: parseIngredientSpec(recipe.singleIngredient || 'cobblemon:upgrade'),
    result: buildRecipeResult(namespace, recipe, resultKind, false),
    experience: recipe.experience ?? 0.35,
    cookingtime: recipe.cookingtime ?? defaultCookTime,
  }
}

export async function exportProjectZip(
  project: StudioProject,
  assets: StudioAsset[],
  mode: 'combined' | 'resourcepack' | 'datapack' = 'combined'
) {
  const zip = new JSZip()
  const ns = slugify(project.namespace)
  const assetMap = new Map(assets.map((a) => [a.id, a]))

  const packFormat =
    mode === 'datapack' ? project.packFormatData : project.packFormatResource
  zip.file(
    'pack.mcmeta',
    JSON.stringify(
      {
        pack: {
          pack_format: packFormat,
          description: project.description || `${project.name} (Cobblemon Smartphone Studio)`,
        },
      },
      null,
      2
    )
  )

  if (mode === 'combined' || mode === 'resourcepack') {
    for (const theme of project.themes) {
      const id = slugify(theme.id)
      zip.file(
        `assets/${ns}/themes/${id}.json`,
        JSON.stringify(serializeThemeJson(ns, theme), null, 2)
      )
      const writeImg = (assetId: string | undefined, targetPath: string) => {
        if (!assetId) return
        const asset = assetMap.get(assetId)
        if (asset) zip.file(targetPath, dataUrlToBase64(asset.dataUrl), { base64: true })
      }
      writeImg(theme.wallpaperAssetId, `assets/${ns}/textures/gui/wallpapers/${id}.png`)
      writeImg(theme.largeWallpaperAssetId, `assets/${ns}/textures/gui/wallpapers/${id}_large.png`)
      writeImg(theme.frameAssetId, `assets/${ns}/textures/gui/frames/${id}.png`)
      writeImg(theme.largeFrameAssetId, `assets/${ns}/textures/gui/frames/${id}_large.png`)
      for (const [k, assetId] of Object.entries(theme.elements)) {
        writeImg(assetId, `assets/${ns}/textures/gui/elements/${id}_${k}.png`)
      }
      for (const [appId, override] of Object.entries(theme.appIcons)) {
        const iconSlug = slugify(appId.includes(':') ? appId.split(':')[1] : appId)
        writeImg(override.textureAssetId, `assets/${ns}/textures/gui/apps/${id}_${iconSlug}.png`)
        writeImg(override.hoverAssetId, `assets/${ns}/textures/gui/apps/${id}_${iconSlug}_hover.png`)
      }
    }

    for (const skin of project.skins) {
      const id = slugify(skin.id)
      zip.file(
        `assets/${ns}/smartphone_skins/${id}.json`,
        JSON.stringify(serializeSkinJson(ns, skin), null, 2)
      )
      const writeSkinAsset = (assetId: string | undefined, targetPath: string) => {
        if (!assetId) return
        const asset = assetMap.get(assetId)
        if (asset) zip.file(targetPath, dataUrlToBase64(asset.dataUrl), { base64: true })
      }
      writeSkinAsset(skin.frameAssetId, `assets/${ns}/textures/gui/skin_frames/${id}.png`)
      writeSkinAsset(skin.largeFrameAssetId, `assets/${ns}/textures/gui/skin_frames/${id}_large.png`)
      writeSkinAsset(skin.handModelAssetId, `assets/${ns}/models/item/smartphone_skins/${id}_hand.json`)
      writeSkinAsset(skin.rotomModelAssetId, `assets/${ns}/models/item/smartphone_skins/${id}_rotom.json`)
      for (const [texRef, assetId] of Object.entries(skin.modelTextures ?? {})) {
        writeSkinAsset(assetId, resolveModelTextureZipPath(ns, id, texRef))
      }
    }

    for (const action of project.actions) {
      const id = slugify(action.id)
      const writeBtnAsset = (assetId: string | undefined, targetPath: string) => {
        if (!assetId) return
        const asset = assetMap.get(assetId)
        if (asset) zip.file(targetPath, dataUrlToBase64(asset.dataUrl), { base64: true })
      }
      writeBtnAsset(action.textureAssetId, `assets/${ns}/textures/gui/buttons/${id}.png`)
      writeBtnAsset(action.hoverTextureAssetId, `assets/${ns}/textures/gui/buttons/${id}_hover.png`)
    }
  }

  if (mode === 'combined' || mode === 'datapack') {
    for (const screen of project.screens) {
      const id = slugify(screen.id)
      zip.file(
        `data/${ns}/smartphone_screens/${id}.json`,
        JSON.stringify(serializeScreenJson(ns, screen), null, 2)
      )
    }

    for (const action of project.actions) {
      const id = slugify(action.id)
      zip.file(
        `data/${ns}/smartphone_actions/${id}.json`,
        JSON.stringify(serializeActionJson(ns, action), null, 2)
      )
    }

    for (const recipe of project.recipes ?? []) {
      const id = slugify(recipe.id)
      zip.file(
        `data/${ns}/recipe/${id}.json`,
        JSON.stringify(serializeRecipeJson(ns, recipe), null, 2)
      )
    }
  }

  const blob = await zip.generateAsync({ type: 'blob' })
  triggerDownload(blob, `${ns}_${mode}.zip`)
}
