# Debug Scenarios

This file tracks the deterministic scenario inventory used by GameTests, debug commands, and manual visual review.

Namespace note:

- Command examples use the target namespace `settlementroads` from the project spec.
- The current implementation already registers commands under that namespace.

## Commands

Expected debug command surface:

```text
/settlementroads debug spawn_scenario <id>
/settlementroads debug plan_here
/settlementroads debug place_here
/settlementroads debug clear_here
```

## Scenario List

### `flat_grassy_twins`

- Purpose: baseline path generation on simple grassy terrain
- Current automation: covered by `rerunPlacementIsIdempotent`
- Structures: 2
- Terrain: flat grassy
- Water crossing: no
- Expected output: dirt path connection, sparse coarse dirt detail, no bridge
- Primary assertions: palette choice, no bridge segments, stable anchor selection

### `grassy_river_crossing`

- Purpose: validate narrow-river bridge placement in grassy terrain
- Current automation: covered by `grassyRiverCrossingProducesBridge`
- Structures: 2
- Terrain: grassy with narrow river
- Water crossing: yes
- Expected output: dirt-path approaches, stone-brick bridge, valid supports
- Primary assertions: bridge allowed, clearance valid, supports reach solid terrain

### `rocky_crossing`

- Purpose: validate non-grassy palette selection with a bridge
- Current automation: covered by `rockyCrossingUsesGravelAndBridge`
- Structures: 2
- Terrain: rocky or otherwise non-grassy
- Water crossing: yes
- Expected output: gravel path, stone-brick bridge
- Primary assertions: gravel palette, valid supports, no grassy palette leak

### `cave_under_riverbed`

- Purpose: validate support descent through water and cave air
- Current automation: covered by `caveUnderRiverContinuesSupportsToSolid`
- Structures: 2
- Terrain: river with void below bed and true solid deeper down
- Water crossing: yes
- Expected output: bridge supports continue through cavity to valid support block
- Primary assertions: no false support termination, no floating bridge sections

### `too_wide_river`

- Purpose: validate bridge rejection logic
- Current automation: covered by `tooWideRiverDetoursWithoutBridge`
- Structures: 2
- Terrain: crossing exceeds configured span
- Water crossing: yes
- Expected output: no bridge; reroute or no connection based on config
- Primary assertions: bridge rejection reason stable, planner does not force invalid bridge

### `three_structure_cluster`

- Purpose: validate connected-component clustering and sparse graph selection
- Current automation: JVM planner test coverage only
- Structures: 3
- Terrain: mixed but simple
- Water crossing: optional
- Expected output: one transitive cluster with MST-style sparse roads
- Primary assertions: one cluster id set, no redundant loop in v1

### `rerun_stability`

- Purpose: validate planner and placer idempotence
- Current automation: covered by `rerunPlacementIsIdempotent`
- Structures: scenario-dependent
- Terrain: any deterministic setup
- Water crossing: optional
- Expected output: identical world state after second run
- Primary assertions: no duplicate blocks, no duplicate placement stamps

### `chunk_boundary_split`

- Purpose: validate chunk-safe placement across boundaries
- Current automation: JVM chunk-index coverage only
- Structures: 2
- Terrain: route forced over chunk edge
- Water crossing: optional
- Expected output: correct placement on both sides with stable reruns
- Primary assertions: no boundary duplication, chunk stamps remain stable

## Per-Scenario Authoring Rules

Every scenario template should include:

- explicit structure markers or known synthetic `StructureNode` inputs
- fixed camera pads
- optional top-down pillar or marker
- signage or labels for intended inspection angles
- a clean reset area for `clear_here`

## Update Policy

When a phase changes planner behavior, update this file with:

- new scenario ids
- changed expectations
- newly required assertions
- deprecated scenarios, if any
