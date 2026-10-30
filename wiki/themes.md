# Theme System

The Cobblemon Smartphone theme system allows players and pack creators to fully customize the appearance of the smartphone using **Resource Packs** — no datapacks, mods, coding, or server restarts required.

Themes can customize:
- **Wallpapers** for both normal and large screens (Pokédex, Social, GPS, etc.)
- **Smartphone Frames / Casings** (custom phone bezels/borders)
- **App Icons** with custom textures and hover states
- **UI Elements & Buttons** (home, previous, next, settings gear, drag handle, visibility eye, social pokéball)
- **Semantic GUI Color Palettes** (primary accents, surfaces, headers, texts, clock color, and dynamic battery indicators)

---

## Theme Creator Studio (Visual Web Tool)

The mod includes an interactive, standalone HTML tool: **Theme Creator Studio**.
You can open [`theme_creator.html`](file:///home/rapid/workspace/rapidmesck/cobblemon_smartphone_main/theme_creator.html) (located in the repository root or [`tools/theme-creator/index.html`](file:///home/rapid/workspace/rapidmesck/cobblemon_smartphone_main/tools/theme-creator/index.html)) directly in any modern web browser without needing a web server or internet connection (works 100% offline via `file://`).

### Studio Capabilities
- **Real-Time Pixel-Perfect Preview**: High-DPI canvas preview rendering Small (`131×207` px) and Large (`211×207` px) phone interfaces with nearest-neighbor scaling.
- **Interactive Screen Switcher**:
  - **Home Screen**: App icon grid (2×3), clock, battery indicator, and footer navigation buttons.
  - **Settings Screen**: Apps and Themes tabs, list items, drag handles, and eye visibility toggles.
  - **Large Screen**: Header pokéball branding, search bar, contact list cards, and wide wallpaper.
- **16 Dyed Phone Casings**: Switch between all 16 smartphone colors (Black, White, Red, Blue, Yellow, Pink, Cyan, etc.) to test how your theme looks on dyed items.
- **Asset Uploaders**:
  - Custom standard wallpaper (`131×207`) & large wallpaper (`211×207`).
  - Custom standard phone casing frame & large casing frame.
  - Custom UI elements (`home_button`, `prev_button`, `next_button`, `settings_button`, `drag_handle`, `view_select`, `social_pokeball`).
  - Custom app icons (36×36 px with hover state support).
- **22 Semantic GUI Color Controls**: Live color pickers with instant preview updates across surfaces, texts, highlights, and borders.
- **Dynamic Battery Tester**: Slider (0%–100%) and charging toggle to preview real-time battery border, cavity, and charge level tinting (`battery_high`, `battery_med`, `battery_low`).
- **One-Click Export & Import**:
  - **Export JSON**: Download `<theme_id>.json` directly.
  - **Export Resource Pack ZIP**: Generates and downloads a complete, ready-to-load Minecraft resource pack (`.zip`) containing `pack.mcmeta` and all PNG textures arranged in the correct `assets/<namespace>/...` folder structure.
  - **Import JSON**: Load existing theme files to modify colors and metadata visually.

---

## Quick Start

You can create a theme using either **PNG Autodiscovery** (quickest) or a **JSON Theme Definition** (full control).

### 1. Quick Mode (PNG Images Only)

Drop a `131×207` px PNG into your resource pack:

```
assets/<namespace>/textures/gui/themes/<theme_name>.png
```

Optionally, add a `211×207` px version for large screens (PokeInfo, Social, GPS):

```
assets/<namespace>/textures/gui/themes/<theme_name>_large.png
```

Press `F3 + T` in-game to reload resource packs. The theme is automatically detected and added to the in-game theme selector.

### 2. Full Mode (JSON Definition)

Place a JSON file in your resource pack:

```
assets/<namespace>/themes/<theme_id>.json
```

**Minimal Example:**

```json
{
  "id": "mypack:neon",
  "name": "Neon Cyber",
  "wallpaper": "mypack:textures/gui/wallpapers/neon.png",
  "large_wallpaper": "mypack:textures/gui/wallpapers/neon_large.png"
}
```

---

## In-Game Selection & Persistence

Players can browse and switch themes directly inside Minecraft:
1. Open the Smartphone.
2. Click the **Settings Gear** icon in the top-right corner.
3. Click the **Themes** tab at the top.
4. Click any theme card to preview and apply it.

The selected theme is saved directly to the smartphone item's custom data (`cobblemon_smartphone:theme`), meaning each smartphone in the player's inventory can have its own distinct theme!

---

## JSON Specification Reference

### Root Properties

| Property | Type | Default | Description |
|---|---|---|---|
| `id` | String | *Auto-generated from path* | Unique identifier (e.g. `"mypack:cyberpunk"`). |
| `name` | String | `"theme.<id>"` | Display name. If it contains a dot (`.`), it is resolved as a translation key (e.g. `"theme.mypack.cyberpunk"`); otherwise, it is displayed as literal text. |
| `wallpaper` | String | **Required** | Texture path to the standard wallpaper (`131×207` px). Alternate alias: `"texture"`. |
| `large_wallpaper` | String | `null` (auto-fallback) | Texture path to the wide wallpaper (`211×207` px) used in PokeInfo, Social, and GPS screens. If omitted, the game automatically checks for `<wallpaper>_large.png` before falling back to the default. Alternate alias: `"large_texture"`. |
| `frame` | String | `null` | Custom phone case/bezel texture for standard screens (`131×207` px). If omitted or invalid, uses the smartphone item's dyed casing texture. |
| `large_frame` | String | `null` | Custom phone case/bezel texture for large screens (`211×207` px). If omitted or invalid, uses the smartphone item's dyed casing texture. |
| `elements` | Object | `{}` | Custom UI button and icon textures. See [Elements](#elements). |
| `app_icons` | Object | `{}` | Custom app icon texture overrides. See [App Icons](#app-icons). |
| `colors` | Object | `{}` | Semantic GUI color palette overrides. See [Colors](#colors). |
| `order` | Integer | `50` | Display order in the Settings screen (lower values appear first). |

---

### Elements

The `elements` object overrides individual UI control textures. All fields are optional; any omitted element automatically uses the default texture.

```json
{
  "elements": {
    "home_button": "mypack:textures/gui/elements/home_button.png",
    "prev_button": "mypack:textures/gui/elements/prev_button.png",
    "next_button": "mypack:textures/gui/elements/next_button.png",
    "settings_button": "mypack:textures/gui/elements/settings_button.png",
    "drag_handle": "mypack:textures/gui/elements/drag_handle.png",
    "view_select": "mypack:textures/gui/elements/view_select.png",
    "social_pokeball": "mypack:textures/gui/elements/social_pokeball.png"
  }
}
```

| Element | Dimensions | Sprite Layout | Description |
|---|---|---|---|
| `home_button` | 7×14 px | Top 7×7 normal, Bottom 7×7 hover | Footer center button (returns to home screen). |
| `prev_button` | 7×14 px | Top 7×7 normal, Bottom 7×7 hover | Footer left button (previous page). |
| `next_button` | 7×14 px | Top 7×7 normal, Bottom 7×7 hover | Footer right button (next page). |
| `settings_button` | 11×22 px | Top 11×11 normal, Bottom 11×11 hover | Header gear button (opens Settings screen). |
| `drag_handle` | 8×16 px | Top 8×8 normal, Bottom 8×8 hover | Drag handle in Settings to reorder apps. |
| `view_select` | 8×16 px | Top 8×8 normal, Bottom 8×8 hover | Eye toggle in Settings to hide/unhide apps. |
| `social_pokeball` | 12×12 px | Single 12×12 frame | Header branding pokéball in Social/DM screens. |

---

### App Icons

The `app_icons` object overrides the 36×36 px icon textures of any installed application (built-in or added via Datapack/Mod API).

You can specify icons in two ways:

#### 1. Short String Format
If you only specify the base texture, the mod will also automatically check for `<texture>_hover.png` for the hover state:

```json
{
  "app_icons": {
    "cobblemon_smartphone:social": "mypack:textures/gui/apps/social.png"
  }
}
```

#### 2. Explicit Object Format
Allows you to explicitly specify distinct normal and hover textures:

```json
{
  "app_icons": {
    "cobblemon_smartphone:pokedex": {
      "texture": "mypack:textures/gui/apps/pokedex.png",
      "hover": "mypack:textures/gui/apps/pokedex_hover.png"
    }
  }
}
```

#### Common Built-in App IDs
- `cobblemon_smartphone:pokedex`
- `cobblemon_smartphone:social`
- `cobblemon_smartphone:gps`
- `cobblemon_smartphone:structure_compass`
- `cobblemon_smartphone:heal`
- `cobblemon_smartphone:pc`
- `cobblemon_smartphone:ender_chest`

---

### Colors

Themes can override every semantic color drawn across all mod screens. Colors accept standard `#RRGGBB` or `#AARRGGBB` hex strings.

```json
{
  "colors": {
    "primary": "#00FFCC",
    "primary_hover": "#33FFDD",
    "primary_border": "#00997A",
    "surface": "#0D1117",
    "surface_alt": "#161B22",
    "surface_hover": "#21262D",
    "shadow": "#00000044",
    "header_navy": "#070B10",
    "header_navy_light": "#0E1520",
    "text_primary": "#FFFFFF",
    "text_secondary": "#00FFCC",
    "text_muted": "#58A6FF",
    "text_dark": "#C9D1D9",
    "text_dark_muted": "#8B949E",
    "gold": "#FFD700",
    "danger": "#FF4444",
    "success": "#00FFAA",
    "time": "#00FFCC",
    "battery_border": "#00FFCC",
    "battery_empty": "#070B10",
    "battery_high": "#00FFAA",
    "battery_med": "#FFCC00",
    "battery_low": "#FF4444"
  }
}
```

#### Color Catalog Reference

| Color Key | Default (Hex) | Purpose |
|---|---|---|
| `primary` | `#5BD1DA` | Main theme accent color, active tab indicators, primary buttons. |
| `primary_hover` | `#7CE3EB` | Hover state for accent buttons and interactable links. |
| `primary_border` | `#35454F` | Outlines and borders for accent cards and popups. |
| `surface` | `#FFFFFF` | Background fill for cards, modal dialogs, and lists. |
| `surface_alt` | `#F5F5FA` | Alternate alternating rows and subtle background panels. |
| `surface_hover` | `#ECECF4` | Hover background highlight for list items and menu rows. |
| `shadow` | `#22000000` | Subtle dropshadows for cards and floated elements. |
| `header_navy` | `#111114` | Title bar and dark header backgrounds in screens like Social/GPS. |
| `header_navy_light`| `#1A1A20` | Secondary header panels and sub-headers. |
| `text_primary` | `#FFFFFF` | Main header titles and high-contrast labels. |
| `text_secondary` | `#A0A0B0` | Subtitles, timestamps, and secondary info. |
| `text_muted` | `#686878` | Inactive tabs, disabled states, and faint descriptions. |
| `text_dark` | `#1A1A2E` | Dark text rendered on light surfaces and cards. |
| `text_dark_muted`| `#6E6E82` | Secondary muted text on light surfaces. |
| `gold` | `#FFD700` | Selection checkmarks, star ratings, and special highlights. |
| `danger` | `#FF4444` | Delete buttons, errors, and critical warnings. |
| `success` | `#3AAA55` | Online status indicator, success notices, and confirmations. |
| `time` | `#FFFFFF` | Clock time text displayed on the phone home screen. |
| `battery_border` | `#5BD1DA` | Outer bezel border of the phone battery icon. |
| `battery_empty` | `#1D4A59` | Unfilled cavity background of the battery. |
| `battery_high` | `#22C55E` | Battery charge fill color when charge > 50% (healthy green). |
| `battery_med` | `#EAB308` | Battery charge fill color when 20% < charge ≤ 50% (warning yellow). |
| `battery_low` | `#EF4444` | Battery charge fill color when charge ≤ 20% (critical red). |

---

## Complete Example Theme

Here is a full, advanced example demonstrating every customizable feature:

```json
{
  "id": "cyberpunk:neon_tokyo",
  "name": "theme.cyberpunk.neon_tokyo",
  "wallpaper": "cyberpunk:textures/gui/wallpapers/neon_tokyo.png",
  "large_wallpaper": "cyberpunk:textures/gui/wallpapers/neon_tokyo_large.png",
  "frame": "cyberpunk:textures/gui/frame.png",
  "large_frame": "cyberpunk:textures/gui/large_frame.png",
  "elements": {
    "home_button": "cyberpunk:textures/gui/elements/home_button.png",
    "prev_button": "cyberpunk:textures/gui/elements/prev_button.png",
    "next_button": "cyberpunk:textures/gui/elements/next_button.png",
    "settings_button": "cyberpunk:textures/gui/elements/settings_button.png",
    "drag_handle": "cyberpunk:textures/gui/elements/drag_handle.png",
    "view_select": "cyberpunk:textures/gui/elements/view_select.png",
    "social_pokeball": "cyberpunk:textures/gui/elements/social_pokeball.png"
  },
  "app_icons": {
    "cobblemon_smartphone:pokedex": {
      "texture": "cyberpunk:textures/gui/apps/pokedex.png",
      "hover": "cyberpunk:textures/gui/apps/pokedex_hover.png"
    },
    "cobblemon_smartphone:social": "cyberpunk:textures/gui/apps/social.png"
  },
  "colors": {
    "primary": "#FF0055",
    "primary_hover": "#FF3377",
    "primary_border": "#990033",
    "surface": "#120517",
    "surface_alt": "#1E0A26",
    "surface_hover": "#2B0F38",
    "header_navy": "#0A030D",
    "header_navy_light": "#15061A",
    "text_primary": "#FFFFFF",
    "text_secondary": "#FF0055",
    "text_muted": "#885577",
    "text_dark": "#FFE6F0",
    "text_dark_muted": "#CC99B3",
    "gold": "#FFCC00",
    "danger": "#FF2222",
    "success": "#00FF88",
    "time": "#FF0055",
    "battery_border": "#FF0055",
    "battery_empty": "#0A030D",
    "battery_high": "#00FF88",
    "battery_med": "#FFCC00",
    "battery_low": "#FF2222"
  },
  "order": 10
}
```

---

## Error Handling & Fallbacks

The theme system is built to be resilient against missing textures and corrupt definitions:

- **Missing Textures**: If a custom wallpaper, frame, app icon, or element texture cannot be found in any loaded resource pack, the mod logs a warning and automatically falls back to the default texture without crashing.
- **Missing or Invalid Colors**: If any color key is omitted or contains invalid hex formatting, the default semantic color is used seamlessly.
- **Hot-Reload Support**: Pressing `F3 + T` re-reads all theme definitions and textures instantly without requiring a client or server restart.
- **Resource Pack Ordering**: If multiple packs define a theme with the same ID, the pack positioned higher in Minecraft's Resource Pack screen overrides the lower one.
