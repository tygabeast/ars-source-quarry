"""Tiny software renderer for block models (orthographic, textured, per-face shading).

Used for the mod icon and for texture previews. Reads the mod's own generated model JSON and
textures. Textures from other mods (ars_nouveau:...) are never copied: a look-alike stand-in drawn
by textures.py is used in their place.
"""
import json
import math
import os

import numpy as np
from PIL import Image

import textures

SHADE = {"up": 1.0, "down": 0.55, "north": 0.8, "south": 0.8, "east": 0.68, "west": 0.68}


def load_texture(ref, assets):
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    if ns != "ars_source_quarry":
        return np.asarray(textures.stone_base(textures.SST, 9).convert("RGBA"), dtype=np.float32) / 255.0
    p = os.path.join(assets, "textures", path + ".png")
    return np.asarray(Image.open(p).convert("RGBA"), dtype=np.float32) / 255.0


def load_model(name, assets):
    """name is a block model, or "item:<name>" for an item model (the marker's item model has its gem)."""
    folder = "block"
    if name.startswith("item:"):
        folder, name = "item", name[5:]
    with open(os.path.join(assets, "models", folder, name + ".json")) as f:
        m = json.load(f)
    tex = m.get("textures", {})
    if m.get("parent") == "minecraft:block/cube_bottom_top":
        faces = {s: {"texture": "#side"} for s in ("north", "south", "east", "west")}
        faces["up"] = {"texture": "#top"}
        faces["down"] = {"texture": "#bottom"}
        elements = [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]
    else:
        elements = m["elements"]

    def resolve(t):
        while t.startswith("#"):
            t = tex[t[1:]]
        return t
    return elements, resolve


def face_quad(side, f, t):
    """Corners (top-left, top-right, bottom-right, bottom-left) of a face, in model space."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[side]


def default_uv(side, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    if side in ("north", "south"):
        return [x0, 16 - y1, x1, 16 - y0]
    if side in ("east", "west"):
        return [z0, 16 - y1, z1, 16 - y0]
    return [x0, z0, x1, z1]


def rotate(p, rot):
    if not rot:
        return p
    a = math.radians(rot["angle"])
    ox, oy, oz = rot["origin"]
    x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
    c, s = math.cos(a), math.sin(a)
    if rot["axis"] == "y":
        x, z = x * c + z * s, -x * s + z * c
    elif rot["axis"] == "x":
        y, z = y * c - z * s, y * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + ox, y + oy, z + oz)


def render_models(placements, assets, size=512, yaw=-35, pitch=28, scale=None, ss=3):
    """placements: list of (model_name, offset_xyz_in_blocks). Returns an RGBA PIL image."""
    W = size * ss
    color = np.zeros((W, W, 4), np.float32)
    depth = np.full((W, W), np.inf, np.float32)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def project(p):
        x, y, z = p
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp + z * sp, -y * sp + z * cp   # tilt so the camera looks down onto top faces
        return x, -y, z

    quads = []
    for name, off in placements:
        elements, resolve = load_model(name, assets)
        for el in elements:
            for side, fd in el["faces"].items():
                pts = face_quad(side, el["from"], el["to"])
                pts = [rotate(p, el.get("rotation")) for p in pts]
                pts = [((p[0] - 8) / 16 + off[0], (p[1]) / 16 + off[1], (p[2] - 8) / 16 + off[2]) for p in pts]
                uv = fd.get("uv") or default_uv(side, el["from"], el["to"])
                tex = load_texture(resolve(fd["texture"]), assets)
                glow = "neoforge_data" in el
                quads.append(([project(p) for p in pts], uv, tex, 1.0 if glow else SHADE[side]))

    xs = [p[0] for q in quads for p in q[0]]
    ys = [p[1] for q in quads for p in q[0]]
    cx, cyy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    span = max(max(xs) - min(xs), max(ys) - min(ys))
    k = (scale or 0.86) * W / span

    for pts, uv, tex, shade in quads:
        P = np.array([[(p[0] - cx) * k + W / 2, (p[1] - cyy) * k + W / 2, p[2]] for p in pts])
        u0, v0, u1, v1 = uv
        UV = np.array([[u0, v0], [u1, v0], [u1, v1], [u0, v1]], np.float32) / 16.0
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster(color, depth, P[list(tri)], UV[list(tri)], tex, shade)

    img = Image.fromarray((np.clip(color, 0, 1) * 255).astype(np.uint8), "RGBA")
    return img.resize((size, size), Image.LANCZOS)


def raster(color, depth, P, UV, tex, shade):
    W = color.shape[0]
    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = P
    den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    if abs(den) < 1e-9:
        return
    minx, maxx = max(int(min(x0, x1, x2)), 0), min(int(max(x0, x1, x2)) + 1, W)
    miny, maxy = max(int(min(y0, y1, y2)), 0), min(int(max(y0, y1, y2)) + 1, W)
    if minx >= maxx or miny >= maxy:
        return
    gx, gy = np.meshgrid(np.arange(minx, maxx) + 0.5, np.arange(miny, maxy) + 0.5)
    a = ((y1 - y2) * (gx - x2) + (x2 - x1) * (gy - y2)) / den
    b = ((y2 - y0) * (gx - x2) + (x0 - x2) * (gy - y2)) / den
    c = 1 - a - b
    inside = (a >= -1e-6) & (b >= -1e-6) & (c >= -1e-6)
    if not inside.any():
        return
    z = a * z0 + b * z1 + c * z2
    u = a * UV[0, 0] + b * UV[1, 0] + c * UV[2, 0]
    v = a * UV[0, 1] + b * UV[1, 1] + c * UV[2, 1]
    th, tw = tex.shape[:2]
    ti = np.clip((v * th).astype(int), 0, th - 1)
    tj = np.clip((u * tw).astype(int), 0, tw - 1)
    texel = tex[ti, tj]
    sub_d = depth[miny:maxy, minx:maxx]
    sub_c = color[miny:maxy, minx:maxx]
    m = inside & (texel[..., 3] > 0.1) & (z < sub_d)
    sub_d[m] = z[m]
    rgb = texel[..., :3] * shade
    sub_c[..., :3][m] = rgb[m]
    sub_c[..., 3][m] = 1.0


ICON_SCENE = [("source_quarry", (0, 0, 0)),
              ("item:ley_marker", (1.15, 0, 0.15))]


def mod_icon(assets, _unused=None):
    """In-jar logo: the quarry with a Ley Marker beside it, transparent 512x512."""
    return render_models(ICON_SCENE, assets, size=512, yaw=145, pitch=26)


def curseforge_icon(assets, size=512):
    """Square project icon for CurseForge: the same scene, larger, on a deep violet backdrop with a soft
    Source glow and a faint gold ring. Everything important sits inside the central circle, so the icon
    still reads when a site crops it round."""
    S = size * 2
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    r = np.hypot(xx - S / 2, yy - S / 2) / (S / 2)
    deep = np.array([22, 14, 38], np.float32); mid = np.array([58, 30, 92], np.float32)
    glow = np.array([150, 70, 200], np.float32)
    t = np.clip(r, 0, 1)[..., None]
    rgb = mid * (1 - t) + deep * t
    g = np.exp(-((r / 0.42) ** 2))[..., None]          # Source glow behind the quarry
    rgb = rgb * (1 - 0.55 * g) + glow * 0.55 * g
    ring = np.exp(-(((r - 0.86) / 0.012) ** 2))[..., None]   # thin gold ring
    rgb = rgb * (1 - 0.6 * ring) + np.array([220, 197, 69], np.float32) * 0.6 * ring
    rng = np.random.default_rng(7)                       # a few Source motes
    for _ in range(26):
        ang, rad = rng.uniform(0, 2 * np.pi), rng.uniform(0.25, 0.8)
        cx, cy = S / 2 + np.cos(ang) * rad * S / 2, S / 2 + np.sin(ang) * rad * S / 2
        d = np.hypot(xx - cx, yy - cy)
        m = np.exp(-((d / (S * rng.uniform(0.004, 0.009))) ** 2))[..., None]
        rgb = rgb * (1 - 0.8 * m) + np.array([240, 172, 255], np.float32) * 0.8 * m
    bg = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    scene = render_models(ICON_SCENE, assets, size=int(S * 0.78), yaw=145, pitch=26)
    bg.alpha_composite(scene, (int(S * 0.11), int(S * 0.1)))
    return bg.resize((size, size), Image.LANCZOS).convert("RGB")
