# Datapack Action API

Add custom action buttons to the Cobblemon Smartphone using JSON files — no coding required. Actions can execute commands, call built-in smartphone functions, have cooldowns, require specific mods, and be locked behind smartphone upgrades.

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

Every action must define at least one entry in `commands` or `functions`.

### Optional Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `commands` | String[] | `[]` | Commands executed as the player when the button is clicked. |
| `functions` | String[] | `[]` | Built-in smartphone functions executed when the button is clicked. See [Built-in Functions](#built-in-functions). |
| `order` | Integer | `0` | Sort order. Lower numbers appear first. |
| `cooldown_seconds` | Integer | `0` | Cooldown in seconds between uses. `0` = no cooldown. |
| `require_mod` | String | `null` | Mod ID required for this action. If the mod is not loaded, the action is completely hidden. |
| `require_upgrade` | String | `null` | Upgrade NBT key required on the smartphone. The action is hidden unless the player's smartphone has this upgrade installed, unless the action ID is listed in `ignoreUpgrades` in the mod config. See [Upgrade Support](#upgrade-support). |

## Built-in Functions

The `functions` field exposes features that cannot normally be implemented with datapack commands.

| Function | Behavior |
|---|---|
| `open_pc` | Opens the player's Cobblemon PC. |
| `heal_party` | Heals the player's Cobblemon party. |
| `open_pokedex` | Opens the Cobblemon Pokédex. |
| `open_ender_chest` | Opens the player's Ender Chest. |
| `open_pokenav` | Opens CobbleNav's PokéNav when available. |
| `open_waystone` | Opens the Waystones destination interface when available. |
| `open_cobbledollars_shop` | Opens the CobbleDollars shop when available. |

Example — custom PC button without commands:

```json
{
  "id": "mypack:portable_pc",
  "texture": "mypack:textures/gui/buttons/pc.png",
  "hover_texture": "mypack:textures/gui/buttons/pc_hover.png",
  "functions": ["open_pc"],
  "cooldown_seconds": 10
}
```

Functions and commands can be combined. Functions execute first, followed by commands in their listed order:

```json
{
  "id": "mypack:healing_service",
  "texture": "mypack:textures/gui/buttons/heal.png",
  "hover_texture": "mypack:textures/gui/buttons/heal_hover.png",
  "functions": ["heal_party"],
  "commands": [
    "playsound minecraft:block.beacon.activate player @s",
    "tellraw @s {\"text\":\"Your party was sent to the healing service!\",\"color\":\"green\"}"
  ]
}
```

Built-in functions use the same server-side checks, feature toggles, battle restrictions, mod requirements, and upgrade requirements as the original smartphone buttons. They do not consume or check the official button cooldown; datapack actions use only their own `cooldown_seconds`.

## Upgrade Support

The `require_upgrade` field locks an action behind a smartphone upgrade. When set, the button only appears if the player's smartphone has the corresponding upgrade NBT tag.

Server owners can bypass upgrade requirements per action by adding action IDs to `ignoreUpgrades` in `config/cobblemon_smartphone.json`. Listed datapack actions with `require_upgrade` appear and can be clicked without the matching smartphone upgrade. Other requirements, such as `require_mod`, still apply.

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

**Result**: By default, the player must use `cobblemon:upgrade` + their smartphone + an ender pearl in a smithing table. After upgrading, the portal button appears on their smartphone. If `"mypack:portal"` is listed in `ignoreUpgrades`, the portal button appears without this smithing upgrade.

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

- **Registered functions only** — `functions` can only use the identifiers listed above. Unknown identifiers are ignored and logged by the server.
- **Other custom GUIs** — Interfaces not exposed as built-in functions still require the [Mod API](mod-api.md).
- **Permission level 2** — Commands run at permission level 2 (bypasses spawn protection, etc.). Use command modifiers like `execute as @s` when needed.

## Troubleshooting

| Problem | Solution |
|---|---|
| Button not appearing | Check `require_mod` — is the mod installed? Check `require_upgrade` — is the smartphone upgraded, or is this action ID listed in `ignoreUpgrades`? Verify the JSON file is in `smartphone_actions/` folder. Run `/reload`. |
| Command not running | Commands execute as the player. Ensure the player has permission for the command. Check server logs for errors. |
| Function not running | Verify the function identifier against the table above and check whether its native feature, mod, cooldown, battle, or upgrade requirements allow it. |
| Texture not showing | Verify the texture path matches `<namespace>:textures/gui/buttons/<file>.png`. Check the file exists in your assets. |
| Cooldown not working | `cooldown_seconds` must be a positive integer. Zero = no cooldown. |
