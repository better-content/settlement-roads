# Changelog

## Unreleased

### Added

- Initial repo-ready project specification in [SPEC.md](SPEC.md)
- Deterministic verification strategy in [TESTPLAN.md](TESTPLAN.md)
- Debug scenario inventory in [docs/DEBUG_SCENARIOS.md](docs/DEBUG_SCENARIOS.md)
- Manual visual verification checklist in [docs/VISUAL_CHECKLIST.md](docs/VISUAL_CHECKLIST.md)

### Changed

- Renamed the codebase from the template namespace to the `settlement_roads` mod namespace
- Added repo execution notes for tests, GameTests, and datagen entry points
- Removed the legacy `fissionreactor` code and assets instead of excluding them from the build
- Trimmed the Gradle configuration and mod metadata down to the dependencies used by `settlement_roads`

### Implemented

- Added core planner domain models, cluster/ring/route planning scaffolding, palette selection, bridge-support planning helpers, chunk indexing, and placement ledger utilities
- Added Overworld `SavedData` scaffolding for persisted planner state
- Added debug command registration for scenario selection, planning, placement, and clearing
- Added starter datapack tag resources for roadable structures, biome palette classes, and bridge support materials
- Added deterministic JVM tests covering the current pure-planner slice
- Added deterministic synthetic scenario definitions shared by debug commands, planner entry points, and GameTests
- Added terrain-aware route planning for short-span bridges, wide-river detours, and cave-support descent probes
- Added debug placement for widened roads, stone-brick bridge decks, parapet walls, and vertical support columns
- Added in-world GameTests for grassy river bridges, rocky gravel routing, cave support descent, wide-river detours, and rerun idempotence

### Identity normalization

- Standardized the project as **Settlement Roads** with mod ID `settlement_roads`, artifact `settlement-roads`, and package `com.bettercontent.settlementroads`.
- Adopted Java 17 and Forge 1.20.1-47.4.13 as the build baseline without changing the project version.
- This is a clean break; legacy worlds, configurations, and integrations are not migrated.
