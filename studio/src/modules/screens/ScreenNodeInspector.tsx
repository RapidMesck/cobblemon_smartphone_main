import { useState } from 'react'
import { Code2, Copy, Download, Plus, Settings, Sliders, Trash2, Zap } from 'lucide-react'
import {
  BUILTIN_ACTION_FUNCTIONS,
  BUILTIN_VECTOR_ICONS,
  type ScreenEffectDef,
  type ScreenNodeDef,
  type StudioScreen,
} from '@/types/schemas'
import { serializeScreenJson } from '@/export/packExporter'
import { slugify, triggerDownload } from '@/lib/utils'

export type ScreenInspectorTab = 'node' | 'screen' | 'logic' | 'json'

interface Props {
  namespace: string
  screen: StudioScreen
  selectedNode: ScreenNodeDef | null
  tab: ScreenInspectorTab
  onTabChange: (tab: ScreenInspectorTab) => void
  onUpdateNode: (patch: Partial<ScreenNodeDef>) => void
  onUpdateScreen: (patch: Partial<StudioScreen>) => void
  onToast: (text: string, kind?: 'info' | 'success' | 'error') => void
}

export function ScreenNodeInspector({
  namespace,
  screen,
  selectedNode,
  tab,
  onTabChange,
  onUpdateNode,
  onUpdateScreen,
  onToast,
}: Props) {
  const [newStateKey, setNewStateKey] = useState('')
  const [newStateVal, setNewStateVal] = useState('')
  const [newActionKey, setNewActionKey] = useState('')
  const [jsonError, setJsonError] = useState<string | null>(null)

  const serializedJson = JSON.stringify(serializeScreenJson(namespace, screen), null, 2)

  const addStateVar = () => {
    const k = slugify(newStateKey)
    if (!k) return
    let parsed: string | number | boolean = newStateVal
    if (newStateVal === 'true') parsed = true
    else if (newStateVal === 'false') parsed = false
    else if (newStateVal !== '' && !Number.isNaN(Number(newStateVal))) parsed = Number(newStateVal)
    onUpdateScreen({ default_state: { ...screen.default_state, [k]: parsed } })
    setNewStateKey('')
    setNewStateVal('')
  }

  const addAction = () => {
    const k = slugify(newActionKey)
    if (!k) return
    onUpdateScreen({
      actions: {
        ...screen.actions,
        [k]: {
          cooldown_seconds: 0,
          effects: [{ type: 'toast', text: `Triggered ${k}!`, kind: 'success', icon: 'CHECK' }],
        },
      },
    })
    setNewActionKey('')
  }

  const addEffect = (actionKey: string, type: ScreenEffectDef['type']) => {
    const act = screen.actions[actionKey]
    if (!act) return
    const defaults: Record<ScreenEffectDef['type'], ScreenEffectDef> = {
      toast: { type: 'toast', text: 'Done!', kind: 'success', icon: 'CHECK' },
      set_state: { type: 'set_state', key: 'active_tab', value: 'overview' },
      toggle_state: { type: 'toggle_state', key: 'notifications' },
      inc_state: { type: 'inc_state', key: 'qty', delta: 1 },
      function: { type: 'function', function: 'heal_party' },
      command: { type: 'command', command: 'say Hello {player.name}!' },
      give_item: { type: 'give_item', item: 'cobblemon:poke_ball', count: 1 },
      consume_item: { type: 'consume_item', item: 'minecraft:emerald', count: 1 },
      message: { type: 'message', text: 'Hello from smartphone!' },
      sound: { type: 'sound', sound: 'minecraft:entity.experience_orb.pickup' },
      open_screen: { type: 'open_screen', screen: `${namespace}:other_screen` },
      close_screen: { type: 'close_screen' },
      open_modal: { type: 'open_modal' },
      close_modal: { type: 'close_modal' },
    }
    onUpdateScreen({
      actions: {
        ...screen.actions,
        [actionKey]: { ...act, effects: [...act.effects, defaults[type]] },
      },
    })
  }

  const updateEffect = (actionKey: string, idx: number, patch: Partial<ScreenEffectDef>) => {
    const act = screen.actions[actionKey]
    if (!act) return
    onUpdateScreen({
      actions: {
        ...screen.actions,
        [actionKey]: {
          ...act,
          effects: act.effects.map((e, i) => (i === idx ? { ...e, ...patch } : e)),
        },
      },
    })
  }

  const removeEffect = (actionKey: string, idx: number) => {
    const act = screen.actions[actionKey]
    if (!act) return
    onUpdateScreen({
      actions: {
        ...screen.actions,
        [actionKey]: { ...act, effects: act.effects.filter((_, i) => i !== idx) },
      },
    })
  }

  const handleJsonEdit = (raw: string) => {
    try {
      const parsed = JSON.parse(raw)
      setJsonError(null)
      onUpdateScreen({
        title: parsed.title ?? screen.title,
        viewport: parsed.viewport ?? screen.viewport,
        corner_radius: parsed.corner_radius ?? screen.corner_radius,
        default_state: parsed.default_state ?? screen.default_state,
        actions: parsed.actions ?? screen.actions,
        body: Array.isArray(parsed.body) ? parsed.body : screen.body,
      })
    } catch (err) {
      setJsonError(err instanceof Error ? err.message : 'Invalid JSON')
    }
  }

  const actionKeys = Object.keys(screen.actions)

  return (
    <div className="flex w-[360px] shrink-0 flex-col border-l border-zinc-800/80 bg-zinc-950">
      {/* Tab Bar */}
      <div className="grid grid-cols-4 border-b border-zinc-800 p-1.5 text-[11px]">
        {(
          [
            { id: 'node', label: 'Node', icon: Sliders },
            { id: 'screen', label: 'Screen', icon: Settings },
            { id: 'logic', label: 'Logic', icon: Zap },
            { id: 'json', label: 'JSON', icon: Code2 },
          ] as const
        ).map((t) => {
          const Icon = t.icon
          return (
            <button
              key={t.id}
              onClick={() => onTabChange(t.id)}
              className={`flex items-center justify-center gap-1 rounded-lg py-1.5 font-medium transition ${
                tab === t.id
                  ? 'bg-violet-500/20 text-violet-300'
                  : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              {t.label}
            </button>
          )
        })}
      </div>

      <div className="flex-1 overflow-auto p-4">
        {tab === 'node' &&
          (selectedNode ? (
            <div className="space-y-3.5 text-xs">
              <div className="flex items-center justify-between rounded-lg border border-zinc-800 bg-zinc-900/60 px-3 py-2">
                <span className="font-mono font-semibold text-red-300">&lt;{selectedNode.type}&gt;</span>
                <span className="text-[10px] text-zinc-500">Selected Component</span>
              </div>

              {/* Common text / title / subtitle / label fields */}
              {['app_header', 'accordion', 'list_tile', 'empty_state'].includes(selectedNode.type) && (
                <div>
                  <label className="mb-1 block text-[11px] text-zinc-400">Title</label>
                  <input
                    value={selectedNode.title ?? ''}
                    onChange={(e) => onUpdateNode({ title: e.target.value })}
                    className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none focus:border-violet-500"
                  />
                </div>
              )}

              {['app_header', 'accordion', 'list_tile'].includes(selectedNode.type) && (
                <div>
                  <label className="mb-1 block text-[11px] text-zinc-400">Subtitle</label>
                  <input
                    value={selectedNode.subtitle ?? ''}
                    onChange={(e) => onUpdateNode({ subtitle: e.target.value })}
                    className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none focus:border-violet-500"
                  />
                </div>
              )}

              {selectedNode.type === 'empty_state' && (
                <div>
                  <label className="mb-1 block text-[11px] text-zinc-400">Description</label>
                  <input
                    value={selectedNode.description ?? ''}
                    onChange={(e) => onUpdateNode({ description: e.target.value })}
                    className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none focus:border-violet-500"
                  />
                </div>
              )}

              {selectedNode.type === 'text' && (
                <>
                  <div>
                    <label className="mb-1 block text-[11px] text-zinc-400">Text (supports {'{state.*}'})</label>
                    <textarea
                      rows={2}
                      value={selectedNode.text ?? ''}
                      onChange={(e) => onUpdateNode({ text: e.target.value })}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none focus:border-violet-500"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="mb-1 block text-[11px] text-zinc-400">Font Size</label>
                      <input
                        type="number"
                        value={selectedNode.size ?? 11}
                        onChange={(e) => onUpdateNode({ size: Number(e.target.value) || 11 })}
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none focus:border-violet-500"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-[11px] text-zinc-400">Weight</label>
                      <select
                        value={selectedNode.weight ?? 'normal'}
                        onChange={(e) =>
                          onUpdateNode({ weight: e.target.value as 'normal' | 'medium' | 'bold' })
                        }
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 outline-none focus:border-violet-500"
                      >
                        <option value="normal">Normal</option>
                        <option value="medium">Medium</option>
                        <option value="bold">Bold</option>
                      </select>
                    </div>
                  </div>
                </>
              )}

              {[
                'button',
                'pill',
                'switch',
                'checkbox',
                'slider',
                'number_stepper',
                'badge',
                'stat_bar',
                'empty_state',
              ].includes(selectedNode.type) && (
                <div>
                  <label className="mb-1 block text-[11px] text-zinc-400">Label</label>
                  <input
                    value={selectedNode.label ?? ''}
                    onChange={(e) => onUpdateNode({ label: e.target.value })}
                    className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none focus:border-violet-500"
                  />
                </div>
              )}

              {['app_header', 'accordion', 'list_tile', 'button', 'pill', 'badge', 'icon', 'empty_state'].includes(
                selectedNode.type
              ) && (
                <div>
                  <label className="mb-1 block text-[11px] text-zinc-400">Vector Icon</label>
                  <select
                    value={selectedNode.icon ?? ''}
                    onChange={(e) => onUpdateNode({ icon: e.target.value || undefined })}
                    className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 font-mono text-[11px] outline-none focus:border-violet-500"
                  >
                    <option value="">None</option>
                    {BUILTIN_VECTOR_ICONS.map((ic) => (
                      <option key={ic} value={ic}>
                        {ic}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {['button', 'badge'].includes(selectedNode.type) && (
                <div>
                  <label className="mb-1 block text-[11px] text-zinc-400">Variant</label>
                  <select
                    value={selectedNode.variant ?? 'primary'}
                    onChange={(e) => onUpdateNode({ variant: e.target.value })}
                    className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 outline-none focus:border-violet-500"
                  >
                    <option value="primary">Primary</option>
                    <option value="secondary">Secondary</option>
                    <option value="success">Success</option>
                    <option value="gold">Gold</option>
                    <option value="danger">Danger</option>
                  </select>
                </div>
              )}

              {[
                'tabs',
                'switch',
                'checkbox',
                'slider',
                'number_stepper',
                'segmented_control',
                'text_input',
                'accordion',
              ].includes(selectedNode.type) && (
                <div>
                  <label className="mb-1 block text-[11px] text-zinc-400">State Key</label>
                  <input
                    value={selectedNode.state_key ?? ''}
                    onChange={(e) => onUpdateNode({ state_key: e.target.value })}
                    placeholder="e.g. active_tab"
                    className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono outline-none focus:border-violet-500"
                  />
                </div>
              )}

              {['slider', 'number_stepper'].includes(selectedNode.type) && (
                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label className="mb-1 block text-[11px] text-zinc-400">Min</label>
                    <input
                      type="number"
                      value={selectedNode.min ?? 0}
                      onChange={(e) => onUpdateNode({ min: Number(e.target.value) })}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 outline-none"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-[11px] text-zinc-400">Max</label>
                    <input
                      type="number"
                      value={selectedNode.max ?? 100}
                      onChange={(e) => onUpdateNode({ max: Number(e.target.value) })}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 outline-none"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-[11px] text-zinc-400">Step</label>
                    <input
                      type="number"
                      value={selectedNode.step ?? 1}
                      onChange={(e) => onUpdateNode({ step: Number(e.target.value) || 1 })}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 outline-none"
                    />
                  </div>
                </div>
              )}

              {selectedNode.type === 'stat_bar' && (
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="mb-1 block text-[11px] text-zinc-400">Value</label>
                    <input
                      value={selectedNode.value ?? '50'}
                      onChange={(e) => onUpdateNode({ value: e.target.value })}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 font-mono outline-none"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-[11px] text-zinc-400">Max Value</label>
                    <input
                      type="number"
                      value={selectedNode.max_value ?? 100}
                      onChange={(e) => onUpdateNode({ max_value: Number(e.target.value) || 100 })}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 outline-none"
                    />
                  </div>
                  <div className="col-span-2">
                    <label className="mb-1 block text-[11px] text-zinc-400">Value Text</label>
                    <input
                      value={selectedNode.value_text ?? ''}
                      onChange={(e) => onUpdateNode({ value_text: e.target.value })}
                      placeholder="e.g. 72%"
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none"
                    />
                  </div>
                </div>
              )}

              {selectedNode.type === 'pokemon_view' && (
                <div>
                  <label className="mb-1 block text-[11px] text-zinc-400">Species ID</label>
                  <input
                    value={selectedNode.species ?? ''}
                    onChange={(e) => onUpdateNode({ species: e.target.value })}
                    placeholder="cobblemon:pikachu"
                    className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono outline-none"
                  />
                </div>
              )}

              {selectedNode.type === 'item_icon' && (
                <div>
                  <label className="mb-1 block text-[11px] text-zinc-400">Item ID</label>
                  <input
                    value={selectedNode.item ?? ''}
                    onChange={(e) => onUpdateNode({ item: e.target.value })}
                    placeholder="cobblemon:poke_ball"
                    className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono outline-none"
                  />
                </div>
              )}

              {['column', 'row', 'card', 'grid', 'flow_row', 'scroll'].includes(selectedNode.type) && (
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="mb-1 block text-[11px] text-zinc-400">Gap (dp)</label>
                    <input
                      type="number"
                      value={selectedNode.gap ?? 6}
                      onChange={(e) => onUpdateNode({ gap: Number(e.target.value) || 0 })}
                      className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 outline-none"
                    />
                  </div>
                  {selectedNode.type === 'grid' && (
                    <div>
                      <label className="mb-1 block text-[11px] text-zinc-400">Columns</label>
                      <input
                        type="number"
                        min={1}
                        max={6}
                        value={selectedNode.columns ?? 2}
                        onChange={(e) => onUpdateNode({ columns: Number(e.target.value) || 2 })}
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 outline-none"
                      />
                    </div>
                  )}
                </div>
              )}

              {['button', 'pill', 'list_tile', 'empty_state'].includes(selectedNode.type) && (
                <div>
                  <label className="mb-1 block text-[11px] text-zinc-400">
                    on_click (action key or toast:Message)
                  </label>
                  <input
                    value={selectedNode.on_click ?? ''}
                    onChange={(e) => onUpdateNode({ on_click: e.target.value || undefined })}
                    list="studio-action-keys"
                    placeholder="Optional action ID"
                    className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono outline-none focus:border-violet-500"
                  />
                  <datalist id="studio-action-keys">
                    {actionKeys.map((k) => (
                      <option key={k} value={k} />
                    ))}
                  </datalist>
                </div>
              )}

              {/* Universal visible_if */}
              <div className="border-t border-zinc-800 pt-3">
                <label className="mb-1 block text-[11px] text-zinc-400">
                  Conditional Visibility (visible_if)
                </label>
                <input
                  value={selectedNode.visible_if ?? ''}
                  onChange={(e) => onUpdateNode({ visible_if: e.target.value || undefined })}
                  placeholder="e.g. state.active_tab == 'overview'"
                  className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono text-[11px] outline-none focus:border-violet-500"
                />
              </div>
            </div>
          ) : (
            <div className="py-10 text-center text-xs text-zinc-500">
              Select a component in the Tree or switch preview to &quot;Click to Select&quot;.
            </div>
          ))}

        {tab === 'screen' && (
          <div className="space-y-3.5 text-xs">
            <div>
              <label className="mb-1 block text-[11px] text-zinc-400">Screen Title</label>
              <input
                value={screen.title}
                onChange={(e) => onUpdateScreen({ title: e.target.value })}
                className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 outline-none focus:border-violet-500"
              />
            </div>
            <div>
              <label className="mb-1 block text-[11px] text-zinc-400">Screen ID</label>
              <input
                value={screen.id}
                onChange={(e) => onUpdateScreen({ id: slugify(e.target.value) })}
                className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono outline-none focus:border-violet-500"
              />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="mb-1 block text-[11px] text-zinc-400">Viewport</label>
                <select
                  value={screen.viewport}
                  onChange={(e) =>
                    onUpdateScreen({ viewport: e.target.value as StudioScreen['viewport'] })
                  }
                  className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 outline-none"
                >
                  <option value="compact">Compact (131×207 @ 2×)</option>
                  <option value="large">Large (211×207 @ 2×)</option>
                  <option value="free">Free Panel (no bezel)</option>
                </select>
              </div>
              <div>
                <label className="mb-1 block text-[11px] text-zinc-400">Corner Radius</label>
                <input
                  type="number"
                  value={screen.corner_radius}
                  onChange={(e) => onUpdateScreen({ corner_radius: Number(e.target.value) || 0 })}
                  className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 outline-none"
                />
              </div>
            </div>

            {screen.viewport === 'free' && (
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="mb-1 block text-[11px] text-zinc-400">Width (dp)</label>
                  <input
                    type="number"
                    value={screen.width_dp ?? 360}
                    onChange={(e) => onUpdateScreen({ width_dp: Number(e.target.value) || 360 })}
                    className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 outline-none"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-[11px] text-zinc-400">Height (dp)</label>
                  <input
                    type="number"
                    value={screen.height_dp ?? 420}
                    onChange={(e) => onUpdateScreen({ height_dp: Number(e.target.value) || 420 })}
                    className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 outline-none"
                  />
                </div>
              </div>
            )}

            <div className="space-y-2 rounded-xl border border-zinc-800 bg-zinc-900/50 p-3">
              <div className="text-[11px] font-semibold text-zinc-300">Phone Bezel Chrome</div>
              {(
                [
                  ['show_bezel_back', 'Show Back Button'],
                  ['show_bezel_battery', 'Show Battery Indicator'],
                  ['show_bezel_hd_badge', 'Show HD Badge'],
                ] as const
              ).map(([field, label]) => (
                <label key={field} className="flex cursor-pointer items-center justify-between text-xs">
                  <span className="text-zinc-300">{label}</span>
                  <input
                    type="checkbox"
                    checked={screen[field]}
                    onChange={(e) => onUpdateScreen({ [field]: e.target.checked })}
                    className="accent-violet-400"
                  />
                </label>
              ))}
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="mb-1 block text-[11px] text-zinc-400">Require Mod</label>
                <input
                  value={screen.require_mod ?? ''}
                  onChange={(e) => onUpdateScreen({ require_mod: e.target.value || undefined })}
                  placeholder="optional"
                  className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 font-mono outline-none"
                />
              </div>
              <div>
                <label className="mb-1 block text-[11px] text-zinc-400">Require Upgrade</label>
                <input
                  value={screen.require_upgrade ?? ''}
                  onChange={(e) => onUpdateScreen({ require_upgrade: e.target.value || undefined })}
                  placeholder="upgrade_key"
                  className="w-full rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1.5 font-mono outline-none"
                />
              </div>
            </div>
          </div>
        )}

        {tab === 'logic' && (
          <div className="space-y-5 text-xs">
            {/* Default State */}
            <div className="space-y-2">
              <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                Default State Variables
              </div>
              <div className="space-y-1">
                {Object.entries(screen.default_state).map(([k, v]) => (
                  <div
                    key={k}
                    className="flex items-center justify-between rounded-lg border border-zinc-800 bg-zinc-900/60 px-2.5 py-1.5 font-mono text-[11px]"
                  >
                    <span>
                      <span className="text-red-300">{k}</span> ={' '}
                      <span className="text-zinc-200">{String(v)}</span>
                    </span>
                    <button
                      onClick={() => {
                        const next = { ...screen.default_state }
                        delete next[k]
                        onUpdateScreen({ default_state: next })
                      }}
                      className="text-zinc-500 hover:text-red-400"
                    >
                      <Trash2 className="h-3 w-3" />
                    </button>
                  </div>
                ))}
              </div>
              <div className="flex gap-1.5">
                <input
                  value={newStateKey}
                  onChange={(e) => setNewStateKey(e.target.value)}
                  placeholder="key"
                  className="w-24 rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1 font-mono text-[11px] outline-none"
                />
                <input
                  value={newStateVal}
                  onChange={(e) => setNewStateVal(e.target.value)}
                  placeholder="value (true / 5 / text)"
                  className="flex-1 rounded-lg border border-zinc-800 bg-zinc-900 px-2 py-1 font-mono text-[11px] outline-none"
                />
                <button
                  onClick={addStateVar}
                  className="rounded-lg bg-zinc-800 px-2 py-1 text-zinc-200 hover:bg-zinc-700"
                >
                  <Plus className="h-3.5 w-3.5" />
                </button>
              </div>
            </div>

            {/* Actions & Effects */}
            <div className="space-y-2.5 border-t border-zinc-800 pt-4">
              <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                Screen Actions & Effects
              </div>
              <div className="flex gap-1.5">
                <input
                  value={newActionKey}
                  onChange={(e) => setNewActionKey(e.target.value)}
                  placeholder="action_id (e.g. claim_reward)"
                  className="flex-1 rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 font-mono text-[11px] outline-none"
                />
                <button
                  onClick={addAction}
                  className="rounded-lg bg-violet-500/20 px-2.5 py-1.5 font-medium text-violet-300 hover:bg-violet-500/30"
                >
                  + Action
                </button>
              </div>

              {Object.entries(screen.actions).map(([actKey, act]) => (
                <div key={actKey} className="space-y-2 rounded-xl border border-zinc-800 bg-zinc-900/50 p-3">
                  <div className="flex items-center justify-between">
                    <span className="font-mono font-semibold text-violet-300">{actKey}</span>
                    <button
                      onClick={() => {
                        const next = { ...screen.actions }
                        delete next[actKey]
                        onUpdateScreen({ actions: next })
                      }}
                      className="text-zinc-500 hover:text-red-400"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>

                  <div className="space-y-1.5">
                    {act.effects.map((eff, idx) => (
                      <div key={idx} className="space-y-1 rounded-lg border border-zinc-800 bg-zinc-950 p-2">
                        <div className="flex items-center justify-between">
                          <span className="font-mono text-[10px] font-semibold text-red-300">
                            {eff.type}
                          </span>
                          <button
                            onClick={() => removeEffect(actKey, idx)}
                            className="text-zinc-500 hover:text-red-400"
                          >
                            <Trash2 className="h-3 w-3" />
                          </button>
                        </div>
                        {eff.type === 'toast' && (
                          <input
                            value={eff.text ?? ''}
                            onChange={(e) => updateEffect(actKey, idx, { text: e.target.value })}
                            placeholder="Toast message"
                            className="w-full rounded border border-zinc-800 bg-zinc-900 px-2 py-1 text-[11px] outline-none"
                          />
                        )}
                        {eff.type === 'function' && (
                          <select
                            value={eff.function ?? 'heal_party'}
                            onChange={(e) => updateEffect(actKey, idx, { function: e.target.value })}
                            className="w-full rounded border border-zinc-800 bg-zinc-900 px-2 py-1 font-mono text-[11px] outline-none"
                          >
                            {BUILTIN_ACTION_FUNCTIONS.map((fn) => (
                              <option key={fn.id} value={fn.id}>
                                {fn.id}
                              </option>
                            ))}
                          </select>
                        )}
                        {eff.type === 'command' && (
                          <input
                            value={eff.command ?? ''}
                            onChange={(e) => updateEffect(actKey, idx, { command: e.target.value })}
                            placeholder="say Hello!"
                            className="w-full rounded border border-zinc-800 bg-zinc-900 px-2 py-1 font-mono text-[11px] outline-none"
                          />
                        )}
                        {(eff.type === 'set_state' || eff.type === 'toggle_state') && (
                          <div className="flex gap-1">
                            <input
                              value={eff.key ?? ''}
                              onChange={(e) => updateEffect(actKey, idx, { key: e.target.value })}
                              placeholder="state key"
                              className="w-1/2 rounded border border-zinc-800 bg-zinc-900 px-2 py-1 font-mono text-[11px] outline-none"
                            />
                            {eff.type === 'set_state' && (
                              <input
                                value={eff.value ?? ''}
                                onChange={(e) => updateEffect(actKey, idx, { value: e.target.value })}
                                placeholder="value"
                                className="w-1/2 rounded border border-zinc-800 bg-zinc-900 px-2 py-1 font-mono text-[11px] outline-none"
                              />
                            )}
                          </div>
                        )}
                        {eff.type === 'give_item' && (
                          <div className="flex gap-1">
                            <input
                              value={eff.item ?? ''}
                              onChange={(e) => updateEffect(actKey, idx, { item: e.target.value })}
                              placeholder="minecraft:diamond"
                              className="flex-1 rounded border border-zinc-800 bg-zinc-900 px-2 py-1 font-mono text-[11px] outline-none"
                            />
                            <input
                              type="number"
                              value={eff.count ?? 1}
                              onChange={(e) =>
                                updateEffect(actKey, idx, { count: Number(e.target.value) || 1 })
                              }
                              className="w-14 rounded border border-zinc-800 bg-zinc-900 px-2 py-1 text-[11px] outline-none"
                            />
                          </div>
                        )}
                      </div>
                    ))}
                  </div>

                  <div className="flex flex-wrap gap-1 pt-1">
                    {(['toast', 'set_state', 'toggle_state', 'function', 'command', 'give_item'] as const).map(
                      (effType) => (
                        <button
                          key={effType}
                          onClick={() => addEffect(actKey, effType)}
                          className="rounded border border-zinc-800 bg-zinc-950 px-1.5 py-0.5 font-mono text-[10px] text-zinc-400 hover:border-violet-500/50 hover:text-violet-300"
                        >
                          +{effType}
                        </button>
                      )
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {tab === 'json' && (
          <div className="flex h-full flex-col gap-2">
            <div className="flex items-center justify-between">
              <span className="truncate font-mono text-[10px] text-zinc-400">
                data/{namespace}/smartphone_screens/{screen.id}.json
              </span>
              <div className="flex gap-1">
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(serializedJson)
                    onToast('Screen JSON copied!', 'success')
                  }}
                  className="rounded border border-zinc-800 bg-zinc-900 p-1 text-zinc-300 hover:bg-zinc-800"
                >
                  <Copy className="h-3.5 w-3.5" />
                </button>
                <button
                  onClick={() =>
                    triggerDownload(
                      new Blob([serializedJson], { type: 'application/json' }),
                      `${slugify(screen.id)}.json`
                    )
                  }
                  className="rounded bg-violet-500 p-1 text-zinc-950 hover:bg-violet-400"
                >
                  <Download className="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
            <textarea
              key={`${screen.uid}_${screen.updatedAt}`}
              defaultValue={serializedJson}
              onBlur={(e) => handleJsonEdit(e.target.value)}
              spellCheck={false}
              className="flex-1 resize-none rounded-xl border border-zinc-800 bg-zinc-900/60 p-3 font-mono text-[11px] leading-relaxed text-red-200 outline-none focus:border-violet-500"
            />
            {jsonError && (
              <div className="rounded-lg border border-red-500/30 bg-red-950/50 px-2.5 py-1.5 font-mono text-[10px] text-red-300">
                {jsonError}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
