#!/usr/bin/env python3
"""Regenerates every resource of Ars Source Quarry into src/main/resources.

Run from the project root:  python3 tools/gen.py
"""
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import textures  # noqa: E402
import render  # noqa: E402
import gui_texture  # noqa: E402

MOD = "ars_source_quarry"
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", MOD)
DATA = os.path.join(RES, "data", MOD)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def clean():
    for d in (os.path.join(RES, "assets"), os.path.join(RES, "data")):
        if os.path.isdir(d):
            shutil.rmtree(d)


# ---------------------------------------------------------------- models
GLOW = {"block_light": 15, "sky_light": 15}   # full-bright elements (the Source parts)


def faces(tex, uv=None, sides=("north", "south", "east", "west", "up", "down")):
    return {s: ({"texture": tex, "uv": uv} if uv else {"texture": tex}) for s in sides}


def el(a, b, f, glow=False, rot=None):
    e = {"from": a, "to": b, "faces": f}
    if glow:
        e["neoforge_data"] = GLOW
    if rot:
        e["rotation"] = rot
    return e


def quarry_model():
    """A one-block sourcestone table in the spirit of Ars Nouveau's Scribe's Table. Built facing north:
    the front (north) and back stay open, the runner lies east-west and drapes over the left and right
    sides. The blockstate turns it so the front faces the player who placed it."""
    r45 = {"origin": [8, 7, 8], "axis": "y", "angle": 45, "rescale": False}
    els = [
        # smooth tabletop with a cloth runner across it, hanging down the left and right sides
        el([0, 13, 0], [16, 16, 16], faces("#top")),
        el([0, 16, 4], [16, 16.2, 12], faces("#cloth_top", [0, 0, 16, 16], ("up",))),
        el([-0.2, 7, 4], [0, 16.2, 12], faces("#cloth", [0, 0, 16, 9], ("west", "north", "south"))),
        el([16, 7, 4], [16.2, 16.2, 12], faces("#cloth", [0, 0, 16, 9], ("east", "north", "south"))),
        el([-0.3, 6.4, 4], [0.1, 7, 12], faces("#gold")),
        el([15.9, 6.4, 4], [16.3, 7, 12], faces("#gold")),
        # gold corner fittings
        *[el([x, 12.8, z], [x + 2.2, 16.1, z + 2.2], faces("#gold")) for x in (-0.1, 13.9) for z in (-0.1, 13.9)],
        # apron and four legs with gold feet
        el([1, 10, 1], [15, 13, 15], faces("#bricks")),
        *[el([x, 0, z], [x + 3, 10, z + 3], faces("#bricks", [0, 0, 3, 10])) for x in (1, 12) for z in (1, 12)],
        *[el([x - 0.2, 0, z - 0.2], [x + 3.2, 1, z + 3.2], faces("#gold")) for x in (1, 12) for z in (1, 12)],
        # lower shelf with raised lips front and back, and a few things on it
        el([2, 3, 2], [14, 4, 14], faces("#shelf")),
        el([2, 4, 2], [14, 4.6, 2.8], faces("#bricks", [2, 0, 14, 1])),
        el([2, 4, 13.2], [14, 4.6, 14], faces("#bricks", [2, 0, 14, 1])),
        el([3.5, 4, 10], [9.5, 5.4, 11.4], faces("#parchment")),                    # rolled scroll
        el([10, 4, 4.5], [11.4, 6.6, 5.9], faces("#crystal", [5, 2, 7, 8]), glow=True),   # small Source cluster
        el([11.2, 4, 5.4], [12.2, 5.6, 6.4], faces("#crystal", [5, 4, 7, 8]), glow=True),
        el([9.6, 4, 5.8], [10.4, 5.2, 6.6], faces("#crystal", [5, 5, 7, 8]), glow=True),
        # Source crystal hanging under the tabletop, pointing at the ground it mines
        el([6.5, 6, 6.5], [9.5, 10, 9.5], faces("#crystal", [5, 1, 11, 11]), glow=True, rot=r45),
        el([7.25, 4.6, 7.25], [8.75, 6, 8.75], faces("#crystal", [5, 0, 8, 4]), glow=True, rot=r45),
        # gold bowl on top holding a pool of Source
        el([5.5, 16.2, 5.5], [10.5, 17.2, 10.5], faces("#gold")),
        el([6.5, 16.3, 6.5], [9.5, 17.3, 9.5], faces("#orb", [4, 4, 12, 12], ("up",)), glow=True),
    ]
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": f"{MOD}:block/sourcestone_bricks", "top": f"{MOD}:block/quarry_top",
                         "bricks": f"{MOD}:block/sourcestone_bricks", "cloth": f"{MOD}:block/quarry_cloth",
                         "cloth_top": f"{MOD}:block/quarry_cloth_top", "gold": f"{MOD}:block/gold_trim",
                         "crystal": f"{MOD}:block/source_crystal", "orb": f"{MOD}:block/source_orb",
                         "shelf": f"{MOD}:block/quarry_shelf", "parchment": f"{MOD}:block/parchment"},
            "elements": els}


def marker_model(item=False):
    """Magelight-style marker: sourcestone foot, two gold prongs, a floating Source orb."""
    els = [
        el([6.5, 0, 6.5], [9.5, 2, 9.5], faces("#bricks", [6, 0, 9, 2])),
        el([6, 2, 6], [10, 3, 10], faces("#mgold")),
        el([6.25, 3, 7.5], [7.25, 4, 8.5], faces("#mgold")),
        el([5.25, 3.5, 7.5], [6.25, 10, 8.5], faces("#mgold")),
        el([6.25, 10, 7.5], [7.25, 11, 8.5], faces("#mgold")),
        el([8.75, 3, 7.5], [9.75, 4, 8.5], faces("#mgold")),
        el([9.75, 3.5, 7.5], [10.75, 10, 8.5], faces("#mgold")),
        el([8.75, 10, 7.5], [9.75, 11, 8.5], faces("#mgold")),
    ]
    # The crystal itself is drawn spinning by MarkerRenderer; the item model gets a still one.
    if item:
        rot = {"origin": [8, 6.75, 8], "axis": "y", "angle": 45, "rescale": False}
        els.append(el([7, 5.75, 7], [9, 7.75, 9], {
            "north": {"texture": "#gem", "uv": [8, 0, 16, 8]}, "south": {"texture": "#gem", "uv": [0, 8, 8, 16]},
            "east": {"texture": "#gem", "uv": [8, 8, 16, 16]}, "west": {"texture": "#gem", "uv": [0, 0, 8, 8]},
            "up": {"texture": "#gem", "uv": [0, 0, 8, 8]}, "down": {"texture": "#gem", "uv": [8, 8, 16, 16]}},
            glow=True, rot=rot))
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": f"{MOD}:block/source_gem", "bricks": f"{MOD}:block/sourcestone_bricks",
                         "mgold": f"{MOD}:block/marker_gold", "gem": f"{MOD}:block/source_gem"},
            "elements": els}


def models():
    m = os.path.join(ASSETS, "models")
    write(os.path.join(m, "block", "source_quarry.json"), quarry_model())
    write(os.path.join(m, "block", "ley_marker.json"), marker_model())
    write(os.path.join(m, "item", "source_quarry.json"), {"parent": f"{MOD}:block/source_quarry"})
    item = marker_model(item=True)
    item["parent"] = "minecraft:block/block"
    write(os.path.join(m, "item", "ley_marker.json"), item)
    write(os.path.join(ASSETS, "blockstates", "ley_marker.json"),
          {"variants": {"": {"model": f"{MOD}:block/ley_marker"}}})
    # the quarry's front faces the player who placed it
    write(os.path.join(ASSETS, "blockstates", "source_quarry.json"),
          {"variants": {f"facing={d}": ({"model": f"{MOD}:block/source_quarry", "y": r} if r else
                                        {"model": f"{MOD}:block/source_quarry"})
                        for d, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})


def texture_files():
    out = os.path.join(ASSETS, "textures")
    for name, img in textures.all_textures().items():
        p = os.path.join(out, name + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p, optimize=True)
    p = os.path.join(out, "gui", "source_quarry.png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    gui_texture.sheet().save(p, optimize=True)


# ---------------------------------------------------------------- data
def loot(block):
    return {"type": "minecraft:block", "random_sequence": f"{MOD}:blocks/{block}",
            "pools": [{"rolls": 1, "bonus_rolls": 0,
                       "entries": [{"type": "minecraft:item", "name": f"{MOD}:{block}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


def data():
    for b in ("source_quarry", "ley_marker"):
        write(os.path.join(DATA, "loot_table", "blocks", f"{b}.json"), loot(b))

    write(os.path.join(RES, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"),
          {"values": [f"{MOD}:source_quarry", f"{MOD}:ley_marker"]})
    write(os.path.join(DATA, "tags", "block", "quarry_immune.json"),
          {"values": ["#minecraft:wither_immune", "minecraft:budding_amethyst"]})

    r = os.path.join(DATA, "recipe")
    write(os.path.join(r, "ley_marker.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["g", "r", "s"],
        "key": {"g": {"tag": "c:gems/source"}, "r": {"tag": "c:ingots/gold"},
                "s": {"item": "ars_nouveau:sourcestone"}},
        "result": {"id": f"{MOD}:ley_marker", "count": 2}})
    write(os.path.join(r, "source_quarry.json"), {
        "type": "ars_nouveau:enchanting_apparatus",
        "keepNbtOfReagent": False,
        "reagent": {"item": "minecraft:diamond_pickaxe"},
        "pedestalItems": [{"tag": "c:gems/source"}, {"tag": "c:gems/source"},
                          {"tag": "c:gems/source"}, {"tag": "c:gems/source"},
                          {"item": "ars_nouveau:sourcestone"}, {"item": "ars_nouveau:sourcestone"},
                          {"tag": "c:obsidians"}, {"tag": "c:obsidians"}],
        "result": {"id": f"{MOD}:source_quarry", "count": 1},
        "sourceCost": 1000})


# ---------------------------------------------------------------- lang
LANG = {
    f"block.{MOD}.source_quarry": "Source Quarry",
    f"block.{MOD}.ley_marker": "Ley Marker",

    f"message.{MOD}.no_marker": "No Ley Marker within %s blocks of the quarry.",
    f"message.{MOD}.no_partner": "The nearest Ley Marker has no partner. Place a second marker within %s blocks of it.",
    f"message.{MOD}.marker_waiting": "Ley Marker placed. Place a second one to mark the opposite corner.",
    f"message.{MOD}.marker_paired": "Ley Markers paired: %s by %s boundary.",
    f"message.{MOD}.too_big": "The marked area is %s by %s; the limit is %s per side.",
    f"message.{MOD}.too_small": "The markers are %s by %s apart; they need to be at least 3 by 3, since the boundary itself is not mined.",
    f"message.{MOD}.linked": "Linked: %s by %s area, %s layers (y=%s to %s). Mining from the top layer down.",

    f"status.{MOD}.not_linked": "Not linked",
    f"status.{MOD}.running": "Mining",
    f"status.{MOD}.no_source": "Waiting for Source",
    f"status.{MOD}.no_output": "No container next to the quarry",
    f"status.{MOD}.output_full": "Output container full",
    f"status.{MOD}.waiting_redstone": "Waiting for redstone",
    f"status.{MOD}.disabled": "Turned off",
    f"status.{MOD}.player_in_area": "Paused: a player is in the area",
    f"status.{MOD}.waiting_chunk": "Waiting for the area to load",
    f"status.{MOD}.finishing": "Finishing up",
    f"status.{MOD}.done": "Done",

    f"redstone.{MOD}.always": "Always on (ignores redstone)",
    f"redstone.{MOD}.high": "On with a redstone signal",
    f"redstone.{MOD}.low": "On without a redstone signal",
    f"redstone.{MOD}.off": "Off",

    f"gui.{MOD}.no_markers": "No markers linked",
    f"gui.{MOD}.range": "y %s to %s (%s)",
    f"gui.{MOD}.up": "Up",
    f"gui.{MOD}.down": "Down",
    f"gui.{MOD}.apply_restart": "Apply & restart",
    f"gui.{MOD}.apply_extend": "Apply",
    f"gui.{MOD}.link": "Link markers",
    f"gui.{MOD}.glyphs": "Glyphs",
    f"gui.{MOD}.cost": "Cost %s/block",
    f"gui.{MOD}.void": "Void",
    f"gui.{MOD}.mining_layer": "Mining, layer y=%s",
    f"gui.{MOD}.glyph.0": "Amplify",
    f"gui.{MOD}.glyph.0.desc": "Mines one more block per action (up to 3)",
    f"gui.{MOD}.glyph.1": "Extract",
    f"gui.{MOD}.glyph.1.desc": "Silk Touch. Not with Fortune",
    f"gui.{MOD}.glyph.2": "Fortune",
    f"gui.{MOD}.glyph.2.desc": "Fortune I to III. Not with Extract",
    f"gui.{MOD}.glyph.3": "Smelt",
    f"gui.{MOD}.glyph.3.desc": "Smelts what it mines",
    f"gui.{MOD}.glyph.4": "Crush",
    f"gui.{MOD}.glyph.4.desc": "Runs drops through Ars Nouveau's crushing recipes",
    f"gui.{MOD}.tip.progress": "%s%% of the area passed, %s blocks mined",
    f"gui.{MOD}.tip.source": "Source: %s / %s",
    f"gui.{MOD}.tip.estimate": "About %s more Source to finish",
    f"gui.{MOD}.tip.cycle": "Click to change, right-click to go back",
    f"gui.{MOD}.tip.outline_on": "Outline shown to nearby players",
    f"gui.{MOD}.tip.outline_off": "Outline hidden (still shown while holding a Ley Marker or Dominion Wand)",
    f"gui.{MOD}.tip.bedrock": "Dig to the bottom of the world",
    f"gui.{MOD}.tip.step": "Shift-click for steps of 10",
    f"gui.{MOD}.tip.extend": "Digs deeper and keeps the progress so far",
    f"gui.{MOD}.tip.restart": "Re-reads the markers and starts again from the top layer",
    f"gui.{MOD}.tip.no_change": "Nothing to apply",
    f"gui.{MOD}.tip.cost": "Source per stone block with the current glyphs",
    f"gui.{MOD}.tip.void": "Click with an item to set a filter (the item is not used up); empty hand clears",
    f"gui.{MOD}.tip.blacklist": "Destroy listed items (click: keep only listed items)",
    f"gui.{MOD}.tip.whitelist": "Keep only listed items, destroy the rest (click: destroy listed items)",
    f"gui.{MOD}.tip.tags_off": "Exact items only (click: also match items sharing a common tag)",
    f"gui.{MOD}.tip.tags_on": "Also matches items sharing a common (c:) tag with a filter, e.g. any ore or gem (click: exact items only)",
    f"gui.{MOD}.tip.skipped": "%s blocks left in place (machines, unbreakable, too hard or protected)",
    f"gui.{MOD}.tip.chunk_loaded": "Keeping its chunks loaded (costs Source every second)",
    f"gui.{MOD}.tip.locked": "Locked: owner, their team and operators only",
    f"gui.{MOD}.tip.unlocked": "Unlocked: anyone can use this quarry",
    f"gui.{MOD}.tip.owner": "Owner: %s",
    f"gui.{MOD}.tip.lock_toggle": "Click to lock or unlock",
    f"gui.{MOD}.tip.lock_owner_only": "Only the owner or an operator can change this",
    f"message.{MOD}.locked": "This quarry is locked by %s.",
    f"jade.{MOD}.progress": "%s%% of the area done",
    f"config.jade.plugin_{MOD}.quarry_info": "Source Quarry",

    f"{MOD}.page1.source_quarry": "Mines out an area marked by two Ley Markers, layer by layer from the top, using Source from nearby jars. Items go into a container next to the quarry, fluids into a neighbouring tank or are evaporated. Right-click it to open its controls.",
    f"{MOD}.page.source_quarry.area": "The markers set the corners; their height is the base level. In the quarry's screen, Up and Down set how many layers to clear above and below it. Digging deeper keeps progress; other changes restart from the top. A redstone mode button and an outline toggle sit beside the progress bars, and a comparator reads the progress.",
    f"{MOD}.page.source_quarry.glyphs": "Glyph slots: Amplify mines more blocks per action, Extract gives Silk Touch, Fortune raises yields, Smelt smelts drops and Crush runs them through crushing recipes. Each one raises the Source cost. The Void slots destroy matching items instead of outputting them; their buttons switch to keeping only matching items, and to matching whole groups by common tags (any ore, any gem).",
    f"{MOD}.page.source_quarry.output": "Only the inside of the boundary is mined: the rows and columns the markers stand on are left in place at every depth. The quarry pauses when its output is full, when a player stands inside the area, and when it runs out of Source. Source Relays linked to the quarry with a Dominion Wand can feed it from afar. A placed quarry is locked to its owner, their team and operators; the door button unlocks it.",
    f"{MOD}.page1.ley_marker": "Place two Ley Markers at opposite corners of the area you want quarried, then place a Source Quarry within 32 blocks of either one. The markers mark the boundary, which is left standing; everything inside it is mined. A new marker pairs with the nearest unpaired marker, so several marked areas can sit side by side. Holding a marker shows paired markers in cyan and unpaired ones in red. Holding a marker or a Dominion Wand shows the markers and the outline of nearby quarry areas.",
}


def lang():
    write(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


def logo():
    img = render.mod_icon(ASSETS)
    img.save(os.path.join(RES, f"{MOD}.png"), optimize=True)
    # CurseForge project icon (uploaded separately, not shipped in the jar)
    out = os.path.join(ROOT, "build", "curseforge_icon.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    render.curseforge_icon(ASSETS).save(out, optimize=True)


def main():
    clean()
    models()
    texture_files()
    data()
    lang()
    logo()
    print("resources regenerated")


if __name__ == "__main__":
    main()
