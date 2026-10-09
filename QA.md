# Release QA — 1.6.5+26.2

Verified 2026-09-17 on Java 25 and Minecraft 26.2. `check build` and the dedicated and graphical client suites passed. The original 16×16 textures remain unchanged; the release includes separate 32×32 textures and models.

| Configuration | Result | Evidence |
| --- | --- | --- |
| Dedicated server, Polymer enabled and toggled | 20 cases, 679 assertions, zero failures | [Results](docs/qa-results/release-1.6.5/polymer.txt) · [Log](docs/qa-results/release-1.6.5/polymer-server.log) |
| Dedicated server, Polymer absent | 20 cases, 449 assertions, zero failures | [Results](docs/qa-results/release-1.6.5/absent.txt) · [Log](docs/qa-results/release-1.6.5/absent-server.log) |
| Real client sprite selection | 37 assertions, zero failures | [Results](docs/qa-results/release-1.6.5/client/sprite-clientqa-result.txt) |
| Real client tunnel movement regression | 79 assertions, zero failures | [Results](docs/qa-results/release-1.6.5/client/clientqa-result.txt) |

The graphical sprite test changes actual selected resource packs and reloads the running client. It verifies default 16×16, automatic 32×32, conservative mixed-pack fallback, 64×64 packs selecting the available 32×32 variant, and manual overrides taking priority. For all three tools, the native renderer's actual baked atlas sprite identifier and pixel width are checked, including after a real integrated-server shutdown, world reopen, and reconnect with a saved manual override. The client includes both model sets locally; this run does not depend on accepting a server resource pack. Server tests separately verify Polymer 16/32 model encoding, unchanged stack identity, no-pack fallback, generated pack assets, independent player choices, disk persistence, session-report cleanup, and operation without Polymer.

Permission tests accept only the three personal cosmetic subcommands for nonadmins; the bare root, help, tools, and reload still reject permission levels 0–2. Existing inventory, recipe, Creative, survival, tunnel, and protection checks remain included. The real movement regression again walks **11.865 blocks**, builds **13 enclosed slices**, checks sideways/rotation restrictions and hotbar release/re-equip, then builds **8 air-wall/ceiling slices**.

[Graphical build/run log](docs/qa-results/release-1.6.5/client/client.log) · [16×16 screenshot](docs/qa-results/release-1.6.5/client/0000_sprites-auto16.png) · [32×32 screenshot](docs/qa-results/release-1.6.5/client/0001_sprites-auto32.png) · [Tunnel screenshot](docs/qa-results/release-1.6.5/client/0002_tunnel-straight-client-walk.png) · [Machine-readable results and release hash](docs/qa-results/release-1.6.5/result.json).

Reproduce with `./gradlew check build runClient -PclientQa -PwithPolymer --offline`. The development client harness creates isolated worlds and synthetic texture-size packs and is excluded from the release JAR. Its reload helper waits for the loading overlay and integrated-server pause to finish before asserting packet-driven changes. Dedicated runs use `./gradlew runServer -PliveQa -PqaRunDir=build/qa-sprites-polymer -PqaJava=/usr/lib/jvm/java-25-openjdk/bin/java -PwithPolymer --offline`; omit `-PwithPolymer` and choose a separate run directory for the absent case.

---

# Release QA — 1.6.4+26.2

Audited 2026-09-17. `check build qaJar` passed after promoting the combined mod, standardizing tool names, adding Creative entries and automatic recipes, restricting `/blocktools`, allowing open-air construction/air surface palettes, and locking the held tunnel run's position and cardinal heading.

| Final server configuration | Result | Evidence |
| --- | --- | --- |
| Polymer enabled, including live disable/re-enable | 20 cases, 633 assertions, zero failures | [Results](docs/qa-results/release-1.6.4/enabled.txt) · [Build and server log](docs/qa-results/release-1.6.4/enabled-server.log) |
| Polymer absent | 20 cases, 421 assertions, zero failures | [Results](docs/qa-results/release-1.6.4/absent.txt) · [Server log](docs/qa-results/release-1.6.4/absent-server.log) |

Both runs use Minecraft 26.2 with Java 25 in isolated localhost worlds. Admin tests reject every `/blocktools` subcommand for permission levels 0–2 in Survival and Creative, allow admin/owner/console access, and preserve public tool commands. Polymer checks exercise Tools & Utilities/search contents across enable/disable, actual recipe-book unlocks with empty inventory, recipe crafting, exact display names, and generated resource-pack assets.

Tunnel tests cover building a shell around a single supported lane, exact Survival charges, atomic shortages and unsafe-block refusal, missing-footing rejection, rail/torch support, air wall/ceiling clearing without cost/refunds, and idempotent revisits. Movement tests retain the first held-tool origin after failed placement, sideways/diagonal/elevation/dimension changes, excessive jumps, and cardinal rotation; returning to the original lane/heading resumes. Backtracking, small same-cardinal turns, offhand use, both-hands release, and a new line after release are covered. Preview geometry stays on the original lane and elevation.

## Graphical single-player client test

The final `./gradlew --offline --no-daemon check build runClient -PclientQa -PwithPolymer` run passed **79 client assertions** using Fabric's client GameTest input driver and a real Minecraft graphical client with an integrated server. After the initial fixture teleport, walking, strafing, turning, and hotbar changes use client input; the test does not simulate the walk by moving server-player positions.

Holding forward moved the client **11.865 blocks** and built **13 enclosed slices**, with each floor, wall, and ceiling checked. Turning and walking backward while facing another direction made no edits; real sideways input and off-line forward movement retained the original anchor. Switching hotbar slots put the tool away, and re-equipping established a new line; another forward walk built **8 air-wall/ceiling slices**. All three actual client Creative names and client crafting-book entries passed.

Screenshots at **854×480** exposed clipped reset instructions; the final build uses compact action-bar reminders and wrapping chat instructions once per reason/run. The final screenshots were visually inspected. The client used the no-resource-pack fallback, so the held custom tool appears as a named nugget; resource-pack generation/encoding is covered by server QA, but accepted-pack rendering was not part of this client run.

[Client results](docs/qa-results/release-1.6.4/client/result.txt) · [Client build/run log](docs/qa-results/release-1.6.4/client/client-walk.log) · [Straight walk screenshot](docs/qa-results/release-1.6.4/client/0000_tunnel-straight-client-walk.png) · [Off-line screenshot](docs/qa-results/release-1.6.4/client/0001_tunnel-off-line-no-extension.png) · [New air-surface line screenshot](docs/qa-results/release-1.6.4/client/0002_tunnel-reequipped-new-line.png).

The client QA source set and its mod are development-only and excluded from the release. The final client run also reran the complete `check` task and rebuilt the release after the warning readability fix.

The installable JAR passes archive integrity and metadata checks. All 72 Java 25 production classes match the tested output byte-for-byte; no QA/test classes are packaged. [Release validation](docs/qa-results/release-1.6.4/release-validation.txt).

Earlier release audits follow for historical context; their descriptions of experimental branding, public `/blocktools`, iron-triggered recipe unlocks, and enclosed pre-dug tunnel requirements are superseded by this release.

---

# Named tools and crafting QA — 1.6.3-tunnel-test+26.2

Audited 2026-09-16. The final `build qaJar` and every existing check suite passed, followed by these disposable Minecraft 26.2 / Java 25 servers:

| Startup mode | Result | Evidence |
| --- | --- | --- |
| Polymer absent | 16 cases, 260 assertions, zero failures | [Results](docs/qa-results/recipes-1.6.3/absent.txt) · [Log](docs/qa-results/recipes-1.6.3/absent-server.log) |
| Polymer installed, enabled | 16 cases, 424 assertions, zero failures | [Results](docs/qa-results/recipes-1.6.3/enabled.txt) · [Log](docs/qa-results/recipes-1.6.3/enabled-server.log) |
| Polymer installed, disabled; custom feather fallback | 16 cases, 424 assertions, zero failures | [Results](docs/qa-results/recipes-1.6.3/disabled.txt) · [Build and server log](docs/qa-results/recipes-1.6.3/disabled-server.log) |

The live crafting checks use the actual recipe manager to verify the documented grids, one-item outputs, requested Toning Tool / Path Tool / Tunnel Tool names, incorrect or missing ingredients, and absence of collisions against all loaded vanilla crafting recipes. Loaded advancement predicates accept iron acquisition, reject unrelated items, and reward the corresponding recipe. Both recipes and advancements are absent at disabled/no-Polymer startup and after disabling; they return after enabling.

The harness also changes integration mode repeatedly without waiting, asserts each command returns before its resource reload finishes, then pumps server work only inside development QA and verifies the final recipes/advancements match the final requested state. Production starts vanilla reloads on a worker executor, serializes them per server, publishes configuration for worker reads, reports completion/errors on the server thread, supports retry after failure, and detaches stopped-server queues. Existing saved items, role isolation, commands, material charging, movement, config preservation, and generated-pack checks remain covered.

The release ZIP passes integrity and metadata checks; all three recipes and advancements are packaged; no development QA classes are included. Sprite PNG bytes are identical to 1.6.2. Reproduce with `./gradlew runServer -PliveQa -PqaRunDir=build/qa-recipes-absent -PqaJava=/usr/lib/jvm/java-25-openjdk/bin/java`; add `-PwithPolymer` and use another run directory for installed modes. Seed startup-disabled config with `enablePolymerTools:false` and `selectionItem:"minecraft:feather"`.

Earlier release audits follow for historical context.

---

# Optional Polymer tools QA — 1.6.2-tunnel-test+26.2

Audited 2026-09-16. `build qaJar` passed every existing logic, geometry, inventory, material-safety, permission API, and integration regression check. Three disposable Minecraft 26.2 / Fabric servers then passed the complete live suite using Java 25:

| Startup mode | Result | Evidence |
| --- | --- | --- |
| Polymer absent | 16 cases, 236 assertions, zero failures | [Results](docs/qa-results/polymer-1.6.2/absent.txt) · [Log](docs/qa-results/polymer-1.6.2/absent-server.log) |
| Polymer 0.17.5 installed, integration enabled | 16 cases, 330 assertions, zero failures | [Results](docs/qa-results/polymer-1.6.2/enabled.txt) · [Log](docs/qa-results/polymer-1.6.2/enabled-server.log) |
| Polymer installed, integration disabled, feather fallback | 16 cases, 330 assertions, zero failures | [Results](docs/qa-results/polymer-1.6.2/disabled.txt) · [Log](docs/qa-results/polymer-1.6.2/disabled-server.log) |

The added suite verifies schema 3-to-4 migration preserving customized widths and assignments; default-enabled configuration; custom role isolation across main/off hands; legacy items and ordinary nuggets inactive under Polymer; player tool grants; startup-disabled item registration; live disable/re-enable through `/blocktools reload`; and preservation of palettes, selected corners, and mutually exclusive path/tunnel states. Existing malformed/future configuration preservation tests still pass.

Polymer runs convert actual server stacks to vanilla-client iron nuggets, check accepted-pack models and original-item decoding, verify selection mining/offhand handling, and generate the resource pack. Every tool's generated-pack assets have a handheld model, the correct texture reference, an actual 16×16 PNG, and exclusively opaque or transparent pixels. The no-Polymer run boots and completes with no Polymer runtime dependencies.

Reproduce with `./gradlew runServer -PliveQa -PqaRunDir=build/qa-polymer-absent -PqaJava=/usr/lib/jvm/java-25-openjdk/bin/java`; add `-PwithPolymer` and use a separate run directory for installed modes. For startup-disabled testing, seed that run directory's `config/block-tone-path-tools.json` with schema 4, `enablePolymerTools:false`, and `selectionItem:"minecraft:feather"`. These QA harness classes are development-only and excluded from the release JAR.

The sprites have not been manually checked in a graphical Minecraft client. Existing historical claims/visualization audits are retained below; this release did not change the protection integration.

---

# Visual interface QA — 1.6.1-tunnel-test+26.2

Audited 2026-09-09. Added the Precious Stones particle-visualization approach to Blocktone selections, Path Tool terrain footprints, and Tunnel Tool interior/lining/rail alignment. Precious Stones itself was inspected but not changed.

`check build qaJar` passed, including all previous regression suites and new selection/tool-preview geometry tests. New geometry tests cover inclusive outer boundaries, all eight corners, partial selections, reversed/extreme coordinates, saturated volume, 100 randomized selections, every tunnel width/height/cardinal direction/rail mode, booster coordinates, and bounded particle counts.

Live checks exercise real selection callbacks and all three `visualize [off]` command families: automatic activation, quiet repeat-click refresh, partial selection, expiry, clear/hide independence, wrong-dimension refusal, short alias, and paused previews retaining all tool state. World snapshots and every inventory stack/component remain unchanged.

| Run | Result | Evidence |
| --- | --- | --- |
| Standalone, Java 26 | 15 cases, 208 assertions, zero failures | [Results](docs/qa-results/visualization-1.6.1/standalone.txt) · [Log](docs/qa-results/visualization-1.6.1/standalone-server.log) |
| Precious Stones installed, Java 25 | 16 cases, 233 assertions, zero failures | [Results](docs/qa-results/visualization-1.6.1/claims-java25.txt) · [Log](docs/qa-results/visualization-1.6.1/claims-java25-server.log) |

[Build log](docs/qa-results/visualization-1.6.1/build.log). The release JAR passes ZIP integrity, Fabric identity/version/entrypoint validation, Java 25 bytecode validation, and byte-for-byte comparison with tested production classes. It contains 67 production classes, including both preview helpers, and no development QA/test classes. Previous release/checksum archived under `Backups/old builds/Minecraft/`.

Visual appearance has not been manually inspected in a graphical Minecraft client. Previews are private, temporary geometric guides; they do not promise material availability, authorization, unobstructed tunnels, or final stair/palette results. Particle sampling is bounded and limited to loaded chunks within 128 blocks; already emitted particles fade naturally after hiding.

The remainder records the prior 1.6.0 migration audit and its evidence.

---

# QA audit — Block Tone, Path & Tunnel Tools

Release: `1.6.0-tunnel-test+26.2` · Audited 2026-09-09

## Scope and outcome

Copied Block Tone & Path Tools 1.5.2 into this independent experiment, integrated the tunnel tool, and reviewed all production Java sources across commands, configuration, selection, tone, path, inventory, tunnel geometry, permissions, and lifecycle. The original Blocktone source and the separate stable Precious Stones source remain unchanged. The retired Precious Stones tunnel experiment directory, including its sources, JARs, caches, and logs, has been deleted.

The experiment retains the `blocktonepathtools` mod ID. It replaces the normal Blocktone JAR when installed. Precious Stones is optional and can run alongside it. No Precious Stones claim engine, shop implementation, mixins, or hard dependency is included.

## Findings fixed

| Finding | Result |
| --- | --- |
| Tunnel inventory bookkeeping used an emptied stack's AIR item key, potentially charging later stacks again. | Replaced duplicate inventory implementation with Blocktone's atomic, shared reservation code. |
| Neighbor updates during partial writes could break other planned cells or produce drops before a failed operation rolled back. | All tools stage and verify their writes, commit materials, then publish world callbacks. Rejected writes/reservations restore blocks without neighbor callbacks. |
| Direct edits could bypass Adventure/Spectator restrictions or world-border checks. | Shared permission guard enforces live-player build ability, bounds, loaded chunks, border, and native interaction restrictions. |
| Moving tunnel code out of the claim engine could discard its protection checks. | Optional reflective integration preserves build access checks; structural field stones and owned blocks cannot be edited even by owners. Broken integration denies edits. |
| Path and tunnel tools could both react to the same held rod. | Arming/resuming either tool pauses the other while retaining settings. |
| Boosters could overwrite collisionless wiring, crops, or lighting. | Torch cells accept air or the existing redstone torch only. Waterlogged special rails are also rejected. |
| Double slabs and other counted states were refunded as one item; multipart edits could lose or duplicate companion blocks. | State-aware item quantities; general multipart sources skipped and destinations rejected. Path Tool handles both halves of tall grass together and refunds one plant. |
| Custom material item components could disappear during plain block placement. | Named/customized materials are excluded from payment; shulker contents and overflow component slots are preserved. |
| Path scans could synchronously load new chunks. | Bounds and loaded-chunk checks precede terrain reads. |
| Cuboid volume/iteration could overflow at extreme coordinates. | Bounded arithmetic and iteration, with explicit extreme-coordinate regressions. |
| Source lists accepted trailing commas and mishandled explicit cave/void air. | Strict empty-entry rejection and air-aware filters. |
| Tunnel traversal history grew without a limit. | History capped at 4,096 centers; reset behavior covered by geometry and live movement checks. |
| Bare `/tunnel` alias lacked the root status executor. | Both aliases support status and subcommands. |
| Config reload/lifecycle integration was incomplete. | Shared rod setting, both configs reloaded, trails reset, player state removed on disconnect and server stop, newer/malformed config files preserved. |

## Automated verification

`./gradlew --offline --no-daemon check build qaJar` passes. Build started from a clean output directory; two test-only Minecraft bed API references were corrected before the final passing run. Production classes target Java 25 (class-file version 69).

- `logicTest`: existing palettes, calculation, shuffle, selection, autocomplete, inventories, and stair cases; additional counted-state safety, overflow geometry, malformed input, inventory preservation, and staged transaction tests.
- `integrationLogicTest`: 177 assertions covering command registration/aliases, completion offsets, 99 weighted-palette combinations with negative hashes, and configuration bounds.
- `compatibilityTest`: 13 checks of the optional protection API contract.
- `tunnelGeometryTest`: all 80 width/height/cardinal-direction combinations, rail/torch lanes, signed booster coordinates, parser rejection, unsafe palette blocks, and bounded trail history.

The transaction suite injects rejected writes, mismatched states, cross-write corruption, rejected inventory commits, and commit exceptions. It verifies restoration and absence of premature callbacks.

Build evidence: [build.log](docs/qa-results/build.log).

## Live server verification

Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, disposable local worlds and an in-process test player with a packet listener. The development QA mod is a separate artifact and is excluded from the installable JAR.

| Run | Result | Evidence |
| --- | --- | --- |
| Standalone, Java 26 | 14 cases, 124 assertions, zero failures | [Results](docs/qa-results/standalone.txt) · [Log](docs/qa-results/standalone-server.log) |
| With Vanilla Friendly Protection and Shop 0.4.3, Java 26 | 15 cases, 149 assertions, zero failures | [Results](docs/qa-results/claims.txt) · [Log](docs/qa-results/claims-server.log) |
| With Vanilla Friendly Protection and Shop 0.4.3, Java 25 | 15 cases, 149 assertions, zero failures | [Results](docs/qa-results/java25.txt) · [Log](docs/qa-results/java25-server.log) |

Live cases verify real shell placement, exact shulker payment, idempotent repeated slices, shortage/obstruction/fluid/container/unbreakable refusal without partial edits, Creative behavior, native permissions, straight rail runs, powered rails energized by actual redstone torches, detector-rail preservation, command aliases and mode pausing, real tone/path transactions, paired vegetation and double-slab refunds, movement/catch-up/backtracking/offhand/release behavior, and byte-for-byte configuration preservation.

The optional-mod cases create real claim and owned-block records. They verify outsider refusal, owner access to ordinary blocks, owner refusal at structural stones, exact authorized tunnel payment, and unchanged world/inventory/ownership records after denial. Fixtures are removed afterward.

Logs intentionally contain config errors from malformed/newer-version JSON tests. Those are expected test stimuli, followed by assertions that the files were preserved. Gradle/JOML emit upstream deprecation warnings; no production compilation errors remain.

Reproduce standalone server QA with:

```bash
./gradlew --offline --no-daemon -PliveQa runServer
```

This explicit QA mode uses `build/qa-run`, binds to localhost, generates a disposable offline test world, runs the separate QA mod, checks its result, and exits. For optional-mod coverage, place the stable Precious Stones JAR in `build/qa-run-claims/mods/`, then run:

```bash
./gradlew --offline --no-daemon -PliveQa -PqaRunDir=build/qa-run-claims runServer
```

`-PqaJava=/path/to/java` selects another server runtime while compilation uses the build JDK.

## Release validation and limits

The installable JAR's archive integrity, metadata, main entrypoint, version, expected tone/path/tunnel classes, and Java class version are checked. It contains 59 production classes and no QA/test classes or Precious Stones engine/mixins. A SHA-256 checksum accompanies the release in `Builds/Minecraft/`.

Live tests exercise the logical server with a test player, not a graphical client. Actual mouse selection/visual feedback, multiplayer latency, minecart rides over long routes, integrated-server UI behavior, and a large third-party modpack/load test remain manual checks. Third-party protection mods other than the tested Precious Stones integration have no universal bulk-edit compatibility guarantee. Normal neighbor physics occurs after successful commits; this is not a general rollback system for arbitrary external mod callbacks or server crashes.

Tunnel Tool supports enclosed, pre-dug, straight, flat cardinal runs. Corners/intersections require manual finishing. Survival tunnel lining intentionally does not refund the replaced shell, preserving the original experiment's behavior.
