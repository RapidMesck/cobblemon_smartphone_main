#!/usr/bin/env python3
import os
import glob
import json
import colorsys
import numpy as np
from PIL import Image

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(BASE_DIR, "common/src/main/resources/assets/cobblemon_smartphone")
GUI_DIR = os.path.join(RES_DIR, "textures/gui")
WALLPAPERS_DIR = os.path.join(GUI_DIR, "wallpapers")
THEMES_DIR = os.path.join(RES_DIR, "themes")
BUTTONS_DIR = os.path.join(GUI_DIR, "buttons")
ELEMENTS_DIR = os.path.join(GUI_DIR, "elements")
THEMES_TEXTURES_DIR = os.path.join(GUI_DIR, "themes")

os.makedirs(WALLPAPERS_DIR, exist_ok=True)
os.makedirs(THEMES_DIR, exist_ok=True)
os.makedirs(THEMES_TEXTURES_DIR, exist_ok=True)

home_src = Image.open(os.path.join(GUI_DIR, "home_screen.png")).convert("RGBA")
large_src = Image.open(os.path.join(GUI_DIR, "large_screen.png")).convert("RGBA")

themes_data = {
    'red': {
        'order': 1,
        'wallpaper': {'hue': 0.00, 'sat_mult': 1.15, 'val_mult': 1.00},
        'colors': {
            'primary': '#D32F2F', 'primary_hover': '#E53935', 'primary_border': '#B71C1C',
            'surface': '#FFF5F5', 'surface_alt': '#FFEBEB', 'surface_hover': '#FFCDD2',
            'header_navy': '#320B0B', 'header_navy_light': '#4D1212',
            'text_primary': '#FFFFFF', 'text_secondary': '#FFCDD2', 'text_muted': '#B08585',
            'text_dark': '#1C1917', 'text_dark_muted': '#57534E',
            'time': '#FFFFFF', 'battery_border': '#E53935', 'battery_empty': '#320B0B',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'blue': {
        'order': 2,
        'wallpaper': {'hue': 0.60, 'sat_mult': 1.20, 'val_mult': 0.98},
        'colors': {
            'primary': '#1976D2', 'primary_hover': '#2196F3', 'primary_border': '#0D47A1',
            'surface': '#F0F7FF', 'surface_alt': '#E3F0FF', 'surface_hover': '#BBDEFB',
            'header_navy': '#0A1C30', 'header_navy_light': '#132F4C',
            'text_primary': '#FFFFFF', 'text_secondary': '#BBDEFB', 'text_muted': '#7D98B3',
            'text_dark': '#0F172A', 'text_dark_muted': '#475569',
            'time': '#FFFFFF', 'battery_border': '#2196F3', 'battery_empty': '#0A1C30',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'green': {
        'order': 3,
        'wallpaper': {'hue': 0.36, 'sat_mult': 1.15, 'val_mult': 0.95},
        'colors': {
            'primary': '#2E7D32', 'primary_hover': '#388E3C', 'primary_border': '#1B5E20',
            'surface': '#F1F8F1', 'surface_alt': '#E6F4E6', 'surface_hover': '#C8E6C9',
            'header_navy': '#0D230F', 'header_navy_light': '#183B1B',
            'text_primary': '#FFFFFF', 'text_secondary': '#C8E6C9', 'text_muted': '#79987B',
            'text_dark': '#142316', 'text_dark_muted': '#4A5D4C',
            'time': '#FFFFFF', 'battery_border': '#4CAF50', 'battery_empty': '#0D230F',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'yellow': {
        'order': 4,
        'wallpaper': {'hue': 0.13, 'sat_mult': 1.30, 'val_mult': 1.05},
        'colors': {
            'primary': '#996800', 'primary_hover': '#B58105', 'primary_border': '#734E00',
            'surface': '#FFFDF2', 'surface_alt': '#FFF9E0', 'surface_hover': '#FFF0B3',
            'header_navy': '#261E05', 'header_navy_light': '#3D3108',
            'text_primary': '#FFFFFF', 'text_secondary': '#FFE082', 'text_muted': '#A69668',
            'text_dark': '#211B0B', 'text_dark_muted': '#5C5033',
            'time': '#FFFFFF', 'battery_border': '#D4A017', 'battery_empty': '#261E05',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'orange': {
        'order': 5,
        'wallpaper': {'hue': 0.08, 'sat_mult': 1.20, 'val_mult': 1.05},
        'colors': {
            'primary': '#D84315', 'primary_hover': '#E64A19', 'primary_border': '#BF360C',
            'surface': '#FFF7ED', 'surface_alt': '#FFEDD5', 'surface_hover': '#FED7AA',
            'header_navy': '#261005', 'header_navy_light': '#3D1C0B',
            'text_primary': '#FFFFFF', 'text_secondary': '#FED7AA', 'text_muted': '#A38370',
            'text_dark': '#24140A', 'text_dark_muted': '#594030',
            'time': '#FFFFFF', 'battery_border': '#F97316', 'battery_empty': '#261005',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'purple': {
        'order': 6,
        'wallpaper': {'hue': 0.77, 'sat_mult': 1.10, 'val_mult': 0.95},
        'colors': {
            'primary': '#7B1FA2', 'primary_hover': '#8E24AA', 'primary_border': '#4A148C',
            'surface': '#FAF5FF', 'surface_alt': '#F3E8FF', 'surface_hover': '#E9D5FF',
            'header_navy': '#1D0724', 'header_navy_light': '#31103D',
            'text_primary': '#FFFFFF', 'text_secondary': '#E9D5FF', 'text_muted': '#9A80A6',
            'text_dark': '#1C0E21', 'text_dark_muted': '#4E3A54',
            'time': '#FFFFFF', 'battery_border': '#AB47BC', 'battery_empty': '#1D0724',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'magenta': {
        'order': 7,
        'wallpaper': {'hue': 0.85, 'sat_mult': 1.15, 'val_mult': 1.00},
        'colors': {
            'primary': '#AD1457', 'primary_hover': '#C2185B', 'primary_border': '#880E4F',
            'surface': '#FDF2F8', 'surface_alt': '#FCE7F3', 'surface_hover': '#FBCFE8',
            'header_navy': '#260817', 'header_navy_light': '#3D1227',
            'text_primary': '#FFFFFF', 'text_secondary': '#FBCFE8', 'text_muted': '#A37B8E',
            'text_dark': '#210B16', 'text_dark_muted': '#543243',
            'time': '#FFFFFF', 'battery_border': '#E91E63', 'battery_empty': '#260817',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'pink': {
        'order': 8,
        'wallpaper': {'hue': 0.93, 'sat_mult': 1.05, 'val_mult': 1.10},
        'colors': {
            'primary': '#C2185B', 'primary_hover': '#D81B60', 'primary_border': '#880E4F',
            'surface': '#FFF0F5', 'surface_alt': '#FFE4EC', 'surface_hover': '#F8BBD0',
            'header_navy': '#290A17', 'header_navy_light': '#421629',
            'text_primary': '#FFFFFF', 'text_secondary': '#F8BBD0', 'text_muted': '#A87E8F',
            'text_dark': '#240D17', 'text_dark_muted': '#593444',
            'time': '#FFFFFF', 'battery_border': '#F06292', 'battery_empty': '#290A17',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'lime': {
        'order': 9,
        'wallpaper': {'hue': 0.28, 'sat_mult': 1.25, 'val_mult': 1.00},
        'colors': {
            'primary': '#457B1E', 'primary_hover': '#558B2F', 'primary_border': '#2E5612',
            'surface': '#F9FDF5', 'surface_alt': '#F1FBE7', 'surface_hover': '#DCF5BD',
            'header_navy': '#122107', 'header_navy_light': '#1F360E',
            'text_primary': '#FFFFFF', 'text_secondary': '#DCF5BD', 'text_muted': '#809670',
            'text_dark': '#14210B', 'text_dark_muted': '#455437',
            'time': '#FFFFFF', 'battery_border': '#689F38', 'battery_empty': '#122107',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'light_blue': {
        'order': 10,
        'wallpaper': {'hue': 0.56, 'sat_mult': 1.05, 'val_mult': 1.08},
        'colors': {
            'primary': '#0277BD', 'primary_hover': '#0288D1', 'primary_border': '#01579B',
            'surface': '#F0F9FF', 'surface_alt': '#E0F2FE', 'surface_hover': '#BAE6FD',
            'header_navy': '#071F2E', 'header_navy_light': '#0F334A',
            'text_primary': '#FFFFFF', 'text_secondary': '#BAE6FD', 'text_muted': '#7094AB',
            'text_dark': '#091B26', 'text_dark_muted': '#3A4F5E',
            'time': '#FFFFFF', 'battery_border': '#03A9F4', 'battery_empty': '#071F2E',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'cyan': {
        'order': 11,
        'wallpaper': {'hue': 0.52, 'sat_mult': 1.00, 'val_mult': 1.00},
        'colors': {
            'primary': '#00838F', 'primary_hover': '#0097A7', 'primary_border': '#006064',
            'surface': '#EFFDFF', 'surface_alt': '#E0F7FA', 'surface_hover': '#B2EBF2',
            'header_navy': '#042226', 'header_navy_light': '#0B363D',
            'text_primary': '#FFFFFF', 'text_secondary': '#B2EBF2', 'text_muted': '#6C9499',
            'text_dark': '#061F24', 'text_dark_muted': '#324F54',
            'time': '#FFFFFF', 'battery_border': '#00ACC1', 'battery_empty': '#042226',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'brown': {
        'order': 12,
        'wallpaper': {'hue': 0.08, 'sat_mult': 0.75, 'val_mult': 0.65},
        'colors': {
            'primary': '#5D4037', 'primary_hover': '#6D4C41', 'primary_border': '#3E2723',
            'surface': '#FDF8F5', 'surface_alt': '#F7ECE6', 'surface_hover': '#EFEBE9',
            'header_navy': '#21130D', 'header_navy_light': '#362118',
            'text_primary': '#FFFFFF', 'text_secondary': '#D7CCC8', 'text_muted': '#9E867E',
            'text_dark': '#21130D', 'text_dark_muted': '#544037',
            'time': '#FFFFFF', 'battery_border': '#8D6E63', 'battery_empty': '#21130D',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'gray': {
        'order': 13,
        'wallpaper': {'achromatic': True, 'val_range': (0.35, 0.50), 'tint_hue': 0.55, 'tint_sat': 0.05},
        'colors': {
            'primary': '#455A64', 'primary_hover': '#546E7A', 'primary_border': '#263238',
            'surface': '#F4F6F7', 'surface_alt': '#ECEFF1', 'surface_hover': '#CFD8DC',
            'header_navy': '#171D21', 'header_navy_light': '#263036',
            'text_primary': '#FFFFFF', 'text_secondary': '#CFD8DC', 'text_muted': '#839296',
            'text_dark': '#13181C', 'text_dark_muted': '#414D52',
            'time': '#FFFFFF', 'battery_border': '#607D8B', 'battery_empty': '#171D21',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'light_gray': {
        'order': 14,
        'wallpaper': {'achromatic': True, 'val_range': (0.65, 0.82), 'tint_hue': 0.58, 'tint_sat': 0.05},
        'colors': {
            'primary': '#607D8B', 'primary_hover': '#78909C', 'primary_border': '#455A64',
            'surface': '#F8FAFB', 'surface_alt': '#F0F2F4', 'surface_hover': '#E2E6E9',
            'header_navy': '#1F2428', 'header_navy_light': '#31383E',
            'text_primary': '#FFFFFF', 'text_secondary': '#E2E6E9', 'text_muted': '#8B959C',
            'text_dark': '#171A1C', 'text_dark_muted': '#485054',
            'time': '#FFFFFF', 'battery_border': '#90A4AE', 'battery_empty': '#1F2428',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'black': {
        'order': 15,
        'wallpaper': {'achromatic': True, 'val_range': (0.13, 0.26), 'tint_hue': 0.60, 'tint_sat': 0.08},
        'colors': {
            'primary': '#5C6370', 'primary_hover': '#707887', 'primary_border': '#3B4049',
            'surface': '#1C1E22', 'surface_alt': '#24272C', 'surface_hover': '#2F333A',
            'header_navy': '#111215', 'header_navy_light': '#1A1C20',
            'text_primary': '#FFFFFF', 'text_secondary': '#ABB2BF', 'text_muted': '#676F7D',
            'text_dark': '#F0F2F5', 'text_dark_muted': '#9DA5B4',
            'time': '#FFFFFF', 'battery_border': '#828997', 'battery_empty': '#111215',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    },
    'white': {
        'order': 16,
        'wallpaper': {'achromatic': True, 'val_range': (0.86, 0.98), 'tint_hue': 0.60, 'tint_sat': 0.03},
        'colors': {
            'primary': '#545D70', 'primary_hover': '#667085', 'primary_border': '#3D4452',
            'surface': '#FFFFFF', 'surface_alt': '#F5F6F8', 'surface_hover': '#EAECF0',
            'header_navy': '#1A1D24', 'header_navy_light': '#282C36',
            'text_primary': '#FFFFFF', 'text_secondary': '#E4E7EB', 'text_muted': '#858E9E',
            'text_dark': '#14161A', 'text_dark_muted': '#444A57',
            'time': '#FFFFFF', 'battery_border': '#7A8496', 'battery_empty': '#1A1D24',
            'battery_high': '#22C55E', 'battery_med': '#EAB308', 'battery_low': '#EF4444'
        }
    }
}

def recolor_wallpaper(src_img, wp_conf):
    arr = np.array(src_img)
    arr_out = arr.copy()
    h_dim, w_dim = arr.shape[:2]
    is_achromatic = wp_conf.get('achromatic', False)

    for y in range(h_dim):
        for x in range(w_dim):
            if arr[y, x, 3] == 0:
                continue
            r, g, b, a = arr[y, x]
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)

            if is_achromatic:
                vmin, vmax = wp_conf['val_range']
                norm_v = np.clip((v - 0.7137) / (0.9059 - 0.7137), 0.0, 1.0)
                new_v = vmin + norm_v * (vmax - vmin)
                new_h = wp_conf.get('tint_hue', 0.0)
                new_s = wp_conf.get('tint_sat', 0.02)
            else:
                target_hue = wp_conf['hue']
                sat_mult = wp_conf.get('sat_mult', 1.0)
                val_mult = wp_conf.get('val_mult', 1.0)
                new_h = (target_hue + (h - 0.520)) % 1.0
                new_s = np.clip(s * sat_mult, 0.0, 1.0)
                new_v = np.clip(v * val_mult, 0.0, 1.0)

            nr, ng, nb = colorsys.hsv_to_rgb(new_h, new_s, new_v)
            arr_out[y, x, 0] = int(nr * 255)
            arr_out[y, x, 1] = int(ng * 255)
            arr_out[y, x, 2] = int(nb * 255)

    return Image.fromarray(arr_out)

def recolor_icon_image(src_img, wp_conf):
    arr = np.array(src_img.convert("RGBA"), dtype=np.float32)
    h_dim, w_dim = arr.shape[:2]
    arr_out = np.zeros_like(arr, dtype=np.uint8)
    is_achromatic = wp_conf.get("achromatic", False)

    for y in range(h_dim):
        for x in range(w_dim):
            r, g, b, a = arr[y, x]
            if a == 0:
                continue

            hv, sv, vv = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)

            # Detect dark pupil / deep contrast pixel
            if vv < 0.22 and sv < 0.6:
                if is_achromatic:
                    nh, ns, nv = wp_conf.get("tint_hue", 0.0), wp_conf.get("tint_sat", 0.0), vv
                else:
                    nh, ns, nv = wp_conf["hue"], sv * 0.4, vv
            elif is_achromatic:
                vmin, vmax = (0.35, 0.75)
                if 'val_range' in wp_conf:
                    if wp_conf['val_range'][0] < 0.25: # black
                        vmin, vmax = (0.22, 0.78)
                    elif wp_conf['val_range'][1] > 0.90: # white
                        vmin, vmax = (0.45, 0.95)
                    elif wp_conf['val_range'][0] >= 0.60: # light_gray
                        vmin, vmax = (0.50, 0.88)
                    else: # gray
                        vmin, vmax = (0.35, 0.75)
                norm_v = np.clip((vv - 0.55) / 0.45, 0.0, 1.0)
                nv = vmin + norm_v * (vmax - vmin)
                nh = wp_conf.get("tint_hue", 0.0)
                ns = np.clip(sv * wp_conf.get("tint_sat", 0.05), 0.0, 1.0)
            else:
                target_hue = wp_conf["hue"]
                sat_mult = wp_conf.get("sat_mult", 1.0)
                val_mult = wp_conf.get("val_mult", 1.0)
                nh = (target_hue + (hv - 0.535)) % 1.0
                ns = np.clip(sv * sat_mult, 0.0, 1.0)
                nv = np.clip(vv * val_mult, 0.0, 1.0)

            nr, ng, nb = colorsys.hsv_to_rgb(nh, ns, nv)
            arr_out[y, x] = [int(nr * 255), int(ng * 255), int(nb * 255), int(a)]

    return Image.fromarray(arr_out)

print("Starting full generation of 16 themes (wallpapers, button icons, elements, JSON)...")

button_files = [os.path.basename(p) for p in sorted(glob.glob(os.path.join(BUTTONS_DIR, "*.png")))]
element_files = ["home_button.png", "prev_button.png", "next_button.png", "settings_button.png"]

for color_name, data in themes_data.items():
    wp_conf = data['wallpaper']
    
    # 1. Wallpapers
    recolored_home = recolor_wallpaper(home_src, wp_conf)
    recolored_large = recolor_wallpaper(large_src, wp_conf)
    wp_small_path = os.path.join(WALLPAPERS_DIR, f"{color_name}.png")
    wp_large_path = os.path.join(WALLPAPERS_DIR, f"{color_name}_large.png")
    recolored_home.save(wp_small_path, format="PNG")
    recolored_large.save(wp_large_path, format="PNG")

    # 2. Buttons
    theme_btn_dir = os.path.join(THEMES_TEXTURES_DIR, color_name, "buttons")
    os.makedirs(theme_btn_dir, exist_ok=True)
    for bfile in button_files:
        src_btn = os.path.join(BUTTONS_DIR, bfile)
        dst_btn = os.path.join(theme_btn_dir, bfile)
        im = Image.open(src_btn)
        rec = recolor_icon_image(im, wp_conf)
        rec.save(dst_btn, format="PNG")

    # 3. Elements
    theme_elem_dir = os.path.join(THEMES_TEXTURES_DIR, color_name, "elements")
    os.makedirs(theme_elem_dir, exist_ok=True)
    for efile in element_files:
        src_elem = os.path.join(ELEMENTS_DIR, efile)
        dst_elem = os.path.join(theme_elem_dir, efile)
        if os.path.exists(src_elem):
            im = Image.open(src_elem)
            rec = recolor_icon_image(im, wp_conf)
            rec.save(dst_elem, format="PNG")

    # 4. JSON
    theme_json = {
        "id": f"cobblemon_smartphone:{color_name}",
        "name": f"theme.cobblemon_smartphone.{color_name}",
        "wallpaper": f"cobblemon_smartphone:textures/gui/wallpapers/{color_name}.png",
        "large_wallpaper": f"cobblemon_smartphone:textures/gui/wallpapers/{color_name}_large.png",
        "elements": {
            "home_button": f"cobblemon_smartphone:textures/gui/themes/{color_name}/elements/home_button.png",
            "prev_button": f"cobblemon_smartphone:textures/gui/themes/{color_name}/elements/prev_button.png",
            "next_button": f"cobblemon_smartphone:textures/gui/themes/{color_name}/elements/next_button.png",
            "settings_button": f"cobblemon_smartphone:textures/gui/themes/{color_name}/elements/settings_button.png"
        },
        "colors": {
            "primary": data['colors']['primary'],
            "primary_hover": data['colors']['primary_hover'],
            "primary_border": data['colors']['primary_border'],
            "surface": data['colors']['surface'],
            "surface_alt": data['colors']['surface_alt'],
            "surface_hover": data['colors']['surface_hover'],
            "header_navy": data['colors']['header_navy'],
            "header_navy_light": data['colors']['header_navy_light'],
            "text_primary": data['colors']['text_primary'],
            "text_secondary": data['colors']['text_secondary'],
            "text_muted": data['colors']['text_muted'],
            "text_dark": data['colors']['text_dark'],
            "text_dark_muted": data['colors']['text_dark_muted'],
            "gold": "#FFD700",
            "danger": "#EF4444",
            "success": "#3AAA55",
            "time": data['colors']['time'],
            "battery_border": data['colors']['battery_border'],
            "battery_empty": data['colors']['battery_empty'],
            "battery_high": data['colors']['battery_high'],
            "battery_med": data['colors']['battery_med'],
            "battery_low": data['colors']['battery_low']
        },
        "order": data['order']
    }

    theme_path = os.path.join(THEMES_DIR, f"{color_name}.json")
    with open(theme_path, "w", encoding="utf-8") as f:
        json.dump(theme_json, f, indent=2)

    print(f" -> {color_name:12}: generated wallpapers, {len(button_files)} buttons, {len(element_files)} elements, and JSON (order {data['order']})")

print("All 16 themes and complete assets generated successfully!")
