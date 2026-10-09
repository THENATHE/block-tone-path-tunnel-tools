> Historical verification of this version, recorded before public publication. Local backup references describe retained private originals. Reproduction commands below use the configured Java 25 JDK; use `--offline` only after dependencies are cached.

# Block Tone, Path & Tunnel Tools 1.6.9+26.3 verification

Verified **2026-10-08** on Minecraft **26.3**, Fabric Loader **0.19.5**, Fabric API **0.161.0+26.3**, Java **25**, with and without optional Polymer Core/Resource Pack **0.18.2+26.3**. Gradle 9.6.0, Loom 1.17.20, Microsoft JDK 25 built the Java 25 bytecode; OpenJDK 25 ran the game. Dependencies are unchanged from 1.6.8.

## Results

- Final `clean check build qaJar`: PASS. Air palette, legacy/namespaced syntax, percentage quotas, partial remainders, free calculate fallback, source negation, invalid syntax, and autocomplete tests passed alongside all existing regression suites.
- Geometry/state regressions: 573 original shape states, 1,241 expanded positional/connection states, and 120 torch mounting replacements: PASS.
- Final disposable live server without Polymer: **13,176 assertions**, zero failed cases.
- Final disposable live server with Polymer: **13,406 assertions**, zero failed cases.
- JAR integrity, unchanged dependency metadata, compiled help, absence of QA classes/bundled dependencies, checksum and source manifest: PASS. Prior releases retained unchanged.

## New live gameplay coverage

`LiveAirDestinationQa` uses actual dispatched commands, a synthetic server player, and a loaded server world:

- Air-only removal with no carried items; namespaced, bracket and legacy forms; exact mixed percentages and unchanged remainder.
- Stair/slab/trapdoor source filtering, double-slab two-item refunds, air-to-air no-op without duplication, and clearing contained water from waterlogged blocks.
- Read-only calculate preview and apply; free automatic air after carried and existing requested materials; explicit air quotas; creative equal distribution.
- Default protection of block entities, multipart blocks, unbreakable blocks and pure fluids; explicit liquid selection can clear fluids.
- Atomic shortage rejection for mixed paid/removal palettes, itemless cave/void air rejection, and rollback when removing a support would invalidate a selected surviving torch.
- `from:not(grass_block,dirt) to:air` preserves those exact blocks and pure fluids while clearing other eligible blocks; `type:not(stairs,slabs)` preserves the excluded shapes. Invalid or unclosed filters leave inventory/world untouched.

Earlier roof, attachment, inventory, optional Polymer, path/tunnel, permissions, configuration and integration cases also passed. The 72-stair mixed roof retains facings, halves, corners and foundations.

## Scope

Authoritative server commands, world states and inventory were exercised. No new graphical session or connected native/vanilla-client gameplay session was run. Arbitrary modded blocks and third-party protection integrations are not freshly verified.

Negation selects eligible non-air blocks and never implicitly opts into pure fluids. Compatible properties copy only when supported by the destination; vanilla physics, support, connections and redstone updates remain active. Unselected attached neighbors may react normally to removed supports. Clearing a waterlogged block removes its contained water. Automatic air in survival calculate fills remaining capacity after requested material allocations; regular toning shares automatic percentages evenly. Path Tool still rejects air destinations; Tunnel Tool rules are unchanged.

## Evidence and reproduction

Evidence: `qa/results/1.6.9+26.3/`. New fixtures: `src/test/java/com/thenathe/blocktools/command/AirDestinationTest.java` and `src/qa/java/com/thenathe/blocktools/qa/LiveAirDestinationQa.java`.

```bash
./gradlew --no-daemon clean check build qaJar
./gradlew --no-daemon runServer -PliveQa -PqaRunDir=build/qa-air-negation-plain
./gradlew --no-daemon runServer -PliveQa -PwithPolymer -PqaRunDir=build/qa-air-negation-polymer
```

## Provenance

Artifact `block-tone-path-tunnel-tools-1.6.9+26.3.jar`; SHA-256 `1c284ee1463b9f4c476ecd3190bb9a3a6b83ec8051e139893233e2f7c34b3c2d`. Local source: `Minecraft/block-tone-path-tunnel-tools-26.2/` (historical folder name; target 26.3). Standalone main-mod update; no ported counterpart exists, no dependency port or public publication was made.

Pre-update snapshot: `Backups/General Utilities (Non-WP)/Minecraft/block-tone-path-tunnel-tools-26.2/Snapshots/2026-10-08-before-1.6.9-air-negation/`. Previous compile inputs were verified against the 1.6.8 source manifest. Prior 1.6.8 JAR SHA-256: `d201e7a5796a6d80eea8cf1d0dd224bf90d8b06e2841c58a6085faa0376efe35`. Prior release folders remain available.

Local non-Git source; `SOURCE-SHA256.sha256` identifies all main compile inputs. Manifest SHA-256 `6ef83081a048717a1f5aa181250bb508cd89ab33aada0798e248093e8a9f5b81`. Development sources JAR remains in `build/libs/`.
