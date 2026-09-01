# Settlement Roads Spec

## 1. Project

- Project: Settlement Roads
- Target: Forge 1.20.1
- Language: Kotlin preferred
- Delivery mode: deterministic, test-first, scenario-driven

## 2. Repository Adoption Notes

- The repository now uses the target namespace `settlement_roads` in Gradle metadata, source packages, resources, and command registration.
- The implemented supported slice covers planner-domain and persistence code, dry terrain-aware routing, debug commands, deterministic JVM tests, and GameTests.
- Bridge planning and placement code exists only as dormant experimental scaffolding. The supported configuration keeps `allow_water_bridges` false; bridge-specific tests enable it only to isolate that code.

## 3. Goal

Build a mod that:

- detects clusters of nearby supported structures
- generates an encircling path around each structure
- connects structures in the same cluster with terrain-aware paths
- routes around water on dry terrain when a bounded detour exists
- omits a connection when no supported dry route exists
- uses `minecraft:dirt_path` in grassy biomes
- uses `minecraft:gravel` in non-grassy biomes
- uses `minecraft:coarse_dirt` as sparse edge-biased detail

## 4. Non-Goals For V1

The v1 implementation does not attempt:

- automatic support for all modded structures
- supported bridge or other over-water placement
- long-span mega-bridges
- tunnels
- diagonal or curved bridges
- ocean or lake crossing
- dynamic replanning every tick
- client-authoritative generation

## 5. Source Of Truth

The supported product decision and checked-in default configuration define the intended behavior;
code and tests must then be interpreted within that boundary. No artifact has blanket precedence
over the others. When the spec, tests, config, code, or observed world state disagree, obtain an
owner decision for that behavior and reconcile the affected artifacts. For checks of the same
intended behavior, invariant assertions remain stronger evidence than screenshots until the scene
is inspected manually.

## 6. Design Principles

- Do not rely on natural Overworld generation for deterministic verification.
- Use golden synthetic scenes for integration and visual confirmation.
- Treat screenshot capture as a human-review aid, not the primary merge gate.
- Prefer pure planning functions and explicit placement phases.
- Version all persistent plan data.
- Rebuild locally affected plans when possible instead of rescanning globally.

## 7. High-Level Architecture

### 7.1 Modules

- `structure-index`
- `cluster-planner`
- `ring-planner`
- `route-planner`
- `bridge-planner` (dormant experimental scaffolding)
- `segment-placer`
- `world-state-storage`
- `debug-tools`
- `test-harness`

### 7.2 Persistent Storage

Store global road-network state in Overworld `SavedData`:

- indexed roadable structures
- cluster assignments
- planned road segments
- planned bridge segments retained for dormant scaffolding
- chunk placement stamps
- planner version

### 7.3 Data-Driven Configuration

Use datapack tags and JSON config for:

- roadable structures
- grassy biomes
- non-grassy biomes
- solid support blocks
- soft support blocks
- water and other forbidden routing terrain
- per-structure padding overrides
- per-structure road width overrides
- routing thresholds and dormant bridge-test thresholds

## 8. Domain Model

```kotlin
data class StructureNode(
    val id: String,
    val structureKey: String,
    val center: BlockPos,
    val bounds: BoundingBox,
    val ringPadding: Int,
    val clusterRadius: Int
)

data class ClusterPlan(
    val clusterId: Long,
    val structures: List<String>,
    val connections: List<ConnectionPlan>
)

data class RingPath(
    val structureId: String,
    val perimeter: List<BlockPos>
)

data class ConnectionPlan(
    val fromStructureId: String,
    val toStructureId: String,
    val fromRingAnchor: BlockPos,
    val toRingAnchor: BlockPos,
    val segments: List<PathSegment>
)

sealed class PathSegment {
    data class Ground(val blocks: List<BlockPos>) : PathSegment()
    data class Bridge(val blocks: List<BlockPos>, val supports: List<SupportColumn>) : PathSegment()
}

data class SupportColumn(
    val x: Int,
    val z: Int,
    val fromY: Int,
    val toY: Int,
    val baseWidth: Int,
    val reachedSolid: Boolean
)
```

Implementation note:

- Add planner-format versioning to any serialized top-level plan objects.
- Keep planner outputs immutable and placement-oriented.

## 9. Functional Requirements

### 9.1 Structure Detection

The implementation shall:

- index only whitelisted roadable structures
- use structure bounds and instances, not block-pattern scanning
- ignore unsupported structures
- avoid duplicate indexing of the same structure start

### 9.2 Clustering

The implementation shall:

- group structures by configurable proximity
- use connected components over pairwise proximity edges
- rebuild only affected local clusters when new structures appear

Acceptance criteria:

- two structures within radius belong to the same cluster
- three structures chained by radius belong to the same cluster
- isolated structures form single-member clusters

### 9.3 Ring Generation

For each structure, the implementation shall:

- compute a padded perimeter around structure bounds
- generate a closed ring
- keep the ring outside the structure footprint
- smooth obvious jagged corners where practical
- not allow the ring to cut through the structure

Anchor rule:

- Select the nearest reachable ring edge point to the target route direction.
- Do not implement entrance detection or door heuristics in v1.

Acceptance criteria:

- ring is closed
- ring does not intersect the structure footprint
- ring padding is respected
- anchor is the nearest reachable ring edge point toward the other structure center

### 9.4 Inter-Structure Routing

The implementation shall:

- connect cluster members with a sparse graph
- use MST by default
- defer optional loops beyond v1
- pathfind across terrain with a configurable cost map

Terrain cost priorities:

- preferred: existing walkable ground
- acceptable: flat dirt, grass, gravel
- expensive: slopes
- very expensive: shallow water
- forbidden: lava, cliffs, large water crossings

### 9.5 Surface Palette

Grassy biomes:

- main surface: `minecraft:dirt_path`
- sparse detail: `minecraft:coarse_dirt`

Non-grassy biomes:

- main surface: `minecraft:gravel`
- sparse detail: `minecraft:coarse_dirt`

Rules:

- `coarse_dirt` must remain sparse
- detail placement must be edge-biased
- no checkerboard patterns

### 9.6 Dormant Bridge Scaffolding

Water bridges are not supported. `allow_water_bridges` defaults to false and must remain false for
supported deployments; the planner takes a dry detour when possible and otherwise emits no
connection. The bridge planner, placer, data model, and isolated tests are retained only as
experimental scaffolding. Enabling the flag or passing bridge-specific isolated tests does not
establish working mod or pack behavior.

If bridge development is explicitly resumed, the retained scaffolding models the following rules:

Bridges are allowed only when:

- water span is within configured limit
- both banks are reachable
- reroute cost exceeds bridge cost
- approach grade is acceptable

Bridge material palette:

- `minecraft:stone_bricks`
- `minecraft:stone_brick_stairs`
- `minecraft:stone_brick_slabs`
- `minecraft:stone_brick_walls`
- `minecraft:cobblestone` allowed for buried foundation contact

Bridge form:

- width: 3 blocks for minor routes
- width: 5 blocks for main routes
- chunky parapets or side walls required
- no floating underside
- supports descend to solid terrain
- mid-piers inserted every configured span threshold

Support rules:

- supports start from underside load points
- supports descend through air and water
- leaves, plants, logs, and fluids are not valid terminal support
- supports stop only on valid solid support blocks
- footing widens on soft or uneven base material

Acceptance criteria:

- every bridge has valid end abutments
- every required support reaches a valid support block
- no bridge block remains structurally floating by planner rules
- bridge deck remains above configured waterline clearance

### 9.7 Placement And Idempotence

The implementation shall:

- place from a saved plan rather than replanning on every chunk load
- stamp placed chunks and segments
- be safe to rerun without duplicating blocks
- support partial placement as chunks become available

## 10. Deterministic Execution Model

The deterministic test and debug model shall:

- never rely on natural village generation for hard verification
- load fixed scenario templates
- use fixed planner inputs
- use fixed random seeds or deterministic ordering
- run planner and placer explicitly through GameTests or dev commands

Determinism requirements:

- sort structure ids
- sort graph edges before tie-breaking
- sort candidate anchors before tie-breaking
- keep seed handling explicit in planner APIs

Synthetic structures are acceptable in test mode:

- marker blocks
- simple template buildings
- explicit fake `StructureNode` definitions with bounding boxes

## 11. Agent Delivery Phases

### Phase 1

- scaffolding
- data model
- config and tag system
- debug commands
- test harness skeleton

### Phase 2

- cluster planner
- ring planner
- anchor selection
- unit tests

### Phase 3

- route planner
- terrain classifier
- surface palette selection
- unit and integration tests

### Phase 4

- dormant bridge planner
- dormant support descent and footing widening
- isolated bridge tests that explicitly enable the unsupported flag

### Phase 5

- chunk-safe placement
- persistence
- rerun and idempotence tests

### Phase 6

- visual harness
- overlay and debug packets
- profiling and cleanup

## 12. Agent Rules

Implementation agents must:

- write tests before or alongside each planner component
- avoid widening scope without config and tests
- avoid refactoring unrelated files during feature work
- not merge features unless deterministic tests pass
- not rely on natural worldgen to verify planner behavior
- prefer pure planning functions
- keep placement separate from planning
- version persistent plan formats

## 13. Required Outputs Per Phase

Every phase must produce:

- code
- tests
- a changelog note
- an updated debug scenario list
- a manual visual verification checklist

## 14. Test Harness

### 14.1 Unit Tests

Prefer plain JVM tests for deterministic planner logic.

Required test cases:

- `clusters_single_structure_isolated`
- `clusters_two_nearby_structures_together`
- `clusters_chain_connectivity_forms_one_cluster`
- `clusters_out_of_range_structures_separate`
- `ring_is_closed`
- `ring_does_not_intersect_bounds`
- `ring_padding_respected`
- `anchor_chooses_nearest_ring_edge_point`
- `grassy_surface_chooses_dirt_path`
- `non_grassy_surface_chooses_gravel`
- `coarse_dirt_noise_is_sparse`
- `bridge_cost_lower_than_detour_when_span_short`
- `bridge_rejected_when_span_too_wide`
- `bridge_rejected_when_bank_grade_too_steep`
- `supports_descend_through_water_to_solid`
- `supports_ignore_non_solid_terminal_blocks`
- `soft_ground_widens_foundation`
- `mid_piers_inserted_at_spacing_threshold`
- `placement_idempotent_for_same_plan`
- `segment_intersection_per_chunk_stable`
- `reloading_saved_plan_round_trips`

The bridge- and support-named cases above are isolated regression coverage for dormant scaffolding.
They explicitly opt into unsupported behavior and are not acceptance evidence for a pack feature.

### 14.2 GameTests

GameTests shall:

- load a named structure template
- invoke planner explicitly
- invoke placer explicitly
- assert world-state invariants
- leave a human-inspectable result in-world

Required scenarios:

- Scenario A: flat grassy twin structures
- Scenario B: grassy river crossing
- Scenario C: rocky non-grassy crossing
- Scenario D: cave under riverbed
- Scenario E: too-wide river
- Scenario F: three-structure cluster
- Scenario G: rerun stability
- Scenario H: chunk-boundary split

Bridge-producing scenarios B through D are isolated scaffolding runs with bridges explicitly
enabled. Supported behavior is exercised with the default disabled value: use a dry detour when
available and otherwise omit the connection.

### 14.3 Assertion Style

Prefer invariant assertions over snapshots. Bridge-specific assertions below apply only to the
dormant isolated scenarios, not to supported deployments:

- ring is closed
- anchor lies on ring edge
- route palette matches terrain class
- bridge span is within configured maximum
- bridge deck clears waterline
- support columns terminate on valid support material
- no unsupported bridge sections remain
- rerun leaves world state unchanged

Use exact block snapshots only for tiny synthetic scenes where the snapshot is stable and improves readability.

## 15. Repository Execution Notes

The implementation repo should expose these Gradle entry points during delivery:

- `./gradlew test` for JVM planner tests
- `./gradlew runGameTestServer` for deterministic in-world scenarios
- `./gradlew runData` for generated tags, scenario registries, and debug assets

Expected source layout as the project grows:

- `src/main/kotlin/...` for mod code
- `src/test/kotlin/...` for JVM planner tests
- `src/gametest/kotlin/...` if the team separates GameTests from main sources
- `src/main/resources/data/<namespace>/...` for tags and scenario data
- `src/generated/resources/...` for datagen outputs

## 16. Reference Basis

This spec is grounded in the following Forge guidance cited by the original design brief:

- SavedData for persistent level-attached world data
- GameTests for structure-template-driven deterministic scenarios
- datapack tags for mergeable compatibility classification
- data generators plus `ExistingFileHelper` for validated generated assets
- `SimpleChannel` for simple debug-overlay packet sync
- debug profiler output and custom profiler sections for planner performance checks

Prefer invariant assertions over exact snapshots:

- ring closed
- anchor on ring edge
- palette correct for terrain class
- bridge span within limit
- support columns terminate on valid blocks
- no unsupported bridge sections
- placement stable after rerun

Use exact block snapshots only for small synthetic scenes.

## 14. Debug And Visual Harness

### 14.1 Dev Commands

Add debug commands with deterministic scene control:

```text
/settlement_roads debug spawn_scenario <id>
/settlement_roads debug plan_here
/settlement_roads debug place_here
/settlement_roads debug clear_here
```

Purpose:

- spawn exact scenes
- run planning without waiting for worldgen
- rerun quickly during iteration

### 14.2 Fixed Camera Markers

Each scenario template should include:

- one or more camera pads
- one sign or marker naming intended screenshot angles
- optional debug pillar for top-down inspection

### 14.3 Debug Overlay

Debug visualization should expose:

- cluster ids
- ring paths
- selected anchors
- chosen route
- bridge spans
- support columns

If client sync is needed, use a simple server-to-client packet path.

### 14.4 Golden Scene Policy

The project uses golden scenes, not golden screenshots.

Per scenario:

- scene template is fixed
- config is fixed
- planner seed is fixed
- expected invariants are fixed
- screenshots are taken from fixed camera pads for human review

## 15. Performance And Profiling

Planner work must be profiled in populated synthetic scenes.

Performance gates:

- no per-tick global rescans
- no planner invocation from the client thread
- no duplicate replanning on chunk reload
- route planning bounded to local cluster radius

## 16. Suggested Data Definitions

Suggested tags:

- `worldpaths:roadable_structures`
- `worldpaths:grassy_biomes`
- `worldpaths:solid_bridge_support_blocks`
- `worldpaths:soft_bridge_support_blocks`
- `worldpaths:bridge_forbidden_biomes`
- `worldpaths:bridge_forbidden_blocks`

Suggested generated or checked resources:

- tags
- scenario registries
- debug asset references
- optional test helper data

## 17. Acceptance Gates

A build is acceptable only if:

- all unit tests pass
- all GameTests pass
- rerun and idempotence tests pass
- retained dormant-bridge regression tests, including cave support descent, pass without being
  treated as supported-feature evidence
- the visual checklist is reviewed for required scenarios
- no unexpected chunk duplication occurs
- profiling shows no runaway planner path during normal play

## 18. Decision Changers

Revisit this spec if any of the following become requirements:

- tests must use only true vanilla or modded structure starts
- CI must enforce pixel-perfect screenshot comparison
- v1 must support huge bridges, tunnels, or arbitrary terrain complexity
- pack authors need broader control over supported structures and terrain classes

Preferred response if those conditions change:

- retain synthetic scenes for most coverage
- expand data-driven config rather than hardcoding more cases
- widen the bridge and terrain test matrix before feature work proceeds
