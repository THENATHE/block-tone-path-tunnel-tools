# Block Tone, Path & Tunnel Tools

[Download 1.6.9+26.3](https://github.com/THENATHE/block-tone-path-tunnel-tools/releases/tag/v1.6.9%2B26.3) · [All releases](https://github.com/THENATHE/block-tone-path-tunnel-tools/releases) · [Report an issue](https://github.com/THENATHE/block-tone-path-tunnel-tools/issues)

Version **1.6.9+26.3** is the maintained release, with Block Toning Tool, Path Tool, and Tunnel Tool together. The former non-tunnel release, Block Tone & Path Tools 1.5.4+26.2, is retired; its source is preserved in the author's private historical backups; this repository contains the maintained three-tool source.

Install `block-tone-path-tunnel-tools-1.6.9+26.3.jar` in place of any previous Blocktone JAR: they share the `blocktonepathtools` mod ID and must not be installed together. Registry IDs and configuration paths remain unchanged, preserving existing tool stacks and settings. Precious Stones is optional and may remain installed alongside this build. This mod contains no claim engine, shop code, or Precious Stones mixins.

A Minecraft 26.3 Fabric mod for survival-friendly block toning, terrain-following path construction, tunnel construction and lining, and rails. All world changes are authoritative on the logical server and use server commands and vanilla-compatible packets, with optional Polymer tool items.

## Compatibility

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API 0.161.0+26.3 or newer
- Java 25 or newer
- Dedicated server: install this mod and Fabric API on the server. Vanilla 26.3 clients can join; no client mod is required.
- Single-player/LAN host: install this mod and Fabric API in the local Fabric instance so its integrated server can run the mod.

No client mod or custom command argument is required. Optional Polymer items are presented to vanilla clients as iron nuggets with custom models.

## Optional Polymer tools

The default `"enablePolymerTools": true` setting in `config/block-tone-path-tools.json` enables three separate tools when **Polymer Core and Polymer Resource Pack** are both installed. This build was tested with Polymer **0.18.2+26.3**. Install the matching Polymer bundle alongside Fabric API and this JAR, enable Polymer resource-pack hosting, and accept the pack when joining. No client-side mod is required. Polymer remains optional and is not bundled in this mod.

Craft the tools using the recipes below, or use the Creative inventory. Administrators can also run **`/blocktools tools`** to receive one of each custom tool. If your inventory is full, remaining tools drop at your feet.

| Role | Item | Registry ID |
| --- | --- | --- |
| Blocktone corners | Block Toning Tool (triangular mortar trowel) | `blocktonepathtools:mortar_trowel` |
| Path placement | Path Tool | `blocktonepathtools:garden_trowel` |
| Tunnel construction, lining, and rails | Tunnel Tool (prospecting pick / rock hammer) | `blocktonepathtools:prospecting_pick` |

Each tool has its original **16×16 transparent sprite** plus a native **32×32 variant** with finer pixel detail that preserves the same design, and vanilla handheld item transforms. All use an iron-nugget client base; no stick or breeze-rod models are replaced. The real items remain distinct from ordinary iron nuggets.

With integration active, all three tools appear in the **Tools & Utilities** Creative tab and Creative search when the local mod or compatible Polymer client support is installed. For vanilla clients on a server-only installation, Creative players can open Polymer's menu with `/polymer creative minecraft:tools_and_utilities`; an unmodified vanilla client's native Creative tabs cannot be changed by a server mod.

With integration active, the old configured tool no longer selects corners or triggers paths/tunnels. Each custom tool triggers only its own role. Path and Tunnel Tools retain their existing mutual pause behavior; holding their separate tools does not automatically switch the active mode.

If either Polymer module is absent, or you set `"enablePolymerTools": false`, all roles automatically return to your exact saved `selectionItem` assignment (Breeze Rod by default). `/blocktools tools` explains the fallback. Change this setting and run `/blocktools reload` to switch live; tool registration is retained while Polymer is installed so existing custom stacks survive disabling/re-enabling. Palettes, corners, and configured legacy IDs are preserved. Removing Polymer from the server requires a restart; use the config switch if you want to keep saved custom stacks safely registered.

Replace the previous Blocktone JAR with this build, restart to register items and regenerate Polymer's pack, then reconnect and accept the updated pack. If a vanilla client has not loaded the pack, custom tools still function but display as named iron nuggets.

## Tool sprite resolution

Every player can choose the sprites they see:

```text
/blocktools auto
/blocktools 16x16
/blocktools 32x32
```

**Auto** is the default. With this mod installed on the client, it checks the iron ingot, copper ingot, and flint textures selected by the active resource packs after connecting or reloading resources. It uses 32×32 tool sprites only when all three samples have square animation frames at least 32×32; otherwise it uses the original 16×16 sprites. Mixed resolutions below that threshold, missing resources, invalid frames, and read errors conservatively select 16×16. Animated textures are assessed by frame size, not the full animation strip. Packs using 64×64 or larger samples receive the available 32×32 variants.

Manual choices override detection and are saved per player in that world across reconnects and restarts. `/blocktools auto` clears the manual override. Each viewer's choice applies to the tools they see, without changing item IDs, crafting, or tool behavior; the native client models and Polymer's server-rendered item models both use it.

A server-only installation cannot detect an unmodified vanilla client's resource packs. Those clients default to 16×16 in Auto and can choose `/blocktools 32x32` manually. Regenerate and accept the updated Polymer server resource pack to see the new sprites on vanilla clients. A client with this mod installed includes both model sets locally.

## Crafting

All three recipes use a crafting table and produce **one tool**. All three recipes unlock automatically on the first player tick while Polymer integration is active, including after re-enabling it; acquiring an iron ingot is no longer required. Sprite resolution does not change recipe ingredients or output item IDs.

`I` = iron ingot, `C` = copper ingot, `F` = flint, `S` = stick, `.` = empty.

```text
Block Toning Tool   Path Tool   Tunnel Tool
. I .               . I .       I I F
I I .               . C .       . S .
. S .               . S .       . S .
```

- **Block Toning Tool:** 3 iron ingots + 1 stick; triangular mortar-trowel sprite.
- **Path Tool:** 1 iron ingot + 1 copper ingot + 1 stick; garden-trowel sprite.
- **Tunnel Tool:** 2 iron ingots + 1 flint + 2 sticks; prospecting-pick sprite.

The sticks are crafting ingredients; each finished tool still uses the iron-nugget client base. When integration is disabled or Polymer is absent, the recipes and their unlock advancements do not load. Changing the integration setting with `/blocktools reload` refreshes datapacks asynchronously; wait for the recipe-refresh completion message before crafting. Rapid mode toggles are serialized. Other datapack contents are reloaded by Minecraft normally, and existing tool stacks remain registered whenever Polymer is installed.

## Permissions

The bare `/blocktools` command and its `help`, `tools`, and `reload` subcommands require Minecraft administrator permission (operator level 3 or higher). The personal cosmetic commands `/blocktools auto`, `/blocktools 16x16`, and `/blocktools 32x32` are available to every player, as are `/blocktone`, `/pathtool`, `/tunneltool`, and `/tunnel`. Native world-border/spawn interaction checks still apply. World edits require a living Survival or Creative player with build ability; Adventure and Spectator cannot edit. Arming or resuming Path Tool pauses Tunnel Tool, and vice versa, retaining the paused tool’s settings.

## Visual selection and tool previews

All three tools use private vanilla particle previews inspired by Precious Stones' `/ps visualize`. No client mod is required. Selecting a corner or configuring/resizing a tool opens its preview automatically for 20 seconds. The preview refreshes twice per second and displays dimensions or tool status above the hotbar. Path previews follow the player; Tunnel previews follow the player's progress along the locked tunnel line once a run begins. Chat includes clickable **Refresh** and **Hide** controls; Blocktone also offers **Clear selection**.

```text
/blocktone visualize
/pathtool visualize
/tunneltool visualize
```

Add `off` to any of those commands to stop that preview. `/tunnel visualize` is also supported. Hiding or expiring a preview preserves corners, palettes, and the tool's enabled/paused state. Existing particles fade naturally. Re-clicking a selected corner refreshes its timer without repeating the chat controls.

- **Block Toning Tool:** white outline on the outer boundary of the inclusive selected blocks, green Point 1, purple Point 2. A single selected corner is outlined until the other corner is chosen. Dimensions, block count, and an over-limit notice appear above the hotbar.
- **Path Tool:** cyan outlines mark each width lane's terrain surface at the current position and the next two rows; white arrows show direction. Each lane follows the actual loaded terrain height.
- **Tunnel Tool:** white outlines mark three slices of clear interior; purple marks walls, green the floor, and blue the ceiling. Cyan arrows mark the player lane. Gold marks rails, and red marks powered-rail/torch positions using the configured booster interval. Rail-only mode omits lining markers. Even-width previews retain the left-center player/right-center rail alignment. During a locked run, turning or stepping sideways does not rotate or shift the preview onto a new line.

Previews show geometry, not a material or permission approval. Palette choices, stair shaping, missing inventory, obstructions, and protection rules are checked by the actual tools when they run. To inspect without placing blocks, pause first with `/pathtool off` or `/tunneltool off`, then use `visualize`. Visualization never arms a paused tool, consumes items, or changes the world. Configuring a palette still arms the tool as before.

Particles are sent only to their requesting player within 128 blocks and loaded chunks; sampling is bounded. Preview state is cleared on disconnect/server shutdown, config reload, or dimension change. Disarming a tool hides its preview; switching active path/tunnel modes hides the other tool's preview.

## Block Toning Tool

1. Hold the Block Toning Tool, or your configured legacy selection item when Polymer integration is inactive (a Breeze Rod by default).
2. Left-click an opposite corner to set Point 1, then right-click the other corner to set Point 2. Left-click always updates Point 1 and right-click always updates Point 2, so either boundary can be adjusted without resetting the other. Both interactions are consumed and will not damage/use the clicked block. The corners define an inclusive cuboid; selecting corners that share one coordinate naturally creates a flat wall or floor. Sneak-click with either button to clear both points.
3. Run a palette. Without a source filter, every safe, existing non-air block in the cuboid is eligible:

```text
/blocktone to:bricks:50,terracotta:30,stone:10,granite:10
/blocktone to:bricks,granite,polished_granite
/blocktone to:bricks[20],granite[],polished_granite[]
```

The legacy `block:percent` form remains available when its whole-number percentages total exactly 100. In the bracket form, `block` and `block[]` are automatic entries. Automatic entries divide the percentage remaining after every explicit `block[percent]` entry. Thus three bare materials split the full selection equally, while `bricks[20],granite[],polished_granite[]` assigns 20% to Bricks and splits the remaining 80% evenly between Granite and Polished Granite. If there are no automatic entries and explicit percentages total less than 100, the unallocated positions retain their existing blocks.

Limit replacement to one source block or a comma-separated source block list with `from:`:

```text
/blocktone to:bricks:100 from:stone
/blocktone from:stone,diorite,andesite to:bricks:50,granite:50
```

To recolor a roof without changing its shape, select both corners around the roof and run:

```text
/blocktone type:stairs to:stone_brick_stairs[70],mossy_stone_brick_stairs[30]
```

`type:` selects categories across materials, so this changes only the stairs and leaves the full blocks underneath alone. Stair replacements retain each position's facing, upper/lower half, straight/inner/outer corner shape, and waterlogging. Use **stair blocks in the destination palette** to retain stair geometry. A full-block destination intentionally creates full blocks; the tool does not infer stair/slab variants from a material name.

### Source types

Use one type or a comma-separated union, for example `type:slab`, `type:stairs,slabs`, or `type:stairs,slabs,blocks,liquids,air`. Any listed type can match; categories may overlap. Singular names work too, including `stair`, `slab`, `fence`, `wall`, `bar`, `fence_gate`, `pressure_plate`, `leaf`, `torch`, and `lantern`.

| Type | Matches |
| --- | --- |
| `stairs` | All stair materials. |
| `slabs` | Bottom, top, and double slabs. |
| `trapdoors` | Open or closed trapdoors. |
| `fences` | Wooden, nether-brick, and compatible modded fences. |
| `walls` | Cobblestone and other wall materials. |
| `bars` | Iron bars and all copper-bar weathering/waxed variants, plus blocks in the vanilla bars tag. |
| `panes` | Glass panes, including stained glass; excludes bars. |
| `fence_gates` | Fence gates; `gate` and `gates` also work. |
| `railings` | **Fences, walls, and bars together**; exactly the union `type:fences,walls,bars`. Excludes panes and fence gates. Aliases: `railing`, `fences_walls_bars`. |
| `logs` | Blocks in the standard logs tag, including stripped logs and wood. |
| `pillars` | Axis-oriented pillar blocks, such as logs, hay bales, and quartz pillars. |
| `buttons` | Wall, floor, and ceiling buttons. |
| `pressure_plates` | Ordinary and weighted pressure plates. |
| `rails` | Ordinary, powered, detector, and activator rails. |
| `chains` | Chains on any axis. |
| `ladders` | Ladders on any supported face. |
| `leaves` | Leaves. Singular alias: `leaf`. |
| `carpets` | Ordinary, moss, and pale moss carpets. |
| `torches` | Floor and wall torches, including soul and redstone torches. |
| `lanterns` | Standing and hanging lanterns, including soul lanterns. |
| `light_sources` | Blocks that emit light in any possible state, including currently unlit redstone torches, lamps, candles, and copper bulbs. Aliases: `light_source`, `light`, `lights`. Safety exclusions below still apply. |
| `blocks` | Full collision cubes, excluding stairs, slabs, trapdoors, fences, walls, bars, panes, gates, and pure liquids. Full-cube logs and pillars also match this type. |
| `liquids` | Pure water/lava blocks, including flowing states; excludes waterlogged structures. Aliases: `liquid`, `fluid`, `fluids`. |
| `air` | Normal, cave, and void air. |

Compatible modded blocks match the corresponding vanilla class/tag or light-emitting states. In **1.6.8**, `panes` is refined to glass panes; in 1.6.7 it also selected bars. Use `type:panes,bars` to retain that earlier combined selection.

Examples of individual families and the combined railing filter:

```text
/blocktone type:fences to:spruce_fence[100]
/blocktone type:walls to:stone_brick_wall[100]
/blocktone type:bars to:waxed_copper_bars[100]
/blocktone calc type:railings to:iron_bars[100]
```

`railings` makes it easy to select all three families together. A single destination family converts every matching position to that family: a fence changed into iron bars adopts bar geometry and vanilla bar connections. To retain each family's original geometry, run separate `fences`, `walls`, and `bars` passes with matching destination families. The calculate example previews the conversion without changing the world.

`type:` replaces `from:`; using both, repeating a category, or entering an unknown category is rejected. Either clause can appear before or after `to:`. `from:type:stairs` is also supported. Tab completes types after each comma. Calculate preview/apply supports the same filters:

```text
/blocktone calc type:stairs to:stone_brick_stairs,mossy_stone_brick_stairs
/blocktone calc apply type:stairs to:stone_brick_stairs,mossy_stone_brick_stairs
```

### Orientation, connections, and lighting

All toning and calculate-apply replacements preserve state properties that the destination supports:

- **Stairs:** facing, top/bottom half, straight/inner/outer corner shape, and waterlogging.
- **Slabs:** bottom/top/double state and waterlogging. Double slab replacements cost two destination slabs and return two original slabs.
- **Trapdoors:** facing, top/bottom attachment, open/closed, powered, and waterlogged state.
- **Fences, walls, bars, and panes:** supported directional connections, wall heights/posts, and waterlogging. **Fence gates:** facing, open/closed, powered, and in-wall state.
- **Logs, pillars, and chains:** axis, plus waterlogging where supported. **Buttons:** wall/floor/ceiling attachment, facing, and powered state. **Ladders:** facing and waterlogging. **Rails:** supported rail shape and powered state.
- **Lanterns:** hanging/standing position and waterlogging. **Torches:** floor/wall mounting and wall-facing direction, plus compatible lit state for redstone torches.
- **Other blocks:** shared compatible properties, such as leaf persistence/distance, candle count/lit state, and carpet connection states.

Vanilla neighbor updates maintain valid connections, support, and redstone behavior. They can change a copied state when the destination follows different rules or when neighboring blocks change. The tool cannot preserve a shape or attachment that the destination block does not support. It checks destination support against the complete planned edit before consuming inventory; an unsupported placement rejects the operation without changing blocks or spending items.

To change torch materials while retaining floor/wall mounting, or lantern materials while retaining hanging/standing positions:

```text
/blocktone type:torches to:soul_torch[100]
/blocktone type:lanterns to:soul_lantern[100]
```

Use the ordinary inventory item `torch`, `soul_torch`, or `redstone_torch` in the palette. The tool chooses its wall counterpart automatically for wall-mounted source torches and keeps their horizontal facing. Lantern palettes preserve each source lantern's hanging state. Use **torch destinations for wall torches** and **lantern destinations for hanging lanterns**: a wall torch cannot become a lantern at the same mounting location, and a hanging lantern cannot become a torch. Such incompatible conversions reject the entire operation. Floor torches and standing lanterns can convert between those families when the planned support is valid.

For broader light-emitter selections, the same filter works in toning and calculate preview/apply:

```text
/blocktone calc type:light_sources to:glowstone[100]
/blocktone calc apply type:light_sources to:glowstone[100]
```

This example converts eligible light sources to full glowstone blocks, changing their geometry. A broad `light_sources` selection can contain several mounting families; a torch/lantern destination palette may reject incompatible positions. Use `type:torches` or `type:lanterns` for lighting recolors that retain their mounting. Block entities such as campfires and beacons remain protected, and lava is skipped unless explicitly included with `type:liquids` or a water/lava source ID. Selecting `light_sources` alone does not bypass these safeguards.

`to:` is always required. `from:` is optional, and the two selectors can appear in either order:

```text
/blocktone from:bricks to:iron_block[10],quartz_block[10]
/blocktone to:iron_block[10],quartz_block[10] from:bricks
```

Explicit percentages may total less than 100: without an automatic entry, their exact quotas are scattered across the matching selection and every remainder position retains the block already occupying it. Percentages over 100 are rejected.

Both selectors autocomplete. Omitting `from:` is exactly equivalent to `from:any`, which selects every safe non-air block. `from:any+air` selects every safe block including air. `from:air` selects only air, and explicit lists such as `from:stone,air` are also supported. The older `all` spelling remains accepted as a compatibility synonym for `any`.

The command plans an exact distribution over the matching blocks. Air is skipped unless the source filter explicitly includes it. Pure liquids are skipped unless explicitly selected through `type:liquids` or an explicit water/lava source ID. Block entities, unbreakable blocks, and multi-block structures such as doors, beds, and tall plants are skipped. Multi-block destinations are rejected because bulk replacement cannot safely place their companion blocks. It counts ordinary loose inventory stacks and items inside top-level shulker boxes; named/customized materials are preserved. It checks usable supplies before changing anything. If the net material requirement is short, it reports every shortage and changes nothing. Creative players do not consume materials.

### Clear blocks with air and exclude source blocks

Air is a destination in Blocktone toning and calculate preview/apply. It requires no inventory item:

```text
/blocktone from:stone to:air
/blocktone type:stairs to:air[100]
/blocktone from:not(grass_block,dirt) to:air
/blocktone calc from:not(grass_block,dirt) to:air
/blocktone calc apply from:not(grass_block,dirt) to:air
```

`from:not(grass_block,dirt)` matches other eligible non-air blocks while preserving Grass Blocks and Dirt. Use the canonical ID `grass_block`; `grass` is not an alias. This filter excludes exactly those block IDs: Coarse Dirt, Rooted Dirt, and other variants need their own entries if you want to preserve them too. A negated filter does not implicitly include air or pure liquids, and never bypasses block-entity, multipart, unbreakable-block, or protection checks.

You can also exclude categories:

```text
/blocktone type:not(stairs,slabs) to:air
/blocktone from:not(type:stairs,slabs) to:air
```

Both commands preserve stairs and slabs and clear other eligible blocks. Use one non-empty `not(...)` expression with a block list or a `type:` list; nested negation, extra parentheses, and mixing block IDs with categories are rejected. As with positive filters, standalone `type:` is available in Blocktone toning and calculate mode. Path Tool and shuffle can use negation through their shared `from:` filter, but retain their existing terrain and shuffle behavior.

`to:air`, `to:minecraft:air`, `to:air[100]`, and `to:air[]` all clear the eligible selection. Regular toning also accepts legacy `to:air:100`; calculate mode uses the bare/bracket forms. **`type:air` selects empty source positions; `to:air` clears destination positions.** Selecting air alone and replacing it with air changes nothing.

Air can share a destination palette with blocks:

```text
/blocktone from:stone to:bricks[70],air[30]
/blocktone from:stone to:air[20]
/blocktone from:stone to:bricks[],air[]
```

The first command replaces 70% with Bricks and clears 30%. The second clears exactly 20% and leaves the other positions unchanged. In regular toning, automatic air shares the remaining percentage equally with the other automatic entries, just like a block material. Percentages and unchanged remainders use the same exact distribution rules as other palettes. Shortages or invalid support reject the whole operation, including its air placements, without changing blocks or returning materials.

Recoverable cleared source items use the existing transaction: they fund requested placements first, then surplus goes to inventory, carried shulkers, or a safe overflow drop. Double slabs return two slab items. Clearing a waterlogged block also removes its contained water; nearby water may flow back normally. Removing supports can affect attached neighbors through normal Minecraft physics; planned replacements and selected survivors are checked for valid support before applying the operation. These clearing rules apply to Blocktone; Path Tool still rejects air destinations, and Tunnel Tool keeps its separate wall/ceiling air rules below.

### Calculate mode

Calculate mode derives a survival-friendly distribution instead of requiring you to know the contents of your inventory:

```text
/blocktone calc to:bricks,granite,polished_granite
/blocktone calc from:grass_block to:bricks,granite,polished_granite
/blocktone calc to:bricks,granite,polished_granite from:grass_block
```

`to:` remains mandatory in calculate mode. Omitting `from:` considers every safe non-air block through the `from:any` default. The command is a read-only preview; apply the displayed plan explicitly:

```text
/blocktone calc apply to:bricks,granite,polished_granite
```

Bare materials and `material[]` are automatic. Calculate mode fits as many carried automatic materials as possible, using their inventory quantities relative to one another when they cannot all fit. It then uses requested materials already placed in the selection to fill the remaining capacity. Scarce placed accents are preserved while the dominant existing material naturally supplies the filler; any capacity that still cannot be supplied retains the existing blocks unless automatic air is requested.

For example, selecting 10,000 Bricks with 500 Granite and 500 Polished Granite in inventory produces a preview of 9,000 Bricks, 500 Granite, and 500 Polished Granite. Existing requested blocks count toward the result, loose inventory and carried shulkers are both considered, and every usable carried accent is included when it fits.

Explicit percentages can be combined additively with automatic entries:

```text
/blocktone calc to:bricks[20],granite[],polished_granite[]
/blocktone calc from:stone to:bricks[20],granite[],polished_granite[]
```

Here Bricks reserve an exact 20% quota and the automatic Granite entries fit themselves into the other 80%. If all materials use `[]`, or none use brackets at all, the entire selection is automatic. If no automatic entry is present and explicit percentages total under 100, calculate mode leaves the unallocated positions unchanged just like regular Blocktone mode.

For an air fallback, use `/blocktone calc from:stone to:bricks[],air[]`. Calculate mode first fits the carried and existing requested automatic block materials, then clears any remaining automatic capacity with free air. Automatic air does not compete with carried blocks for an equal quota as it does in regular toning. Explicit air percentages remain fixed: `to:bricks[],air[20]` reserves exactly 20% air and fits Bricks into the remaining 80%. Preview reports the distribution without editing; use `calc apply` to execute it.

### Shuffle mode

Shuffle mode rerandomizes the matching blocks already inside the two-corner selection:

```text
/blocktone shuffle from:bricks,granite,polished_granite
```

`from:` is required for this source-only command; there is no `to:` selector because no new material is introduced. Category filters use the nested form, for example `/blocktone shuffle from:type:stairs`. The exact matching block states are randomly permuted among their existing positions, preserving material counts and each moved state's properties such as orientation. This moves orientations between positions rather than recoloring the shape at each position; use normal toning or calculate-apply for a roof recolor that keeps its shape. It uses no loose or shulker inventory. At least two different safe matching states must be present. Each execution uses a fresh random shuffle.

### Survival transactions

Each replacement is planned as one net transaction. Removed blocks of the same item fund new placements during that operation; only the remaining deficit must already be in the player's inventory or top-level shulker boxes. Calc planning and calc-apply consumption now share one atomic inventory snapshot, so every loose or shulker stack counted as available is reserved from that same snapshot. Removed and unused surplus blocks are returned to loose inventory first, then carried shulker boxes. If both are full, overflow is dropped at the player instead of being deleted. The free Dirt Path terrain conversion is not treated as a removed Dirt/Grass Block, so it cannot duplicate ground blocks.

Successful Blocktone, calculate-apply, and shuffle operations retain both selected corners so the same area can be adjusted or rerandomized repeatedly. Sneak-left/right-click with the selector or run `/blocktone clear` to clear it manually. Material names use canonical registry IDs only; press Tab while entering each comma-separated block name to autocomplete it. Namespaced IDs such as `minecraft:bricks` and blocks from installed mods are supported.

## Path Tool

Configure and arm a normal palette:

```text
/pathtool to:cobblestone[70],gravel[30]
```

Optionally add a stair palette for one-block slopes:

```text
/pathtool to:dirt_path[60],cobblestone[40] stairs:cobblestone_stairs[100]
```

Path and stair palettes use `block[percent]` entries totaling exactly 100. Air destinations are rejected. The former `block:percent` Path Tool form is no longer accepted.

Use position-independent named clauses to restrict which terrain materials the tool may replace:

```text
/pathtool from:grass_block,dirt to:dirt_path[60],cobblestone[40] stairs:cobblestone_stairs[100]
/pathtool stairs:cobblestone_stairs[100] to:dirt_path[60],cobblestone[40] from:grass_block,dirt
```

`from:`, `to:`, and `stairs:` may appear in any order and autocomplete canonical block IDs. `to:` is required. Missing `from:` defaults to `from:any`; source filters may otherwise use one block, a comma-separated list, or a nested category filter such as `from:type:blocks`. The standalone `type:` clause is only supported by Blocktone toning and calculate commands. Although `from:any+air` is accepted consistently, air cannot become Path Tool terrain because it has no solid surface. `stairs:` is optional. A normal path block just placed by the tool remains eligible for conversion into a downhill stair even when it is not listed in `from:`.

Then hold the Path Tool in either hand and walk. When Polymer integration is inactive, use the configured legacy item (a Breeze Rod by default), which also selects Blocktone corners when clicked. Neither tool is consumed. Releasing it ends the current trail, so re-holding it elsewhere will not connect a long accidental path.

Each width lane finds the nearby solid surface independently, allowing the path to follow cross-slopes and terrain. Transparent, replaceable, non-colliding vegetation such as short/tall grass does not count as terrain and is cleared only where a path cell changes. Leaves and fluids are not treated as ground. Tall grass is cleared as a checked pair, returning one plant item for both halves. A configured stair palette is used for one-block rises and drops. Uphill stairs occupy the new upper terrain block and face the travel direction; downhill stairs occupy the previous upper terrain block and face opposite travel. The high side therefore always faces the higher ground.

`dirt_path` is a special inventory-free material. When chosen over grass block, dirt, podzol, coarse dirt, mycelium, or rooted dirt, the ground is converted to Dirt Path without consuming a block. On other terrain its share is deterministically reassigned among the ordinary blocks in the palette; if there are none, that cell remains unchanged.

Ordinary path and stair replacements use the same net survival transaction as block toning. Recoverable replaced ground and cleared vegetation are returned to inventory/shulkers, with overflow dropped safely.

Useful commands:

```text
/pathtool width 1
/pathtool width 5
/pathtool status
/pathtool off
/pathtool on
/pathtool disarm
```

Widths from 1 through 5 include the block beneath the player and default to 3. Even widths have one extra lane on the player's right relative to travel.

`/pathtool off` pauses placement while retaining the configured palettes, so `/pathtool on` can resume them. `/pathtool disarm` disables the tool and clears both its normal and stair palettes while preserving the chosen width.

Path transactions reserve their net inventory cost before changing terrain. Counting and consumption now use one authoritative, no-partial-write reservation over loose inventory and the 27 usable slots in each carried shulker. Generic component data outside those slots is preserved but is not falsely counted as available. A failed reservation reports the actual material shortage instead of an “inventory changed” error or server-tick exception.

## Tunnel Tool


Tunnel Tool builds walls, floor, and ceiling around a clear passage, either replacing existing surfaces or filling empty space from the selected palettes. Wall and ceiling palettes can also use air to leave those surfaces open or clear their existing safe blocks. Each slice needs an existing dry, sturdy supporting block beneath the player's lane; enclosing walls and ceiling are not required beforehand.

It follows the established named-clause and `block[percent]` syntax used by the workspace's Path Tool. Clauses may appear in any order. Width is limited to 1–5 and height to 2–5; both describe the **clear walkable interior**, not the outside dimensions. Rail modes require width 2–5 so every booster can use a valid adjacent redstone torch.

Use one mix for the walls, floor, and ceiling:

```text
/tunneltool width:2 height:3 to:stone_bricks[70],cracked_stone_bricks[30] rail:off
```

Or specify all three surfaces independently:

```text
/tunneltool width:3 height:4 walls:stone_bricks[70],andesite[30] floor:deepslate_tiles[100] ceiling:smooth_stone[100] rail:on
```

Build a floor with open sides and top:

```text
/tunneltool width:3 height:3 walls:air[100] floor:stone_bricks[100] ceiling:air[100] rail:off
```

`walls:` and `ceiling:` accept air alone or mixed with solid materials, such as `walls:stone_bricks[70],air[30]`. Air clears safe existing blocks at those surface positions without returning their materials. It does not excavate the walkable interior. The `floor:` palette and shared `to:` palette must remain solid.

Install only rails without changing the tunnel lining:

```text
/tunneltool width:2 height:3 rail:only
```

Every palette percentage must be a whole number and each mix must total exactly 100. Namespaced block IDs are supported. Air is allowed only in explicit `walls:` and `ceiling:` palettes. Other unsafe lining choices—including partial-height/non-cube blocks, fluids, falling blocks, rails, pistons, TNT, blocks without inventory items, and block-entity blocks—are rejected.

After configuring it, stand on the intended starting block, face along the tunnel, and pull out the Tunnel Tool (or configured legacy item when Polymer integration is inactive). The tool locks that block line, height, dimension, and cardinal facing, then builds as you walk forward or backward along the same line while facing the original cardinal direction. Small turns within that direction are allowed; turning toward a different cardinal direction pauses edits. `/tunnel` is a shorter alias. Other commands are:

```text
/tunneltool status
/tunneltool on
/tunneltool off
/tunneltool disarm
/tunneltool width <1-5>
/tunneltool height <2-5>
/tunneltool help [verbose]
```

### Alignment

- For an **even-width** tunnel, always stand on the **left center block** relative to the facing captured when you pull out the tool. Rails are placed on the **right center block**, including when you backtrack.
- For an **odd-width** tunnel, stand in the **middle**. Rails are also placed in the **middle** for supported rail widths 3 and 5. Width 1 is lining-only because there is no separate lane for a valid booster torch.

The configure, width, on, status, and help commands repeat this reminder in chat.

Stepping sideways or diagonally off the locked block line, changing height or dimension, or moving beyond the catch-up limit prevents further edits without resetting the run. The tool shows a short reset reminder above the hotbar and sends the full instruction to chat: **“Put the tool away and pull it back out to reset position.”** Returning to the original line, height, and dimension within the catch-up limit allows the run to continue. The lock is captured even if the first slice cannot be built.

Turning toward a different cardinal direction preserves the lock and pauses edits with: **“Put the tool away, rotate, and pull it back out to start a new line.”** Facing the original direction again on the original block line allows the run to resume, including walking backward.

To turn or start on another line, remove the tool from **both hands**, reposition, rotate to face the new direction, and pull it out again. Turning while holding the tool or merely waiting at an invalid position does not reset the lock. Explicit tool configuration, pause/disarm, and config reload retain their existing run-reset behavior.

### Construction and safety rules

- The entire requested interior must already be clear, dry, and non-colliding. Use a full-height floor so the player's block position remains in the clear interior while walking.
- Each slice requires an existing dry block with a sturdy top face under the player's lane; use full-height blocks for the walkway. Walls, ceiling, and the remaining floor positions may start as air; lining modes fill them with the selected materials. Explicit air entries in wall/ceiling palettes leave openings or clear safe existing surface blocks. The tool does not excavate the interior, replace shell fluids, or bypass unsafe-block checks.
- `rail:only` leaves the shell unchanged and needs existing sturdy support beneath each rail and booster torch.
- Tunnels are flat, straight, and cardinal, following the line and facing captured when the tool is pulled out. Remove it from both hands before starting a different line. Finish corners and intersections manually; each automatic run follows a straight shell, and solid wall palettes may close side openings.
- Each slice is fully checked before it changes: unloaded/world-border positions, unsafe blocks, missing inventory, claim permissions, field stones, and owned unbreakable blocks stop the operation. A failed write rolls the slice back before any inventory is charged.
- Survival players pay from ordinary loose inventory stacks and the 27 usable slots of carried shulker boxes. Named or customized materials are preserved and excluded from payment. Replaced shell blocks are not dropped or refunded. Creative players do not consume materials.
- `rail:on` adds rails while lining; `rail:only` changes only ordinary rail and redstone-torch positions. Coordinate-stable booster positions use a powered rail and same-level real redstone torch in the adjacent lane every 8 blocks by default, suitable for occupied and empty carts. Existing powered, detector, and activator rails are never downgraded or replaced. The interval is configurable.
- When Precious Stones (including Vanilla Friendly Protection and Shop) is installed, all three tools check its build permissions and refuse to alter field stones or owned unbreakable blocks. If that optional integration cannot load, edits are denied instead of bypassing protection.

Tunnel settings are written to `config/block-tone-tunnel-tools.json`:

```json
{
  "configVersion": 1,
  "defaultWidth": 3,
  "defaultHeight": 3,
  "poweredRailInterval": 8,
  "maxCatchUp": 8
}
```

The active tunnel tool is the Tunnel Tool with Polymer integration, otherwise the shared legacy item from `selectionItem` in `config/block-tone-path-tools.json`. `/blocktools reload` reloads both files and resets active trails. No Precious Stones configuration or world data is read unless its separate mod is installed for permission checks.


## Help

```text
/blocktools help
/blocktools help verbose
/blocktone help
/blocktone help verbose
/pathtool help
/pathtool help verbose
/tunneltool help verbose
```

## Server configuration

The first launch creates `config/block-tone-path-tools.json`:

```json
{
  "configVersion": 4,
  "enablePolymerTools": true,
  "selectionItem": "minecraft:breeze_rod",
  "defaultPathWidth": 3,
  "maxToneBlocks": 65536,
  "pathVerticalSearch": 6,
  "maxPathCatchUp": 8
}
```

`enablePolymerTools` defaults to true and takes effect only with both optional Polymer modules. `selectionItem` is preserved as the fallback trigger for Block Toning Tool, Path Tool, and Tunnel Tool; it defaults to the Breeze Rod. Schema 4 adds the integration setting without changing existing custom assignments or widths. Version 1.4.1 removes the separate legacy `pathItem` setting when the configuration is rewritten. Width is always clamped to 1–5, and the other safety values are bounded. Version 1.4 migrates an unchanged legacy default width of 1 to the new default of 3; other configured widths are preserved. Administrators can run `/blocktools reload` after the file is edited.

## Build

Use a complete **Java 25 JDK** (including `javac`) and set `JAVA_HOME` to it. The Gradle wrapper downloads the declared dependencies on the first build. Windows users can run `gradlew.bat clean check build`.

```bash
./gradlew clean check build
```

A development-only graphical single-player walk test can be run with `./gradlew --offline --no-daemon runClient -PclientQa -PwithPolymer`. It creates an isolated world in `build/clientqa-run`, drives actual movement/hotbar input, checks tunnel and inventory behavior, and saves screenshots/results. Its QA mod is never packaged in the installable release.

The installable JAR is written to `build/libs/block-tone-path-tunnel-tools-1.6.9+26.3.jar`. Use `-PwithPolymer` to add the optional modules to development runtime; the default runtime deliberately excludes Polymer to verify fallback. The `check` task runs palette parsing, automatic/additive percentages, shuffle state preservation, Path Tool filters/bracket syntax/default migration, named-selector/remainder behavior, two-corner cuboid geometry and duplicate-event suppression, autocomplete, calculate-distribution, mixed loose/shulker and multi-material shulker reservations, material-ledger behavior, message styling, and stair-orientation regression checks.

Command messages use section-specific headings and colors: gold `[Block Tools]`, light-purple `[Block Toning Tool]`, aqua `[Path Tool]`, and dark aqua `[Tunnel Tool]`. Commands are gray, information labels are yellow, and X/Y/Z coordinates are red/green/aqua. Errors retain the section color in the heading and use red message text.

## Notes for server owners

The mod uses native spawn/world interaction checks and an optional Precious Stones permission bridge. Direct server-side bulk changes cannot universally invoke every third-party claim mod's placement hooks. Test it with the protection solution used by your server. The configurable tone limit and short path catch-up limit prevent unbounded operations.

Expanded types and light-source mounting verification: [QA-1.6.8.md](QA-1.6.8.md).

Previous type filters and shape preservation verification: [QA-1.6.7.md](QA-1.6.7.md).

See [QA.md](QA.md) for the audit findings, automated checks, live server results, and remaining manual checks.

26.3 verification: [QA-26.3.md](QA-26.3.md).

Air destination, negated-source, and support verification: [QA-1.6.9.md](QA-1.6.9.md).

## Source and credits

Published by THENATHE under the [MIT license](LICENSE). Development was assisted by ChatGPT/Codex. Tool sprite artwork and generation/source details are documented in [ASSETS.md](ASSETS.md). Minecraft, Fabric, and optional Polymer remain external dependencies.

The repository includes production code/resources, logic tests, live-server QA, graphical client QA, original artwork, Gradle wrapper/build definitions, and historical verification evidence. Public evidence replaces personal filesystem paths with placeholders; untouched originals remain in local records. See [publication verification](PUBLICATION-1.6.9.md) for the source export and release provenance.
