#!/usr/bin/env python3
"""
Generate minimalist, monochromatic 36x36 pixel-art default app icons (normal + hover).

Design system:
- 32x32 rounded squircle centered in 36x36.
- Semi-transparent dark smoked-glass container that softly harmonizes with any
  theme wallpaper (cyan, red, blue, black, white, yellow, etc.) without needing
  per-theme recoloring.
- Crisp 100% monochromatic white/slate pixel-art glyphs.
- Hover state: brighter glass body + crisp white 1px border + pure white glyph.
"""

from __future__ import annotations

from pathlib import Path
from typing import Callable

from PIL import Image

BASE_DIR = Path(__file__).resolve().parent.parent
BUTTONS_DIR = (
    BASE_DIR
    / "common/src/main/resources/assets/cobblemon_smartphone/textures/gui/buttons"
)

SIZE = 36


def hex_rgba(h: str, a: int = 255) -> tuple[int, int, int, int]:
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def build_squircle_masks() -> tuple[
    set[tuple[int, int]],
    set[tuple[int, int]],
    set[tuple[int, int]],
    set[tuple[int, int]],
]:
    """Build exact 32x32 rounded squircle masks (border, inner_rim, top_sheen, body)."""
    # Exact row span of the 32x32 squircle inside 36x36 (rows 2..33)
    # Corner insets: row 2 -> 7, row 3 -> 5, row 4 -> 4, row 5..6 -> 3, row 7..28 -> 2
    insets = {
        2: 7, 3: 5, 4: 4, 5: 3, 6: 3,
        33: 7, 32: 5, 31: 4, 30: 3, 29: 3,
    }
    inside: set[tuple[int, int]] = set()
    for y in range(2, 34):
        ins = insets.get(y, 2)
        for x in range(ins, SIZE - ins):
            inside.add((x, y))

    border: set[tuple[int, int]] = set()
    body: set[tuple[int, int]] = set()
    for x, y in inside:
        # 4-connected boundary
        if any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            border.add((x, y))
        else:
            body.add((x, y))

    # Subtle 1px inner rim around the inside edge (helps separation on black/dark wallpapers)
    inner_rim: set[tuple[int, int]] = set()
    for x, y in body:
        if any((x + dx, y + dy) in border for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            inner_rim.add((x, y))

    # Subtle top glass sheen (rows 4..9, inside inner_rim)
    top_sheen: set[tuple[int, int]] = {
        (x, y) for (x, y) in body if 4 <= y <= 9 and (x, y) not in inner_rim
    }

    return border, inner_rim, top_sheen, body


BORDER_MASK, INNER_RIM_MASK, TOP_SHEEN_MASK, BODY_MASK = build_squircle_masks()

# Minimalist monochromatic palette
GLASS_BODY_N = (16, 20, 28, 178)
GLASS_SHEEN_N = (34, 40, 52, 184)
GLASS_INNER_N = (255, 255, 255, 34)   # subtle light inner edge for dark themes
GLASS_BORDER_N = (10, 13, 19, 232)

GLASS_BODY_H = (32, 39, 52, 206)
GLASS_SHEEN_H = (52, 62, 78, 212)
GLASS_INNER_H = (255, 255, 255, 58)
GLASS_BORDER_H = (255, 255, 255, 228)

WHITE_N = hex_rgba("#EEF2F6")
WHITE_H = hex_rgba("#FFFFFF")
MID_N = hex_rgba("#9AA6B8")
MID_H = hex_rgba("#CBD5E1")
DIM_N = hex_rgba("#5A6678")
DIM_H = hex_rgba("#7A889C")
DARK_CUT = (12, 16, 24, 220)


class Canvas:
    def __init__(self, hover: bool):
        self.img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        self.px = self.img.load()
        self.hover = hover

        body_col = GLASS_BODY_H if hover else GLASS_BODY_N
        sheen_col = GLASS_SHEEN_H if hover else GLASS_SHEEN_N
        inner_col = GLASS_INNER_H if hover else GLASS_INNER_N
        border_col = GLASS_BORDER_H if hover else GLASS_BORDER_N

        for x, y in BODY_MASK:
            self.px[x, y] = body_col
        for x, y in TOP_SHEEN_MASK:
            self.px[x, y] = sheen_col
        for x, y in INNER_RIM_MASK:
            # Blend subtle inner highlight over body
            br, bg, bb, ba = body_col
            ir, ig, ib, ia = inner_col
            t = ia / 255.0
            self.px[x, y] = (
                round(br * (1 - t) + ir * t),
                round(bg * (1 - t) + ig * t),
                round(bb * (1 - t) + ib * t),
                min(255, ba + 14),
            )
        for x, y in BORDER_MASK:
            self.px[x, y] = border_col

    @property
    def w(self) -> tuple[int, int, int, int]:
        return WHITE_H if self.hover else WHITE_N

    @property
    def m(self) -> tuple[int, int, int, int]:
        return MID_H if self.hover else MID_N

    @property
    def d(self) -> tuple[int, int, int, int]:
        return DIM_H if self.hover else DIM_N

    @property
    def k(self) -> tuple[int, int, int, int]:
        return DARK_CUT

    def put(self, x: int, y: int, col: tuple[int, int, int, int]) -> None:
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.px[x, y] = col

    def rect(self, x0: int, y0: int, x1: int, y1: int, col: tuple[int, int, int, int]) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, col)

    def hline(self, x0: int, x1: int, y: int, col: tuple[int, int, int, int]) -> None:
        for x in range(x0, x1 + 1):
            self.put(x, y, col)

    def vline(self, x: int, y0: int, y1: int, col: tuple[int, int, int, int]) -> None:
        for y in range(y0, y1 + 1):
            self.put(x, y, col)

    def draw_bitmap(self, ox: int, oy: int, rows: list[str], color_map: dict[str, tuple[int, int, int, int]]) -> None:
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch in color_map:
                    self.put(ox + dx, oy + dy, color_map[ch])


# ---------------------------------------------------------------------------
# Minimalist monochromatic glyphs (21 apps)
# ---------------------------------------------------------------------------

def draw_app_store(c: Canvas) -> None:
    # Minimalist shopping bag with monochrome play triangle cutout
    art = [
        ".....WWWWWW.....",
        "....WW....WW....",
        "....WW....WW....",
        "....WW....WW....",
        "..WWWWWWWWWWWW..",
        ".WWWWWWWWWWWWWW.",
        "WWWWWWWWWWWWWWWW",
        "WWWWWWKWWWWWWWWW",
        "WWWWWWKKKWWWWWWW",
        "WWWWWWKKKKKWWWWW",
        "WWWWWWKKKKKKKWWW",
        "WWWWWWKKKKKWWWWW",
        "WWWWWWKKKWWWWWWW",
        "WWWWWWKWWWWWWWWW",
        "WWWWWWWWWWWWWWWW",
        "MMMMMMMMMMMMMMMM",
        ".MMMMMMMMMMMMMM.",
    ]
    c.draw_bitmap(10, 10, art, {"W": c.w, "M": c.m, "K": c.k})


def draw_pokedex(c: Canvas) -> None:
    # Minimalist Pokédex silhouette
    art = [
        "..WWWWWWWWWWWWWWWW..",
        ".WWWWWWWWWWWWWWWWWW.",
        ".WWKKKKWWWWWWWWWWWW.",
        ".WKKMMKKWWMWMWMWWWW.",
        ".WKKKKKKWWWWWWWWWWW.",
        ".WWKKKKWWWWWWWWWWWW.",
        ".WWWWWWWWWWWWWWWWWW.",
        ".WKKKKKKKKKKKKKKKKW.",
        ".WWWWWWWWWWWWWWWWWW.",
        ".WWKKKKKKKKWWWWWWWW.",
        ".WWKDDDDDDKWWWKKWWW.",
        ".WWKDDDDDDKWKKKKKKW.",
        ".WWKDDDDDDKWWWKKWWW.",
        ".WWKDDDDDDKWWWWWWWW.",
        ".WWKDDDDDDKWWKKKKWW.",
        ".WWKKKKKKKKWWWWWWWW.",
        ".WWWWWWWWWWWWWWWWWW.",
        ".MMMMMMMMMMMMMMMMMM.",
        "..MMMMMMMMMMMMMMMM..",
    ]
    c.draw_bitmap(8, 9, art, {"W": c.w, "M": c.m, "D": c.d, "K": c.k})


def draw_heal(c: Canvas) -> None:
    # Clean medical cross
    art = [
        "......WWWWWW......",
        "......WWWWWW......",
        "......WWWWWW......",
        "......WWWWWW......",
        "......WWWWWW......",
        "......WWWWWW......",
        "WWWWWWWWWWWWWWWWWW",
        "WWWWWWWWWWWWWWWWWW",
        "WWWWWWWWWWWWWWWWWW",
        "WWWWWWWWWWWWWWWWWW",
        "WWWWWWWWWWWWWWWWWW",
        "MMMMMMWWWWWWMMMMMM",
        "......WWWWWW......",
        "......WWWWWW......",
        "......WWWWWW......",
        "......WWWWWW......",
        "......WWWWWW......",
        "......MMMMMM......",
    ]
    c.draw_bitmap(9, 9, art, {"W": c.w, "M": c.m})


def draw_pc(c: Canvas) -> None:
    # Clean monitor with minimal Pokéball glyph on dark screen
    art = [
        ".WWWWWWWWWWWWWWWWWW.",
        "WWWWWWWWWWWWWWWWWWWW",
        "WWKKKKKKKKKKKKKKKKWW",
        "WWKDDDDDDDDDDDDDDKWW",
        "WWKDDDDDDDDDDDDDDKWW",
        "WWKDDDDDWWWWDDDDDKWW",
        "WWKDDDDDKWWKDDDDDKWW",
        "WWKDDDDDMMMMDDDDDKWW",
        "WWKDDDDDDDDDDDDDDKWW",
        "WWKDDDDDDDDDDDDDDKWW",
        "WWKKKKKKKKKKKKKKKKWW",
        "WWWWWWWWWWWWWWWWWWWW",
        ".MMMMMMMMMMMMMMMMMM.",
        ".......WWWWWW.......",
        ".......WWWWWW.......",
        "....WWWWWWWWWWWW....",
        "....MMMMMMMMMMMM....",
    ]
    c.draw_bitmap(8, 10, art, {"W": c.w, "M": c.m, "D": c.d, "K": c.k})


def draw_social(c: Canvas) -> None:
    # Crisp speech bubble
    art = [
        "..WWWWWWWWWWWWWWWW..",
        ".WWWWWWWWWWWWWWWWWW.",
        "WWWWWWWWWWWWWWWWWWWW",
        "WWWWWWWWWWWWWWWWWWWW",
        "WWWKKKKKKKKKKKKKKWWW",
        "WWWKKKKKKKKKKKKKKWWW",
        "WWWWWWWWWWWWWWWWWWWW",
        "WWWKKKKKKKKKKKKKKWWW",
        "WWWKKKKKKKKKKKKKKWWW",
        "WWWWWWWWWWWWWWWWWWWW",
        "WWWKKKKKKKKKKWWWWWWW",
        "WWWKKKKKKKKKKWWWWWWW",
        "WWWWWWWWWWWWWWWWWWWW",
        ".MMMMMMMMMMMMMMMMMM.",
        "..MMMMMMMMMMMWWWW...",
        ".............WWWW...",
        "..............WWW...",
        "...............MM...",
    ]
    c.draw_bitmap(8, 9, art, {"W": c.w, "M": c.m, "K": c.k})


def draw_gps(c: Canvas) -> None:
    # Folded map + clean location pin
    art = [
        ".......WWWWWW.......",
        "......WWWWWWWW......",
        ".....WWWKKKKWWW.....",
        ".....WWWKKKKWWW.....",
        ".....WWWWWWWWWW.....",
        "......WWWWWWWW......",
        ".......WWWWWW.......",
        "..WWWW..WWWW..WWWW..",
        ".WWWWWW..WW..WWWWWW.",
        "WWWWWWMM....MMWWWWWW",
        "WWWWWWMMWWWWMMWWWWWW",
        "WWWWWWMMWWWWMMWWWWWW",
        "WWWWWWMMWWWWMMWWWWWW",
        "WWWWWWMMWWWWMMWWWWWW",
        "WWWWWWMMWWWWMMWWWWWW",
        "WWWWWWMMWWWWMMWWWWWW",
        "MMMMMM..MMMM..MMMMMM",
        ".MMMM..........MMMM.",
    ]
    c.draw_bitmap(8, 9, art, {"W": c.w, "M": c.m, "K": c.k})


def draw_structure_compass(c: Canvas) -> None:
    # Faithful Structure Compass: crisp location pin (top-right) + creeper/structure head (bottom-left)
    art = [
        "........WWWWWW....",
        ".......WWWWWWWW...",
        "......WWWKKKKWWW..",
        "......WWKKKKKKWW..",
        "......WWKKKKKKWW..",
        "......WWWKKKKWWW..",
        ".......WWWWWWWW...",
        "........WWWWWW....",
        ".........WWWW.....",
        ".WWWWWWW..WW......",
        "WWWWWWWWW.MM......",
        "WWKKWKKWW.........",
        "WWKKWKKWW.........",
        "WWWWKWWWW.........",
        "WWWKWWKWW.........",
        "WWWKWWKWW.........",
        "WWWWWWWWW.........",
        ".MMMMMMM..........",
    ]
    c.draw_bitmap(9, 9, art, {"W": c.w, "M": c.m, "K": c.k})


def draw_crafting(c: Canvas) -> None:
    # Crisp 3x3 crafting grid
    art = [
        ".WWWWWWWWWWWWWWWW.",
        "WWWWWWWWWWWWWWWWWW",
        "WWKKKKWKKKKWKKKKWW",
        "WWKKKKWKKKKWKKKKWW",
        "WWKKKKWKKKKWKKKKWW",
        "WWKKKKWKKKKWKKKKWW",
        "WWWWWWWWWWWWWWWWWW",
        "WWKKKKWKKKKWKKKKWW",
        "WWKKKKWKKKKWKKKKWW",
        "WWKKKKWKKKKWKKKKWW",
        "WWKKKKWKKKKWKKKKWW",
        "WWWWWWWWWWWWWWWWWW",
        "WWKKKKWKKKKWKKKKWW",
        "WWKKKKWKKKKWKKKKWW",
        "WWKKKKWKKKKWKKKKWW",
        "WWKKKKWKKKKWKKKKWW",
        "MMMMMMMMMMMMMMMMMM",
        ".MMMMMMMMMMMMMMMM.",
    ]
    c.draw_bitmap(9, 9, art, {"W": c.w, "M": c.m, "K": c.k})


def draw_cloud(c: Canvas) -> None:
    # Clean minimalist cloud
    art = [
        "........WWWWWW........",
        "......WWWWWWWWWW......",
        ".....WWWWWWWWWWWW.....",
        "....WWWWWWWWWWWWWW....",
        "..WWWWWWWWWWWWWWWWWW..",
        ".WWWWWWWWWWWWWWWWWWWW.",
        "WWWWWWWWWWWWWWWWWWWWWW",
        "WWWWWWWWWWWWWWWWWWWWWW",
        "WWWWWWWWWWWWWWWWWWWWWW",
        "WWWWWWWWWWWWWWWWWWWWWW",
        "WWWWWWWWWWWWWWWWWWWWWW",
        ".MMMMMMMMMMMMMMMMMMMM.",
        "..MMMMMMMMMMMMMMMMMM..",
    ]
    c.draw_bitmap(7, 12, art, {"W": c.w, "M": c.m})


def draw_pokeinfo(c: Canvas) -> None:
    # Clean circle with "i"
    art = [
        ".....WWWWWWWW.....",
        "...WWWWWWWWWWWW...",
        "..WWWKKKKKKKKWWW..",
        ".WWKKKKWWWWKKKKWW.",
        ".WKKKKWWWWWWKKKKW.",
        "WWKKKKWWWWWWKKKKWW",
        "WKKKKKKWWWWKKKKKKW",
        "WKKKKKKKKKKKKKKKKW",
        "WKKKKKWWWWWWKKKKKW",
        "WKKKKKKWWWWKKKKKKW",
        "WKKKKKKWWWWKKKKKKW",
        "WKKKKKKWWWWKKKKKKW",
        "WKKKKKKWWWWKKKKKKW",
        "WWKKKKWWWWWWKKKKWW",
        ".WKKKKMMMMMMKKKKW.",
        ".MMKKKKKKKKKKKKMM.",
        "..MMMKKKKKKKKMM...",
        ".....MMMMMMMM.....",
    ]
    c.draw_bitmap(9, 9, art, {"W": c.w, "M": c.m, "K": c.k})


def draw_rotom(c: Canvas) -> None:
    # Faithful Rotom sprite silhouette: tall straight top spire, round body with
    # tall oval eyes & smile, pointed bottom tail, and wide Z-shaped lightning arms
    # extending downward-outward to both sides
    art = [
        "...........MM...........",
        "...........WW...........",
        "..........MWWM..........",
        "..........WWWW..........",
        ".........MWWWWM.........",
        "........WWWWWWWW........",
        ".......WWKWWWWKWW.......",
        ".......WKWKWWKWKW.......",
        ".......WKWKWWKWKW.......",
        ".......WWWWKKWWWW.......",
        "..MMMMM.WWWWWWWW.MMMMM..",
        ".MMWWWMMMWWWWWWMMMWWWMM.",
        "...MMWWMW.WWWW.WMWWMM...",
        "....MMWW..MWWM..WWMM....",
        "..MMWWMM...MM...MMWWMM..",
        ".MMWWMM....MM....MMWWMM.",
        "MMWWMM............MMWWMM",
        "MMMM...............MMMM.",
    ]
    c.draw_bitmap(6, 9, art, {"W": c.w, "M": c.m, "K": c.k})


def draw_cobbledollars(c: Canvas) -> None:
    # Bold monochrome CobbleDollar emblem
    art = [
        "......WWWWWWWWWW....",
        "....WWWWWWWWWWWWWW..",
        "...WWWWWWWWWWWWWWWW.",
        "..WWWWWW......WWWWWW",
        "..WWWWW........WWWWW",
        "WWWWWWWWWWW.........",
        "WWWWWWWWWWW.........",
        "..WWWWW.............",
        "..WWWWW.............",
        "WWWWWWWWWWW.........",
        "WWWWWWWWWWW.........",
        "..WWWWW........WWWWW",
        "..WWWWWW......WWWWWW",
        "...WWWWWWWWWWWWWWWW.",
        "....MMMMMMMMMMMMMM..",
        "......MMMMMMMMMM....",
    ]
    c.draw_bitmap(8, 10, art, {"W": c.w, "M": c.m})


def draw_pokenav(c: Canvas) -> None:
    # Faithful CobbleNav PokeNav handheld silhouette: top clasp + rounded oval body + center Pokeball button
    art = [
        "......WWWWWW......",
        "......WKKKKW......",
        "...WWWWKKKKWWWW...",
        "..WWWWWKKKKWWWWW..",
        ".WWWWWWKKKKWWWWWW.",
        ".WWWWWWWWWWWWWWWW.",
        "WWWWWWWWWWWWWWWWWW",
        "WWWWWWKKKKKKWWWWWW",
        "WWWWWKKWWWWKKWWWWW",
        "WWWWWKWWWWWWKWWWWW",
        "WWWWWKKKKKKKKWWWWW",
        "WWWWWKMMWWMMKWWWWW",
        "WWWWWKKMMMMKKWWWWW",
        "WWWWWWKKKKKKWWWWWW",
        ".WWWWWWWWWWWWWWWW.",
        ".WWWWWWWWWWWWWWWW.",
        "..WWWWWWWWWWWWWW..",
        "...MMMMMMMMMMMM...",
    ]
    c.draw_bitmap(9, 9, art, {"W": c.w, "M": c.m, "K": c.k})


def draw_fishnav(c: Canvas) -> None:
    # Faithful CobbleNav FishingNav silhouette: top-left antenna + top clasp + side grips + circular dial
    art = [
        "..WW..............",
        "..WW..WWWWWW......",
        "..WW.WWKKKKWW.....",
        "..WWWWWKKKKWWWWW..",
        "..WWWWWWWWWWWWWW..",
        ".WWWWWWWWWWWWWWWW.",
        "WWWWWWKKKKKKWWWWWW",
        "WWWWWKKWWWWKKWWWWW",
        "WWWWWKWWKKWWKWWWWW",
        "WWWWWKWWKKWWKWWWWW",
        "WWWWWKWWWWWWKWWWWW",
        "WWWWWKKWWWWKKWWWWW",
        "WWWWWWKKKKKKWWWWWW",
        ".WWWWWWWWWWWWWWWW.",
        "..WWWWWWWWWWWWWW..",
        "...WWWWWWWWWWWW...",
        "....MMMMMMMMMM....",
    ]
    c.draw_bitmap(9, 9, art, {"W": c.w, "M": c.m, "K": c.k})


def draw_trainer(c: Canvas) -> None:
    # Minimal Trainer ID Card
    art = [
        ".WWWWWWWWWWWWWWWWWW.",
        "WWWWWWWWWWWWWWWWWWWW",
        "WWWWWWWWWWWWWWWWWWWW",
        "WWKKKKKKWWMMMMMMMMWW",
        "WWKWWWWKWWWWWWWWWWWW",
        "WWKWKKWKWWKKKKKKKKWW",
        "WWKWWWWKWWWWWWWWWWWW",
        "WWKKKKKKWWKKKKKKWWWW",
        "WWWWWWWWWWWWWWWWWWWW",
        "WWWWWWWWWWWWWWWWWWWW",
        "WWKKKKKKKKKKKKKKWWWW",
        "WWWWWWWWWWWWWWWWWWWW",
        "MMMMMMMMMMMMMMMMMMMM",
        ".MMMMMMMMMMMMMMMMMM.",
    ]
    c.draw_bitmap(8, 11, art, {"W": c.w, "M": c.m, "K": c.k})


def draw_waystone(c: Canvas) -> None:
    # Faithful Ender Pearl / Warp Orb silhouette: crisp circular orb with centered
    # dark core and bright top-left + bottom-right specular crescent highlights
    art = [
        ".....KKKKKK.....",
        "...KKMMMMMMKK...",
        "..KMMWWWWMMMMK..",
        ".KMMWWMMMMDDDDK.",
        ".KMWWMDKKDDMDDK.",
        "KMMWMDKKKKDDMMMK",
        "KMMWMDKKKKDDMMMK",
        "KMMMMDKKKKDDMMMK",
        "KMMMMDKKKKDDMMMK",
        "KMMMDDKKKKDMMMMK",
        "KMMMDDDKKDDWMMMK",
        ".KDDDDMMMMWWMMK.",
        ".KDDDDMMMWWMMMK.",
        "..KMMMMMMWMMMK..",
        "...KKMMMMMMKK...",
        ".....KKKKKK.....",
    ]
    c.draw_bitmap(10, 10, art, {"W": c.w, "M": c.m, "D": c.d, "K": c.k})


def draw_patchouli(c: Canvas) -> None:
    # Minimalist guide book with emblem and bookmark
    art = [
        "..WWWWWWWWWWWWWW..",
        ".WWWWWWWWWWWWWWWW.",
        ".WMMWWWWWWWWWWMMW.",
        ".WMKKKKKKKKKKKKMW.",
        ".WMKKKKWWKKKKKKMW.",
        ".WMKKKKWWKKKKKKMW.",
        ".WMKKWWWWWWKKKKMW.",
        ".WMKKKKWWKKKKKKMW.",
        ".WMKKKKWWKKKKKKMW.",
        ".WMKKKKKKKKKKKKMW.",
        ".WMMKKKKKKKKKKMMW.",
        ".WMMWWWWWWWWWWMMW.",
        ".WWWWWWWWWWWWWWWW.",
        "..MMMMMMMMMMMMMM..",
        ".......WWWW.......",
        ".......MMMM.......",
        ".......M..M.......",
    ]
    c.draw_bitmap(9, 10, art, {"W": c.w, "M": c.m, "K": c.k})


def _draw_terminal_base(c: Canvas) -> None:
    art = [
        "..........WW....",
        "..........WW....",
        "..WWWWWWWWWWWW..",
        ".WWWWWWWWWWWWWW.",
        ".WWKKKKKKKKKKWW.",
        ".WWKDDDDDDDDKWW.",
        ".WWKDDDDDDDDKWW.",
        ".WWKDDDDDDDDKWW.",
        ".WWKDDDDDDDDKWW.",
        ".WWKDDDDDDDDKWW.",
        ".WWKKKKKKKKKKWW.",
        ".WWWWWWWWWWWWWW.",
        ".WWMMMMMMMMMMWW.",
        ".WWWWWWWWWWWWWW.",
        ".MMMMMMMMMMMMMM.",
        "..MMMMMMMMMMMM..",
    ]
    c.draw_bitmap(10, 10, art, {"W": c.w, "M": c.m, "D": c.d, "K": c.k})


def draw_ae2(c: Canvas) -> None:
    _draw_terminal_base(c)
    # Diamond / crystal glyph on screen
    c.rect(16, 16, 19, 18, c.w)
    c.rect(17, 15, 18, 19, c.w)


def draw_refinedstorage(c: Canvas) -> None:
    _draw_terminal_base(c)
    # Storage grid bars
    c.hline(15, 20, 16, c.w)
    c.hline(15, 20, 18, c.m)


def draw_toms_storage(c: Canvas) -> None:
    _draw_terminal_base(c)
    # Chest latch glyph
    c.rect(15, 16, 20, 18, c.m)
    c.rect(17, 16, 18, 18, c.w)


def draw_wireless(c: Canvas) -> None:
    _draw_terminal_base(c)
    # Signal bars
    c.vline(15, 18, 19, c.w)
    c.vline(17, 17, 19, c.w)
    c.vline(19, 16, 19, c.w)


APPS: list[tuple[str, Callable[[Canvas], None]]] = [
    ("app_store", draw_app_store),
    ("pokedex", draw_pokedex),
    ("heal", draw_heal),
    ("pc", draw_pc),
    ("social", draw_social),
    ("gps", draw_gps),
    ("structure_compass", draw_structure_compass),
    ("crafting", draw_crafting),
    ("cloud", draw_cloud),
    ("pokeinfo", draw_pokeinfo),
    ("rotom", draw_rotom),
    ("cobbledollars", draw_cobbledollars),
    ("pokenav", draw_pokenav),
    ("fishnav", draw_fishnav),
    ("trainer", draw_trainer),
    ("waystone", draw_waystone),
    ("patchouli", draw_patchouli),
    ("ae2", draw_ae2),
    ("refinedstorage", draw_refinedstorage),
    ("toms_storage", draw_toms_storage),
    ("wireless", draw_wireless),
]


def save_base_layers() -> None:
    """Export shared white/alpha squircle masks for dynamic Style B runtime tinting."""
    base_dir = BUTTONS_DIR / "base"
    base_dir.mkdir(parents=True, exist_ok=True)

    # 1. Body mask (pure white inside squircle body)
    body_img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    bpx = body_img.load()
    for x, y in BODY_MASK:
        bpx[x, y] = (255, 255, 255, 255)
    body_img.save(base_dir / "squircle_body.png", format="PNG")

    # 2. Sheen + inner rim mask (white with pre-baked subtle alpha)
    sheen_img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    spx = sheen_img.load()
    for x, y in TOP_SHEEN_MASK:
        spx[x, y] = (255, 255, 255, 32)
    for x, y in INNER_RIM_MASK:
        spx[x, y] = (255, 255, 255, 44)
    sheen_img.save(base_dir / "squircle_sheen.png", format="PNG")

    # 3. Border mask (pure white 1px outer border)
    border_img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    rpx = border_img.load()
    for x, y in BORDER_MASK:
        rpx[x, y] = (255, 255, 255, 255)
    border_img.save(base_dir / "squircle_border.png", format="PNG")


class GlyphCanvas(Canvas):
    """Transparent 36x36 canvas that only draws the monochrome glyph (no squircle)."""

    def __init__(self) -> None:
        self.img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        self.px = self.img.load()
        self.hover = False

    @property
    def w(self) -> tuple[int, int, int, int]:
        return (255, 255, 255, 255)

    @property
    def m(self) -> tuple[int, int, int, int]:
        return (188, 196, 206, 255)

    @property
    def d(self) -> tuple[int, int, int, int]:
        return (116, 126, 140, 255)

    @property
    def k(self) -> tuple[int, int, int, int]:
        return (24, 30, 40, 220)


def main() -> None:
    save_base_layers()
    glyphs_dir = BUTTONS_DIR / "glyphs"
    glyphs_dir.mkdir(parents=True, exist_ok=True)

    for stem, drawer in APPS:
        # Transparent glyph layer for runtime Style B tinting
        gc = GlyphCanvas()
        drawer(gc)
        gc.img.save(glyphs_dir / f"{stem}.png", format="PNG")

        # Fallback composed monochrome icons (normal + hover)
        for hover in (False, True):
            canvas = Canvas(hover=hover)
            drawer(canvas)
            suffix = "_hover" if hover else ""
            out_path = BUTTONS_DIR / f"{stem}{suffix}.png"
            canvas.img.save(out_path, format="PNG")
    print(f"Generated 3 base masks, {len(APPS)} glyph layers, and {len(APPS) * 2} fallback icons in {BUTTONS_DIR}")


if __name__ == "__main__":
    main()
