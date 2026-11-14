# 3D Skin & Case System

The Cobblemon Smartphone **3D Skin System** allows pack creators to customize the 3D models of both the **normal Smartphone** and the **Rotom Phone** (in-hand, selfie mode, and shoulder/world companion) using **Resource Packs** and/or **Data Packs**.

Skins can be:
- **Unlocked by default** (available immediately in Settings → Skins)
- **Locked** (`"locked": true`) and unlocked through **Smithing Table crafts**, **Smartphone Upgrades**, **loot tables**, or **commands**
- **Restricted to specific smartphone colors** or available for all 16 colors

---

## Quick Start (Resource Pack)

1. Place your 3D `.json` models (e.g. exported from Blockbench) inside your resource pack:
   ```
   assets/<namespace>/models/item/smartphone_skins/pikachu_hand.json
   assets/<namespace>/models/item/smartphone_skins/pikachu_rotom.json
   ```
2. Create a skin definition JSON in:
   ```
   assets/<namespace>/smartphone_skins/pikachu_case.json
   ```
   ```json
   {
     "id": "mypack:pikachu_case",
     "name": "Pikachu Case",
     "hand_model": "mypack:item/smartphone_skins/pikachu_hand",
     "rotom_model": "mypack:item/smartphone_skins/pikachu_rotom"
   }
   ```
3. Press `F3 + T` in-game to reload resource packs, then open **Smartphone → Settings → Skins** to equip it.

> [!TIP]
> You can inherit from the mod's built-in 3D models (such as `"parent": "cobblemon_smartphone:item/yellow_smartphone_3d"` or `"parent": "cobblemon_smartphone:item/rotom_yellow"`) and override only `"textures"` if you just want to re-texture the default geometry.

---

## Data Pack Support

You can also define skins on the server side using a **Data Pack**:

```
data/<namespace>/smartphone_skins/<skin_id>.json
```

When players join the server (or after `/reload`), datapack skins are automatically synced to clients.

> [!NOTE]
> The 3D `.json` model files and textures themselves must still exist in a **Resource Pack** (client-side). Any model placed under `assets/<namespace>/models/smartphone_skins/` or `assets/<namespace>/models/item/smartphone_skins/` is automatically baked by the client even if the skin definition lives only in a Data Pack.

---

## JSON Field Reference

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | `string` | No | Unique skin ID (e.g. `"mypack:pikachu_case"`). Defaults to `<namespace>:<filename>`. |
| `name` | `string` | No | Display name or translation key (strings containing `.` are treated as translation keys). |
| `hand_model` | `string` | No* | Model `ResourceLocation` used for the normal 3D smartphone (in-hand & selfie). |
| `rotom_model` | `string` | No* | Model `ResourceLocation` used for the Rotom Phone 3D model (in-hand, selfie & shoulder companion). |
| `inventory_model` | `string` | No* | Optional 2D/inventory model override when not held in hand. |
| `frame` | `string` | No* | Optional PNG texture `ResourceLocation` (`131×207` px) to override the normal smartphone GUI frame. Falls back to the active theme/color frame if omitted. |
| `large_frame` | `string` | No* | Optional PNG texture `ResourceLocation` (`211×207` px) to override the large smartphone GUI frame (PokéInfo, Social, GPS, Rotom, etc.). Falls back to the active theme/color large frame if omitted. |
| `colors` | `string[]` | No | List of compatible smartphone colors (e.g. `["red", "yellow"]`). Omit or use `["*"]` to allow all 16 colors. |
| `locked` | `boolean` | No | Alias: `require_unlock`. If `true`, the skin starts locked until unlocked on the smartphone item. Default: `false`. |
| `require_upgrade` | `string` | No | Alias: `required_upgrade`. Upgrade key (from `cobblemon_smartphone:upgrades`) that automatically unlocks this skin when present on the smartphone. |

*\*At least one of `hand_model`, `rotom_model`, `inventory_model`, `frame`, or `large_frame` must be provided. Any omitted field falls back to the active theme or default color asset.*

---

## Unlocking Skins via Craft & Upgrades

Creators can make skins unlockable instead of giving them for free in the Settings menu.

### 1. Unlock via Smithing Table Recipe

Set `"locked": true` in the skin JSON, then add a `minecraft:smithing_transform` recipe in your Data Pack:

```
data/<namespace>/recipe/unlock_pikachu_case.json
```

```json
{
  "type": "minecraft:smithing_transform",
  "template": {
    "item": "cobblemon:upgrade"
  },
  "base": {
    "tag": "cobblemon_smartphone:smartphones"
  },
  "addition": {
    "item": "minecraft:gold_ingot"
  },
  "result": {
    "id": "cobblemon_smartphone:red_smartphone",
    "count": 1,
    "components": {
      "minecraft:custom_data": {
        "cobblemon_smartphone:unlocked_skins": {
          "mypack:pikachu_case": true
        }
      }
    }
  }
}
```

When crafted in a Smithing Table, Cobblemon Smartphone preserves the player's **base smartphone color, energy, upgrades, theme, and fused Rotom**, and adds the unlocked skin to the item.

- Use `"cobblemon_smartphone:unlocked_skins": { "<skin_id>": true }` to **unlock** the skin so the player can toggle it in **Settings → Skins**.
- Use `"cobblemon_smartphone:skin": "<skin_id>"` to **both unlock and immediately equip** the skin.

### 2. Link to a Smartphone Upgrade

If you already have a custom upgrade (e.g. `"upgrade_pikachu"` in `cobblemon_smartphone:upgrades`), set `"require_upgrade": "upgrade_pikachu"` in the skin JSON:

```json
{
  "id": "mypack:pikachu_case",
  "name": "Pikachu Case",
  "hand_model": "mypack:item/smartphone_skins/pikachu_hand",
  "rotom_model": "mypack:item/smartphone_skins/pikachu_rotom",
  "locked": true,
  "require_upgrade": "upgrade_pikachu"
}
```

Any smartphone with that upgrade will automatically have the skin unlocked.

### 3. Commands & Loot Tables

You can also grant a smartphone with a pre-equipped or unlocked skin via commands or loot tables using `minecraft:custom_data`:

```mcfunction
/give @p cobblemon_smartphone:yellow_smartphone[minecraft:custom_data={"cobblemon_smartphone:skin":"mypack:pikachu_case"}]
```

*(Short alias `"smartphone_skin": "mypack:pikachu_case"` is also supported.)*
