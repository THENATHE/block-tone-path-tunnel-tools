# Public source and release verification — 1.6.9+26.3

Publication prepared **2026-10-09** for [THENATHE/block-tone-path-tunnel-tools](https://github.com/THENATHE/block-tone-path-tunnel-tools), tag `v1.6.9+26.3`.

## Included source

All current production, test, live-server QA and graphical-client QA source sets, resources, original artwork/prompts, Gradle build scripts/catalog/wrapper, MIT license, asset provenance and historical verification documentation are included. Local build caches, worlds, runtime logs and downloaded dependencies are excluded. Historical QA evidence is included with personal filesystem paths replaced by placeholders; unchanged originals remain privately retained.

Production source/resources, dependencies and version are unchanged from the tested local 1.6.9+26.3 release. The graphical QA launcher now uses the configured JVM by default, with optional `-PclientQaJava=/path/to/java`; this development-only portability fix is outside the runtime JAR. Public documentation adds download links and portable build examples. The `SOURCE-SHA256.sha256` in this repository identifies exported main compile inputs, including the updated development launcher.

## Verified gameplay baseline

[QA-1.6.9.md](QA-1.6.9.md) records the clean regression build and disposable live-server results: **13,176 assertions without Polymer**, **13,406 with Polymer**, zero failed cases. Shape coverage includes 573 original shape states, 1,241 expanded positional/connection states and 120 torch mounting replacements. Tests exercise actual server commands, world state and inventory; no new graphical or connected-client session is claimed for this publication.

Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25; optional Polymer Core/Resource Pack 0.18.2+26.3. No separate ChatGPT-ported counterpart exists for this main mod.

## Public-source checks

Clean source export `clean check build qaJar clientqaJar`: **PASS**. Every source set compiles and all regression checks pass. The public build reproduces the previously gameplay-tested installable JAR **byte for byte**, with the SHA-256 below. Build evidence: [publication build log](qa/results/publication-1.6.9/build.log). Public tag/commit, repository visibility, release asset ZIP metadata/integrity and downloaded SHA-256 checks are recorded in the release and local publication record once complete.

The installable JAR must match the tested local artifact SHA-256:
`1c284ee1463b9f4c476ecd3190bb9a3a6b83ec8051e139893233e2f7c34b3c2d`.
