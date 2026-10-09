# 1.6.9+26.3 — 2026-10-08

- Allow air destinations in Blocktone toning and calculate preview/apply, alone or mixed with block palettes. Accept bare/namespaced air, explicit bracket percentages, automatic air, and the regular-toning legacy percentage form.
- Treat air as free in material accounting and return recoverable cleared source items through the existing net transaction, including two items for double slabs. Keep exact quotas, unchanged remainders, and all-or-nothing shortage/support checks for mixed clearing/replacement operations.
- In calculate mode, fit requested automatic block materials first, then use automatic air for the remaining capacity; explicit air percentages reserve fixed quotas.
- Add source exclusion with `from:not(grass_block,dirt)`, `from:not(type:stairs,slabs)`, and Blocktone `type:not(stairs,slabs)`. Negation retains the normal non-air, pure-liquid opt-in, unsafe-block, and protection safeguards; reject nested or malformed expressions.
- Validate support for selected surviving blocks when clearing adjacent supports. Waterlogged blocks cleared to air lose their contained water, with normal Minecraft fluid and neighbor updates afterward.
- Update help and README with clearing examples, canonical Grass Block IDs, exclusion syntax, palette accounting, calculate behavior, and the distinction between `type:air` sources and `to:air` destinations. Path Tool air rejection and Tunnel Tool air rules remain unchanged.

# 1.6.8+26.3 — 2026-10-08

- Add distinct `bars` selection for iron and every weathered/waxed copper-bar variant; refine `panes` to glass panes. Use `type:panes,bars` for the combined selection previously matched by `panes`.
- Add `railings` (`railing` / `fences_walls_bars`) as the exact union of fences, walls, and bars, excluding glass panes and fence gates.
- Add `pillars`, `buttons`, `pressure_plates`, `rails`, `chains`, `ladders`, `leaves`, `carpets`, `torches`, `lanterns`, and `light_sources`, with singular aliases, comma unions, and autocomplete.
- Match light-emitting block families even when their current state is unlit, including redstone torches, lamps, candles, and copper bulbs. Retain block-entity, multipart, unbreakable, and explicit-fluid safeguards.
- Map torch palette entries to their floor/wall counterpart while retaining wall facing; preserve lantern hanging/standing and waterlogged states. Reject mounting conversions the destination cannot support.
- Validate support against the complete planned edit before inventory consumption so unsupported placements fail atomically. Continue copying compatible state properties and applying vanilla connection/support rules.
- Expand `/blocktone help`, verbose help, and README guidance with every type, railing examples, light-source mounting examples, shape limitations, and nested source-filter syntax.
- Keep Minecraft/dependency versions, optional Polymer integration, saved settings, and tool IDs unchanged. No ported counterpart exists for this standalone mod update.

# 1.6.7+26.3 — 2026-10-08

- Add position-independent Blocktone `type:` category filters, comma-separated unions, singular aliases, validation, and Tab completion; support calculate preview/apply and nested `from:type:` syntax.
- Preserve compatible blockstate properties during toning and calculate-apply instead of placing default states. Mixed-material stair roofs retain facing, half and corner shape; slabs, trapdoors, fences, walls, panes, gates and logs retain supported states and connections.
- Include waterlogged structural sources and retain waterlogging where supported. Pure liquid sources remain opt-in through the liquids category or explicit water/lava IDs; air remains opt-in.
- Charge and refund two slab items when preserving double slabs; snapshot full destination states before both inventory planning and placement.
- Keep Minecraft/dependency versions, optional Polymer integration, tool IDs, saved configuration, and historical releases unchanged. No ported counterpart exists for this standalone mod update.

# 1.6.6+26.3 — 2026-09-21

- Port to Minecraft 26.3, Fabric Loader 0.19.5 and Fabric API 0.161.0.
- Preserve existing registry IDs, configuration and saved data.

# 1.6.5+26.2 — 2026-09-17

- Add native 32×32 sprites for Block Toning Tool, Path Tool, and Tunnel Tool with finer pixel detail, preserving their designs and the original 16×16 assets.
- Add public per-player `/blocktools auto`, `/blocktools 16x16`, and `/blocktools 32x32` cosmetic commands; retain administrator restrictions on the bare root, help, tool grants, and reload.
- Save manual sprite choices per player and world; apply the viewer's choice to native client models and Polymer item models without changing item IDs or gameplay.
- Detect the client-selected iron ingot, copper ingot, and flint animation-frame resolutions on connection and resource reload. Auto uses 32×32 only when every sample is square and at least 32×32, otherwise 16×16; larger packs use the available 32×32 sprites.
- Default unreported clients to 16×16 and allow vanilla clients to select 32×32 manually using the updated Polymer resource pack.

# 1.6.4+26.2 — 2026-09-17

- Promote the tunnel-inclusive mod to Block Tone, Path & Tunnel Tools, removing Experimental and tunnel-test from the current release identity and naming its JAR `block-tone-path-tunnel-tools-1.6.4+26.2.jar`.
- Retire the non-tunnel 1.5.4+26.2 source into the workspace's mirrored Minecraft source backups.
- Standardize item names and tool interface headings as Block Toning Tool, Path Tool, and Tunnel Tool; retain the existing mod ID, item IDs, textures, saved stacks, and configuration paths.
- Add all three optional Polymer tools to the Tools & Utilities Creative tab and Creative search, including Polymer's Creative catalog integration.
- Unlock all three crafting recipes by default while Polymer integration is active, without requiring iron acquisition; continue excluding tool recipes when integration is disabled or absent.
- Restrict `/blocktools` and every subcommand to Minecraft administrators (operator level 3 or higher), while keeping `/blocktone`, `/pathtool`, `/tunneltool`, and `/tunnel` public.
- Allow Tunnel Tool to build its shell into empty space: only the player's supporting floor block must already exist for each slice. Preserve the clear-interior requirement, material costs, permission checks, and sturdy rail/torch support rules.
- Accept air in explicit wall and ceiling palettes, alone or in weighted mixes, to leave surfaces open or clear safe existing surface blocks without refunds. Keep floor and shared palettes solid, and never excavate the walkable interior.
- Lock Tunnel Tool to the starting block line, height, dimension, and facing when pulled out, including if the first slice fails. Allow forward/backward progress while facing the original cardinal direction, including small turns within that direction; pause on a different cardinal facing, off-line movement, or excessive catch-up without silently resetting the run.
- Keep tunnel previews aligned with the locked run and show “Put the tool away and pull it back out to reset position.” when a positional reset is needed. Facing changes show “Put the tool away, rotate, and pull it back out to start a new line.” Remove the tool from both hands, then reposition and rotate before pulling it out to begin a new run.
- Keep reset reminders readable at the default client window size; send full instructions to wrapping chat once per reason in each held run.

# 1.6.3-tunnel-test+26.2 — 2026-09-16

- Rename displayed items to Toning Tool, Path Tool, and Tunnel Tool while retaining registry IDs, sprites, saved stacks, and behavior.
- Add distinct survival crafting recipes and recipe-book unlocks on iron acquisition.
- Filter both recipes and advancements before decoding whenever Polymer integration is inactive or absent.
- Refresh datapack recipes asynchronously when `/blocktools reload` changes integration mode; serialize rapid toggles and report completion or failure.
- Show human-readable tool names in help/status and grant messages.
- Test live recipe matching, exact outputs/names, wrong ingredients, vanilla collisions, conditional startup, and queued off/on recipe refreshes.

# 1.6.2-tunnel-test+26.2 — 2026-09-16

- Add default-enabled optional Polymer integration, detected only when Core and Resource Pack modules are installed.
- Add separate Mortar Trowel, Garden Trowel, and Prospecting Pick items, each using a 16×16 pixel-locked sprite and iron-nugget client base.
- Disable legacy selection/path/tunnel assignments while integration is active; preserve and automatically restore the exact configured assignment when disabled or Polymer is missing.
- Add `/blocktools tools` for all players and current-role help/status output; support live config toggles without clearing palettes or corners.
- Migrate main settings to schema 4; register items while Polymer is present even if tools are disabled to retain saved stacks.
- Verify optional-dependency startup, role isolation, legacy migration, live toggles, client stack conversion, and generated resource-pack assets in disposable servers.

# 1.6.1-tunnel-test+26.2 — 2026-09-09

- Add private particle selection interfaces for Blocktone, Path Tool, and Tunnel Tool using the Precious Stones visualization approach.
- Automatically preview corner selection, tool configuration, dimension changes to width/height, and pause/resume; add `visualize [off]` commands and clickable refresh/hide controls.
- Show exact Blocktone outer bounds, two colored corner markers, dimensions, volume and limit status; refresh repeated corner clicks without flooding chat.
- Show loaded terrain footprints for three path rows and tunnel interior/lining/rail/player-lane alignment for three slices.
- Keep previews read-only and available while paused; bound emissions, expire after 20 seconds, and clean up on player/server lifecycle changes.
- Add exhaustive preview geometry tests and live command/callback tests preserving complete inventory stacks and nearby world blocks.

# 1.6.0-tunnel-test+26.2 — 2026-09-09

- Copy Block Tone & Path Tools 1.5.2 and integrate pre-dug tunnel lining, per-surface palettes, rail modes, alignment, dimensions, and autocomplete.
- Keep Blocktone identity and configuration; add `block-tone-tunnel-tools.json` for tunnel dimensions and booster/catch-up limits. All tools share the selection item.
- Pause Path Tool and Tunnel Tool mutually while preserving settings. Register help, reload, disconnect, and server-stop cleanup.
- Replace duplicated tunnel inventory code with shared atomic material reservations, fixing over-consumption after a stack emptied.
- Enforce build ability, world/chunk boundaries, and optional Precious Stones permissions for direct edits.
- Harden bulk world transactions, counted-state refunds, multipart blocks, custom inventory data, and parser validation.
- Expand automated regressions and add a separate development-only live server QA harness.

# Changelog

## 1.5.2+26.2

- Fixed calc-apply falsely reporting materials missing when the preview had counted full stacks inside carried shulker boxes.
- Preserved each inner stack's item identity before shrinking it; Minecraft 26.2 reports a fully consumed copied stack as empty/air immediately.
- Unified calc distribution planning, availability reporting, and calc-apply consumption around one atomic inventory snapshot.
- Added exact regressions for 200 Granite plus 155 Polished Granite in one shulker, mixed loose/shulker consumption order, and no-partial-write shortages.

## 1.5.1+26.2

- Added `/blocktone shuffle from:<blocks>` to rerandomize selected matching blocks without consuming inventory, while preserving exact material counts and full block states.
- Added live source-block autocomplete and regressions for shuffle parsing, state preservation, and guaranteed movement when at least two distinct states exist.
- Kept both selected corners after successful Blocktone, calculate-apply, and shuffle operations so an area can be reused until explicitly cleared.
- Gave displayed commands gray text, information labels yellow text, and X/Y/Z coordinates distinct red/green/aqua colors while retaining the existing section-heading palette.

## 1.5.0+26.2

- Standardized every material configuration command on a mandatory `to:` selector and an optional, position-independent `from:` selector.
- Made a missing `from:` equivalent to `from:any` (every safe non-air block), and added `from:any+air`, `from:air`, and explicit source lists containing air.
- Removed the special first-material filler rule from calculate mode. Every automatic material now uses carried inventory first, proportionally when needed, then requested blocks already present in the selection.
- Preserved scarce placed accents while allowing the dominant existing requested material to supply the natural filler, such as 9,000 existing Bricks plus 500 carried Granite and 500 carried Polished Granite in a 10,000-block selection.
- Added bare and blank-weight automatic materials to regular and calculate Blocktone palettes. `block` and `block[]` share the percentage remaining after explicit `block[percent]` entries.
- Retained existing blocks when calculate mode cannot supply its full automatic capacity, and retained the prior partial-overlay behavior when no automatic entries are present.
- Reformatted Blocktone previews/results, selection status, and Path Tool status/configuration as readable multiline summaries.
- Added regressions for mandatory `to:` enforcement, every `from:`/`to:` order and omission case, air-aware filters, automatic remainder splitting, equal carried-material fitting, placed filler inference, and mixed explicit/automatic palettes.

## 1.4.2+26.2

- Suppressed repeated Breeze Rod callbacks for an unchanged selection point, preventing left-click chat spam while continuing to cancel block damage.

## 1.4.1+26.2

- Consolidated Blocktone selection and the Path Tool onto the same configured tool, which defaults to the Breeze Rod.
- Removed the separate Spectral Arrow `pathItem` configuration; version 3 rewrites existing configuration without that legacy field.

## 1.4.0+26.2

- Replaced Path Tool `block:percent` palettes with consistent `block[percent]` syntax for normal and stair materials.
- Added position-independent Path Tool `from:`, `to:`, and `stairs:` clauses with live block autocomplete; omitting `from:` allows any safe terrain material.
- Added per-player Path Tool source filters while preserving downhill stair retrofits of blocks just placed by the tool.
- Changed the new/default Path Tool width to 3 and migrated unchanged legacy width-1 defaults while preserving other configured values.
- Removed the false “inventory changed while preparing” execution path by combining validation and consumption into one no-partial-write reservation.
- Made shulker counting and consumption share the same 27 usable slots while preserving generic component data beyond that boundary.
- Added regressions for bracket parsing, Path Tool selector order/defaults/autocomplete, width migration, and shulker slot boundaries.

## 1.3.1+26.2

- Made left-click always set/update selection Point 1 and right-click always set/update Point 2.
- Preserved the opposite point when one boundary is reselected; changing dimensions safely clears the point from the prior dimension.
- Updated selection status and help to label both point slots independently.

## 1.3.0+26.2

- Added position-independent `from:` and `to:` selectors to `/blocktone` and `/blocktone calc`, while retaining the existing command forms.
- Added partial named palettes such as `to:iron_block[10],quartz_block[10]`; destination percentages may total up to 100 and every unallocated position retains its own existing block.
- Added exact largest-remainder rounding so destination quotas plus retained positions always equal the eligible target count, including awkward selection sizes.
- Added named-selector, canonical block, namespaced ID, comma-entry, and reverse-order autocomplete.
- Added regression coverage for selector order, partial distributions, uneven rounding, retained blocks, invalid totals, and named autocomplete contexts.

## 1.2.0+26.2

- Added left-click selection alongside right-click selection for the configured selector; either interaction is consumed so the selected block is not damaged or used.
- Added `/pathtool disarm`, which disables the path tool and clears both palettes while preserving width. `/pathtool off` continues to preserve palettes for later resume.
- Added separate gold `[Block Tools]`, light-purple `[Blocktone]`, and aqua `[Path Tools]` message headings.
- Fixed a server-tick crash caused when path material inventory no longer matched an earlier preflight after world changes.
- Made material consumption a no-partial-write inventory transaction and moved it before world edits; a late mismatch now pauses safely with an in-game error.

## 1.1.0+26.2

- Changed block toning to a two-corner inclusive cuboid selection.
- Added `from all`, single-block, and comma-separated multi-block source filters to `/blocktone`.
- Added read-only `/blocktone calc` previews and explicit `/blocktone calc apply` execution, using a filler plus inventory-derived accent distribution.
- Counted requested accent blocks already placed in the matching target set before adding carried inventory quantities.
- Netted every toning, path, and stair transaction so recovered blocks fund same-item placements and all surplus is returned to inventory, carried shulkers, or a safe player drop.
- Added calculate/source autocomplete and distribution/material-ledger regression coverage.

## 1.0.1+26.2

- Removed friendly typo/material aliases; palettes now require canonical registry block IDs.
- Added live registry-backed block autocomplete for every comma-separated `/blocktone` and `/pathtool` material, including namespaced and modded blocks.
- Added downhill stairs. Uphill stairs occupy/facing toward the new upper block; downhill stairs retrofit the previous upper block with its high side facing back uphill.
- Added regression coverage for canonical IDs, autocomplete contexts, stair-only completion, stair position, and all four cardinal stair orientations.

## 1.0.0+26.2

- Added three-point, exact-percentage block toning with atomic survival inventory checks.
- Added material consumption from loose inventory and top-level shulker boxes.
- Added terrain-following paths from a held Spectral Arrow, configurable widths 1–5, vegetation-aware surface detection, and gap-safe tracking.
- Added free Dirt Path conversion and optional weighted stairs on one-block ascents.
- Added unrestricted vanilla commands and verbose in-game help.
- Added dedicated-server vanilla-client compatibility and integrated single-player support without client-specific code.
