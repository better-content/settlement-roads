package com.bettercontent.settlementroads.api.event

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.eventbus.api.Event

/** Observes a player's authoritative progress along a generated settlement road. */
class RoadJourneyEvent(
    val player: ServerPlayer,
    val episodeId: String,
    origin: BlockPos,
    position: BlockPos,
    val stage: Stage,
) : Event() {
    val origin: BlockPos = origin.immutable()
    val position: BlockPos = position.immutable()

    enum class Stage { ENTERED_WILDERNESS, ARRIVED_SETTLEMENT }
}
