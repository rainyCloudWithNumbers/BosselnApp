package com.example.bosseln.data.db

import androidx.room.*

@Dao
interface TeamDao {
    @Query("SELECT * FROM Team ORDER BY name")
    suspend fun all(): List<Team>

    @Insert suspend fun insert(team: Team): Long
    @Delete suspend fun delete(team: Team)
}

@Dao
interface MatchDao {
    @Insert suspend fun insert(match: Match): Long
    @Query("SELECT * FROM Match ORDER BY startedAt DESC")
    suspend fun all(): List<Match>
}

@Dao
interface ThrowDao {
    @Query("SELECT * FROM ThrowEvent WHERE matchId=:matchId ORDER BY sequence")
    suspend fun forMatch(matchId: Long): List<ThrowEvent>

    @Insert suspend fun insert(ev: ThrowEvent): Long

    @Query("SELECT MAX(sequence) FROM ThrowEvent WHERE matchId=:matchId")
    suspend fun maxSeq(matchId: Long): Int?
}

@Dao
interface PlayerDao {
    @Query("SELECT * FROM Player WHERE teamId=:teamId ORDER BY orderIndex ASC")
    suspend fun forTeam(teamId: Long): List<Player>

    @Insert suspend fun insert(player: Player): Long
    @Delete suspend fun delete(player: Player)

    @Query("UPDATE Player SET orderIndex=:newIndex WHERE id=:playerId")
    suspend fun updateOrder(playerId: Long, newIndex: Int)

    @Query("SELECT COUNT(*) FROM Player WHERE teamId=:teamId")
    suspend fun countForTeam(teamId: Long): Int

    // *** NEU: atomare Rotation (ersten nach hinten schieben) ***
    @Query("""
        UPDATE Player
        SET orderIndex = CASE
            WHEN orderIndex = (SELECT MIN(orderIndex) FROM Player WHERE teamId=:teamId)
                THEN (SELECT MAX(orderIndex) FROM Player WHERE teamId=:teamId)
            ELSE orderIndex - 1
        END
        WHERE teamId=:teamId
    """)
    suspend fun rotateOnce(teamId: Long)
}