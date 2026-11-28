import { useMemo, useState } from 'react'
import {
  ArrowRight,
  Code2,
  CreditCard,
  Flame,
  Grid3X3,
  Hammer,
  Package,
  Plus,
  Scissors,
  Sliders,
  Sparkles,
  Trash2,
} from 'lucide-react'
import { useStudioStore } from '@/store/studioStore'
import { normalizeRecipe, serializeRecipeJson } from '@/export/packExporter'
import { createId, slugify } from '@/lib/utils'
import type {
  MinecraftRecipeType,
  RecipeIngredient,
  RecipeResultKind,
  StudioRecipe,
} from '@/types/schemas'

const RECIPE_TYPE_OPTIONS: Array<{
  value: MinecraftRecipeType
  label: string
  short: string
}> = [
  { value: 'minecraft:crafting_shapeless', label: 'Crafting Table (Shapeless)', short: 'Shapeless' },
  { value: 'minecraft:crafting_shaped', label: 'Crafting Table (Shaped 3×3)', short: 'Shaped 3×3' },
  { value: 'minecraft:smithing_transform', label: 'Smithing Table (Transform)', short: 'Smithing' },
  { value: 'minecraft:smelting', label: 'Furnace (Smelting)', short: 'Smelting' },
  { value: 'minecraft:blasting', label: 'Blast Furnace (Blasting)', short: 'Blasting' },
  { value: 'minecraft:smoking', label: 'Smoker (Smoking)', short: 'Smoking' },
  { value: 'minecraft:campfire_cooking', label: 'Campfire Cooking', short: 'Campfire' },
  { value: 'minecraft:stonecutting', label: 'Stonecutter', short: 'Stonecutter' },
]

const RESULT_KIND_OPTIONS: Array<{
  value: RecipeResultKind
  label: string
}> = [
  { value: 'sd_card', label: 'SD Card (Upgrade Item)' },
  { value: 'smartphone_upgrade', label: 'Smartphone + Upgrade Data' },
  { value: 'smartphone_skin', label: 'Smartphone + 3D Skin Data' },
  { value: 'smartphone', label: 'Smartphone / Mod Item' },
  { value: 'custom_item', label: 'Custom Item (Any Mod/Vanilla)' },
]

const SMARTPHONE_MOD_ITEMS = [
  'cobblemon_smartphone:red_smartphone',
  'cobblemon_smartphone:blue_smartphone',
  'cobblemon_smartphone:green_smartphone',
  'cobblemon_smartphone:yellow_smartphone',
  'cobblemon_smartphone:black_smartphone',
  'cobblemon_smartphone:white_smartphone',
  'cobblemon_smartphone:pink_smartphone',
  'cobblemon_smartphone:purple_smartphone',
  'cobblemon_smartphone:cyan_smartphone',
  'cobblemon_smartphone:orange_smartphone',
  'cobblemon_smartphone:lime_smartphone',
  'cobblemon_smartphone:magenta_smartphone',
  'cobblemon_smartphone:light_blue_smartphone',
  'cobblemon_smartphone:light_gray_smartphone',
  'cobblemon_smartphone:gray_smartphone',
  'cobblemon_smartphone:brown_smartphone',
  'cobblemon_smartphone:sd_card',
  'cobblemon_smartphone:battery_tier1',
  'cobblemon_smartphone:battery_tier2',
  'cobblemon_smartphone:battery_tier3',
  'cobblemon_smartphone:rotom_accessory_remover',
]

function isCookingType(t: MinecraftRecipeType): boolean {
  return (
    t === 'minecraft:smelting' ||
    t === 'minecraft:blasting' ||
    t === 'minecraft:smoking' ||
    t === 'minecraft:campfire_cooking'
  )
}

function defaultCookTimeFor(t: MinecraftRecipeType): number {
  if (t === 'minecraft:blasting' || t === 'minecraft:smoking') return 100
  if (t === 'minecraft:campfire_cooking') return 600
  return 200
}

function getPreviewTexture(resultKind: RecipeResultKind, resultItem?: string): string | null {
  if (resultKind === 'sd_card') return '/mod-assets/textures/item/sd_card.png'
  if (
    resultKind === 'smartphone_upgrade' ||
    resultKind === 'smartphone_skin' ||
    resultKind === 'smartphone'
  ) {
    const id = (resultItem || 'cobblemon_smartphone:red_smartphone').split(':').pop()
    return `/mod-assets/textures/item/${id}.png`
  }
  if (resultItem?.startsWith('cobblemon_smartphone:')) {
    const id = resultItem.split(':').pop()
    return `/mod-assets/textures/item/${id}.png`
  }
  return null
}

export function RecipeStudioView() {
  const { projects, activeProjectId, updateActiveProject } = useStudioStore()
  const project = projects.find((p) => p.id === activeProjectId)
  const recipes = project?.recipes ?? []
  const [selectedUid, setSelectedUid] = useState<string | null>(recipes[0]?.uid ?? null)
  const [tab, setTab] = useState<'editor' | 'json'>('editor')
  const [newIngType, setNewIngType] = useState<'item' | 'tag'>('item')
  const [newIngVal, setNewIngVal] = useState('minecraft:compass')

  const activeRecipe = recipes.find((r) => r.uid === selectedUid) ?? recipes[0]

  const knownUpgradeKeys = useMemo(() => {
    const set = new Set<string>()
    for (const a of project?.actions ?? []) {
      if (a.require_upgrade) set.add(a.require_upgrade)
    }
    for (const s of project?.skins ?? []) {
      if (s.require_upgrade) set.add(s.require_upgrade)
    }
    return Array.from(set)
  }, [project])

  const knownSkinIds = useMemo(() => {
    if (!project) return []
    return project.skins.map((s) => `${slugify(project.namespace)}:${slugify(s.id)}`)
  }, [project])

  if (!project) return null

  const patchRecipe = (patch: Partial<StudioRecipe>) => {
    if (!activeRecipe) return
    updateActiveProject((p) => ({
      ...p,
      recipes: (p.recipes ?? []).map((r) =>
        r.uid === activeRecipe.uid ? { ...r, ...patch, updatedAt: Date.now() } : r
      ),
    }))
  }

  const createPreset = (preset: 'sd_card' | 'smartphone_upgrade' | 'smartphone_skin' | 'custom_item') => {
    const idx = recipes.length + 1
    const base: StudioRecipe =
      preset === 'sd_card'
        ? {
            uid: createId('recipe'),
            id: `sd_card_custom_${idx}`,
            name: `SD Card ${idx}`,
            recipeType: 'minecraft:crafting_shapeless',
            resultKind: 'sd_card',
            category: 'misc',
            resultCount: 1,
            upgradeKey: knownUpgradeKeys[0] || `upgrade_custom_${idx}`,
            ingredients: [
              { type: 'item', value: 'cobblemon:upgrade' },
              { type: 'item', value: 'minecraft:compass' },
            ],
            grid: [
              '', 'minecraft:iron_ingot', '',
              'minecraft:redstone', 'cobblemon:upgrade', 'minecraft:redstone',
              '', 'minecraft:compass', '',
            ],
            singleIngredient: 'cobblemon:upgrade',
            templateItem: 'cobblemon:upgrade',
            baseItem: '#cobblemon_smartphone:smartphones',
            additionItem: 'minecraft:compass',
            updatedAt: Date.now(),
          }
        : preset === 'smartphone_upgrade'
          ? {
              uid: createId('recipe'),
              id: `upgrade_custom_${idx}`,
              name: `Smartphone Upgrade ${idx}`,
              recipeType: 'minecraft:smithing_transform',
              resultKind: 'smartphone_upgrade',
              category: 'misc',
              resultCount: 1,
              resultItem: 'cobblemon_smartphone:red_smartphone',
              upgradeKey: knownUpgradeKeys[0] || `upgrade_custom_${idx}`,
              templateItem: 'cobblemon:upgrade',
              baseItem: '#cobblemon_smartphone:smartphones',
              additionItem: 'minecraft:diamond',
              ingredients: [
                { type: 'tag', value: 'cobblemon_smartphone:smartphones' },
                { type: 'item', value: 'minecraft:diamond' },
              ],
              grid: Array(9).fill(''),
              singleIngredient: 'cobblemon_smartphone:red_smartphone',
              updatedAt: Date.now(),
            }
          : preset === 'smartphone_skin'
            ? {
                uid: createId('recipe'),
                id: `skin_unlock_${idx}`,
                name: `Skin Unlock ${idx}`,
                recipeType: 'minecraft:smithing_transform',
                resultKind: 'smartphone_skin',
                category: 'misc',
                resultCount: 1,
                resultItem: 'cobblemon_smartphone:red_smartphone',
                skinId: knownSkinIds[0] || `${slugify(project.namespace)}:custom_skin_${idx}`,
                equipOnCraft: false,
                templateItem: 'cobblemon:upgrade',
                baseItem: '#cobblemon_smartphone:smartphones',
                additionItem: 'minecraft:gold_ingot',
                ingredients: [
                  { type: 'tag', value: 'cobblemon_smartphone:smartphones' },
                  { type: 'item', value: 'minecraft:gold_ingot' },
                ],
                grid: Array(9).fill(''),
                singleIngredient: 'cobblemon_smartphone:red_smartphone',
                updatedAt: Date.now(),
              }
            : {
                uid: createId('recipe'),
                id: `custom_recipe_${idx}`,
                name: `Custom Recipe ${idx}`,
                recipeType: 'minecraft:crafting_shaped',
                resultKind: 'smartphone',
                category: 'misc',
                resultCount: 1,
                resultItem: 'cobblemon_smartphone:red_smartphone',
                grid: [
                  'cobblemon:red_apricorn', 'minecraft:diamond', 'minecraft:quartz',
                  'cobblemon:red_apricorn', 'minecraft:redstone', 'minecraft:amethyst_shard',
                  'minecraft:iron_ingot', 'minecraft:diamond', 'minecraft:quartz',
                ],
                ingredients: [{ type: 'item', value: 'minecraft:iron_ingot' }],
                singleIngredient: 'minecraft:iron_ingot',
                templateItem: 'cobblemon:upgrade',
                baseItem: '#cobblemon_smartphone:smartphones',
                additionItem: 'minecraft:diamond',
                updatedAt: Date.now(),
              }

    updateActiveProject((p) => ({
      ...p,
      recipes: [...(p.recipes ?? []), base],
    }))
    setSelectedUid(base.uid)
  }

  const normalized = activeRecipe ? normalizeRecipe(activeRecipe) : null
  const recipeType = normalized?.recipeType ?? 'minecraft:crafting_shapeless'
  const resultKind = normalized?.resultKind ?? 'sd_card'
  const cooking = isCookingType(recipeType)

  const serializedJson = activeRecipe
    ? JSON.stringify(serializeRecipeJson(project.namespace, activeRecipe), null, 2)
    : ''

  const previewTex = activeRecipe ? getPreviewTexture(resultKind, activeRecipe.resultItem) : null
  const resultLabel =
    resultKind === 'sd_card'
      ? 'SD Card'
      : resultKind === 'smartphone_upgrade'
        ? 'Upgraded Phone'
        : resultKind === 'smartphone_skin'
          ? 'Skin Phone'
          : (activeRecipe?.resultItem || 'minecraft:diamond').split(':').pop() || 'Item'

  const resultSubBadge =
    resultKind === 'sd_card' || resultKind === 'smartphone_upgrade'
      ? activeRecipe?.upgradeKey || 'upgrade_custom'
      : resultKind === 'smartphone_skin'
        ? activeRecipe?.skinId || 'custom_skin'
        : activeRecipe?.resultItem || 'cobblemon_smartphone:red_smartphone'

  const typeMeta = RECIPE_TYPE_OPTIONS.find((o) => o.value === recipeType)

  return (
    <div className="flex h-full min-h-0">
      {/* Left Recipe List */}
      <div className="flex w-64 shrink-0 flex-col border-r border-zinc-800/80 bg-zinc-950 p-3">
        <div className="mb-2 flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
            Recipes &amp; SD Cards ({recipes.length})
          </span>
        </div>

        <div className="mb-3 grid grid-cols-2 gap-1 text-[10px]">
          <button
            onClick={() => createPreset('sd_card')}
            className="rounded-md bg-red-500/15 py-1.5 font-medium text-red-300 hover:bg-red-500/25"
          >
            + SD Card
          </button>
          <button
            onClick={() => createPreset('smartphone_upgrade')}
            className="rounded-md bg-amber-500/15 py-1.5 font-medium text-amber-300 hover:bg-amber-500/25"
          >
            + Upgrade
          </button>
          <button
            onClick={() => createPreset('smartphone_skin')}
            className="rounded-md bg-emerald-500/15 py-1.5 font-medium text-emerald-300 hover:bg-emerald-500/25"
          >
            + Skin
          </button>
          <button
            onClick={() => createPreset('custom_item')}
            className="rounded-md bg-violet-500/15 py-1.5 font-medium text-violet-300 hover:bg-violet-500/25"
          >
            + Craft / Item
          </button>
        </div>

        <div className="flex-1 space-y-1 overflow-auto">
          {recipes.map((r) => {
            const norm = normalizeRecipe(r)
            const shortType =
              RECIPE_TYPE_OPTIONS.find((o) => o.value === norm.recipeType)?.short ?? 'Recipe'
            return (
              <div
                key={r.uid}
                onClick={() => setSelectedUid(r.uid)}
                className={`group flex cursor-pointer items-center justify-between rounded-lg px-2.5 py-2 text-xs transition ${
                  activeRecipe?.uid === r.uid
                    ? 'bg-zinc-800 text-zinc-50 ring-1 ring-zinc-700'
                    : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
                }`}
              >
                <div className="min-w-0">
                  <div className="truncate font-medium">{r.name}</div>
                  <div className="truncate font-mono text-[10px] text-zinc-500">
                    {shortType} · {norm.resultKind.replace('_', ' ')}
                  </div>
                </div>
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    updateActiveProject((p) => ({
                      ...p,
                      recipes: (p.recipes ?? []).filter((x) => x.uid !== r.uid),
                    }))
                    if (selectedUid === r.uid) setSelectedUid(null)
                  }}
                  className="opacity-0 hover:text-red-400 group-hover:opacity-100"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              </div>
            )
          })}
        </div>
      </div>

      {/* Center Visual Crafting Preview */}
      {activeRecipe ? (
        <>
          <div className="flex flex-1 flex-col items-center justify-center gap-6 overflow-auto bg-[radial-gradient(#27272a_1px,transparent_1px)] [background-size:18px_18px] p-6">
            <div className="w-full max-w-2xl rounded-3xl border border-zinc-800 bg-zinc-900/80 p-6 shadow-2xl">
              <div className="mb-5 flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-zinc-300">
                  {recipeType === 'minecraft:smithing_transform' ? (
                    <Hammer className="h-4 w-4 text-amber-400" />
                  ) : cooking ? (
                    <Flame className="h-4 w-4 text-orange-400" />
                  ) : recipeType === 'minecraft:stonecutting' ? (
                    <Scissors className="h-4 w-4 text-zinc-300" />
                  ) : recipeType === 'minecraft:crafting_shaped' ? (
                    <Grid3X3 className="h-4 w-4 text-red-400" />
                  ) : (
                    <CreditCard className="h-4 w-4 text-red-400" />
                  )}
                  {typeMeta?.label ?? recipeType}
                </div>
                <span className="font-mono text-[10px] text-zinc-500">
                  data/{project.namespace}/recipe/{activeRecipe.id}.json
                </span>
              </div>

              <div className="flex flex-wrap items-center justify-center gap-5">
                {/* Input side by Minecraft recipe type */}
                {recipeType === 'minecraft:crafting_shaped' ? (
                  <div className="grid grid-cols-3 gap-1.5 rounded-xl border border-zinc-800 bg-zinc-950 p-2.5">
                    {Array.from({ length: 9 }, (_, i) => {
                      const val = activeRecipe.grid?.[i] ?? ''
                      return (
                        <div
                          key={i}
                          title={val || 'Empty slot'}
                          className="flex h-13 w-13 items-center justify-center rounded-lg border border-zinc-800 bg-zinc-900 px-1 text-center font-mono text-[9px] text-zinc-300"
                        >
                          {val ? val.split(':').pop()?.slice(0, 7) : ''}
                        </div>
                      )
                    })}
                  </div>
                ) : recipeType === 'minecraft:crafting_shapeless' ? (
                  <div className="flex max-w-[240px] flex-wrap justify-center gap-2 rounded-xl border border-zinc-800 bg-zinc-950 p-3">
                    {(activeRecipe.ingredients ?? []).length > 0 ? (
                      (activeRecipe.ingredients ?? []).map((ing, idx) => (
                        <div
                          key={idx}
                          title={ing.value}
                          className="flex h-13 w-22 flex-col items-center justify-center rounded-lg border border-zinc-800 bg-zinc-900 px-1.5 text-center"
                        >
                          <span className="text-[8px] uppercase text-zinc-500">{ing.type}</span>
                          <span className="w-full truncate font-mono text-[10px] text-red-200">
                            {ing.value.split(':').pop()}
                          </span>
                        </div>
                      ))
                    ) : (
                      <span className="px-3 py-4 text-[11px] text-zinc-500">No ingredients</span>
                    )}
                  </div>
                ) : recipeType === 'minecraft:smithing_transform' ? (
                  <div className="flex items-center gap-2 rounded-xl border border-zinc-800 bg-zinc-950 p-3">
                    <div
                      title={activeRecipe.templateItem || 'No template'}
                      className="flex h-16 w-22 flex-col items-center justify-center rounded-lg border border-zinc-800 bg-zinc-900 px-1.5 text-center"
                    >
                      <span className="text-[8px] uppercase text-zinc-500">Template</span>
                      <span className="w-full truncate font-mono text-[10px] text-amber-200">
                        {activeRecipe.templateItem?.trim()
                          ? activeRecipe.templateItem.split(':').pop()
                          : '(none)'}
                      </span>
                    </div>
                    <div
                      title={activeRecipe.baseItem || '#cobblemon_smartphone:smartphones'}
                      className="flex h-16 w-22 flex-col items-center justify-center rounded-lg border border-red-500/30 bg-zinc-900 px-1.5 text-center"
                    >
                      {(activeRecipe.baseItem || '#cobblemon_smartphone:smartphones').includes(
                        'smartphone'
                      ) ? (
                        <img
                          src="/mod-assets/textures/item/red_smartphone.png"
                          alt="Base"
                          className="pixelated h-7 w-7"
                        />
                      ) : (
                        <span className="text-[8px] uppercase text-zinc-500">Base</span>
                      )}
                      <span className="w-full truncate font-mono text-[9px] text-zinc-300">
                        {(activeRecipe.baseItem || '#smartphones').split(':').pop()}
                      </span>
                    </div>
                    <div
                      title={activeRecipe.additionItem || 'minecraft:diamond'}
                      className="flex h-16 w-22 flex-col items-center justify-center rounded-lg border border-zinc-800 bg-zinc-900 px-1.5 text-center"
                    >
                      <span className="text-[8px] uppercase text-zinc-500">Addition</span>
                      <span className="w-full truncate font-mono text-[10px] text-emerald-200">
                        {(activeRecipe.additionItem || 'diamond').split(':').pop()}
                      </span>
                    </div>
                  </div>
                ) : (
                  /* Smelting / Blasting / Smoking / Campfire / Stonecutting */
                  <div className="flex flex-col items-center gap-2 rounded-xl border border-zinc-800 bg-zinc-950 p-4">
                    <div
                      title={activeRecipe.singleIngredient || 'cobblemon:upgrade'}
                      className="flex h-16 w-28 flex-col items-center justify-center rounded-lg border border-zinc-800 bg-zinc-900 px-2 text-center"
                    >
                      <span className="text-[8px] uppercase text-zinc-500">
                        {(activeRecipe.singleIngredient || '').startsWith('#') ? 'Tag' : 'Ingredient'}
                      </span>
                      <span className="w-full truncate font-mono text-[10px] text-red-200">
                        {(activeRecipe.singleIngredient || 'cobblemon:upgrade').split(':').pop()}
                      </span>
                    </div>
                    {cooking ? (
                      <div className="flex items-center gap-2 text-[10px] text-orange-300">
                        <Flame className="h-3.5 w-3.5" />
                        <span>
                          {activeRecipe.cookingtime ?? defaultCookTimeFor(recipeType)}t (
                          {((activeRecipe.cookingtime ?? defaultCookTimeFor(recipeType)) / 20).toFixed(1)}
                          s) · {activeRecipe.experience ?? 0.35} XP
                        </span>
                      </div>
                    ) : (
                      <div className="flex items-center gap-1.5 text-[10px] text-zinc-400">
                        <Scissors className="h-3.5 w-3.5" />
                        <span>Stonecutter</span>
                      </div>
                    )}
                  </div>
                )}

                <ArrowRight className="h-5 w-5 text-zinc-500" />

                {/* Result Slot */}
                <div className="relative flex min-w-[140px] flex-col items-center gap-1.5 rounded-2xl border-2 border-red-500/40 bg-zinc-950 p-4">
                  {(activeRecipe.resultCount ?? 1) > 1 && !cooking && (
                    <span className="absolute top-2 right-2 rounded bg-red-500/20 px-1.5 py-0.5 font-mono text-[10px] font-bold text-red-300">
                      ×{activeRecipe.resultCount}
                    </span>
                  )}
                  {previewTex ? (
                    <img
                      src={previewTex}
                      alt="Result"
                      className="pixelated h-12 w-12"
                      onError={(e) => {
                        ;(e.currentTarget as HTMLImageElement).style.display = 'none'
                      }}
                    />
                  ) : (
                    <Package className="h-10 w-10 text-red-400" />
                  )}
                  <div className="text-xs font-bold text-zinc-100">{resultLabel}</div>
                  <div className="max-w-[150px] truncate rounded bg-zinc-900 px-2 py-0.5 font-mono text-[10px] text-red-200">
                    {resultSubBadge}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Right Inspector */}
          <div className="flex w-[390px] shrink-0 flex-col border-l border-zinc-800/80 bg-zinc-950">
            <div className="grid grid-cols-2 border-b border-zinc-800 p-1.5 text-[11px]">
              <button
                onClick={() => setTab('editor')}
                className={`flex items-center justify-center gap-1 rounded-lg py-1.5 font-medium transition ${
                  tab === 'editor' ? 'bg-red-500/15 text-red-300' : 'text-zinc-400 hover:bg-zinc-900'
                }`}
              >
                <Sliders className="h-3.5 w-3.5" /> Recipe Setup
              </button>
              <button
                onClick={() => setTab('json')}
                className={`flex items-center justify-center gap-1 rounded-lg py-1.5 font-medium transition ${
                  tab === 'json' ? 'bg-red-500/15 text-red-300' : 'text-zinc-400 hover:bg-zinc-900'
                }`}
              >
                <Code2 className="h-3.5 w-3.5" /> JSON
              </button>
            </div>

            <div className="flex-1 overflow-auto p-4 text-xs">
              {tab === 'editor' ? (
                <div className="space-y-3.5">
                  <div>
                    <label className="mb-1 block text-zinc-400">Recipe Name</label>
                    <input
                      value={activeRecipe.name}
                      onChange={(e) => patchRecipe({ name: e.target.value })}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="mb-1 block text-zinc-400">Recipe File ID</label>
                      <input
                        value={activeRecipe.id}
                        onChange={(e) => patchRecipe({ id: slugify(e.target.value) })}
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-zinc-400">Book Category</label>
                      <select
                        value={activeRecipe.category ?? 'misc'}
                        onChange={(e) =>
                          patchRecipe({ category: e.target.value as StudioRecipe['category'] })
                        }
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5"
                      >
                        <option value="misc">misc</option>
                        <option value="equipment">equipment</option>
                        <option value="building">building</option>
                        <option value="redstone">redstone</option>
                        <option value="food">food</option>
                        <option value="blocks">blocks</option>
                      </select>
                    </div>
                  </div>

                  {/* Minecraft Datapack Recipe Type */}
                  <div>
                    <label className="mb-1 block text-zinc-400">Minecraft Recipe Type</label>
                    <select
                      value={recipeType}
                      onChange={(e) => {
                        const nextType = e.target.value as MinecraftRecipeType
                        patchRecipe({
                          recipeType: nextType,
                          resultKind,
                          ...(isCookingType(nextType)
                            ? { cookingtime: defaultCookTimeFor(nextType) }
                            : {}),
                        })
                      }}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-medium text-red-200"
                    >
                      {RECIPE_TYPE_OPTIONS.map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  {/* Output / Result Kind */}
                  <div className="space-y-2 rounded-xl border border-zinc-800 bg-zinc-900/40 p-3">
                    <div className="flex items-center justify-between">
                      <label className="font-semibold text-zinc-300">Output Result</label>
                      {!cooking && (
                        <div className="flex items-center gap-1.5">
                          <span className="text-[10px] text-zinc-400">Count</span>
                          <input
                            type="number"
                            min={1}
                            max={64}
                            value={activeRecipe.resultCount ?? 1}
                            onChange={(e) =>
                              patchRecipe({ resultCount: Math.max(1, Number(e.target.value) || 1) })
                            }
                            className="w-14 rounded border border-zinc-800 bg-zinc-950 px-2 py-0.5 text-center font-mono text-[11px]"
                          />
                        </div>
                      )}
                    </div>

                    <select
                      value={resultKind}
                      onChange={(e) =>
                        patchRecipe({
                          recipeType,
                          resultKind: e.target.value as RecipeResultKind,
                        })
                      }
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-2.5 py-1.5"
                    >
                      {RESULT_KIND_OPTIONS.map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </select>

                    {/* Output-specific fields */}
                    {(resultKind === 'sd_card' || resultKind === 'smartphone_upgrade') && (
                      <div>
                        <label className="mb-1 block text-[11px] text-zinc-400">
                          Upgrade Key ({resultKind === 'sd_card' ? 'sd_card_upgrade' : 'upgrades'})
                        </label>
                        <input
                          list="known-upgrade-keys"
                          value={activeRecipe.upgradeKey ?? ''}
                          onChange={(e) => patchRecipe({ upgradeKey: e.target.value })}
                          placeholder="upgrade_custom"
                          className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-2.5 py-1.5 font-mono"
                        />
                        <datalist id="known-upgrade-keys">
                          {knownUpgradeKeys.map((k) => (
                            <option key={k} value={k} />
                          ))}
                        </datalist>
                      </div>
                    )}

                    {resultKind === 'smartphone_skin' && (
                      <div className="space-y-2">
                        <div>
                          <label className="mb-1 block text-[11px] text-zinc-400">Target Skin ID</label>
                          <input
                            list="known-skin-ids"
                            value={activeRecipe.skinId ?? ''}
                            onChange={(e) => patchRecipe({ skinId: e.target.value })}
                            placeholder={`${project.namespace}:custom_skin`}
                            className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-2.5 py-1.5 font-mono"
                          />
                          <datalist id="known-skin-ids">
                            {knownSkinIds.map((k) => (
                              <option key={k} value={k} />
                            ))}
                          </datalist>
                        </div>
                        <label className="flex items-center gap-2 text-zinc-300">
                          <input
                            type="checkbox"
                            checked={Boolean(activeRecipe.equipOnCraft)}
                            onChange={(e) => patchRecipe({ equipOnCraft: e.target.checked })}
                            className="accent-red-500"
                          />
                          Equip skin immediately on craft (instead of unlock only)
                        </label>
                      </div>
                    )}

                    {(resultKind === 'smartphone' ||
                      resultKind === 'smartphone_upgrade' ||
                      resultKind === 'smartphone_skin' ||
                      resultKind === 'custom_item') && (
                      <div>
                        <label className="mb-1 block text-[11px] text-zinc-400">
                          Result Item ID
                        </label>
                        <input
                          list="smartphone-mod-items"
                          value={activeRecipe.resultItem ?? 'cobblemon_smartphone:red_smartphone'}
                          onChange={(e) => patchRecipe({ resultItem: e.target.value })}
                          placeholder="cobblemon_smartphone:red_smartphone"
                          className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-2.5 py-1.5 font-mono text-[11px]"
                        />
                        <datalist id="smartphone-mod-items">
                          {SMARTPHONE_MOD_ITEMS.map((item) => (
                            <option key={item} value={item} />
                          ))}
                        </datalist>
                      </div>
                    )}

                    {resultKind === 'custom_item' && (
                      <div>
                        <label className="mb-1 block text-[11px] text-zinc-400">
                          Custom Components JSON (optional)
                        </label>
                        <textarea
                          rows={3}
                          value={activeRecipe.customComponentsJson ?? ''}
                          onChange={(e) => patchRecipe({ customComponentsJson: e.target.value })}
                          placeholder={'{"minecraft:custom_data": {"my_key": true}}'}
                          className="w-full rounded-lg border border-zinc-800 bg-zinc-950 p-2 font-mono text-[10px]"
                        />
                      </div>
                    )}
                  </div>

                  {/* Inputs per Minecraft Recipe Type */}
                  {recipeType === 'minecraft:crafting_shapeless' && (
                    <div className="space-y-2 rounded-xl border border-zinc-800 bg-zinc-900/40 p-3">
                      <div className="font-semibold text-zinc-300">Shapeless Ingredients</div>
                      {(activeRecipe.ingredients ?? []).map((ing, idx) => (
                        <div
                          key={idx}
                          className="flex items-center justify-between gap-2 rounded-lg border border-zinc-800 bg-zinc-950 px-2.5 py-1.5"
                        >
                          <div className="min-w-0 truncate font-mono text-[11px]">
                            <span className="mr-1.5 text-zinc-500">{ing.type}:</span>
                            {ing.value}
                          </div>
                          <button
                            onClick={() =>
                              patchRecipe({
                                ingredients: (activeRecipe.ingredients ?? []).filter(
                                  (_, i) => i !== idx
                                ),
                              })
                            }
                            className="text-zinc-500 hover:text-red-400"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      ))}
                      <div className="flex gap-1.5 pt-1">
                        <select
                          value={newIngType}
                          onChange={(e) => setNewIngType(e.target.value as 'item' | 'tag')}
                          className="rounded-lg border border-zinc-800 bg-zinc-950 px-2 py-1 text-[11px]"
                        >
                          <option value="item">item</option>
                          <option value="tag">tag</option>
                        </select>
                        <input
                          value={newIngVal}
                          onChange={(e) => setNewIngVal(e.target.value)}
                          placeholder="minecraft:diamond"
                          className="min-w-0 flex-1 rounded-lg border border-zinc-800 bg-zinc-950 px-2 py-1 font-mono text-[11px]"
                        />
                        <button
                          onClick={() => {
                            if (!newIngVal.trim()) return
                            const next: RecipeIngredient = {
                              type: newIngType,
                              value: newIngVal.trim().replace(/^#/, ''),
                            }
                            patchRecipe({
                              ingredients: [...(activeRecipe.ingredients ?? []), next],
                            })
                          }}
                          className="rounded-lg bg-zinc-800 px-2.5 hover:bg-zinc-700"
                        >
                          <Plus className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </div>
                  )}

                  {recipeType === 'minecraft:crafting_shaped' && (
                    <div className="space-y-2 rounded-xl border border-zinc-800 bg-zinc-900/40 p-3">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-zinc-300">
                          3×3 Shaped Grid (prefix <code className="text-red-300">#</code> for tag)
                        </span>
                        <button
                          onClick={() => patchRecipe({ grid: Array(9).fill('') })}
                          className="text-[10px] text-zinc-500 hover:text-zinc-300"
                        >
                          Clear
                        </button>
                      </div>
                      <div className="grid grid-cols-3 gap-1.5">
                        {Array.from({ length: 9 }, (_, i) => (
                          <input
                            key={i}
                            value={activeRecipe.grid?.[i] ?? ''}
                            onChange={(e) => {
                              const next = [...(activeRecipe.grid ?? Array(9).fill(''))]
                              next[i] = e.target.value
                              patchRecipe({ grid: next })
                            }}
                            placeholder={`Slot ${i + 1}`}
                            className="rounded-lg border border-zinc-800 bg-zinc-950 px-2 py-1.5 font-mono text-[10px]"
                          />
                        ))}
                      </div>
                    </div>
                  )}

                  {recipeType === 'minecraft:smithing_transform' && (
                    <div className="space-y-2.5 rounded-xl border border-zinc-800 bg-zinc-900/40 p-3">
                      <div className="font-semibold text-zinc-300">
                        Smithing Table Slots (prefix <code className="text-red-300">#</code> for tag)
                      </div>
                      <div>
                        <label className="mb-1 block text-[11px] text-zinc-400">
                          Template Item (leave empty for none)
                        </label>
                        <input
                          value={activeRecipe.templateItem ?? ''}
                          onChange={(e) => patchRecipe({ templateItem: e.target.value })}
                          placeholder="cobblemon:upgrade"
                          className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-2.5 py-1.5 font-mono"
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-[11px] text-zinc-400">Base Item / Tag</label>
                        <input
                          value={activeRecipe.baseItem ?? '#cobblemon_smartphone:smartphones'}
                          onChange={(e) => patchRecipe({ baseItem: e.target.value })}
                          placeholder="#cobblemon_smartphone:smartphones"
                          className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-2.5 py-1.5 font-mono"
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-[11px] text-zinc-400">Addition Item / Tag</label>
                        <input
                          value={activeRecipe.additionItem ?? ''}
                          onChange={(e) => patchRecipe({ additionItem: e.target.value })}
                          placeholder="minecraft:diamond"
                          className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-2.5 py-1.5 font-mono"
                        />
                      </div>
                    </div>
                  )}

                  {(cooking || recipeType === 'minecraft:stonecutting') && (
                    <div className="space-y-2.5 rounded-xl border border-zinc-800 bg-zinc-900/40 p-3">
                      <div className="font-semibold text-zinc-300">
                        {cooking ? 'Cooking Input' : 'Stonecutter Input'}
                      </div>
                      <div>
                        <label className="mb-1 block text-[11px] text-zinc-400">
                          Ingredient (item ID or <code className="text-red-300">#tag</code>)
                        </label>
                        <input
                          value={activeRecipe.singleIngredient ?? ''}
                          onChange={(e) => patchRecipe({ singleIngredient: e.target.value })}
                          placeholder="cobblemon:upgrade"
                          className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-2.5 py-1.5 font-mono"
                        />
                      </div>
                      {cooking && (
                        <div className="grid grid-cols-2 gap-2">
                          <div>
                            <label className="mb-1 block text-[11px] text-zinc-400">Experience</label>
                            <input
                              type="number"
                              step={0.05}
                              min={0}
                              value={activeRecipe.experience ?? 0.35}
                              onChange={(e) =>
                                patchRecipe({ experience: Math.max(0, Number(e.target.value) || 0) })
                              }
                              className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-2.5 py-1.5 font-mono"
                            />
                          </div>
                          <div>
                            <label className="mb-1 block text-[11px] text-zinc-400">
                              Cooking Time (ticks)
                            </label>
                            <input
                              type="number"
                              step={20}
                              min={1}
                              value={activeRecipe.cookingtime ?? defaultCookTimeFor(recipeType)}
                              onChange={(e) =>
                                patchRecipe({ cookingtime: Math.max(1, Number(e.target.value) || 200) })
                              }
                              className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-2.5 py-1.5 font-mono"
                            />
                          </div>
                        </div>
                      )}
                    </div>
                  )}

                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="mb-1 block text-zinc-400">Recipe Group (optional)</label>
                      <input
                        value={activeRecipe.group ?? ''}
                        onChange={(e) => patchRecipe({ group: e.target.value || undefined })}
                        placeholder="e.g. sd_cards"
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-zinc-400">Require Mod ID</label>
                      <input
                        value={activeRecipe.require_mod ?? ''}
                        onChange={(e) => patchRecipe({ require_mod: e.target.value || undefined })}
                        placeholder="e.g. waystones"
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono"
                      />
                    </div>
                  </div>
                </div>
              ) : (
                <pre className="overflow-auto rounded-xl border border-zinc-800 bg-zinc-900 p-3 font-mono text-[11px] text-emerald-300">
                  {serializedJson}
                </pre>
              )}
            </div>
          </div>
        </>
      ) : (
        <div className="flex flex-1 flex-col items-center justify-center gap-4 p-8 text-center">
          <Sparkles className="h-10 w-10 text-red-400/70" />
          <div className="max-w-md space-y-1">
            <h2 className="text-base font-semibold text-zinc-100">Recipes &amp; SD Cards</h2>
            <p className="text-xs text-zinc-400">
              Create Minecraft 1.21.1 datapack recipes — Crafting Table (Shaped &amp; Shapeless),
              Smithing Table, Furnace, Blast Furnace, Smoker, Campfire, and Stonecutter — for SD
              Cards, Smartphone Upgrades, 3D Skins, or any item.
            </p>
          </div>
          <div className="flex flex-wrap justify-center gap-2">
            <button
              onClick={() => createPreset('sd_card')}
              className="rounded-lg bg-red-500/20 px-3.5 py-2 text-xs font-medium text-red-200 hover:bg-red-500/30"
            >
              + New SD Card Recipe
            </button>
            <button
              onClick={() => createPreset('smartphone_upgrade')}
              className="rounded-lg bg-amber-500/20 px-3.5 py-2 text-xs font-medium text-amber-200 hover:bg-amber-500/30"
            >
              + New Upgrade Recipe
            </button>
            <button
              onClick={() => createPreset('smartphone_skin')}
              className="rounded-lg bg-emerald-500/20 px-3.5 py-2 text-xs font-medium text-emerald-200 hover:bg-emerald-500/30"
            >
              + New Skin Recipe
            </button>
            <button
              onClick={() => createPreset('custom_item')}
              className="rounded-lg bg-violet-500/20 px-3.5 py-2 text-xs font-medium text-violet-200 hover:bg-violet-500/30"
            >
              + New Craft / Item
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
