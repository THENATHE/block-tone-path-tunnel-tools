> Historical verification of this version, recorded before public publication. Local backup references describe retained private originals. Reproduction commands below use the configured Java 25 JDK; use `--offline` only after dependencies are cached.

# Block Tone, Path & Tunnel Tools 1.6.8+26.3 verification

Verified **2026-10-08** against Minecraft **26.3**, Fabric Loader **0.19.5**, Fabric API **0.161.0+26.3**, Java **25**, and optional Polymer Core/Resource Pack **0.18.2+26.3**. Build tools: Gradle 9.6.0, Loom 1.17.20, Microsoft JDK 25; bytecode target 25. Game runtime: OpenJDK 25. Required and optional dependency versions are unchanged from 1.6.7.

## Results

- Final `clean check build qaJar`: PASS. Includes the existing inventory, transaction rollback, permission, geometry and integration regressions.
- Original shape suite: **573 states** PASS; expanded attachment/lighting/connection suite: **1,241 states** PASS; **120 torch mounting replacements** PASS. Registry-wide category membership, light emission across every block's state definition, aliases, mixed filters and autocomplete passed.
- Final disposable live server **without Polymer**: PASS, **13,114 assertions**, zero failed cases.
- Final disposable live server **with Polymer**: PASS, **13,344 assertions**, zero failed cases.
- Packaged JAR ZIP integrity, metadata/dependency identity, required classes, updated compiled help, exclusion of QA/bundled dependencies, source manifest and JAR checksum: PASS. Prior release bytes retained unchanged.

## Gameplay cases

`LiveExpandedTypesQa` dispatches actual commands with a synthetic server player in a real loaded server world. It verifies:

- Separate bars and glass panes; iron and all eight weathered/waxed copper-bar variants; exact `railings` union of fences/walls/bars, excluding panes/gates; comma union and aliases.
- Natural copper-bar connections and waterlogging, exact survival material payment/refund, and repeated no-op replacement.
- Light emitters including unlit lamps and redstone torches; preserved campfire block entities and default fluid exclusions; explicit liquid opt-in.
- Standing and wall torches across regular, soul and redstone families, all four wall facings, automatic counterpart resolution, valid support, correct item charges/refunds, repeated no-ops and calculate preview/apply using existing wall-torch materials.
- Standing/hanging and dry/waterlogged lanterns; same supports and complete properties after callbacks; exact item settlement.
- Buttons on every wall/floor/ceiling face and cardinal facing; chains and pillars on each axis; wall-facing ladders; straight/curved rail states; pressure plates, leaves and carpets.
- Atomic rejection of wall-torch to lantern, hanging-lantern to torch, shortages, and unsupported placement. A simultaneous-edit case removes a selected unchanged torch's support: the whole operation rolls back before spending items. A standing torch-to-lantern-to-torch round trip preserves support and settles materials correctly.

The original 72-stair mixed roof test also passes: every facing, both halves, every straight/inner/outer corner, exact 50/50 quotas, unchanged full-block foundations, and repeated shape-preserving toning. Existing path/tunnel, movement, visual-interface, configuration preservation, recipes and optional tools remain covered by the same harness.

## Scope and behavior

Tests exercise authoritative server commands, world state and inventory; no new graphical client session or connected native/vanilla-client gameplay test was run. They do not claim fresh testing of arbitrary modded blocks or third-party protection integrations.

`type:light_sources` matches any block that can emit light in any state, including temporarily unlit variants. Existing safety rules still exclude block entities, multipart/unbreakable sources and pure fluids without explicit liquid selection. `panes` now means glass panes; use `type:panes,bars` for the broader 1.6.7 meaning. `railings` is a selector union, not automatic shape matching for a mixed destination palette.

Supported destination properties copy by name/value; vanilla connection/support/redstone updates remain active. Use matching destination families to retain geometry. Torch floor/wall counterparts are resolved by the shared item, and lantern hanging state is copied. Impossible wall/ceiling mounting requests fail atomically. Other unsupported properties cannot be preserved by a destination that lacks them. Shuffle retains its existing whole-state permutation behavior and is not position-preserving recoloring.

## Evidence and reproduction

Reports: `qa/results/1.6.8+26.3/`. Fixtures: `src/qa/java/com/thenathe/blocktools/qa/LiveExpandedTypesQa.java`, `LiveToneShapeQa.java`, and `src/test/java/com/thenathe/blocktools/material/ExpandedTypesTest.java`.

```bash
./gradlew --no-daemon clean check build qaJar
./gradlew --no-daemon runServer -PliveQa -PqaRunDir=build/qa-expanded-types-plain
./gradlew --no-daemon runServer -PliveQa -PwithPolymer -PqaRunDir=build/qa-expanded-types-polymer
```

## Artifact and provenance

- Installable artifact: `block-tone-path-tunnel-tools-1.6.8+26.3.jar`; SHA-256 `d201e7a5796a6d80eea8cf1d0dd224bf90d8b06e2841c58a6085faa0376efe35`.
- Local source: `Minecraft/block-tone-path-tunnel-tools-26.2/`; folder name is historical and target remains 26.3. This is the maintained standalone main mod update; no separate ported counterpart exists. No new shim/dependency port, paused SSO track or public repository/release was created.
- Pre-update source: `Backups/General Utilities (Non-WP)/Minecraft/block-tone-path-tunnel-tools-26.2/Snapshots/2026-10-08-before-1.6.8-expanded-types/`. Every previous release compile input was verified against its retained `SOURCE-SHA256.sha256`.
- Prior 1.6.7 release SHA-256: `58228e8661e46fb2eb7bbbdb2742b26e0dfae6668217d57d200548cef4a584ea`. Prior versions remain available in their release folders.
- Source is a local non-Git tree. The new release's `SOURCE-SHA256.sha256` identifies all main compile inputs; manifest SHA-256 `08a9c5d3038bd27f20e32a85b80ab21e2817646ac833bf1603474e436bc28f11`. Matching development sources JAR remains in `build/libs/`.
