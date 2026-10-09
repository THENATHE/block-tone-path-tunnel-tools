> Historical verification of this version, recorded before public publication. Local backup references describe retained private originals. Reproduction commands below use the configured Java 25 JDK; use `--offline` only after dependencies are cached.

# Block Tone, Path & Tunnel Tools 1.6.7+26.3 verification

Verified 2026-10-08 against Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25, and optional Polymer Core/Resource Pack 0.18.2+26.3. Gradle 9.6.0, Loom 1.17.20; compiler Microsoft JDK 25, game runtime OpenJDK 25, bytecode target 25. No dependency versions or integrations were replaced.

## Results

- `clean check build`: PASS, including logic, geometry, permission, integration, inventory and transactional rollback suites.
- New type/state logic suite: PASS for 573 complete state combinations, aliases/unions/errors, incompatible property domains, category boundaries, and autocomplete offsets/order/source conflicts.
- Disposable live server **without Polymer**: PASS, **12,710 assertions**, zero failed cases.
- Disposable live server **with Polymer**: PASS, **12,940 assertions**, zero failed cases.
- Release JAR ZIP integrity, metadata, version, required new classes, QA exclusion, no bundled dependencies, SHA-256 and unchanged previous release: PASS.

`LiveToneShapeQa` drives actual server command dispatch and block/inventory writes with a synthetic server player. Its roof has 40 naturally valid stair motifs: every cardinal facing, both halves and all five straight/inner/outer shapes; 72 stairs include corner partners and three original materials. A two-material 50/50 palette changes exactly 36 stairs to each material. Every stair retains its complete properties after placement/neighbor callbacks, and foundations, other full blocks and slabs inside the same selected cuboid remain unchanged. Reapplying the palette and the singular/nested source syntax preserve the same geometry.

Additional live cases cover calculate preview/apply, bottom/top/double slabs, exact survival charges/refunds, repeat no-op accounting, trapdoor facing/half/open/powered state, natural fence/wall/pane connections, in-wall powered gates, log axes, waterlogged replacements in six shaped families, pure-fluid vs waterlogged category boundaries, explicit air and liquids, the requested `type:stairs,slabs,blocks,liquids,air` list, and atomic invalid-selector/material-shortage failures including insufficient double-slab payment. Existing tunnel, path, movement, recipe, config preservation and optional-tool checks also passed.

These tests exercise server behavior; no new graphical client session or connected native/vanilla-client gameplay test was run. They do not claim fresh verification of third-party claim mods or arbitrary modded block behavior. State copying is limited to properties supported by the destination; vanilla updates maintain valid connections/support/redstone. Use stair destinations for stair roofs. Existing shuffle still permutes whole states and is not a position-preserving recoloring command.

## Evidence and reproduction

Reports/logs: `qa/results/1.6.7+26.3/`. Development-only fixtures: `src/qa/java/com/thenathe/blocktools/qa/LiveToneShapeQa.java` and `src/test/java/com/thenathe/blocktools/material/TypeAndStateTest.java`.

```bash
./gradlew --no-daemon clean check build
./gradlew --no-daemon runServer -PliveQa -PqaRunDir=build/qa-type-shape-plain
./gradlew --no-daemon runServer -PliveQa -PwithPolymer -PqaRunDir=build/qa-type-shape-polymer
```

## Artifact and provenance

- Artifact: `block-tone-path-tunnel-tools-1.6.7+26.3.jar`; SHA-256 `58228e8661e46fb2eb7bbbdb2742b26e0dfae6668217d57d200548cef4a584ea`.
- Local source: `Minecraft/block-tone-path-tunnel-tools-26.2/` (folder name is historical; target remains 26.3).
- This is the maintained standalone main mod update. No separate ChatGPT-ported counterpart exists for this update; no shim, dependency port or paused SSO track was changed. No public repository/release was created.
- Pre-change source snapshot: `Backups/General Utilities (Non-WP)/Minecraft/block-tone-path-tunnel-tools-26.2/Snapshots/2026-10-08-before-1.6.7-type-filters/`.
- Retained original 1.6.6+26.3 release SHA-256: `4bdda4ab4e9b052f61d6f55ae19e9ba695f327a22ef913989337077082a61c99`.
- Source is a local non-Git tree. The release's `SOURCE-SHA256.sha256` records each compile input; SHA-256 of that manifest is `0d1d137f95d844f9c0d9baeeb366b521bec81876e85af1297d49a46dd25c30ff`. Matching sources JAR remains in `build/libs/` for development use.
