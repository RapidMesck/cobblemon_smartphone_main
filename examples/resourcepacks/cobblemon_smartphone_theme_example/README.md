# Cobblemon Smartphone - Theme Resource Pack Example

This example demonstrates how to create custom themes for the **Cobblemon Smartphone** using **Resource Packs only** (without requiring datapacks, mods, coding, or server restarts).

---

## 1. Ways to Add a Theme

### Quick Mode (PNG Images Only)
Simply drop a PNG image (recommended dimensions: `131x207` px for standard screens, and optionally `211x207` with a `_large.png` suffix for large screens) into:
`assets/<namespace>/textures/gui/themes/<theme_name>.png` and `<theme_name>_large.png` (or in `wallpapers/`).

The mod automatically discovers the image, links the large version if present, formats the display name, and registers the theme in the in-game selector!

---

### Full Mode (JSON Theme Definition)
Allows comprehensive customization of:
- **Standard Wallpaper (`wallpaper`)** (`131x207` px)
- **Large Wallpaper (`large_wallpaper`)** (`211x207` px, used in PokeInfo, Social, GPS, etc.)
- **Phone Case / Bezel Frame (`frame` and `large_frame`)**
- **App Icons (`app_icons`)** with optional hover states (`hover`)
- **UI Button & Control Textures (`elements`)**:
  - `home_button`
  - `prev_button`
  - `next_button`
  - `settings_button`
  - `drag_handle`
  - `view_select`
  - `social_pokeball`
- **Semantic GUI Color Palettes (`colors`)** in `#RRGGBB` or `#AARRGGBB` hex format
- **Display Order (`order`)** in the theme selector tab

Create the file at `assets/<namespace>/themes/<theme_id>.json`:

```json
{
  "id": "mypack:neon",
  "name": "Neon Cyber",
  "wallpaper": "mypack:textures/gui/wallpapers/neon.png",
  "large_wallpaper": "mypack:textures/gui/wallpapers/neon_large.png",
  "frame": "mypack:textures/gui/frame.png",
  "large_frame": "mypack:textures/gui/large_frame.png",
  "elements": {
    "home_button": "mypack:textures/gui/elements/home_button.png",
    "prev_button": "mypack:textures/gui/elements/prev_button.png",
    "next_button": "mypack:textures/gui/elements/next_button.png",
    "settings_button": "mypack:textures/gui/elements/settings_button.png",
    "drag_handle": "mypack:textures/gui/elements/drag_handle.png",
    "view_select": "mypack:textures/gui/elements/view_select.png",
    "social_pokeball": "mypack:textures/gui/elements/social_pokeball.png"
  },
  "app_icons": {
    "cobblemon_smartphone:pokedex": {
      "texture": "mypack:textures/gui/apps/pokedex.png",
      "hover": "mypack:textures/gui/apps/pokedex_hover.png"
    },
    "cobblemon_smartphone:social": "mypack:textures/gui/apps/social.png"
  },
  "colors": {
    "primary": "#00FFCC",
    "primary_hover": "#33FFDD",
    "primary_border": "#00997A",
    "surface": "#0D1117",
    "surface_alt": "#161B22",
    "surface_hover": "#21262D",
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
  },
  "order": 50
}
```

> **Note:** All fields within `elements`, `app_icons`, and `colors` are optional. If any field is omitted or a texture is missing, the mod safely and gracefully falls back to default textures and colors without crashing.
