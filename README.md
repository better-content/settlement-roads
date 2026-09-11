# Settlement Roads

Deterministic, scenario-driven settlement road planning for Forge 1.20.1.

This repository contains the mod implementation and its execution docs:

- [SPEC.md](SPEC.md)
- [TESTPLAN.md](TESTPLAN.md)
- [docs/DEBUG_SCENARIOS.md](docs/DEBUG_SCENARIOS.md)
- [docs/VISUAL_CHECKLIST.md](docs/VISUAL_CHECKLIST.md)
- [CHANGELOG.md](CHANGELOG.md)

The current design bias is:

- deterministic synthetic scenes over natural worldgen
- world-state assertions over screenshots
- planning separated from placement
- persistence via Overworld `SavedData`
- data-driven compatibility via tags and config

Current implementation slice:

- mod metadata, code namespace, and resources now consistently use `settlement_roads`
- legacy `fissionreactor` sources, assets, tests, and dependency baggage have been removed
- core planner/domain/storage scaffolding exists in `src/main/kotlin`
- deterministic synthetic scenarios now drive both debug commands and GameTests
- supported route planning avoids water with a dry detour when one exists and otherwise omits the connection
- placement renders three-wide dirt paths in grassy biomes, gravel roads in non-grassy biomes, and sparse coarse-dirt edges
- bridge planning and placement code remains dormant experimental scaffolding; `allow_water_bridges` defaults to false and must remain false for supported use
- deterministic JVM tests cover clustering, rings, dry-route selection, palette choice, chunk indexing, and save-data round trips; isolated bridge tests deliberately enable the unsupported flag
- in-world GameTests retain isolated bridge-placement scaffolding alongside supported dry routing and rerun-idempotence coverage; they do not establish working bridge support
- debug commands register `/settlement_roads debug spawn_scenario`, `plan_here`, `place_here`, and `clear_here`

Immediate implementation entry points:

- `./gradlew verifyFast`
- `./gradlew verifyFull`
- `./gradlew runData`

## Community and support

For modpack and mod discussion, playtest feedback, and bug reports, join the [Better Content Discord](https://discord.gg/EkRnZbzqS9).

## Canonical identity

- Repository and release artifact: `settlement-roads`
- Mod ID and resource namespace: `settlement_roads`
- Java package: `com.bettercontent.settlementroads`
- Validation: `./gradlew verifyFull`

This normalization is a clean break. Worlds, configuration files, and integrations created for earlier identities are not migrated or aliased.

### Coverage gate regression

`./gradle/verify-coverage-gate.sh` checks the production coverage gate, then confirms
that an empty report scope and an unmet 100% coverage requirement fail. It restores
the normal report afterward and retains diagnostic logs under
`build/coverage-gate-regression/`. The production thresholds are unchanged.
