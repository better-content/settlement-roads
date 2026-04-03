package com.gerald.settlementroads.data

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData

class SettlementRoadsSavedData(
    var state: RoadNetworkState = RoadNetworkState()
) : SavedData() {
    override fun save(tag: net.minecraft.nbt.CompoundTag): net.minecraft.nbt.CompoundTag {
        tag.merge(state.toTag())
        return tag
    }

    fun update(transform: (RoadNetworkState) -> RoadNetworkState) {
        state = transform(state)
        setDirty()
    }

    companion object {
        private const val DATA_NAME: String = "settlementroads_network"

        fun get(level: ServerLevel): SettlementRoadsSavedData =
            level.server.overworld().dataStorage.computeIfAbsent(
                { tag -> SettlementRoadsSavedData(RoadNetworkState.fromTag(tag)) },
                ::SettlementRoadsSavedData,
                DATA_NAME
            )
    }
}
