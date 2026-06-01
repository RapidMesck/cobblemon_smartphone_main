# Cobblemon Smartphone

A Cobblemon addon that adds smartphone items with an extensible action system. Built with **Architectury** for Fabric and NeoForge (Minecraft 1.21.1).

## Project Structure

```
├── common/           # Shared code (Kotlin + Java mixins)
│   └── src/main/kotlin/com/nbp/cobblemon_smartphone/
│       ├── actions/          # Built-in action implementations
│       ├── api/              # Public API (SmartphoneAction, DatapackAction, registry)
│       ├── client/           # Client-side code (GUI, keybinds, scanner)
│       ├── config/           # JSON config loader
│       ├── item/             # SmartphoneItem, SmartphoneColor enum (16 variants)
│       ├── network/          # Packets + handlers (packet/ and handler/ subpackages)
│       ├── registry/         # Item registration via RegistryProvider
│       ├── upgrade/          # Upgrade system (registry, helpers, SimulatedItemUse)
│       └── util/             # Utilities (SmartphoneHelper, Utils)
├── fabric/           # Fabric loader implementation
│   └── src/main/kotlin/com/nbp/cobblemon_smartphone/
│       ├── compat/           # Trinkets integration
│       ├── client/           # Fabric client init + keybind handler
│       ├── CobblemonSmartphoneFabric.kt          # ModInitializer + Implementation
│       └── DatapackActionReloadListenerWrapper.kt
├── neoforge/         # NeoForge loader implementation
│   └── src/main/kotlin/com/nbp/neoforge/
│       ├── compat/           # Curios integration
│       ├── CobblemonSmartphoneNeoForge.kt        # @Mod class + Implementation
│       └── ...
├── wiki/             # User-facing API docs (GitBook synced)
└── build.gradle      # Root build (Architectury Loom)
```

### Architecture Decision: Common + Platform Modules

All logic lives in `common/`. Platform modules (`fabric/`, `neoforge/`) contain only loader-specific code:

| Concern | Location |
|---|---|
| Actions, upgrades, recipes, network packets | `common/` |
| Item registration | `common/registry/` via `RegistryProvider` |
| Platform event wiring | `fabric/` / `neoforge/` |
| Mod compat (Trinkets, Curios) | `fabric/compat/` / `neoforge/compat/` |
| Mixins | `common/src/main/java/.../mixin/` |

### Build System

- **Gradle 8.11** with Architectury Loom 1.11
- **Kotlin 2.0** for common + platform code
- **Java 21** for mixins
- Targets: `fabric` (Fabric Loader 0.16+) and `neoforge` (NeoForge 21.1+)

## Core Design Patterns

### 1. Registry Pattern

All registries follow the same singleton + register pattern:

```
SmartphoneActionRegistry    → actions shown in the smartphone GUI
SmartphoneUpgradeRegistry   → known upgrade types (NBT keys → metadata)
CobblemonSmartphoneItems    → items (extends RegistryProvider)
```

Example flow for the upgrade registry:

```kotlin
// Registration (in CobblemonSmartphone.init)
SmartphoneUpgradeRegistry.register(
    SmartphoneUpgrade(id = "upgrade_pokenav", nbtKey = "upgrade_pokenav", ...)
)

// Consumption (in action isEnabled)
smartphone.hasUpgrade("upgrade_pokenav")

// Query all installed
SmartphoneUpgradeRegistry.getInstalledUpgrades(stack)
```

### 2. Cross-Platform Helper Pattern

Common code can't reference platform-specific compat managers (Trinkets vs Curios). Solution:

```
common/util/SmartphoneHelper.kt  →  getSmartphoneImpl: ((Player) -> ItemStack?)?
                                       ↓ sets
fabric/CobblemonSmartphoneFabric.kt   SmartphoneHelper.getSmartphoneImpl = { SmartphoneCompatManager.getSmartphone(it) }
neoforge/CobblemonSmartphoneNeoForge.kt   SmartphoneHelper.getSmartphoneImpl = { SmartphoneCompatManager.getSmartphone(it) }
```

All actions and handlers call `SmartphoneHelper.getSmartphone(player)` regardless of platform.

### 3. `Implementation` Interface

The `Implementation` interface defines platform-specific setup steps that `CobblemonSmartphone.init()` delegates to:

```kotlin
interface Implementation {
    val networkManager: NetworkManager
    fun registerItems()
    fun registerCommands()
    fun registerReloadListeners()
}
```

Fabric and NeoForge provide their own implementations, wiring events appropriately (e.g., Fabric uses `ResourceManagerHelper`, NeoForge uses `AddReloadListenerEvent`).

### 4. `SmartphoneAction` + Registry

The core extensibility point. Actions implement a simple interface:

```kotlin
interface SmartphoneAction {
    val id: String
    val texture: ResourceLocation
    val hoverTexture: ResourceLocation
    fun onClick()                         // Client-side
    fun isEnabled(): Boolean = true       // Client-side visibility check
}
```

`SmartphoneActionRegistry.getEnabledActions()` filters by `isEnabled()` before rendering in the GUI. This is how upgrade-locked and mod-conditional actions are hidden.

### 5. Network Packets

Packets extend `CobblemonSmartphoneNetworkPacket<T>` which wraps Cobblemon's `NetworkPacket<T>`:

```
Packet (common/network/packet/)    → sendToServer() / sendToPlayer()
Handler (common/network/handler/)  → ServerNetworkPacketHandler<T>
Registration (CobblemonSmartphoneNetwork.kt) → c2s and s2c PacketRegisterInfo lists
```

Datapack actions use a generic `ExecuteDatapackActionPacket` that carries an action ID string. Built-in actions have dedicated packet classes.

## Upgrade System Architecture

### NBT Storage

Upgrades are stored as boolean flags in `DataComponents.CUSTOM_DATA`:

```
minecraft:custom_data
  └── cobblemon_smartphone:upgrades
        ├── upgrade_pokenav: 1b
        └── upgrade_waystone: 1b
```

### Extension Functions (`SmartphoneUpgradeHelper.kt`)

```kotlin
fun ItemStack.hasUpgrade(nbtKey: String): Boolean   // Read check
fun ItemStack.addUpgrade(nbtKey: String)             // Write (smithing)
fun ItemStack.isSmartphone(): Boolean                // Type check
```

### Smithing Recipe Integration

Recipes use **vanilla `minecraft:smithing_transform`** with the `cobblemon_smartphone:smartphones` tag as the base. A **Mixin on `SmithingTransformRecipe.assemble()`** (`MixinSmithingTransformRecipe.java`) intercepts crafting:

1. Checks if `input.base()` is a `SmartphoneItem`
2. Identifies the upgrade from the addition item's registry key
3. Copies the base (preserving smartphone color) + adds upgrade NBT
4. Returns the colored result instead of the fixed recipe result

This means **one recipe JSON handles all 16 smartphone colors**.

### `SimulatedItemUse`

For addon actions that need to "use" items from optional mods without class references:

```kotlin
SimulatedItemUse.simulate(player, itemPredicate, useAction)
```

Temporarily places a synthetic `ItemStack` in the player's main hand, executes the use action, and restores the original hand in a `finally` block.

### Recipe Conditions

Recipes use dual-condition format for cross-platform conditional loading:

```json
{
  "fabric:load_conditions": [{ "condition": "fabric:all_mods_loaded", "values": ["cobblenav"] }],
  "neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "cobblenav" }]
}
```

Each loader strips its own field before the codec runs; the other field is ignored as an unknown JSON key.

## Mixins

| Mixin | Target | Purpose |
|---|---|---|
| `MixinSmithingTransformRecipe` | `SmithingTransformRecipe.assemble()` | Base-to-result copy for smartphone upgrades |
| `EntityMixin` | `Entity` | Persist player preferences across death |
| `ItemRendererMixin` | `ItemRenderer` | Custom smartphone model rendering |
| `ModelLoaderMixin` | `ModelLoader` | Register smartphone hand models |
| `MixinItemInHandRenderer` | `ItemInHandRenderer` | Smartphone in-hand rendering |
| `MixinPlayerExtensionsKt` | Cobblemon player extensions | Extend player capabilities |
| `MixinPokedexUsageContext` | Cobblemon pokedex context | Scanner integration |

All mixins are declared in `common/src/main/resources/cobblemonsmartphone.mixins.json`.

## Config System

`SmartphoneConfig.kt` loads/saves JSON from `config/cobblemon_smartphone.json`. Structure:

```kotlin
SmartphoneConfig
├── cooldowns: Cooldowns     // Per-action cooldowns in seconds
│   ├── healButton: Int
│   ├── pcButton: Int
│   ├── cloudButton: Int
│   ├── waystoneButton: Int
│   └── pokedexButton: Int
└── features: Features       // Enable/disable toggle per feature
    ├── enableHeal: Boolean
    ├── enablePC: Boolean
    ├── enableCloud: Boolean
    ├── enablePokenav: Boolean
    ├── enableCobbleDollars: Boolean
    ├── enableWaystone: Boolean
    └── enablePokedex: Boolean
```

## Datapack Action System

Datapacks can add actions via JSON files in `data/<namespace>/smartphone_actions/`. See `wiki/datapack-api.md` for the full API reference.

**Loading flow:**

1. `DatapackActionLoader` (common) implements `PreparableReloadListener`
2. Fabric: wrapped by `DatapackActionReloadListenerWrapper` for `IdentifiableResourceReloadListener`
3. NeoForge: registered directly via `AddReloadListenerEvent`
4. On reload: scans all namespaces → filters by `require_mod` → builds `DatapackAction` instances
5. Actions synced to clients via `SyncDatapackActionsPacket` on player join

## Adding a New Built-in Action

1. Create action class in `common/.../actions/` implementing `SmartphoneAction`
2. Create packet in `common/.../network/packet/` extending `CobblemonSmartphoneNetworkPacket`
3. Create handler in `common/.../network/handler/` implementing `ServerNetworkPacketHandler`
4. Register packet + handler in `CobblemonSmartphoneNetwork.kt`
5. Register action in `CobblemonSmartphone.registerDefaultActions()`
6. Add config flags in `SmartphoneConfig.kt` if feature should be toggleable
7. Add localization in `en_us.json`

## Adding a New Upgrade Type

1. Register `SmartphoneUpgrade` in `CobblemonSmartphone.registerDefaultUpgrades()`
2. Create smithing recipe JSON in `common/src/main/resources/data/cobblemon_smartphone/recipe/`
3. Add recipe conditions for optional mods
4. In the action's handler, check upgrade NBT + add `SimulatedItemUse` fallback
5. Add localization messages

If the upgrade's addition item comes from another mod, no code changes are needed in `MixinSmithingTransformRecipe` — the mixin identifies upgrades by the addition item's registry key.

## Key Dependencies

| Dependency | Version | Notes |
|---|---|---|
| Cobblemon | 1.6+ | Required. Provides sounds, network API, Pokedex integration |
| Architectury | 13+ | Multi-loader abstraction |
| Fabric API | 0.100+ | Fabric loader (Fabric target only) |
| Kotlin for Forge | 4.11+ | Kotlin runtime on NeoForge |
| Trinkets | 3.10+ | Optional (Fabric) — smartphone accessory slot |
| Curios | 9.0+ | Optional (NeoForge) — smartphone accessory slot |

## Documentation

- [Datapack API](wiki/datapack-api.md) — Add actions via JSON
- [Mod API](wiki/mod-api.md) — Add actions programmatically
- [Overview & Upgrade Recipes](wiki/README.md) — Index and smithing recipe format
