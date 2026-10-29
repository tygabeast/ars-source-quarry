"""Procedural textures for Ars Source Quarry (drawn from scratch, no third-party art).

Style follows Ars Nouveau's blocks without copying them: soft low-contrast sourcestone with carved spirals,
a smooth mauve tabletop, a deep purple runner with gold border lines and interlocking square spirals,
gold fittings and glowing Source. Noise is deterministic so regenerating is byte-stable.
"""
from PIL import Image

SST = [(44, 36, 64), (56, 46, 78), (64, 53, 88), (72, 60, 97), (86, 72, 112)]             # sourcestone
SMOOTH = [(88, 64, 104), (104, 78, 120), (116, 88, 132), (128, 98, 144), (148, 116, 162)]  # smooth sourcestone
CLOTH = [(56, 16, 78), (80, 26, 108), (104, 40, 138), (132, 60, 168)]                    # runner
GOLD = [(150, 112, 30), (204, 166, 52), (220, 197, 69), (234, 222, 110), (252, 248, 168)]   # Ars-style yellow gold
PARCHMENT = [(176, 150, 108), (206, 182, 138), (228, 210, 170), (242, 230, 200)]
SRC = [(92, 20, 128), (150, 46, 196), (204, 96, 246), (240, 172, 255), (255, 240, 255)]  # Source glow

SPIRAL = ["1111", "0001", "0101", "0111"]   # small square spiral carved into stone


def h(x, y, seed):
    """Small deterministic hash -> 0..1."""
    v = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    v = (v ^ (v >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((v ^ (v >> 16)) & 0xFFFF) / 65535.0


def img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def carve(px, ox, oy, color, flip=False):
    for y in range(4):
        for x in range(4):
            if SPIRAL[y][3 - x if flip else x] == "1":
                px[ox + x, oy + y] = color + (255,)


def stone_base(ramp, seed):
    """Soft stone: low-contrast 2x2 clusters, very few specks."""
    im = img(); px = im.load()
    for y in range(16):
        for x in range(16):
            n = 0.75 * h(x // 2, y // 2, seed) + 0.25 * h(x, y, seed + 3)
            i = 1 + int(n * 2.999)
            if h(x, y, seed + 9) > 0.96:
                i = min(4, i + 1)
            px[x, y] = ramp[i] + (255,)
    return im


def bricks():
    """Large carved sourcestone bricks: two 8px courses, offset, with faint spiral carvings."""
    im = stone_base(SST, 92); px = im.load()
    for y in range(16):
        for x in range(16):
            off = 4 if y // 8 else 0
            if y % 8 == 7 or (x + off) % 8 == 7:
                px[x, y] = SST[0] + (255,)
            elif y % 8 == 0 or (x + off) % 8 == 0:
                px[x, y] = SST[3] + (255,)
    carve(px, 2, 2, SST[1])
    carve(px, 2, 10, SST[1], True)
    carve(px, 10, 10, SST[1])
    return im


def smooth_top():
    """Smooth sourcestone tabletop: soft mauve, bevelled edge, faint carved spiral."""
    im = stone_base(SMOOTH, 91); px = im.load()
    for i in range(16):
        px[i, 0] = SMOOTH[4] + (255,); px[0, i] = SMOOTH[3] + (255,)
        px[i, 15] = SMOOTH[0] + (255,); px[15, i] = SMOOTH[0] + (255,)
    carve(px, 6, 6, SMOOTH[1])
    return im


def cloth():
    """Purple runner: gold border lines with interlocking square spirals between them, drawn across the
    band and rotated so the pattern runs along the runner's length."""
    im = img(); px = im.load()
    for y in range(16):
        for x in range(16):
            n = 0.7 * h(x // 2, y // 2, 61) + 0.3 * h(x, y, 62)
            px[x, y] = CLOTH[1 + (1 if n > 0.62 else 0)] + (255,)
    g1, g2 = GOLD[3], GOLD[2]
    for x in range(16):
        px[x, 1] = g1 + (255,)
        px[x, 14] = g2 + (255,)
    hook = ["111111", "000001", "011101", "010101", "010001", "011111"]
    for y in range(6):
        for x in range(6):
            if hook[y][x] == "1":
                px[1 + x, 2 + y] = g1 + (255,)       # hangs from the top line
                px[14 - x, 13 - y] = g2 + (255,)     # rises from the bottom line, turned round
    return im.rotate(90)


def cloth_across():
    """The same runner pattern running left to right, for the tabletop of the east-west runner."""
    return cloth().rotate(-90)


def gold():
    im = img(); px = im.load()
    for y in range(16):
        for x in range(16):
            i = 2 + (1 if h(x // 2, y // 2, 71) > 0.6 else 0)
            if x == 0 or y == 0:
                i = 3
            if x == 15 or y == 15:
                i = 1
            px[x, y] = GOLD[i] + (255,)
    return im


def crystal():
    """Faceted Source crystal, lit from the left."""
    im = img(); px = im.load()
    for y in range(16):
        for x in range(16):
            i = 3 if x < 6 else 2 if x < 11 else 1
            if h(x // 2, y // 3, 81) > 0.85:
                i -= 1
            px[x, y] = SRC[max(0, i)] + (255,)
    for y in range(16):
        px[5, y] = SRC[4] + (255,)
        px[15, y] = SRC[0] + (255,)
    return im


def orb():
    """Glowing Source orb / pool: bright core fading outwards."""
    im = img(); px = im.load()
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            px[x, y] = SRC[4 if r < 3 else 3 if r < 5.5 else 2] + (255,)
    return im


def parchment():
    """Rolled parchment for the scroll on the shelf, with two gold ties."""
    im = img(); px = im.load()
    for y in range(16):
        for x in range(16):
            i = 2 if h(x, y // 2, 101) > 0.3 else 1
            if y in (0, 15):
                i = 0
            if y in (1, 14):
                i = 3
            px[x, y] = PARCHMENT[i] + (255,)
    for y in range(16):
        for x in (3, 12):
            px[x, y] = GOLD[2] + (255,)
    return im


def shelf():
    """Shelf board: smooth sourcestone with a darker rim so it does not read as a flat slab."""
    im = stone_base(SMOOTH, 111); px = im.load()
    for i in range(16):
        for (x, y) in ((i, 0), (0, i), (i, 15), (15, i)):
            px[x, y] = SMOOTH[0] + (255,)
        for (x, y) in ((i, 1), (1, i)):
            if 1 <= x <= 14 and 1 <= y <= 14:
                px[x, y] = SMOOTH[4] + (255,)
    return im


GEM_SHADES = [(244, 224, 255), (214, 172, 250), (180, 128, 238), (144, 92, 212)]   # pale to deep violet

# Four 2x2 face patterns: a diagonal fall-off with the bright corner turned differently per face.
GEM_CELLS = [
    [[0, 1], [1, 2]],
    [[1, 0], [2, 1]],
    [[1, 2], [2, 3]],
    [[2, 1], [3, 2]],
]


def gem():
    """4x4 sheet holding four 2x2 face patterns for the spinning marker gem (one cell per face group)."""
    im = Image.new("RGBA", (4, 4), (0, 0, 0, 0)); px = im.load()
    for c, cell in enumerate(GEM_CELLS):
        ox, oy = (c % 2) * 2, (c // 2) * 2
        for y in range(2):
            for x in range(2):
                px[ox + x, oy + y] = GEM_SHADES[cell[y][x]] + (255,)
    return im


def marker_gold():
    """Speckled gold for the Ley Marker, in the manner of Ars Nouveau's magelight gold: mostly a mid
    yellow broken up by lighter and darker pixels, no hard edges."""
    im = img(); px = im.load()
    for y in range(16):
        for x in range(16):
            n = 0.55 * h(x, y, 131) + 0.45 * h(x // 2, y // 2, 132)
            i = 3
            if n > 0.82:
                i = 4
            elif n < 0.18:
                i = 1
            elif n < 0.42:
                i = 2
            px[x, y] = GOLD[i] + (255,)
    return im


def all_textures():
    return {
        "block/sourcestone_bricks": bricks(),
        "block/quarry_top": smooth_top(),
        "block/quarry_cloth": cloth(),
        "block/quarry_cloth_top": cloth_across(),
        "block/gold_trim": gold(),
        "block/source_crystal": crystal(),
        "block/source_orb": orb(),
        "block/source_gem": gem(),
        "block/marker_gold": marker_gold(),
        "block/parchment": parchment(),
        "block/quarry_shelf": shelf(),
    }
