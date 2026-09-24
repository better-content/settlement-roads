package com.bettercontent.settlementroads.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

/** Deterministic radial placement field around seed-stable macro-cell centres. */
public final class ClusteredSpreadStructurePlacement extends StructurePlacement {
    private static final int SITES_PER_CLUSTER = 2;
    public static final Codec<ClusteredSpreadStructurePlacement> CODEC = RecordCodecBuilder.create(instance ->
            StructurePlacement.placementCodec(instance).and(instance.group(
                    Codec.intRange(32, 1024).fieldOf("cluster_spacing").forGetter(ClusteredSpreadStructurePlacement::clusterSpacing),
                    Codec.intRange(8, 64).fieldOf("cluster_radius").forGetter(ClusteredSpreadStructurePlacement::clusterRadius),
                    Codec.intRange(64, 4096).fieldOf("baseline_spacing").forGetter(ClusteredSpreadStructurePlacement::baselineSpacing)
            )).apply(instance, (locateOffset, reductionMethod, frequency, salt, exclusionZone, clusterSpacing, clusterRadius, baselineSpacing) ->
                    new ClusteredSpreadStructurePlacement(locateOffset, reductionMethod, frequency, salt, exclusionZone,
                            clusterSpacing, clusterRadius, baselineSpacing)));

    private final int clusterSpacing;
    private final int clusterRadius;
    private final int baselineSpacing;

    public ClusteredSpreadStructurePlacement(Vec3i locateOffset,
                                             FrequencyReductionMethod frequencyReductionMethod,
                                             float frequency,
                                             int salt,
                                             Optional<ExclusionZone> exclusionZone,
                                             int clusterSpacing,
                                             int clusterRadius,
                                             int baselineSpacing) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
        if (clusterRadius < 8 || clusterRadius * 2 >= clusterSpacing) {
            throw new IllegalArgumentException("cluster_radius must be less than half of cluster_spacing");
        }
        if (baselineSpacing < clusterSpacing) {
            throw new IllegalArgumentException("baseline_spacing must be at least cluster_spacing");
        }
        this.clusterSpacing = clusterSpacing;
        this.clusterRadius = clusterRadius;
        this.baselineSpacing = baselineSpacing;
    }

    public int clusterSpacing() { return clusterSpacing; }
    public int clusterRadius() { return clusterRadius; }
    public int baselineSpacing() { return baselineSpacing; }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        return ClusteredSpreadPolicy.INSTANCE.site(
                state.getLevelSeed(), chunkX, chunkZ, salt(), 0, clusterSpacing, clusterRadius, baselineSpacing, SITES_PER_CLUSTER
        ) != null;
    }

    @Override
    public StructurePlacementType<?> type() {
        return SettlementRoadsWorldgen.CLUSTERED_SPREAD.get();
    }
}
