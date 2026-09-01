# Settlement Roads Test Plan

## 1. Purpose

This plan defines the deterministic verification strategy for Settlement Roads. Supported behavior
keeps `allow_water_bridges` false: routes take a dry detour when possible and otherwise omit the
connection. Bridge tests are retained only as isolated experimental scaffolding.

Primary testing rules:

- use synthetic scenes instead of natural worldgen for hard guarantees
- use invariant assertions as the primary truth
- use screenshots only for human confirmation
- keep planner tests deterministic through fixed seeds and stable ordering

## 2. Repository Preconditions

- The repository now uses the target namespace `settlement_roads`.
- Test names and scenario ids document the supported dry-routing contract and separately labeled dormant scaffolding.
- Command examples continue to use the target namespace `settlement_roads` from the project spec.

## 3. Test Pyramid

### 3.1 Unit Tests

Use plain JVM tests for:

- cluster formation
- ring generation
- anchor choice
- terrain classification
- route cost evaluation
- isolated bridge span, support-descent, and footing behavior with the unsupported flag enabled
- persistence round-trip behavior
- idempotence and chunk stamp logic

Unit tests should dominate the suite because they are fast, deterministic, and isolate planner logic from world state.

### 3.2 GameTests

Use Forge GameTests for:

- synthetic in-world terrain interaction
- isolated dormant-bridge interaction through water and caves
- chunk-boundary placement behavior
- rerun stability after real placement
- debug scene inspection

GameTests must load fixed templates and must not depend on ambient worldgen.

### 3.3 Manual Visual Review

Manual review exists to confirm:

- road shape reads well at player scale
- ring placement feels plausible around structures

Bridge massing and support review is not part of current supported acceptance. Use it only when
explicitly inspecting the dormant bridge scaffolding.

Manual visual review is not the merge gate when invariant assertions already fail.

## 4. Determinism Requirements

Every deterministic test path must:

- run with fixed scenario inputs
- use fixed planner seeds or stable sorting
- avoid nondeterministic iteration order
- avoid dependence on time of day, weather, mobs, or random worldgen

Test harness rules:

- fix time
- fix weather
- disable mob griefing and random interference
- clear scenario areas before reruns

## 5. Unit Test Matrix

### 5.1 Cluster Planner

- `clusters_single_structure_isolated`
  - Given one structure, planner returns one cluster containing only that structure.
- `clusters_two_nearby_structures_together`
  - Given two structures inside cluster radius, planner returns one cluster with both ids.
- `clusters_chain_connectivity_forms_one_cluster`
  - Given A near B and B near C, planner returns one connected component for all three.
- `clusters_out_of_range_structures_separate`
  - Given structures outside radius with no transitive link, planner returns separate clusters.

Assertions:

- cluster ids stable for stable sorted inputs
- no structure assigned to multiple clusters

### 5.2 Ring Planner

- `ring_is_closed`
  - First and last perimeter positions connect as a closed loop.
- `ring_does_not_intersect_bounds`
  - No ring point lies inside the protected structure footprint.
- `ring_padding_respected`
  - Minimum distance from bounds respects configured padding.
- `anchor_chooses_nearest_ring_edge_point`
  - Selected anchor is the nearest reachable ring edge point toward the target direction.

Assertions:

- perimeter is contiguous
- ring generation stable for same bounds and padding

### 5.3 Terrain And Palette

- `grassy_surface_chooses_dirt_path`
- `non_grassy_surface_chooses_gravel`
- `coarse_dirt_noise_is_sparse`
- `bridge_cost_lower_than_detour_when_span_short` (dormant bridge scaffolding only)

Assertions:

- palette selection follows biome classification only
- coarse dirt density remains below configured threshold
- coarse dirt positions cluster near edges rather than checkerboarding the centerline

### 5.4 Dormant Bridge Planner

These tests explicitly enable unsupported bridge routing and only preserve isolated regression
coverage. Passing them does not make bridges a supported feature.

- `bridge_rejected_when_span_too_wide`
- `bridge_rejected_when_bank_grade_too_steep`
- `supports_descend_through_water_to_solid`
- `supports_ignore_non_solid_terminal_blocks`
- `soft_ground_widens_foundation`
- `mid_piers_inserted_at_spacing_threshold`

Assertions:

- bridge deck stays above waterline clearance
- required supports all terminate on valid support blocks
- soft-ground footing width increases as configured
- mid-pier count matches span policy

### 5.5 Placement And Persistence

- `placement_idempotent_for_same_plan`
- `segment_intersection_per_chunk_stable`
- `reloading_saved_plan_round_trips`

Assertions:

- applying the same plan twice does not change resulting block state
- chunk stamps are stable and do not duplicate placement work
- serialized plan data round-trips with matching planner version

## 6. GameTest Scenarios

All GameTests must:

- load a named structure template
- build or load structure nodes explicitly
- call planner explicitly
- call placer explicitly
- assert world-state invariants
- preserve a human-inspectable final result

Scenarios B through D explicitly enable dormant bridge scaffolding. They do not model the supported
pack configuration and must not be cited as proof that water bridges work in ordinary play.

### 6.1 Scenario A: Flat Grassy Twin Structures

Scene:

- two simple buildings on flat grassy terrain
- no river

Expected:

- one connection
- `minecraft:dirt_path` main route
- sparse `minecraft:coarse_dirt` detail
- no bridge segments

### 6.2 Scenario B: Grassy River Crossing (Dormant Bridge Scaffolding)

Scene:

- two simple buildings on grassy terrain
- narrow river between route anchors

Expected:

- dirt-path approaches
- stone-brick bridge inserted
- valid abutments on both banks
- all required supports reach valid solid support

### 6.3 Scenario C: Rocky Non-Grassy Crossing (Dormant Bridge Scaffolding)

Scene:

- non-grassy terrain
- narrow river between structures

Expected:

- gravel approaches
- stone-brick bridge inserted
- coarse dirt remains sparse and decorative only

### 6.4 Scenario D: Cave Under Riverbed (Dormant Bridge Scaffolding)

Scene:

- river above a cave void
- true solid terrain below the cave

Expected:

- supports continue past water and cave air
- supports terminate only when solid support block is reached
- no support stops on plants, logs, or fluids

### 6.5 Scenario E: Too-Wide River

Scene:

- crossing wider than configured bridge limit

Expected:

- no bridge generated
- planner reroutes if valid local detour exists, otherwise yields no connection according to config

### 6.6 Scenario F: Three-Structure Cluster

Scene:

- three structures where one is only transitively connected

Expected:

- one cluster
- sparse MST-style road graph
- no redundant loop added in v1

### 6.7 Scenario G: Rerun Stability

Scene:

- any valid scenario with one route and one placed result

Expected:

- second planner-plus-placer run produces identical world state
- no extra blocks placed
- no duplicate chunk stamps recorded

### 6.8 Scenario H: Chunk-Boundary Split

Scene:

- route crosses a chunk edge

Expected:

- placement succeeds on both sides of the chunk boundary
- rerun does not duplicate border blocks
- chunk-local bookkeeping remains stable

## 7. Assertion Strategy

Prefer these invariant assertions. Bridge-specific assertions apply only to the explicitly enabled
dormant scenarios:

- ring is closed
- anchor lies on ring edge
- route palette matches terrain class
- bridge span is within configured maximum
- bridge deck clears waterline
- support columns terminate on valid support material
- no unsupported bridge sections remain
- rerun leaves world state unchanged

Use exact block snapshots only when:

- the scene is intentionally tiny
- the layout is fully synthetic and stable
- the snapshot adds clarity rather than brittleness

## 8. Test Data And Resources

Expected resource categories:

- GameTest structure templates
- deterministic scenario registry entries
- tag fixtures for biome and support classification
- optional planner fixtures for fake `StructureNode` definitions

Data generation should validate references and keep scenario resources synchronized with code-owned identifiers.

## 9. Execution Surface

Expected Gradle entry points during implementation:

- `./gradlew verifyFast`
- `./gradlew verifyFull`
- `./gradlew runData`

Minimum source layout expected by this plan:

- `src/test/kotlin/...` for JVM planner tests
- `src/main/resources/data/<namespace>/...` for tags and scenario data
- `src/generated/resources/...` for datagen outputs

## 10. Debug Harness Validation

The debug command path should be covered by integration tests or smoke tests for:

- `/settlement_roads debug spawn_scenario <id>`
- `/settlement_roads debug plan_here`
- `/settlement_roads debug place_here`
- `/settlement_roads debug clear_here`

Minimum expectations:

- commands resolve known scenarios
- planner uses deterministic inputs from the loaded scene
- clear command removes placed artifacts from the debug area

## 11. Performance Checks

Performance checks are not replacements for correctness tests.

Required profiling expectations:

- no per-tick global rescans
- no planner work on client thread
- no duplicate replanning on chunk reload
- route search bounded to local cluster scope

Manual profiling pass:

- run a populated synthetic scene
- record profile output
- confirm planner hotspots align with route work rather than repeated index scans; inspect bridge work only during explicit dormant-scaffolding development

## 12. Exit Criteria

A phase is complete only when:

- required unit tests pass
- relevant GameTests pass
- changelog note is updated
- debug scenario list reflects new behavior
- manual visual checklist is updated

Project-wide acceptance requires:

- all unit tests pass
- all GameTests pass
- persistence round-trip passes
- rerun stability passes
- retained dormant-bridge regression coverage, including the support-descent cave case, passes without being treated as supported-feature evidence
- chunk-boundary behavior is stable
- profiling shows no runaway behavior

## 13. Reference Basis

This test plan inherits its technology choices from the project spec:

- Overworld `SavedData` for persisted planner state
- GameTests for template-driven deterministic world assertions
- datapack tags and generated data for compatibility fixtures
- `SimpleChannel` for client debug overlay sync when needed
- Forge profiler output for manual performance validation
