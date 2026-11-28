# Cobblemon Smartphone Studio

> **Launch Online:** [https://cobblemon-smartphone-studio.vercel.app/](https://cobblemon-smartphone-studio.vercel.app/)

**Cobblemon Smartphone Studio** is the official 100% client-side web creator hub for **Cobblemon Smartphone** (Minecraft 1.21.1 — Fabric & NeoForge). It replaces the legacy standalone `theme_creator.html` tool with a full workspace for designing **Themes**, **HD/Pixel UI Screens**, **Custom Actions & Apps**, **3D Skins & Cases**, and **Datapack Recipes & SD Cards**, then exporting ready-to-load Minecraft packs.

All projects, PNG textures, and 3D `.json` models are stored locally in your browser's **IndexedDB** — no account or backend server is required.

---

## Studio Modules

### 1. Theme Studio
- **Unified WebGL2 Preview Engine** with **Pixel Style** (default) and **HD Style** toggle.
- Live replicas of the in-game **Home**, **Settings**, and **Social** screens across all **16 dyed smartphone casings**.
- **22 Semantic GUI Colors** with instant preview, dynamic battery slider (0%–100% + charging), and built-in theme cloner.
- Upload custom wallpapers (`131×207` & `211×207`), frames, UI elements, and `36×36` app icons (with hover textures), or import existing theme `.json` files.

### 2. UI Screen Studio (`smartphone_screens/`)
- Visual drag-and-configure editor for custom datapack UI screens with **31 built-in components** (headers, cards, buttons, tabs, grids, progress bars, modals, toasts, switches, and more).
- Interactive **Play Mode** and **Click-to-Select** canvas with live `visible_if` and `{state.*}` expression evaluation.
- Supports `compact` (`262×414`), `large` (`422×414`), and `free` viewports.

### 3. Action & App Studio (`smartphone_actions/`)
- Configure custom smartphone app buttons with `36×36` normal and hover textures.
- Link actions to custom UI screens (`open_screen`), **12 built-in functions** (`open_pc`, `heal_party`, `open_pokedex`, `open_gps`, etc.), server commands, simulated `use_items`, and Patchouli books.
- Configure `order`, `cooldown_seconds`, `fe_cost`, `require_mod`, and `require_upgrade`.

### 4. 3D Skin & Case Studio (`smartphone_skins/`)
- Real-time Three.js **Minecraft JSON 3D model viewer** using vanilla Minecraft directional lighting, per-face UV mapping, `parent` chain resolution, and missing-texture checkerboard detection.
- Upload Blockbench `.json` models for Hand and Rotom Phone variants with automatic multi-texture slot detection (`In Pack`, `Mod Built-in`, or `Missing PNG`) and automatic namespace rewriting for un-namespaced texture references.
- Configure color filters, lock status, custom GUI frames, and `require_upgrade`.

### 5. Recipes & SD Cards (`data/<namespace>/recipe/`)
- Dedicated visual recipe builder decoupled from Actions and Skins, supporting **all standard Minecraft 1.21.1 datapack recipe types**:
  - **Crafting Table (Shaped 3×3)** (`minecraft:crafting_shaped`) with automatic pattern trimming and `#tag` support
  - **Crafting Table (Shapeless)** (`minecraft:crafting_shapeless`)
  - **Smithing Table** (`minecraft:smithing_transform`)
  - **Furnace** (`minecraft:smelting`), **Blast Furnace** (`minecraft:blasting`), **Smoker** (`minecraft:smoking`), and **Campfire** (`minecraft:campfire_cooking`)
  - **Stonecutter** (`minecraft:stonecutting`)
- Pair any recipe type with any **Output Result**:
  - **SD Card** (`cobblemon_smartphone:sd_card` with `cobblemon_smartphone:sd_card_upgrade`)
  - **Smartphone + Upgrade** (`cobblemon_smartphone:upgrades`)
  - **Smartphone + 3D Skin** (`unlocked_skins` or equip-on-craft `skin`)
  - **Smartphone / Mod Item** (16 colors, batteries, accessories) or **Custom Item** (any item ID + count + optional `components` JSON)
- Automatic cross-loader mod conditions (`fabric:load_conditions` + `neoforge:conditions`).

### 6. Code Explorer
- VS Code-style virtual pack explorer showing the exact generated folder tree (`pack.mcmeta`, `assets/<namespace>/...`, `data/<namespace>/...`).
- Inspect PNG assets and edit JSON files (including uploaded 3D model `.json` files) directly in the browser with `Ctrl+S` save and formatting.

---

## Exporting Packs

From the top bar or **Overview**, export your project in one click:
- **Combined Pack (`.zip`)** — Contains both `assets/` and `data/` (usable as a global pack or split for client/server).
- **Resource Pack (`.zip`)** — Contains `pack.mcmeta` and `assets/<namespace>/...` (themes, textures, 3D models, skin definitions).
- **Data Pack (`.zip`)** — Contains `pack.mcmeta` and `data/<namespace>/...` (actions, UI screens, recipes, datapack skins).

---

## Running Locally

The Studio lives in the `studio/` directory of this repository:

```bash
cd studio
pnpm install
pnpm dev
```

Open `http://localhost:5173/` in your browser. All mod textures, 3D models, built-in themes, and the mod icon are synced automatically from `common/src/main/resources/assets/cobblemon_smartphone/`.

### Building / Deploying on Vercel

The repository includes `vercel.json` at the root (and `studio/vercel.json`) for one-click deployment on Vercel. To build a static production bundle locally:

```bash
cd studio
pnpm run build
```

The static output is written to `studio/dist/`.
