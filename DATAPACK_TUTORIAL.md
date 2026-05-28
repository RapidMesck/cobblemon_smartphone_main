# Creating Custom Actions via Datapack

This tutorial teaches how to create custom actions for the Cobblemon Smartphone using Minecraft datapacks.

## Datapack Structure

Create a datapack with the following structure:

```
my_datapack/
├── pack.mcmeta
└── data/
    └── <your_namespace>/
        └── smartphone_actions/
            └── <action_name>.json
```

### pack.mcmeta

```json
{
  "pack": {
    "pack_format": 48,
    "description": "My custom Smartphone actions"
  }
}
```

> **pack_format**: Use 48 for Minecraft 1.21.1. Check the [Minecraft wiki](https://minecraft.wiki/w/Pack_format) for other versions.

## Action JSON Format

Each `.json` file inside `smartphone_actions/` defines one action. Example:

```json
{
  "id": "mymod:heal_pokemon",
  "texture": "mymod:textures/gui/buttons/heal.png",
  "hover_texture": "mymod:textures/gui/buttons/heal_hover.png",
  "commands": [
    "pokerestore @p"
  ],
  "order": 0,
  "require_mod": "cobblemon",
  "cooldown_seconds": 60
}
```

### Fields

| Field | Required | Type | Description |
|-------|:--------:|------|-------------|
| `id` | Yes | String | Unique identifier in `namespace:name` format |
| `texture` | Yes | String | Button texture path in a resource pack |
| `hover_texture` | Yes | String | Button texture path when hovered |
| `commands` | Yes | List | Commands executed as the player (without `/`) |
| `order` | No | Integer | Display order (lower = first). Default: `0` |
| `require_mod` | No | String | A required mod ID. If absent, the action is skipped |
| `cooldown_seconds` | No | Integer | Cooldown between uses per player. Default: `0` |

## Textures

Button textures must be in a **resource pack** (which can be the server resource pack). The recommended size is **36x36 pixels**.

Place texture files in:

```
assets/<your_namespace>/textures/gui/buttons/
```

And reference them in the JSON as:

```
"texture": "your_namespace:textures/gui/buttons/texture_name.png"
```

> **Tip**: The server can automatically push the resource pack by setting `resource-pack` and `resource-pack-sha1` in `server.properties`.

## Commands

Commands are executed server-side **as if typed by the player** who clicked the button. Use `@p` to reference the player.

### Examples

**Heal Pokemon (requires Cobblemon):**
```json
{
  "id": "mymod:heal_pokemon",
  "texture": "mymod:textures/gui/buttons/heal.png",
  "hover_texture": "mymod:textures/gui/buttons/heal_hover.png",
  "commands": [
    "pokeheal @p"
  ],
  "cooldown_seconds": 120
}
```

**Open an interface (requires a compatible mod):**
```json
{
  "id": "mymod:open_shop",
  "texture": "mymod:textures/gui/buttons/shop.png",
  "hover_texture": "mymod:textures/gui/buttons/shop_hover.png",
  "commands": [
    "shop open @p"
  ],
  "require_mod": "shop_mod"
}
```

**Teleport to spawn:**
```json
{
  "id": "mymod:teleport_spawn",
  "texture": "mymod:textures/gui/buttons/spawn.png",
  "hover_texture": "mymod:textures/gui/buttons/spawn_hover.png",
  "commands": [
    "spawn @p"
  ],
  "cooldown_seconds": 300
}
```

**Multiple commands in sequence:**
```json
{
  "id": "mymod:starter_kit",
  "texture": "mymod:textures/gui/buttons/kit.png",
  "hover_texture": "mymod:textures/gui/buttons/kit_hover.png",
  "commands": [
    "say @p claimed the starter kit!",
    "give @p minecraft:iron_sword 1",
    "give @p minecraft:bread 16",
    "give @p minecraft:leather_chestplate 1"
  ],
  "cooldown_seconds": 86400
}
```

## Testing

1. Place the datapack in the `datapacks/` folder of your world or server
2. Place the resource pack in the `resourcepacks/` folder or configure it as the server resource pack
3. Run `/reload` on the server
4. Join the server and open the Smartphone — the new actions will appear alongside the existing ones

## Tips

- The **namespace** in `id` can be anything, but use a unique one to avoid conflicts
- If `require_mod` is set and the mod is not installed, the action is silently skipped
- `cooldown_seconds` is per-player — each player has their own cooldown
- Datapack actions appear **after** the mod's default actions (Heal, PC, Ender Chest, etc.)
- If the JSON is invalid, the action is skipped and an error message appears in the server log
