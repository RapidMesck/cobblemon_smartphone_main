import type { StudioTheme, ThemeColors } from '@/types/schemas'

export interface BuiltinAppMeta {
  id: string
  stem: string
  label: string
}

export const BUILTIN_APPS: BuiltinAppMeta[] = [
  { id: 'cobblemon_smartphone:pokedex', stem: 'pokedex', label: 'Pokédex' },
  { id: 'cobblemon_smartphone:pokeinfo', stem: 'pokeinfo', label: 'PokéInfo' },
  { id: 'cobblemon_smartphone:social', stem: 'social', label: 'Social' },
  { id: 'cobblemon_smartphone:gps', stem: 'gps', label: 'GPS' },
  { id: 'cobblemon_smartphone:structure_compass', stem: 'structure_compass', label: 'Structure Compass' },
  { id: 'cobblemon_smartphone:heal', stem: 'heal', label: 'Heal Party' },
  { id: 'cobblemon_smartphone:pc', stem: 'pc', label: 'PC Box' },
  { id: 'cobblemon_smartphone:ender_chest', stem: 'cloud', label: 'Ender Cloud' },
  { id: 'cobblemon_smartphone:rotom', stem: 'rotom', label: 'Rotom Drone' },
  { id: 'cobblemon_smartphone:app_store', stem: 'app_store', label: 'App Store' },
  { id: 'cobblemon_smartphone:calculator', stem: 'calculator', label: 'Calculator' },
  { id: 'cobblemon_smartphone:notes', stem: 'notes', label: 'Notes' },
  { id: 'cobblemon_smartphone:crafting', stem: 'crafting', label: 'Crafting' },
  { id: 'cobblemon_smartphone:cobblenav_pokenav', stem: 'pokenav', label: 'PokéNav' },
  { id: 'cobblemon_smartphone:cobblenav_fishingnav', stem: 'fishnav', label: 'FishingNav' },
  { id: 'cobblemon_smartphone:waystones_warp_stone', stem: 'waystone', label: 'Waystones' },
  { id: 'cobblemon_smartphone:cobbledollars', stem: 'cobbledollars', label: 'CobbleDollars' },
  { id: 'cobblemon_smartphone:rct_trainer_card', stem: 'trainer', label: 'Trainer Card' },
  { id: 'cobblemon_smartphone:toms_storage', stem: 'toms_storage', label: "Tom's Storage" },
  { id: 'cobblemon_smartphone:refined_storage', stem: 'refinedstorage', label: 'Refined Storage' },
  { id: 'cobblemon_smartphone:ae2', stem: 'ae2', label: 'AE2 Terminal' },
  { id: 'cobblemon_smartphone:ae2_crafting', stem: 'wireless', label: 'AE2 Wireless' },
  { id: 'cobblemon_smartphone:patchouli', stem: 'patchouli', label: 'Patchouli' },
]

export type ThemeElementKey = keyof StudioTheme['elements']

export interface ThemeElementMeta {
  key: ThemeElementKey
  label: string
  dimensions: string
  layout: string
  defaultPath: string
}

export const THEME_ELEMENTS: ThemeElementMeta[] = [
  {
    key: 'home_button',
    label: 'Home Button',
    dimensions: '7×14 px',
    layout: 'Top 7×7 normal, Bottom 7×7 hover',
    defaultPath: '/mod-assets/textures/gui/elements/home_button.png',
  },
  {
    key: 'prev_button',
    label: 'Previous Button',
    dimensions: '7×14 px',
    layout: 'Top 7×7 normal, Bottom 7×7 hover',
    defaultPath: '/mod-assets/textures/gui/elements/prev_button.png',
  },
  {
    key: 'next_button',
    label: 'Next Button',
    dimensions: '7×14 px',
    layout: 'Top 7×7 normal, Bottom 7×7 hover',
    defaultPath: '/mod-assets/textures/gui/elements/next_button.png',
  },
  {
    key: 'settings_button',
    label: 'Settings Button',
    dimensions: '11×22 px',
    layout: 'Top 11×11 normal, Bottom 11×11 hover',
    defaultPath: '/mod-assets/textures/gui/elements/settings_button.png',
  },
  {
    key: 'drag_handle',
    label: 'Drag Handle',
    dimensions: '8×16 px',
    layout: 'Top 8×8 normal, Bottom 8×8 hover',
    defaultPath: '/mod-assets/textures/gui/elements/drag_handle.png',
  },
  {
    key: 'view_select',
    label: 'Visibility Eye',
    dimensions: '8×16 px',
    layout: 'Top 8×8 normal, Bottom 8×8 hover',
    defaultPath: '/mod-assets/textures/gui/elements/view_select.png',
  },
  {
    key: 'social_pokeball',
    label: 'Social Pokéball',
    dimensions: '12×12 px',
    layout: 'Single 12×12 frame',
    defaultPath: '/mod-assets/textures/gui/social/background_poke_ball.png',
  },
]

export interface ColorGroup {
  title: string
  keys: Array<{ key: keyof ThemeColors; label: string; hint: string }>
}

export const THEME_COLOR_GROUPS: ColorGroup[] = [
  {
    title: 'Brand & Accents',
    keys: [
      { key: 'primary', label: 'Primary', hint: 'Main accent, active tabs, buttons' },
      { key: 'primary_hover', label: 'Primary Hover', hint: 'Hovered accent buttons & links' },
      { key: 'primary_border', label: 'Primary Border', hint: 'Card borders & outlines' },
    ],
  },
  {
    title: 'Surfaces & Containers',
    keys: [
      { key: 'surface', label: 'Surface', hint: 'Cards, modals, main panels' },
      { key: 'surface_alt', label: 'Surface Alt', hint: 'Alternating rows & sub-panels' },
      { key: 'surface_hover', label: 'Surface Hover', hint: 'Hovered list items' },
      { key: 'shadow', label: 'Shadow', hint: 'Drop shadow (#AARRGGBB)' },
    ],
  },
  {
    title: 'Header & Navigation',
    keys: [
      { key: 'header_navy', label: 'Header Navy', hint: 'Top bars & dark headers' },
      { key: 'header_navy_light', label: 'Header Light', hint: 'Secondary header panels' },
    ],
  },
  {
    title: 'Typography',
    keys: [
      { key: 'text_primary', label: 'Text Primary', hint: 'Header titles & bright labels' },
      { key: 'text_secondary', label: 'Text Secondary', hint: 'Subtitles & icon glyphs' },
      { key: 'text_muted', label: 'Text Muted', hint: 'Inactive tabs & disabled hints' },
      { key: 'text_dark', label: 'Text Dark', hint: 'Primary text on surface cards' },
      { key: 'text_dark_muted', label: 'Text Dark Muted', hint: 'Secondary text on cards' },
    ],
  },
  {
    title: 'Status & Clock',
    keys: [
      { key: 'time', label: 'Clock Time', hint: 'Home screen clock color' },
      { key: 'gold', label: 'Gold', hint: 'Checkmarks, stars, highlights' },
      { key: 'success', label: 'Success', hint: 'Online dot & confirmations' },
      { key: 'danger', label: 'Danger', hint: 'Delete actions & errors' },
    ],
  },
  {
    title: 'Battery Indicator',
    keys: [
      { key: 'battery_border', label: 'Battery Border', hint: 'Outer battery bezel' },
      { key: 'battery_empty', label: 'Battery Empty', hint: 'Unfilled battery cavity' },
      { key: 'battery_high', label: 'Battery High', hint: 'Charge > 50%' },
      { key: 'battery_med', label: 'Battery Med', hint: '20% < charge ≤ 50%' },
      { key: 'battery_low', label: 'Battery Low', hint: 'Charge ≤ 20%' },
    ],
  },
]
