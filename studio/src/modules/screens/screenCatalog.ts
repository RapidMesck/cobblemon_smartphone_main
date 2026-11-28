import type { ScreenNodeDef, StudioScreen, UiComponentType } from '@/types/schemas'
import { createId } from '@/lib/utils'

export type ComponentCategory = 'Layout' | 'Navigation' | 'Controls' | 'Feedback & Data'

export interface ComponentCatalogItem {
  type: UiComponentType
  label: string
  category: ComponentCategory
  hasChildren: boolean
  description: string
  createDefault: () => ScreenNodeDef
}

export const COMPONENT_CATALOG: ComponentCatalogItem[] = [
  // Layout
  {
    type: 'app_header',
    label: 'App Header',
    category: 'Layout',
    hasChildren: true,
    description: 'Top header bar with title, subtitle & icon',
    createDefault: () => ({
      type: 'app_header',
      title: 'Title',
    }),
  },
  {
    type: 'card',
    label: 'Card',
    category: 'Layout',
    hasChildren: true,
    description: 'Elevated surface container with border',
    createDefault: () => ({
      type: 'card',
      padding: 10,
      gap: 6,
      children: [],
    }),
  },
  {
    type: 'column',
    label: 'Column',
    category: 'Layout',
    hasChildren: true,
    description: 'Vertical flex stack',
    createDefault: () => ({ type: 'column', gap: 6, children: [] }),
  },
  {
    type: 'row',
    label: 'Row',
    category: 'Layout',
    hasChildren: true,
    description: 'Horizontal flex row',
    createDefault: () => ({ type: 'row', gap: 6, children: [] }),
  },
  {
    type: 'grid',
    label: 'Grid',
    category: 'Layout',
    hasChildren: true,
    description: 'Multi-column responsive grid',
    createDefault: () => ({ type: 'grid', columns: 2, gap: 6, children: [] }),
  },
  {
    type: 'flow_row',
    label: 'Flow Row',
    category: 'Layout',
    hasChildren: true,
    description: 'Wrapping horizontal chip/tag row',
    createDefault: () => ({ type: 'flow_row', gap: 4, children: [] }),
  },
  {
    type: 'accordion',
    label: 'Accordion',
    category: 'Layout',
    hasChildren: true,
    description: 'Collapsible section with header',
    createDefault: () => ({
      type: 'accordion',
      title: 'Section',
      children: [],
    }),
  },
  {
    type: 'list_tile',
    label: 'List Tile',
    category: 'Layout',
    hasChildren: false,
    description: 'Structured row with icon, title, subtitle & chevron',
    createDefault: () => ({
      type: 'list_tile',
      title: 'List Item',
      show_chevron: true,
    }),
  },
  {
    type: 'scroll',
    label: 'Scroll View',
    category: 'Layout',
    hasChildren: true,
    description: 'Scrollable viewport container',
    createDefault: () => ({ type: 'scroll', gap: 6, children: [] }),
  },
  {
    type: 'stack',
    label: 'Stack',
    category: 'Layout',
    hasChildren: true,
    description: 'Layered z-index container',
    createDefault: () => ({ type: 'stack', children: [] }),
  },
  {
    type: 'divider',
    label: 'Divider',
    category: 'Layout',
    hasChildren: false,
    description: 'Subtle horizontal rule',
    createDefault: () => ({ type: 'divider' }),
  },
  {
    type: 'spacer',
    label: 'Spacer',
    category: 'Layout',
    hasChildren: false,
    description: 'Empty spacing block',
    createDefault: () => ({ type: 'spacer', height: '8' }),
  },

  // Navigation
  {
    type: 'tabs',
    label: 'Tabs',
    category: 'Navigation',
    hasChildren: false,
    description: 'Tab bar (pills, bottom_nav, floating_dock)',
    createDefault: () => ({
      type: 'tabs',
      state_key: 'tab',
      style: 'pills',
      tabs: [
        { id: 'tab_1', label: 'Tab 1' },
        { id: 'tab_2', label: 'Tab 2' },
      ],
    }),
  },

  // Controls
  {
    type: 'button',
    label: 'Button',
    category: 'Controls',
    hasChildren: false,
    description: 'Clickable primary/secondary/danger button',
    createDefault: () => ({
      type: 'button',
      label: 'Button',
      variant: 'primary',
    }),
  },
  {
    type: 'pill',
    label: 'Pill Toggle',
    category: 'Controls',
    hasChildren: false,
    description: 'Compact pill filter button',
    createDefault: () => ({
      type: 'pill',
      label: 'Pill',
    }),
  },
  {
    type: 'switch',
    label: 'Switch',
    category: 'Controls',
    hasChildren: false,
    description: 'Modern boolean toggle switch',
    createDefault: () => ({
      type: 'switch',
      label: 'Toggle',
      state_key: 'toggle',
    }),
  },
  {
    type: 'checkbox',
    label: 'Checkbox',
    category: 'Controls',
    hasChildren: false,
    description: 'Checkbox bound to state_key',
    createDefault: () => ({
      type: 'checkbox',
      label: 'Option',
      state_key: 'checked',
    }),
  },
  {
    type: 'slider',
    label: 'Slider',
    category: 'Controls',
    hasChildren: false,
    description: 'Range slider bound to state_key',
    createDefault: () => ({
      type: 'slider',
      label: 'Value',
      state_key: 'value',
      min: 0,
      max: 100,
      step: 1,
    }),
  },
  {
    type: 'number_stepper',
    label: 'Number Stepper',
    category: 'Controls',
    hasChildren: false,
    description: 'Increment/decrement numeric control',
    createDefault: () => ({
      type: 'number_stepper',
      label: 'Amount',
      state_key: 'amount',
      min: 0,
      max: 64,
      step: 1,
    }),
  },
  {
    type: 'segmented_control',
    label: 'Segmented Control',
    category: 'Controls',
    hasChildren: false,
    description: 'Multi-option segmented selector',
    createDefault: () => ({
      type: 'segmented_control',
      state_key: 'option',
      options: [
        { id: 'opt_1', label: 'Option 1' },
        { id: 'opt_2', label: 'Option 2' },
      ],
    }),
  },
  {
    type: 'text_input',
    label: 'Text Input',
    category: 'Controls',
    hasChildren: false,
    description: 'Editable text field',
    createDefault: () => ({
      type: 'text_input',
      state_key: 'input',
      placeholder: 'Enter text...',
    }),
  },

  // Feedback & Data
  {
    type: 'text',
    label: 'Text',
    category: 'Feedback & Data',
    hasChildren: false,
    description: 'Styled text with {state.*} interpolation',
    createDefault: () => ({
      type: 'text',
      text: 'Text',
      size: 11,
      weight: 'normal',
    }),
  },
  {
    type: 'badge',
    label: 'Badge / Chip',
    category: 'Feedback & Data',
    hasChildren: false,
    description: 'Semantic status pill',
    createDefault: () => ({
      type: 'badge',
      label: 'Badge',
      variant: 'primary',
    }),
  },
  {
    type: 'stat_bar',
    label: 'Stat Bar',
    category: 'Feedback & Data',
    hasChildren: false,
    description: 'Labeled progress/stat bar',
    createDefault: () => ({
      type: 'stat_bar',
      label: 'Stat',
      value: '50',
      max_value: 100,
    }),
  },
  {
    type: 'progress_bar',
    label: 'Progress Bar',
    category: 'Feedback & Data',
    hasChildren: false,
    description: 'Simple horizontal progress bar',
    createDefault: () => ({
      type: 'progress_bar',
      progress: '0.5',
    }),
  },
  {
    type: 'pokemon_view',
    label: 'Pokémon View',
    category: 'Feedback & Data',
    hasChildren: false,
    description: 'Renders a Cobblemon species preview',
    createDefault: () => ({
      type: 'pokemon_view',
      species: 'cobblemon:pikachu',
    }),
  },
  {
    type: 'item_icon',
    label: 'Item Icon',
    category: 'Feedback & Data',
    hasChildren: false,
    description: 'Renders a Minecraft/mod item stack',
    createDefault: () => ({
      type: 'item_icon',
      item: 'cobblemon:poke_ball',
    }),
  },
  {
    type: 'avatar',
    label: 'Player Avatar',
    category: 'Feedback & Data',
    hasChildren: false,
    description: 'Renders a player head avatar',
    createDefault: () => ({
      type: 'avatar',
    }),
  },
  {
    type: 'icon',
    label: 'Vector Icon',
    category: 'Feedback & Data',
    hasChildren: false,
    description: 'Built-in vector/pixel icon',
    createDefault: () => ({
      type: 'icon',
      icon: 'STAR',
      size: 16,
    }),
  },
  {
    type: 'image',
    label: 'Image',
    category: 'Feedback & Data',
    hasChildren: false,
    description: 'Custom resource texture',
    createDefault: () => ({
      type: 'image',
    }),
  },
  {
    type: 'empty_state',
    label: 'Empty State',
    category: 'Feedback & Data',
    hasChildren: false,
    description: 'Placeholder when a list is empty',
    createDefault: () => ({
      type: 'empty_state',
      title: 'Empty',
      description: '',
    }),
  },
]

export const CONTAINER_TYPES = new Set<UiComponentType>(
  COMPONENT_CATALOG.filter((c) => c.hasChildren).map((c) => c.type)
)

// ---------------------------------------------------------------------------
// Tree Path Utilities (path = number[] e.g. [0, 2, 1])
// ---------------------------------------------------------------------------

export function getNodeAtPath(body: ScreenNodeDef[], path: number[]): ScreenNodeDef | null {
  if (path.length === 0) return null
  let current: ScreenNodeDef | undefined = body[path[0]]
  for (let i = 1; i < path.length; i++) {
    if (!current?.children) return null
    current = current.children[path[i]]
  }
  return current ?? null
}

export function updateNodeAtPath(
  body: ScreenNodeDef[],
  path: number[],
  updater: (node: ScreenNodeDef) => ScreenNodeDef
): ScreenNodeDef[] {
  if (path.length === 0) return body
  const [head, ...rest] = path
  return body.map((node, idx) => {
    if (idx !== head) return node
    if (rest.length === 0) return updater(node)
    return {
      ...node,
      children: updateNodeAtPath(node.children ?? [], rest, updater),
    }
  })
}

export function deleteNodeAtPath(body: ScreenNodeDef[], path: number[]): ScreenNodeDef[] {
  if (path.length === 0) return body
  const [head, ...rest] = path
  if (rest.length === 0) {
    return body.filter((_, idx) => idx !== head)
  }
  return body.map((node, idx) =>
    idx === head
      ? { ...node, children: deleteNodeAtPath(node.children ?? [], rest) }
      : node
  )
}

export function moveNodeAtPath(
  body: ScreenNodeDef[],
  path: number[],
  direction: -1 | 1
): { body: ScreenNodeDef[]; nextPath: number[] } {
  if (path.length === 0) return { body, nextPath: path }
  const parentPath = path.slice(0, -1)
  const index = path[path.length - 1]

  const swapInList = (list: ScreenNodeDef[]): { list: ScreenNodeDef[]; newIdx: number } => {
    const target = index + direction
    if (target < 0 || target >= list.length) return { list, newIdx: index }
    const copy = [...list]
    const [moved] = copy.splice(index, 1)
    copy.splice(target, 0, moved)
    return { list: copy, newIdx: target }
  }

  if (parentPath.length === 0) {
    const { list, newIdx } = swapInList(body)
    return { body: list, nextPath: [newIdx] }
  }

  let nextIdx = index
  const updated = updateNodeAtPath(body, parentPath, (parent) => {
    const { list, newIdx } = swapInList(parent.children ?? [])
    nextIdx = newIdx
    return { ...parent, children: list }
  })
  return { body: updated, nextPath: [...parentPath, nextIdx] }
}

export function insertChildAtPath(
  body: ScreenNodeDef[],
  parentPath: number[] | null,
  newNode: ScreenNodeDef
): { body: ScreenNodeDef[]; insertedPath: number[] } {
  if (!parentPath || parentPath.length === 0) {
    const next = [...body, newNode]
    return { body: next, insertedPath: [next.length - 1] }
  }
  let childIdx = 0
  const updated = updateNodeAtPath(body, parentPath, (parent) => {
    const children = [...(parent.children ?? []), newNode]
    childIdx = children.length - 1
    return { ...parent, children }
  })
  return { body: updated, insertedPath: [...parentPath, childIdx] }
}

// ---------------------------------------------------------------------------
// Expression & Placeholder Evaluator for Live Preview
// ---------------------------------------------------------------------------

export function interpolateText(
  text: string | undefined,
  state: Record<string, string | number | boolean>
): string {
  if (!text) return ''
  return text.replace(/\{([^}]+)\}/g, (_, rawKey: string) => {
    const key = rawKey.trim()
    if (key === 'player.name') return 'TrainerRed'
    if (key === 'player.health') return '20'
    if (key.startsWith('state.')) {
      const k = key.slice(6)
      return state[k] !== undefined ? String(state[k]) : `{${key}}`
    }
    if (state[key] !== undefined) return String(state[key])
    return `{${key}}`
  })
}

export function evalVisibleIf(
  expr: string | undefined,
  state: Record<string, string | number | boolean>
): boolean {
  if (!expr || !expr.trim()) return true
  const trimmed = expr.trim()
  // Handle equality / inequality: state.foo == 'bar' or state.flag == true
  const eqMatch = trimmed.match(/^(!?)\s*state\.([a-zA-Z0-9_]+)\s*(==|!=)\s*['"]?([^'"]+)['"]?$/)
  if (eqMatch) {
    const [, neg, key, op, expectedRaw] = eqMatch
    const actual = String(state[key] ?? '')
    const expected = expectedRaw.trim()
    let res = op === '==' ? actual === expected : actual !== expected
    if (neg === '!') res = !res
    return res
  }
  // Handle simple truthy / falsy: state.flag or !state.flag
  const boolMatch = trimmed.match(/^(!?)\s*state\.([a-zA-Z0-9_]+)$/)
  if (boolMatch) {
    const [, neg, key] = boolMatch
    const val = Boolean(state[key])
    return neg === '!' ? !val : val
  }
  return true
}

// ---------------------------------------------------------------------------
// Screen Starter Templates
// ---------------------------------------------------------------------------

export function createScreenFromTemplate(
  template: 'blank' | 'dashboard' | 'settings' | 'shop',
  index: number
): StudioScreen {
  const now = Date.now()
  const base = {
    uid: createId('screen'),
    viewport: 'large' as const,
    corner_radius: 16,
    show_bezel_back: true,
    show_bezel_battery: true,
    show_bezel_hd_badge: false,
    updatedAt: now,
  }

  if (template === 'settings') {
    return {
      ...base,
      id: `settings_screen_${index}`,
      title: `Settings Panel ${index}`,
      default_state: { notifications: true, volume: 80, mode: 'normal' },
      actions: {
        save: {
          effects: [{ type: 'toast', text: 'Settings saved!', kind: 'success', icon: 'CHECK' }],
        },
      },
      body: [
        { type: 'app_header', title: 'App Settings', subtitle: 'Preferences & audio', icon: 'SETTINGS' },
        {
          type: 'card',
          padding: 10,
          gap: 8,
          children: [
            { type: 'switch', label: 'Push Notifications', state_key: 'notifications' },
            { type: 'slider', label: 'Alert Volume', state_key: 'volume', min: 0, max: 100, step: 5 },
            {
              type: 'segmented_control',
              state_key: 'mode',
              options: [
                { id: 'eco', label: 'Eco' },
                { id: 'normal', label: 'Balanced' },
                { id: 'turbo', label: 'Turbo' },
              ],
            },
          ],
        },
        { type: 'button', label: 'Save Changes', icon: 'CHECK', variant: 'primary', on_click: 'save' },
      ],
    }
  }

  if (template === 'shop') {
    return {
      ...base,
      id: `poke_shop_${index}`,
      title: `Poké Mart ${index}`,
      default_state: { category: 'balls', qty: 5 },
      actions: {
        buy_ball: {
          cooldown_seconds: 1,
          effects: [
            { type: 'give_item', item: 'cobblemon:poke_ball', count: 5 },
            { type: 'toast', text: 'Purchased {state.qty}× Poké Ball!', kind: 'success', icon: 'CHECK' },
          ],
        },
      },
      body: [
        { type: 'app_header', title: 'Poké Mart', subtitle: 'Instant delivery to inventory', icon: 'STAR' },
        {
          type: 'tabs',
          state_key: 'category',
          style: 'pills',
          tabs: [
            { id: 'balls', label: 'Poké Balls' },
            { id: 'meds', label: 'Medicine' },
          ],
        },
        {
          type: 'card',
          visible_if: "state.category == 'balls'",
          padding: 10,
          gap: 6,
          children: [
            {
              type: 'row',
              gap: 8,
              children: [
                { type: 'item_icon', item: 'cobblemon:poke_ball' },
                {
                  type: 'column',
                  gap: 2,
                  children: [
                    { type: 'text', text: 'Poké Ball', size: 12, weight: 'bold' },
                    { type: 'badge', label: '₽200 each', variant: 'gold' },
                  ],
                },
              ],
            },
            { type: 'number_stepper', label: 'Quantity', state_key: 'qty', min: 1, max: 64, step: 1 },
            { type: 'button', label: 'Buy Now', icon: 'CHECK', variant: 'primary', on_click: 'buy_ball' },
          ],
        },
        {
          type: 'empty_state',
          visible_if: "state.category == 'meds'",
          title: 'Out of Stock',
          description: 'Medicine restocks at sunrise.',
          icon: 'INFO',
        },
      ],
    }
  }

  if (template === 'dashboard') {
    return {
      ...base,
      id: `trainer_dashboard_${index}`,
      title: `Trainer Dashboard ${index}`,
      default_state: { active_tab: 'team', shiny_filter: false },
      actions: {
        heal: {
          cooldown_seconds: 10,
          effects: [
            { type: 'function', function: 'heal_party' },
            { type: 'toast', text: 'Party healed for {player.name}!', kind: 'success', icon: 'HEART_FILLED' },
          ],
        },
      },
      body: [
        { type: 'app_header', title: 'Trainer Dashboard', subtitle: 'Welcome, {player.name}', icon: 'SPARKLES' },
        {
          type: 'tabs',
          state_key: 'active_tab',
          style: 'pills',
          tabs: [
            { id: 'team', label: 'Team' },
            { id: 'stats', label: 'Stats' },
          ],
        },
        {
          type: 'card',
          visible_if: "state.active_tab == 'team'",
          padding: 10,
          gap: 6,
          children: [
            {
              type: 'row',
              gap: 8,
              children: [
                { type: 'pokemon_view', species: 'cobblemon:pikachu' },
                {
                  type: 'column',
                  gap: 3,
                  children: [
                    { type: 'text', text: 'Pikachu • Lv. 38', size: 12, weight: 'bold' },
                    { type: 'stat_bar', label: 'HP', value: '92', max_value: 100, value_text: '92/100' },
                  ],
                },
              ],
            },
            { type: 'button', label: 'Heal Party', icon: 'HEART_FILLED', variant: 'primary', on_click: 'heal' },
          ],
        },
        {
          type: 'card',
          visible_if: "state.active_tab == 'stats'",
          padding: 10,
          gap: 6,
          children: [
            { type: 'list_tile', title: 'Battles Won', subtitle: 'Ranked & Wild', icon: 'SWORDS', trailing_text: '128' },
            { type: 'list_tile', title: 'Trades Completed', subtitle: 'Global Network', icon: 'TRADE', trailing_text: '24' },
          ],
        },
      ],
    }
  }

  return {
    ...base,
    id: `screen_${index}`,
    title: `Screen ${index}`,
    default_state: {},
    actions: {},
    body: [],
  }
}
