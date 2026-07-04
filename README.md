# Settlement Roads

Deterministic, scenario-driven settlement road and bridge planning for Forge 1.20.1.

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

- mod metadata, code namespace, and resources now consistently use `settlementroads`
- legacy `fissionreactor` sources, assets, tests, and dependency baggage have been removed
- core planner/domain/storage scaffolding exists in `src/main/kotlin`
- deterministic synthetic scenarios now drive both debug commands and GameTests
- route planning can choose a bridge over a short shallow-water span or detour around a too-wide crossing
- debug placement renders road rings, 3-wide roads, stone-brick bridge decks, parapet walls, and support columns
- deterministic JVM tests cover clustering, rings, route selection, palette choice, bridge support logic, chunk indexing, and save-data round trips
- in-world GameTests cover grassy bridge placement, rocky palette selection, cave support descent, wide-river detours, and rerun idempotence
- debug commands register `/settlementroads debug spawn_scenario`, `plan_here`, `place_here`, and `clear_here`

Immediate implementation entry points:

- `./gradlew verifyFast`
- `./gradlew verifyFull`
- `./gradlew runData`
