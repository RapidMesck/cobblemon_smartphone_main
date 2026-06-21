# Cobblemon Smartphone

Welcome to the Cobblemon Smartphone developer documentation. This mod provides a smartphone item for Cobblemon that can be extended with custom actions through two APIs:

- **Datapack API** — Add actions via JSON files in datapacks or resource packs. No coding required.
- **Mod API** — Add actions programmatically from your own mod. Full control with Kotlin/Java.

## Quick Start

| I want to... | Use |
|---|---|
| Add a button that runs commands | [Datapack API](datapack-api.md) |
| Add a button that opens a GUI / custom logic | [Mod API](mod-api.md) |
| Require a smartphone upgrade to unlock an action | Both APIs support this |
| Configure cooldowns, feature toggles, or upgrade checks | [Mod Configuration](#mod-configuration) |

## Supported Versions

- Minecraft **1.21.1**
- Cobblemon **1.6+**
- Loaders: **Fabric** (0.16+), **NeoForge** (21.1+)

## API Overview

### Datapack API

Place JSON files in `data/<namespace>/smartphone_actions/` inside any datapack. Each file defines one action button.

```json
{
  "id": "mypack:my_action",
  "texture": "mypack:textures/gui/buttons/my_button.png",
  "hover_texture": "mypack:textures/gui/buttons/my_button_hover.png",
  "commands": ["say Hello from smartphone!"],
  "order": 100,
  "cooldown_seconds": 10,
  "require_mod": "optional_mod_id",
  "require_upgrade": "upgrade_my_feature"
}
```

→ [Full Datapack API Reference](datapack-api.md)

### Mod API

Implement the `SmartphoneAction` interface in your Kotlin/Java code and register it.

```kotlin
object MyAction : SmartphoneAction {
    override val id = "mymod:my_action"
    override val texture = ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/buttons/my_button.png")
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/buttons/my_button_hover.png")

    override fun onClick() {
        // Your logic here
    }

    override fun isEnabled(): Boolean {
        // Return false to hide the button
        return true
    }
}

// In your mod init:
SmartphoneActionRegistry.register(MyAction)
```

→ [Full Mod API Reference](mod-api.md)

## Upgrade System

Both APIs support the **smartphone upgrade** mechanic. Upgrades allow actions to be locked behind a smithing table recipe that permanently adds an NBT tag to the smartphone.

| Feature | Datapack API | Mod API |
|---|---|---|
| Require upgrade for action | `"require_upgrade": "upgrade_<name>"` | Use `SmartphoneHelper.satisfiesUpgradeRequirement()` in `isEnabled()` |
| Define upgrade/item | Via smithing recipe JSON | Register `SmartphoneUpgrade` |
| Simulate item use | ❌ (use commands) | `SimulatedItemUse.simulate()` |

If `ignoreUpgrades` is enabled in the mod config, upgrade-locked actions are shown and can be used without the matching smartphone upgrade.

## Mod Configuration

Cobblemon Smartphone writes its config to:

```
config/cobblemon_smartphone.json
```

The file is created automatically on first load and rewritten with any missing default fields.

```json
{
  "ignoreUpgrades": false,
  "cooldowns": {
    "healButton": 60,
    "pcButton": 5,
    "cloudButton": 5,
    "waystoneButton": 5,
    "pokedexButton": 1
  },
  "features": {
    "enableHeal": true,
    "enablePC": true,
    "enableCloud": true,
    "enablePokenav": true,
    "enableCobbleDollars": true,
    "enableWaystone": true,
    "enablePokedex": true,
    "enableScanner": true
  }
}
```

| Field | Type | Default | Description |
|---|---|---|---|
| `ignoreUpgrades` | Boolean | `false` | When `true`, upgrade requirements are ignored. Built-in and datapack actions locked by upgrades appear without requiring smithing upgrades. |
| `cooldowns` | Object | See example | Cooldown values, in seconds, for built-in actions. |
| `features` | Object | See example | Toggles for built-in smartphone actions. Disabled features remain hidden even when `ignoreUpgrades` is enabled. |

## Upgrade Smithing Recipe Format

Upgrade recipes use vanilla `minecraft:smithing_transform` with a smartphone tag as the base:

```json
{
  "type": "minecraft:smithing_transform",
  "fabric:load_conditions": [{ "condition": "fabric:all_mods_loaded", "values": ["mymod"] }],
  "neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "mymod" }],
  "template": { "item": "cobblemon:upgrade" },
  "base": { "tag": "cobblemon_smartphone:smartphones" },
  "addition": { "item": "mymod:my_item" },
  "result": {
    "id": "cobblemon_smartphone:red_smartphone",
    "count": 1,
    "components": {
      "minecraft:custom_data": {
        "cobblemon_smartphone:upgrades": {
          "upgrade_my_feature": true
        }
      }
    }
  }
}
```

The **Mixin on `SmithingTransformRecipe`** automatically preserves the smartphone color when crafting — the `red_smartphone` in the result is just a placeholder for JEI/EMI/REI display.

### Recipe Conditions

Use platform-specific conditions to hide recipes when required mods are absent:

- **Fabric**: `fabric:load_conditions` with `fabric:all_mods_loaded`
- **NeoForge**: `neoforge:conditions` with `neoforge:mod_loaded`

Both can coexist in the same JSON — each loader ignores the other's field.

## Links

- [Cobblemon Mod](https://modrinth.com/mod/cobblemon)
- [Architectury API](https://docs.architectury.dev/)
