# Ars Source Quarry

A Source-powered quarry for **Ars Nouveau** (Minecraft 1.21.1, NeoForge). Mark out an area with two Ley Markers,
place the quarry, and it mines the area out layer by layer, sending items and fluids into neighbouring containers.

By Tygabeast. MIT licence (see `LICENSE`); all textures and models are original.

## How to use

1. Place two **Ley Markers** at opposite corners of the area. Their height is the base level of the area.
   The rows and columns the markers stand on form the boundary, which is never mined: only the inside is.
   So the markers must be at least 3 blocks apart each way.
   A new marker pairs with the nearest unpaired marker within 64 blocks, so several marked areas can sit side
   by side. Holding a marker shows paired markers in cyan with their boundary, and unpaired ones in red.
2. Place the **Source Quarry** within 32 blocks of either marker. It works on the nearest marker's pair.
   The second marker can be up to 64 blocks from the first (the maximum area size). The quarry links when placed.
3. Put a container next to the quarry (any side) for items, and Source Jars within 6 blocks. The quarry pulls
   from jars (and anything Ars Nouveau treats as a Source provider) by itself; a Source Relay linked to it with
   a Dominion Wand can also push Source in from further away. Nothing can pull Source back out of it.
   A fluid tank next to it (a cauldron works) collects fluids.
4. Right-click the quarry to open its screen.

## The screen

- **Area:** the two corners, the height range, and **Up** / **Down**: how many layers to clear above and below
  the markers' level. Click for 1, shift-click for 10. The arrow button digs to the bottom of the world.
  - **Apply** when only Down grew: the quarry digs deeper and keeps its progress.
  - **Apply & restart** for any other change: re-reads the markers and starts again from the top layer.
- **Glyphs** (Ars Nouveau's own glyph items):
  - Amplify (up to 3): one more block per mining action. Each multiplies the block cost by 1.5x.
  - Extract: Silk Touch. Fortune (up to 3): Fortune I to III. Extract and Fortune exclude each other.
  - Smelt: smelts what it mines. Crush: runs drops through Ars Nouveau's crushing recipes (before Smelt).
  - The screen shows the Source cost per stone block with the current glyphs.
- **Void:** six filter slots. Click with an item to set a filter (the item is not used up); click with an empty
  hand to clear. Two buttons beside the label:
  - **D / K:** destroy the listed items, or keep only the listed items and destroy the rest.
  - **= / #:** exact items, or also match items sharing a common `c:` tag with a filter (one raw iron covers all
    raw ores; one diamond covers all gems). An empty filter never destroys anything.
- **Status, progress and Source bars.** Hover them for exact numbers, an estimate of the Source still needed,
  and how many blocks were left in place (machines, unbreakable, too hard, or protected by claims).
- **Redstone mode:** always on, on with a signal, on without a signal, or off. Right-click goes back a mode.
- **Lock** (door icon): a placed quarry is locked to its owner, players on the owner's scoreboard team, and
  operators. The owner or an operator can unlock it for everyone. Quarries placed by commands, or before 1.2.0,
  are unowned until someone presses the lock button.
- **Outline:** shows the area to nearby players. The area is also outlined while the screen is open (with
  unapplied Up/Down changes) and for anyone holding a Ley Marker or Dominion Wand.

## How it mines

- Top layer first, down to the bottom, so sand and gravel inside the area never fall into cleared space.
  Gravity blocks that drop in from above the area are rechecked and mined.
- Each block costs Source: 2 plus 1 per point of hardness (stone 4, obsidian 52), then glyph costs.
- Fluid source blocks go into a neighbouring tank (2 Source). With no tank room they are evaporated (25 Source).
  Flowing fluid is left alone. Waterlogged blocks give up their water too.
- Skipped: air, bedrock and other unbreakable blocks, anything in the `ars_source_quarry:quarry_immune` block tag
  (wither-immune blocks and budding amethyst by default), blocks harder than 50, and blocks with block entities
  (chests, jars, machines, spawners) unless enabled in the config.
- Drops are rolled as if mined with a netherite pickaxe carrying the glyphs' enchantments.
- Pauses when the output is full (keeping what it holds), when out of Source, and while a player is inside the area.
- A comparator reads the progress (0 to 14, 15 when done).
- The boundary (the marker rows and columns) is left standing at every depth, so the markers stay put.
- Optionally (config `collectMarkersWhenDone`, off by default), both Ley Markers go into the output when finished.
- By default it only works while its own chunk is loaded. Optional chunk loading (config, off by default) lets
  a quarry keep its own chunk and the one it is mining loaded while it works, for 5 Source per second.
- A quiet hum plays near a quarry while it mines (client config `quarryHum`).
- Each break is reported as a player break, so claim and protection mods can stop it.
- Breaking the quarry drops its glyphs and anything waiting for room in the output.
- With Jade installed, looking at a quarry shows its status, progress and Source.

Ars Nouveau's worn notebook has pages for both blocks (under Crafting).

## Recipes

- Ley Marker x2: Source Gem over Gold Ingot over Sourcestone (crafting table).
- Source Quarry: Enchanting Apparatus, Diamond Pickaxe in the centre; 4 Source Gems, 2 Sourcestone and
  2 Obsidian on the pedestals; 1000 Source.

## Config (`config/ars_source_quarry-server.toml`, and `ars_source_quarry-client.toml` for the hum)

Link range, maximum size, speed, Source range and buffer, all costs including each glyph's, maximum hardness,
whether block entities are mined, pausing for players, collecting markers (off by default), effects, and chunk
loading (off by default) with its upkeep.

## Performance notes

- The quarry keeps a saved cursor and never rescans the area. Air costs one block lookup, capped per tick.
- Source is pulled into a buffer at most once a second, so Ars Nouveau's jar search does not run per block.
- Containers are reached through NeoForge's capability cache; drops go straight in, no item entities.
- Glyph effects, the enchanted tool and the void filter are rebuilt only when their slots change; Smelt and
  Crush results are cached per item until recipes reload.
- The player check walks the level's player list once a second (no entity searches).
- Relay input is a plain capability: a relay pushes Source in on its own schedule; the quarry does nothing extra.
- The owner check runs when the screen is opened and on button presses only.
- Tag matching builds the filter's tag set once when the filter changes; each drop is then one set lookup per tag.
- Skipped blocks are a counter; nothing is stored per block.
- Chunk loading is off by default. When on, a quarry holds at most two chunks (its own and the one it is
  mining), only while mining and able to pay, and checks this once a second. Tickets of removed quarries are
  dropped when the world loads.
- The hum is client-side. The server only sends "started" or "stopped", at most every 2 seconds per quarry.
- Markers are found by reading chunk block-entity lists, only when linking.
- Screen values sync only while the screen is open. The area is sent to clients only when it or the outline
  setting changes. The outline is drawn client-side from a list of loaded quarries; markers are only looked up
  while holding a marker or wand, and particles per pass are capped.

## Building

`gradle build`. If Ars Nouveau's Maven is unreachable, put the Ars Nouveau, Curios and GeckoLib jars in `libs/`.
`python3 tools/gen.py` regenerates all models, textures (including the GUI), recipes, tags, loot tables, lang
and the logo.

## 1.3.5

- Added the MIT licence file to the source and the jar.

## 1.3.4

- New mod icon: the quarry with a Ley Marker beside it, its gem included. `tools/gen.py` also writes a square
  CurseForge project icon to `build/curseforge_icon.png`.

## 1.3.3

- The Ley Marker's spinning gem is simpler: each face is a 2x2 of four violet shades, the bright corner turned
  differently per face, like the gem of the Enchanter's Bow.
- The marker's gold is speckled like Ars Nouveau's magelight gold. The quarry's gold is unchanged.

## 1.3.2

- The Ley Marker's crystal is now a faceted Source gem stood on its point that turns on two axes, like the gem
  of Ars Nouveau's Enchanter's Bow. Drawn client-side by a small block entity renderer (one cube per visible
  marker, within 48 blocks); the item icon shows it still.
- Gold on the blocks and the screen now uses Ars Nouveau's yellower gold.

## 1.3.1

- The Source Quarry faces the player who places it: one open side toward you, with the runner lying across
  and draping over the left and right sides, like the Scribe's Table.
- The lower shelf has raised lips and holds a scroll and a small glowing Source cluster.
- The hitbox is the full block again.
- Paler gold on the blocks and the screen.

## 1.3.0

- New look. The Source Quarry is now a one-block sourcestone table in the spirit of Ars Nouveau's Scribe's
  Table: smooth sourcestone top, carved sourcestone legs and apron, a purple runner with gold spiral borders
  hanging down the front and back, gold fittings, a pool of Source on top and a glowing Source crystal hanging
  underneath.
- The Ley Marker is now magelight-style: a sourcestone foot, two gold prongs and a floating Source orb.
  Its light level went from 7 to 11.
- Glowing parts (Source pool, crystal, orb) render full-bright.
- New mod icon from the new models.

Cleanup:
- Removed the old block textures (quarry side/top, marker crystal and shaft) and the drawing code for them.

## 1.2.1

- Jade (optional): looking at a quarry shows its status, progress and Source. Can be turned off in Jade's
  settings. The server only sends this while a player is looking at a quarry.

## 1.2.0

- Source Relays can feed the quarry (link with a Dominion Wand). It still pulls from nearby jars as before.
- Owner lock: owner, their scoreboard team and operators; the owner can unlock it for everyone.
- Void filter: keep-only (whitelist) mode and matching by common tags.
- Tooltip count of blocks left in place.
- Optional chunk loading with a Source upkeep (off by default).
- Quiet hum while mining (client config).

Cleanup:
- Removed the unused screen-data instance on the quarry (each viewer now gets their own) and the old
  exact-match-only void check.
- Removed four values the screen synced but never showed (area and world height limits).

## 1.1.1

- Only the inside of the marker boundary is mined. The boundary rows and columns, including the blocks the
  markers stand on, are left alone at every depth. Areas now need markers at least 3 apart each way.
- Fixed markers seeming to vanish: when a quarry finished it collected the markers into its output, and
  digging deeper afterwards then carried on without them. Collecting markers is now off by default.
- Ley Markers count as solid, so flowing water can no longer wash them away.
- The config option is now `collectMarkersWhenDone` (was `collectMarkers`), so configs written by 1.1.0 with
  the old default of `true` pick up the new default of `false`.
- Fixed sand or gravel falling into a small area sometimes being left behind: the quarry now keeps watching
  while anything is still falling over the area.
- Ley Markers now pair up when placed (nearest unpaired marker within the maximum area size), and the quarry
  works on the nearest marker's pair. Areas next to each other no longer confuse it. Markers placed with
  1.1.0 are paired the first time a quarry links to them.

## 1.1.0

- The Depth Ley Marker is gone; the Corner Ley Marker is now just the **Ley Marker**. Area height is set in the
  quarry's new screen with Up and Down.
- New screen in Ars Nouveau's style: area controls, Apply that keeps progress when digging deeper, redstone
  mode, outline toggle, progress and Source bars with tooltips.
- Glyph slots: Amplify, Extract, Fortune, Smelt, Crush.
- Void filter, comparator output, pause while a player is in the area, markers collected when done.
- Worn notebook pages.
- Outline shown while holding a Dominion Wand as well as a Ley Marker.

Cleanup:
- Removed the `depth_ley_marker` block, its item, recipe, model, texture and loot table.
- Renamed `corner_ley_marker` to `ley_marker` (id, model, recipe, loot table, texture `ley_crystal`).
- Removed the marker depth offset, the sneak-right-click relink and the chat status message (the screen
  replaces both), the old marker preview class and their unused lang keys.
- Ley Markers placed with 1.0.x are removed from worlds when updating.

## 1.0.1

- The quarry only needs to be within the link range (32) of one marker. It finds the nearest marker and
  looks for the others around it, out to the maximum area size (64).

## 1.0.0

First release.
