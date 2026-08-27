package com.bettercontent.settlementroads.compat

import com.bettercontent.settlementroads.data.PlannedRoadNetwork
import com.bettercontent.settlementroads.planner.model.PathSegment
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

/** Correlates a registered road entered in wilderness with its actual settlement arrival. */
object ThreadsRoadTracker {
    private const val ROOT = "SettlementRoadsThreadEpisode"

    fun tick(level: ServerLevel, network: PlannedRoadNetwork) {
        if (level.gameTime % 10L != 0L || network.structures.isEmpty()) return
        level.players().forEach { player -> tickPlayer(player, network) }
    }

    private fun tickPlayer(player: ServerPlayer, network: PlannedRoadNetwork) {
        val pos = player.blockPosition()
        val settlement = network.structures.firstOrNull { it.bounds.isInside(pos) }
        val onRoad = network.rings.any { ring -> ring.perimeter.any { near(it, pos) } } ||
            network.clusters.any { cluster -> cluster.connections.any { connection -> connection.segments.any { segment -> segment.positions().any { near(it, pos) } } } }
        val persisted = player.persistentData.getCompound(Player.PERSISTED_NBT_TAG)
        var episode = persisted.getCompound(ROOT)
        if (onRoad && settlement == null && episode.isEmpty) {
            val token = "${player.uuid}:road:${player.server.tickCount}"
            episode = CompoundTag().apply { putString("token", token);putLong("origin", pos.asLong());putLong("lastRoad", player.level().gameTime) }
            persisted.put(ROOT, episode);player.persistentData.put(Player.PERSISTED_NBT_TAG, persisted)
            emit(player, "road_enter", "wilderness", token)
            return
        }
        if (episode.isEmpty) return
        if (onRoad) episode.putLong("lastRoad", player.level().gameTime)
        val token = episode.getString("token")
        if (settlement != null && player.level().gameTime - episode.getLong("lastRoad") <= 40L && pos.distSqr(BlockPos.of(episode.getLong("origin"))) >= 32.0 * 32.0 && token.isNotBlank()) {
            emit(player, "road_arrival", "settlement", token)
            persisted.remove(ROOT)
        } else persisted.put(ROOT, episode)
        player.persistentData.put(Player.PERSISTED_NBT_TAG, persisted)
    }

    private fun near(road: BlockPos, player: BlockPos) = road.x == player.x && road.z == player.z && kotlin.math.abs(road.y - player.y) <= 2
    private fun PathSegment.positions(): List<BlockPos> = when (this) { is PathSegment.Ground -> blocks;is PathSegment.Bridge -> blocks }
    private fun emit(player: ServerPlayer, type: String, value: String, token: String) { try { Class.forName("com.bettercontent.threads.api.ThreadSignals").getMethod("emit",ServerPlayer::class.java,String::class.java,String::class.java,String::class.java).invoke(null,player,type,value,token) } catch (_: ReflectiveOperationException) {} }
}
