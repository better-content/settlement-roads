# Visual Verification Checklist

Use this checklist after invariant assertions pass. Visual confirmation is secondary and exists to catch obvious design regressions that remain logically valid.

Supported review uses `allow_water_bridges = false`. The bridge and support sections are retained
only for explicitly commissioned work on dormant experimental scaffolding; they are not current
acceptance gates and must not be used to claim working bridge support.

## Global Review Conditions

- fixed time of day
- fixed weather
- no mobs or unrelated world interaction
- scenario loaded through deterministic template or debug command
- planner and placer run explicitly
- screenshot angle taken from marked camera pad where available

## Roads

- ring stays outside structure footprint
- ring reads as intentional rather than jagged noise
- anchor exits point in a plausible direction toward the destination structure
- path width is visually consistent
- path does not cut through structure walls or corners
- `minecraft:dirt_path` appears only in grassy scenarios
- `minecraft:gravel` appears in non-grassy scenarios
- `minecraft:coarse_dirt` remains sparse and edge-biased
- coarse dirt does not create a checkerboard pattern

## Dormant Bridge Scaffolding

- bridge placement feels cheaper than the visible reroute alternative
- deck width matches route classification
- parapets or side walls feel chunky rather than minimal
- bridge deck clears the waterline
- underside does not appear floating
- supports align with visible load points
- mid-piers appear where longer spans would need them
- buried support contact looks grounded rather than decorative

## Dormant Bridge Supports

- supports continue past water and visible cave air where required
- supports do not terminate on leaves, plants, logs, or fluids
- widened footing is visible on soft or uneven base terrain
- no obvious unsupported deck segments remain

## Clusters And Routing

- clustered structures connect with sparse, readable routes
- v1 routes do not create unnecessary loops
- route choices look consistent with terrain difficulty
- too-wide crossings are visibly rejected or rerouted rather than forced

## Rerun Stability

- second run does not add duplicate path width
- during explicit dormant-bridge review, a second run does not double-place bridge walls or supports
- chunk-edge placements look continuous after rerun

## Review Record

Record each visual pass with:

- date
- scenario id
- reviewer
- result: pass or fail
- notes on anything that looks logically valid but aesthetically weak
