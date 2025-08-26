package com.example.bosseln.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

// ===== Teams =====
@Dao
interface TeamDao {
    @Query("SELECT * FROM teams ORDER BY name")
    suspend fun all(): List<Team>

    @Insert suspend fun insert(team: Team): Long
    @Delete suspend fun delete(team: Team)
}

// ===== Matches =====
@Dao
interface MatchDao {
    @Insert suspend fun insert(match: Match): Long

    @Query("SELECT * FROM matches ORDER BY startedAt DESC")
    suspend fun all(): List<Match>

    @Query("SELECT * FROM matches WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun ongoing(): Match?

    @Query("UPDATE matches SET endedAt=:endedAt WHERE id=:matchId")
    suspend fun endMatch(matchId: Long, endedAt: Long)

    @Query("DELETE FROM matches WHERE id=:matchId")
    suspend fun deleteById(matchId: Long)
}

// ===== MatchTeam =====
@Dao
interface MatchTeamDao {
    @Insert suspend fun insert(mt: MatchTeam)

    @Query("SELECT * FROM match_teams WHERE matchId=:matchId")
    suspend fun forMatch(matchId: Long): List<MatchTeam>
}

// ===== MatchResult =====
@Dao
interface MatchResultDao {
    @Insert suspend fun insertAll(results: List<MatchResult>)

    @Query("SELECT * FROM match_results WHERE matchId=:matchId")
    suspend fun forMatch(matchId: Long): List<MatchResult>
}

// ===== Throws =====
data class TeamDistanceRow(val teamId: Long, val total: Double)


data class PlayerThrowStat(
    val playerId: Long,
    val throws: Int,
    val avgDistance: Double?
)

@Dao
interface ThrowDao {
    @Query("SELECT * FROM throw_events WHERE matchId=:matchId ORDER BY sequence")
    suspend fun forMatch(matchId: Long): List<ThrowEvent>

    @Insert suspend fun insert(ev: ThrowEvent): Long

    @Query("SELECT MAX(sequence) FROM throw_events WHERE matchId=:matchId")
    suspend fun maxSeq(matchId: Long): Int?

    @Query("""
        SELECT COUNT(*) FROM throw_events 
        WHERE matchId=:matchId AND teamId=:teamId AND kind='ABWURF'
    """)
    suspend fun countThrowsForTeam(matchId: Long, teamId: Long): Int

    @Query("""
        SELECT teamId, COALESCE(SUM(distanceSinceLastM),0) as total
        FROM throw_events
        WHERE matchId=:matchId AND teamId IN (:teamIds)
        GROUP BY teamId
    """)
    suspend fun totalDistanceByTeam(matchId: Long, teamIds: List<Long>): List<TeamDistanceRow>

   @Query("""
        SELECT playerId AS playerId,
               COUNT(*)   AS throws,
               AVG(distanceSinceLastM) AS avgDistance
        FROM throw_events
        WHERE matchId=:matchId AND teamId=:teamId AND kind='ABWURF' AND playerId IS NOT NULL
        GROUP BY playerId
        ORDER BY throws DESC
    """)
    suspend fun playerStats(matchId: Long, teamId: Long): List<PlayerThrowStat>

@Query("""
UPDATE throw_events
SET lat=:lat, lon=:lon, accuracyM=:acc, distanceSinceLastM=:dist
WHERE id=:id
""")
suspend fun updateFix(id: Long, lat: Double?, lon: Double?, acc: Float?, dist: Double?)

}

// ===== Players =====
@Dao
interface PlayerDao {
    @Query("SELECT * FROM players WHERE teamId=:teamId ORDER BY orderIndex ASC")
    suspend fun forTeam(teamId: Long): List<Player>

    @Insert suspend fun insert(player: Player): Long
    @Delete suspend fun delete(player: Player)

    @Query("UPDATE players SET orderIndex=:newIndex WHERE id=:playerId")
    suspend fun updateOrder(playerId: Long, newIndex: Int)

    @Query("SELECT COUNT(*) FROM players WHERE teamId=:teamId")
    suspend fun countForTeam(teamId: Long): Int

    @Query("""
        UPDATE players
        SET orderIndex = CASE
            WHEN orderIndex = (SELECT MIN(orderIndex) FROM players WHERE teamId=:teamId)
                THEN (SELECT MAX(orderIndex) FROM players WHERE teamId=:teamId)
            ELSE orderIndex - 1
        END
        WHERE teamId=:teamId
    """)
    suspend fun rotateOnce(teamId: Long)
}




// ===== Counter Adjustments =====
@Dao
interface CounterAdjustmentDao {
    @Upsert suspend fun upsert(adj: CounterAdjustment): Long

    @Query("SELECT * FROM counter_adjustments WHERE matchId=:matchId AND teamId=:teamId LIMIT 1")
    suspend fun get(matchId: Long, teamId: Long): CounterAdjustment?

    @Query("SELECT * FROM counter_adjustments WHERE matchId=:matchId")
    suspend fun forMatch(matchId: Long): List<CounterAdjustment>
}
