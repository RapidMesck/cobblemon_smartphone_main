# Datapack Action API

Add custom action buttons to the Cobblemon Smartphone using JSON files — no coding required. Actions can execute commands, have cooldowns, require specific mods, and be locked behind smartphone upgrades.

## Quick Start

Create a JSON file in any datapack or resource pack under:

```
data/<namespace>/smartphone_actions/<name>.json
```

**Minimal example** — adds a button that runs a command:

```json
{
  "id": "mypack:hello",
  "texture": "mypack:textures/gui/buttons/hello.png",
  "hover_texture": "mypack:textures/gui/buttons/hello_hover.png",
  "commands": ["say Hello from smartphone!"]
}
```

Restart the server or run `/reload` — the button appears automatically.

## JSON Format Reference

### Required Fields

| Field | Type | Description |
|---|---|---|
| `id` | String | Unique identifier. **Must** follow `<namespace>:<path>` format (e.g., `mypack:my_action`). |
| `texture` | String | Texture for the button. Format: `<namespace>:textures/gui/buttons/<file>.png`. Size: 36×36 pixels. |
| `hover_texture` | String | Texture shown on mouse hover. Same format as `texture`. |
| `commands` | String[] | Commands executed when the button is clicked. At least one command is required. Executed **as the player** at permission level 2 (can use `/op`-gated commands if the player has permission). |

### Optional Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `order` | Integer | `0` | Sort order. Lower numbers appear first. |
| `cooldown_seconds` | Integer | `0` | Cooldown in seconds between uses. `0` = no cooldown. |
| `require_mod` | String | `null` | Mod ID required for this action. If the mod is not loaded, the action is completely hidden. |
| `require_upgrade` | String | `null` | Upgrade NBT key required on the smartphone. The action is hidden unless the player's smartphone has this upgrade installed. See [Upgrade Support](#upgrade-support). |

## Upgrade Support

The `require_upgrade` field locks an action behind a smartphone upgrade. When set, the button only appears if the player's smartphone has the corresponding upgrade NBT tag.

### How It Works

1. A smithing recipe adds an NBT tag to the smartphone (see [Smithing Recipe Format](README.md#upgrade-smithing-recipe-format))
2. The action JSON specifies `"require_upgrade": "upgrade_<name>"`
3. The game checks the player's smartphone for the upgrade before showing the button

### Example: Action Locked Behind an Upgrade

**Step 1** — Create the action JSON:

```json
{
  "id": "mypack:portal",
  "texture": "mypack:textures/gui/buttons/portal.png",
  "hover_texture": "mypack:textures/gui/buttons/portal_hover.png",
  "commands": ["execute in minecraft:the_nether run tp @s 0 80 0"],
  "require_upgrade": "upgrade_portal",
  "cooldown_seconds": 60
}
```

**Step 2** — Create the smithing recipe (`data/mypack/recipe/portal_upgrade.json`):

```json
{
  "type": "minecraft:smithing_transform",
  "fabric:load_conditions": [
    { "condition": "fabric:all_mods_loaded", "values": ["mypack"] }
  ],
  "neoforge:conditions": [
    { "type": "neoforge:mod_loaded", "modid": "mypack" }
  ],
  "template": { "item": "cobblemon:upgrade" },
  "base": { "tag": "cobblemon_smartphone:smartphones" },
  "addition": { "item": "minecraft:ender_pearl" },
  "result": {
    "id": "cobblemon_smartphone:red_smartphone",
    "count": 1,
    "components": {
      "minecraft:custom_data": {
        "cobblemon_smartphone:upgrades": {
          "upgrade_portal": true
        }
      }
    }
  }
}
```

**Result**: The player must use `cobblemon:upgrade` + their smartphone + an ender pearl in a smithing table. After upgrading, the portal button appears on their smartphone.

### NBT Structure

Upgrades are stored in the smartphone's `custom_data` component:

```
minecraft:custom_data
  └── cobblemon_smartphone:upgrades
        ├── upgrade_portal: true
        ├── upgrade_pokenav: true
        └── upgrade_waystone: true
```

The `require_upgrade` value in your JSON must match the boolean key inside `cobblemon_smartphone:upgrades`.

## Complete Example

An action that:
- Requires the `thermodynamics` mod
- Requires a smartphone upgrade
- Has a cooldown
- Runs multiple commands with command context

```json
{
  "id": "thermo:heat_scanner",
  "texture": "thermo:textures/gui/buttons/heat_scanner.png",
  "hover_texture": "thermo:textures/gui/buttons/heat_scanner_hover.png",
  "commands": [
    "tellraw @s {\"text\":\"Scanning temperature...\",\"color\":\"gold\"}",
    "execute as @s at @s run thermo:scan_temp"
  ],
  "order": 50,
  "cooldown_seconds": 30,
  "require_mod": "thermodynamics",
  "require_upgrade": "upgrade_heat_scanner"
}
```

## Texture Guidelines

- Button size: **36×36 pixels**
- Format: PNG
- Location: `assets/<namespace>/textures/gui/buttons/`
- The hover texture should be a highlighted/glowing version of the base texture
- Use the same namespace as your action `id` for consistency

## Limitations

- **Commands only** — Datapack actions execute Minecraft commands. For custom GUI, network packets, or complex logic, use the [Mod API](mod-api.md).
- **No item simulation** — Datapack actions cannot simulate using items from other mods. If you need to open a mod's GUI, use the [Mod API](mod-api.md) with `SimulatedItemUse`.
- **Permission level 2** — Commands run at permission level 2 (bypasses spawn protection, etc.). Use command modifiers like `execute as @s` when needed.

## Troubleshooting

| Problem | Solution |
|---|---|
| Button not appearing | Check `require_mod` — is the mod installed? Check `require_upgrade` — is the smartphone upgraded? Verify the JSON file is in `smartphone_actions/` folder. Run `/reload`. |
| Command not running | Commands execute as the player. Ensure the player has permission for the command. Check server logs for errors. |
| Texture not showing | Verify the texture path matches `<namespace>:textures/gui/buttons/<file>.png`. Check the file exists in your assets. |
| Cooldown not working | `cooldown_seconds` must be a positive integer. Zero = no cooldown. |
