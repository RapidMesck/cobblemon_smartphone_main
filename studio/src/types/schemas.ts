import { z } from 'zod'

// ---------------------------------------------------------------------------
// 1. THEME SYSTEM (mirrors SmartphoneTheme.kt & SmartphoneThemeColors.kt)
// ---------------------------------------------------------------------------

export const hexColorSchema = z
  .string()
  .regex(/^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/, 'Use #RRGGBB or #AARRGGBB')

export const themeColorsSchema = z.object({
  primary: hexColorSchema,
  primary_hover: hexColorSchema,
  primary_border: hexColorSchema,
  surface: hexColorSchema,
  surface_alt: hexColorSchema,
  surface_hover: hexColorSchema,
  shadow: hexColorSchema,
  header_navy: hexColorSchema,
  header_navy_light: hexColorSchema,
  text_primary: hexColorSchema,
  text_secondary: hexColorSchema,
  text_muted: hexColorSchema,
  text_dark: hexColorSchema,
  text_dark_muted: hexColorSchema,
  gold: hexColorSchema,
  danger: hexColorSchema,
  success: hexColorSchema,
  time: hexColorSchema,
  battery_border: hexColorSchema,
  battery_empty: hexColorSchema,
  battery_high: hexColorSchema,
  battery_med: hexColorSchema,
  battery_low: hexColorSchema,
})

export type ThemeColors = z.infer<typeof themeColorsSchema>

export const DEFAULT_THEME_COLORS: ThemeColors = {
  primary: '#3A96B6',
  primary_hover: '#4FB4D6',
  primary_border: '#79B7C9',
  surface: '#EFFDFF',
  surface_alt: '#F2FBFD',
  surface_hover: '#D0E8F5',
  shadow: '#35000000',
  header_navy: '#163A49',
  header_navy_light: '#24576A',
  text_primary: '#FFFFFF',
  text_secondary: '#E6FFFF',
  text_muted: '#8AA5AD',
  text_dark: '#1A1A2E',
  text_dark_muted: '#555555',
  gold: '#FFD700',
  danger: '#FD0100',
  success: '#3AAA55',
  time: '#E6FFFF',
  battery_border: '#5BD1DA',
  battery_empty: '#1D4A59',
  battery_high: '#22C55E',
  battery_med: '#EAB308',
  battery_low: '#EF4444',
}

export const DARK_THEME_COLORS: ThemeColors = {
  primary: '#5BD1DA',
  primary_hover: '#7CE3EB',
  primary_border: '#35454F',
  surface: '#1C1C22',
  surface_alt: '#22222A',
  surface_hover: '#2E2E38',
  shadow: '#35000000',
  header_navy: '#111114',
  header_navy_light: '#1A1A20',
  text_primary: '#FFFFFF',
  text_secondary: '#A0A0B0',
  text_muted: '#686878',
  text_dark: '#F0F0F5',
  text_dark_muted: '#A0A0B2',
  gold: '#FFD700',
  danger: '#FF4444',
  success: '#3AAA55',
  time: '#A0A0B0',
  battery_border: '#5BD1DA',
  battery_empty: '#111114',
  battery_high: '#48BB78',
  battery_med: '#E5B842',
  battery_low: '#E05252',
}

export const ROTOM_THEME_COLORS: ThemeColors = {
  primary: '#EA580C',
  primary_hover: '#F97316',
  primary_border: '#9A3412',
  surface: '#24140E',
  surface_alt: '#2D1912',
  surface_hover: '#3B2218',
  shadow: '#35000000',
  header_navy: '#160B07',
  header_navy_light: '#23120C',
  text_primary: '#FFFFFF',
  text_secondary: '#FFAA00',
  text_muted: '#A87E6F',
  text_dark: '#FED7AA',
  text_dark_muted: '#C29E8E',
  gold: '#FBBF24',
  danger: '#EF4444',
  success: '#3AAA55',
  time: '#FFAA00',
  battery_border: '#F97316',
  battery_empty: '#160B07',
  battery_high: '#2EB85C',
  battery_med: '#FBBF24',
  battery_low: '#EF4444',
}

export interface StudioTheme {
  uid: string
  id: string
  name: string
  order: number
  wallpaperAssetId?: string
  largeWallpaperAssetId?: string
  frameAssetId?: string
  largeFrameAssetId?: string
  elements: Partial<
    Record<
      | 'home_button'
      | 'prev_button'
      | 'next_button'
      | 'settings_button'
      | 'drag_handle'
      | 'view_select'
      | 'social_pokeball',
      string
    >
  >
  appIcons: Record<string, { textureAssetId?: string; hoverAssetId?: string }>
  colors: ThemeColors
  updatedAt: number
}

// ---------------------------------------------------------------------------
// 2. DATAPACK UI SCREENS (mirrors DatapackScreenModels.kt)
// ---------------------------------------------------------------------------

export const UI_COMPONENT_TYPES = [
  'column',
  'row',
  'stack',
  'grid',
  'card',
  'scroll',
  'spacer',
  'divider',
  'app_header',
  'flow_row',
  'accordion',
  'list_tile',
  'tabs',
  'button',
  'pill',
  'checkbox',
  'switch',
  'slider',
  'number_stepper',
  'segmented_control',
  'text_input',
  'text',
  'icon',
  'image',
  'avatar',
  'item_icon',
  'pokemon_view',
  'progress_bar',
  'badge',
  'stat_bar',
  'empty_state',
] as const

export type UiComponentType = (typeof UI_COMPONENT_TYPES)[number]

export const BUILTIN_VECTOR_ICONS = [
  'FEED', 'MESSAGE', 'PLUS', 'MINUS', 'BELL', 'SEND', 'PHONE', 'TRASH',
  'HEART', 'HEART_FILLED', 'BACK', 'CLOSE', 'SEARCH', 'USER', 'USERS',
  'FLAG', 'GLOBE', 'CHECK', 'IMAGE', 'SPARKLES', 'CAMERA', 'POLL',
  'SWORDS', 'TRADE', 'CHEVRON_DOWN', 'CHEVRON_UP', 'CHEVRON_RIGHT',
  'SETTINGS', 'FILTER', 'REFRESH', 'STAR', 'STAR_FILLED', 'INFO',
  'WARNING', 'LOCK', 'EDIT', 'COMPASS',
] as const

export interface ScreenNodeDef {
  type: UiComponentType
  id?: string
  width?: string
  height?: string
  padding?: number | number[]
  gap?: number
  columns?: number
  background?: string
  radius?: number
  border_width?: number
  border_color?: string
  visible_if?: string
  for_each?: string
  for_each_as?: string
  tooltip?: string
  on_click?: string
  on_change?: string
  text?: string
  title?: string
  subtitle?: string
  description?: string
  label?: string
  size?: number
  weight?: 'normal' | 'medium' | 'bold'
  color?: string
  icon?: string
  variant?: string
  state_key?: string
  value?: string
  min?: number
  max?: number
  step?: number
  max_value?: number
  value_text?: string
  progress?: string
  placeholder?: string
  species?: string
  item?: string
  trailing_text?: string
  show_chevron?: boolean
  style?: 'pills' | 'bottom_nav' | 'floating_dock'
  tabs?: Array<{ id: string; label: string; icon?: string }>
  options?: Array<{ id: string; label: string; icon?: string }>
  children?: ScreenNodeDef[]
}

export interface ScreenEffectDef {
  type:
    | 'set_state'
    | 'toggle_state'
    | 'inc_state'
    | 'open_screen'
    | 'close_screen'
    | 'open_modal'
    | 'close_modal'
    | 'toast'
    | 'message'
    | 'sound'
    | 'function'
    | 'give_item'
    | 'consume_item'
    | 'command'
  key?: string
  value?: string
  delta?: number
  screen?: string
  text?: string
  kind?: 'info' | 'success' | 'warning' | 'error'
  icon?: string
  duration_ms?: number
  sound?: string
  function?: string
  item?: string
  count?: number
  command?: string
  as_server?: boolean
}

export interface StudioScreen {
  uid: string
  id: string
  title: string
  viewport: 'compact' | 'large' | 'free'
  width_dp?: number
  height_dp?: number
  corner_radius: number
  show_bezel_back: boolean
  show_bezel_battery: boolean
  show_bezel_hd_badge: boolean
  require_mod?: string
  require_upgrade?: string
  default_state: Record<string, string | number | boolean>
  actions: Record<
    string,
    {
      cooldown_seconds?: number
      fe_cost?: number
      require_upgrade?: string
      effects: ScreenEffectDef[]
    }
  >
  body: ScreenNodeDef[]
  updatedAt: number
}

// ---------------------------------------------------------------------------
// 3. DATAPACK ACTIONS (mirrors DatapackActionDefinition.kt & DatapackActionFunction.kt)
// ---------------------------------------------------------------------------

export const BUILTIN_ACTION_FUNCTIONS = [
  { id: 'open_pc', label: 'Open PC', description: 'Opens the player Cobblemon PC' },
  { id: 'heal_party', label: 'Heal Party', description: 'Heals the player Cobblemon party' },
  { id: 'open_pokedex', label: 'Open Pokédex', description: 'Opens the Cobblemon Pokédex' },
  { id: 'open_ender_chest', label: 'Open Ender Chest', description: 'Opens the portable Ender Chest' },
  { id: 'open_pokenav', label: 'Open PokéNav', description: 'Opens CobbleNav PokéNav' },
  { id: 'open_fishingnav', label: 'Open FishingNav', description: 'Opens CobbleNav FishingNav' },
  { id: 'open_waystone', label: 'Open Waystones', description: 'Opens the Waystones destination screen' },
  { id: 'open_cobbledollars_shop', label: 'Open CobbleDollars Shop', description: 'Opens the CobbleDollars shop' },
  { id: 'open_crafting', label: 'Open Crafting Table', description: 'Opens a portable Crafting Table' },
  { id: 'open_gps', label: 'Open GPS', description: 'Opens the Smartphone GPS app' },
  { id: 'open_structure_compass', label: 'Open Structure Compass', description: 'Opens the Structure Compass app' },
  { id: 'open_pokeinfo', label: 'Open PokéInfo', description: 'Opens the PokéInfo encyclopedia' },
] as const

export interface StudioAction {
  uid: string
  id: string
  displayName: string
  textureAssetId?: string
  hoverTextureAssetId?: string
  open_screen?: string
  functions: string[]
  commands: string[]
  use_items: Array<{ id: string; mode: 'use' | 'finish_using' }>
  patchouli_book?: string
  order: number
  cooldown_seconds: number
  fe_cost?: number
  require_mod?: string
  require_upgrade?: string
  upgradeRecipe?: {
    enabled: boolean
    templateItem: string
    additionItem: string
  }
  updatedAt: number
}

// ---------------------------------------------------------------------------
// 4. 3D SKINS & CASES (mirrors SmartphoneSkin.kt)
// ---------------------------------------------------------------------------

export interface StudioSkin {
  uid: string
  id: string
  name: string
  hand_model?: string
  rotom_model?: string
  inventory_model?: string
  handModelAssetId?: string
  rotomModelAssetId?: string
  modelTextures?: Record<string, string> // texture key or resource path -> assetId
  frameAssetId?: string
  largeFrameAssetId?: string
  colors: string[]
  locked: boolean
  require_upgrade?: string
  unlockRecipe?: {
    enabled: boolean
    templateItem: string
    additionItem: string
    equipOnCraft: boolean
  }
  updatedAt: number
}

// ---------------------------------------------------------------------------
// 5. RECIPES & SD CARDS (mirrors Minecraft 1.21.1 data/<ns>/recipe/*.json)
// ---------------------------------------------------------------------------

export type MinecraftRecipeType =
  | 'minecraft:crafting_shaped'
  | 'minecraft:crafting_shapeless'
  | 'minecraft:smithing_transform'
  | 'minecraft:smelting'
  | 'minecraft:blasting'
  | 'minecraft:smoking'
  | 'minecraft:campfire_cooking'
  | 'minecraft:stonecutting'

export type RecipeResultKind =
  | 'sd_card'
  | 'smartphone_upgrade'
  | 'smartphone_skin'
  | 'smartphone'
  | 'custom_item'

/** @deprecated Kept for backwards compatibility with older saved IndexedDB projects */
export type StudioRecipeKind =
  | 'sd_card_shapeless'
  | 'sd_card_shaped'
  | 'smithing_upgrade'
  | 'smithing_skin'

export interface RecipeIngredient {
  type: 'item' | 'tag'
  value: string
}

export interface StudioRecipe {
  uid: string
  id: string
  name: string
  /** Minecraft 1.21.1 datapack recipe type */
  recipeType?: MinecraftRecipeType
  /** What the recipe outputs (SD Card, Smartphone Upgrade/Skin, Smartphone, or any Custom Item) */
  resultKind?: RecipeResultKind
  /** Legacy field migrated automatically via normalizeRecipe */
  kind?: StudioRecipeKind
  category?: 'misc' | 'equipment' | 'building' | 'redstone' | 'food' | 'blocks'
  group?: string
  require_mod?: string
  // Result options
  resultCount?: number
  resultItem?: string // e.g. cobblemon_smartphone:red_smartphone or minecraft:diamond
  customComponentsJson?: string // optional JSON object for custom_item result.components
  // For SD Card & Smartphone Upgrade
  upgradeKey?: string
  // For Smartphone Skin Unlock
  skinId?: string
  equipOnCraft?: boolean
  // For Shapeless
  ingredients?: RecipeIngredient[]
  // For Shaped 3x3 (9 slots, empty string = empty; prefix # for tag)
  grid?: string[]
  // For Smithing Transform
  templateItem?: string
  baseItem?: string // defaults to #cobblemon_smartphone:smartphones
  additionItem?: string
  // For Smelting / Blasting / Smoking / Campfire / Stonecutting
  singleIngredient?: string // item id or #tag
  experience?: number
  cookingtime?: number
  updatedAt: number
}

// ---------------------------------------------------------------------------
// 6. PROJECT & ASSET STORAGE MODELS
// ---------------------------------------------------------------------------

export interface StudioAsset {
  id: string
  projectId: string
  name: string
  kind: 'png' | 'model_json'
  mimeType: string
  dataUrl: string
  sizeBytes: number
  width?: number
  height?: number
  createdAt: number
}

export interface StudioProject {
  id: string
  name: string
  namespace: string
  description: string
  packFormatResource: number
  packFormatData: number
  themes: StudioTheme[]
  screens: StudioScreen[]
  actions: StudioAction[]
  skins: StudioSkin[]
  recipes: StudioRecipe[]
  createdAt: number
  updatedAt: number
}

export type StudioModule = 'dashboard' | 'themes' | 'screens' | 'actions' | 'skins' | 'recipes' | 'code'
