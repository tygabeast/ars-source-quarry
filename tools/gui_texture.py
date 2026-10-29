"""Source Quarry screen texture, drawn from scratch in the spirit of Ars Nouveau's parchment GUIs.

Sheet layout (256x256); coordinates are shared with SourceQuarryScreen.java and SourceQuarryMenu.java:
  background            (0, 0)     248 x 222
  small button          (0, 224)   12 x 12   normal, hover at (12, 224), disabled at (24, 224)
  wide button           (40, 224)  100 x 14  normal, hover at (40, 240), disabled at (142, 224)
  icon button           (142, 238) 18 x 18   normal, hover at (160, 238)
  progress fill         (180, 240) 1 x 5     (stretched)
  source fill           (184, 240) 1 x 5     (stretched)
  bedrock icon          (190, 240) 8 x 8
"""
from PIL import Image, ImageDraw

W, H = 248, 222

PAPER = [(226, 211, 176), (236, 224, 194), (243, 234, 210), (248, 242, 224)]
INK = (120, 92, 58)
INK_LIGHT = (196, 172, 128)
SLOT = (214, 196, 156)
SLOT_DARK = (190, 168, 124)
SLOT_LIGHT = (232, 218, 186)
FRAME = [(36, 28, 56), (58, 44, 86), (82, 62, 112), (110, 86, 140)]
GOLD = [(150, 112, 30), (204, 166, 52), (220, 197, 69), (234, 222, 110)]   # Ars-style yellow gold, as on the blocks
SOURCE = [(92, 22, 124), (160, 60, 210), (214, 120, 255)]

# Layout shared with the Java screen and menu
SLOT_GLYPHS = [(130 + i * 18, 31) for i in range(5)]
SLOT_VOID = [(130 + i * 18, 73) for i in range(6)]
INV_X, INV_Y, HOTBAR_Y = 43, 140, 198
PROGRESS = (10, 112, 130, 5)
SOURCE_BAR = (10, 121, 130, 5)
ICON_BUTTONS = [(176, 103), (198, 103), (220, 103)]
VOID_BUTTONS = [(214, 60), (228, 60)]
APPLY = (10, 80)
BEDROCK_BTN = (96, 65)
COLUMN_X = 126


def h(x, y, seed=0):
    v = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    v = (v ^ (v >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((v ^ (v >> 16)) & 0xFFFF) / 65535.0


def rounded_mask(w, hgt, r):
    m = Image.new("L", (w, hgt), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w - 1, hgt - 1], r, fill=255)
    return m


def paper(img, x0, y0, x1, y1):
    px = img.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = 0.6 * h(x // 3, y // 3, 4) + 0.4 * h(x // 7, y // 5, 9)
            i = 1 + int(n * 2.99)
            edge = min(x - x0, x1 - 1 - x, y - y0, y1 - 1 - y)
            if edge < 3:   # slightly darker towards the page edges, like aged paper
                i -= 1
            px[x, y] = PAPER[max(0, min(3, i))] + (255,)


def slot(img, x, y):
    """18x18 flat Ars-style slot with a soft inner shadow."""
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + 17, y + 17], fill=SLOT + (255,))
    d.line([x, y, x + 17, y], fill=SLOT_DARK + (255,))
    d.line([x, y, x, y + 17], fill=SLOT_DARK + (255,))
    d.line([x + 1, y + 17, x + 17, y + 17], fill=SLOT_LIGHT + (255,))
    d.line([x + 17, y + 1, x + 17, y + 17], fill=SLOT_LIGHT + (255,))


def divider(img, x0, x1, y, icon=None):
    """Thin dashed ink rule with curled ends."""
    px = img.load()
    for x in range(x0 + 4, x1 - 4):
        if (x // 2) % 6 != 5:
            px[x, y] = INK_LIGHT + (255,)
    for (cx, s) in ((x0, 1), (x1 - 1, -1)):
        for dx, dy in ((0, 0), (s, -1), (2 * s, -1), (3 * s, 0), (2 * s, 1)):
            px[cx + dx, y + dy] = INK + (255,)
    if icon == "dot":
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            px[(x0 + x1) // 2 + dx, y - 1 + dy] = GOLD[1] + (255,)


def frame(img):
    """Sourcestone-violet frame with a gold inner line and gold corner caps."""
    base = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = base.load()
    for y in range(H):
        for x in range(W):
            px[x, y] = FRAME[1 + int(h(x // 2, y // 2, 1) * 1.99)] + (255,)
    for i in range(W):
        px[i, 0] = FRAME[3] + (255,)
        px[i, H - 1] = FRAME[0] + (255,)
    for i in range(H):
        px[0, i] = FRAME[3] + (255,)
        px[W - 1, i] = FRAME[0] + (255,)
    d = ImageDraw.Draw(base)
    d.rounded_rectangle([4, 4, W - 5, H - 5], 3, outline=GOLD[1] + (255,))
    d.rounded_rectangle([3, 3, W - 4, H - 4], 4, outline=GOLD[0] + (255,))
    img.alpha_composite(base)
    mask = rounded_mask(W, H, 5)
    img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (W, H), 0), mask))
    cpx = img.load()
    for (cx, cy) in ((6, 6), (W - 7, 6), (6, H - 7), (W - 7, H - 7)):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                c = GOLD[3] if dx == dy == 0 else GOLD[2] if dx == 0 or dy == 0 else GOLD[1]
                cpx[cx + dx, cy + dy] = c + (255,)


def background():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    frame(img)
    paper(img, 6, 6, W - 6, H - 6)
    divider(img, 8, W - 8, 19, "dot")
    px = img.load()
    for y in range(23, 95):   # area column | glyph column
        if (y // 2) % 5 != 4:
            px[COLUMN_X, y] = INK_LIGHT + (255,)
    divider(img, 8, W - 8, 96)
    divider(img, 8, W - 8, 132, "dot")
    for (x, y) in SLOT_GLYPHS + SLOT_VOID:
        slot(img, x, y)
    for r in range(3):
        for c in range(9):
            slot(img, INV_X + c * 18, INV_Y + r * 18)
    for c in range(9):
        slot(img, INV_X + c * 18, HOTBAR_Y)
    d = ImageDraw.Draw(img)
    for (x, y, w, hh) in (PROGRESS, SOURCE_BAR):
        d.rectangle([x - 1, y - 1, x + w, y + hh], fill=SLOT_DARK + (255,))
        d.rectangle([x, y, x + w - 1, y + hh - 1], fill=SLOT + (255,))
    return img


def button(w, hgt, state):
    """state: 0 normal, 1 hover, 2 disabled."""
    img = Image.new("RGBA", (w, hgt), (0, 0, 0, 0))
    fill = [FRAME[2], FRAME[3], (150, 140, 150)][state]
    edge = [GOLD[1], GOLD[2], (170, 160, 150)][state]
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, w - 1, hgt - 1], 2, fill=edge + (255,))
    d.rounded_rectangle([1, 1, w - 2, hgt - 2], 1, fill=fill + (255,))
    d.line([2, 1, w - 3, 1], fill=tuple(min(255, c + 30) for c in fill) + (255,))
    return img


def sheet():
    s = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    s.alpha_composite(background(), (0, 0))
    for i in range(3):
        s.alpha_composite(button(12, 12, i), (i * 12, 224))
    s.alpha_composite(button(100, 14, 0), (40, 224))
    s.alpha_composite(button(100, 14, 1), (40, 240))
    s.alpha_composite(button(100, 14, 2), (142, 224))
    s.alpha_composite(button(18, 18, 0), (142, 238))
    s.alpha_composite(button(18, 18, 1), (160, 238))
    d = ImageDraw.Draw(s)
    for y in range(5):
        d.point((180, 240 + y), fill=(GOLD[2] if y < 2 else GOLD[1]) + (255,))
        d.point((184, 240 + y), fill=(SOURCE[2] if y < 2 else SOURCE[1] if y < 4 else SOURCE[0]) + (255,))
    # "dig to bedrock" icon: arrow pointing down onto a bar
    ic = (255, 240, 210, 255)
    for y in range(0, 4):
        d.point((193, 240 + y), fill=ic)
        d.point((194, 240 + y), fill=ic)
    for i, w in enumerate((3, 2, 1)):
        d.line([(193 - w + 1, 244 + i), (194 + w - 1, 244 + i)], fill=ic)
    d.line([(190, 247), (197, 247)], fill=GOLD[2] + (255,))
    return s
