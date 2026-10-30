# Cobblemon Smartphone - Upgrades Datapack Example

This example datapack replicates the built-in functions for **Party Heal**, **Open PC**, and **Ender Chest (Cloud Storage)**, but gates them behind **Upgrades** installed via the Smithing Table.

---

## 📋 How It Works

By default, Cobblemon Smartphone provides these buttons for free on the home screen. With this datapack and a quick adjustment in the mod configuration, you can:
1. Disable the default free buttons.
2. Enable custom datapack actions that **require specific upgrades** installed on the smartphone before the buttons appear and can be used.
3. Provide Smithing Table recipes to install each upgrade onto the smartphone.

---

## 🚀 Quick Setup

### 1. Configure the Mod (`config/cobblemon_smartphone.json`)
In your server or client configuration file, disable the free native actions to avoid duplicate buttons:

```json
{
  "features": {
    "enableHeal": false,
    "enablePC": false,
    "enableCloud": false
  }
}
```

> **Note:** The actions in this datapack will continue to function properly even with these features set to `false`, because datapack actions operate independently from the built-in actions.

### 2. Install the Datapack
- **Dedicated Server:** Copy the datapack folder (or `.zip`) into the `world/datapacks/` directory.
- **Singleplayer:** Copy it into `.minecraft/saves/<your_world>/datapacks/`.
- In-game, run `/reload` or restart the server.

---

## 🔨 Smithing Table Recipes

All recipes use the standard Cobblemon upgrade item as the template and accept **any smartphone color** in the base slot. The crafted smartphone **retains its original color** and preserves any other upgrades already installed:

| Upgrade | Template | Base | Additional Ingredient | NBT Tag Added |
|---|---|---|---|---|
| **Pokémon Party Heal** | `cobblemon:upgrade` | Any Smartphone | `cobblemon:healing_machine` | `upgrade_heal: true` |
| **Pokémon PC** | `cobblemon:upgrade` | Any Smartphone | `cobblemon:pc` | `upgrade_pc: true` |
| **Ender Chest (Cloud)** | `cobblemon:upgrade` | Any Smartphone | `minecraft:ender_chest` | `upgrade_ender_chest: true` |

---

## ⚡ Useful Testing Commands (Admin)

To directly obtain a test smartphone with all 3 upgrades pre-installed (Minecraft 1.20.5+ / 1.21+):

```mcfunction
/give @s cobblemon_smartphone:red_smartphone[custom_data={"cobblemon_smartphone:upgrades":{upgrade_heal:true,upgrade_pc:true,upgrade_ender_chest:true}}]
```

To give a smartphone with a single specific upgrade:
- **Heal Only:**
  ```mcfunction
  /give @s cobblemon_smartphone:red_smartphone[custom_data={"cobblemon_smartphone:upgrades":{upgrade_heal:true}}]
  ```
- **PC Only:**
  ```mcfunction
  /give @s cobblemon_smartphone:red_smartphone[custom_data={"cobblemon_smartphone:upgrades":{upgrade_pc:true}}]
  ```
- **Ender Chest Only:**
  ```mcfunction
  /give @s cobblemon_smartphone:red_smartphone[custom_data={"cobblemon_smartphone:upgrades":{upgrade_ender_chest:true}}]
  ```

---

## ⚙️ Datapack File Structure

```
cobblemon_smartphone_upgrades_example/
├── pack.mcmeta
├── README.md
└── data/
    └── smartphone_upgrades/
        ├── smartphone_actions/
        │   ├── heal.json          # Heal action (requires upgrade_heal)
        │   ├── pc.json            # PC action (requires upgrade_pc)
        │   └── cloud.json         # Ender Chest action (requires upgrade_ender_chest)
        └── recipe/
            ├── heal_upgrade.json         # Smithing Table recipe for Heal
            ├── pc_upgrade.json           # Smithing Table recipe for PC
            └── ender_chest_upgrade.json  # Smithing Table recipe for Ender Chest
```

---

## 🛠️ Customization

### 1. Changing Cooldowns
Edit the files in `data/smartphone_upgrades/smartphone_actions/*.json` and modify the `"cooldown_seconds"` value. For example:
```json
"cooldown_seconds": 120
```

### 2. Adding Energy Costs (FE)
If Cobblemon Smartphone energy/battery mechanics are enabled (`enableEnergy: true`), you can add an FE cost directly into the action JSON:
```json
"fe_cost": 250
```

### 3. Changing Recipe Ingredients
If you prefer Heal to require a Golden Apple instead of a Healing Machine, simply change the `"addition"` field in `data/smartphone_upgrades/recipe/heal_upgrade.json`:
```json
"addition": {
  "item": "minecraft:golden_apple"
}
```
