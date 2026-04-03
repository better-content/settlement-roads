# WorldPaths

Deterministic, scenario-driven Forge 1.20.1 mod planning for settlement roads and bridges.

This repository now contains both the execution docs and the first implementation slice for the project:

- [SPEC.md](/home/gerald/mcmods/worldpaths/SPEC.md)
- [TESTPLAN.md](/home/gerald/mcmods/worldpaths/TESTPLAN.md)
- [docs/DEBUG_SCENARIOS.md](/home/gerald/mcmods/worldpaths/docs/DEBUG_SCENARIOS.md)
- [docs/VISUAL_CHECKLIST.md](/home/gerald/mcmods/worldpaths/docs/VISUAL_CHECKLIST.md)
- [CHANGELOG.md](/home/gerald/mcmods/worldpaths/CHANGELOG.md)

The design bias is:

- deterministic synthetic scenes over natural worldgen
- world-state assertions over screenshots
- planning separated from placement
- persistence via Overworld `SavedData`
- data-driven compatibility via tags and config

Current implementation slice:

- mod metadata and code namespace now use `settlementroads`
- core planner/domain/storage scaffolding exists in `src/main/kotlin`
- deterministic synthetic scenarios now drive both debug commands and GameTests
- route planning can choose a bridge over a short shallow-water span or detour around a too-wide crossing
- debug placement renders road rings, 3-wide roads, stone-brick bridge decks, parapet walls, and support columns
- deterministic JVM tests cover clustering, rings, route selection, palette choice, bridge support logic, chunk indexing, and save-data round trips
- in-world GameTests cover grassy bridge placement, rocky palette selection, cave support descent, wide-river detours, and rerun idempotence
- debug commands register `/settlementroads debug spawn_scenario`, `plan_here`, `place_here`, and `clear_here`

Immediate implementation entry points:

- `./gradlew test`
- `./gradlew runGameTestServer`
- `./gradlew runData`
