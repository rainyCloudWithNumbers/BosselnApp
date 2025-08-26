package com.example.bosseln.data

import com.example.bosseln.data.db.CounterAdjustment
import com.example.bosseln.data.db.CounterAdjustmentDao
import com.example.bosseln.data.db.Match
import com.example.bosseln.data.db.MatchDao
import com.example.bosseln.data.db.MatchResult
import com.example.bosseln.data.db.MatchResultDao
import com.example.bosseln.data.db.MatchTeam
import com.example.bosseln.data.db.MatchTeamDao
import com.example.bosseln.data.db.Player
import com.example.bosseln.data.db.PlayerDao
import com.example.bosseln.data.db.Team
import com.example.bosseln.data.db.TeamDao
import com.example.bosseln.data.db.ThrowDao
import com.example.bosseln.data.db.ThrowEvent

class GameRepository(
    private val teamDao: TeamDao,
    private val matchDao: MatchDao,
    private val throwDao: ThrowDao,
    private val playerDao: PlayerDao,
    private val adjDao: CounterAdjustmentDao,
    private val matchTeamDao: MatchTeamDao,
    private val resultDao: MatchResultDao
) {
    // ===== Teams =====
    suspend fun upsertTeam(name: String, color: Long = 0xFF2196F3): Team {
        val id = teamDao.insert(Team(name = name, colorArgb = color))
        return Team(id = id, name = name, colorArgb = color)
    }
    suspend fun deleteTeam(team: Team) = teamDao.delete(team)
    suspend fun teams(): List<Team> = teamDao.all()

    // ===== Players =====
    suspend fun addPlayer(teamId: Long, name: String): Player {
        resequence(teamId)
        val idx = playerDao.countForTeam(teamId)
        val id = playerDao.insert(Player(teamId = teamId, name = name, orderIndex = idx))
        return Player(id = id, teamId = teamId, name = name, orderIndex = idx)
    }

  // Spieler-Statistiken (wir mappen direkt Namen dazu)
    suspend fun playerStatsWithNames(matchId: Long, teamId: Long): List<Triple<Long, String, com.example.bosseln.data.db.PlayerThrowStat>> {
        val stats = throwDao.playerStats(matchId, teamId)
        val players = playerDao.forTeam(teamId).associateBy { it.id }
        return stats.mapNotNull { s ->
            val p = players[s.playerId] ?: return@mapNotNull null
            Triple(p.id, p.name, s)
        }
    }

    suspend fun removePlayer(player: Player) { playerDao.delete(player); resequence(player.teamId) }
    suspend fun players(teamId: Long): List<Player> = playerDao.forTeam(teamId)
    suspend fun rotatePlayers(teamId: Long) {
        val size = playerDao.countForTeam(teamId)
        if (size > 1) playerDao.rotateOnce(teamId)
    }
    suspend fun resequence(teamId: Long) {
        val list = playerDao.forTeam(teamId)
        list.forEachIndexed { i, p -> playerDao.updateOrder(p.id, i) }
    }

    // ===== Matches =====
    suspend fun createMatch(title: String, teamIds: Set<Long>): Match {
        val id = matchDao.insert(Match(title = title))
        teamIds.forEach { tid -> matchTeamDao.insert(MatchTeam(matchId = id, teamId = tid)) }
        return Match(id = id, title = title)
    }
    suspend fun ongoingMatch(): Match? = matchDao.ongoing()
    suspend fun participatingTeamIds(matchId: Long): List<Long> =
        matchTeamDao.forMatch(matchId).map { mt -> mt.teamId }
    suspend fun getAllMatches(): List<Match> = matchDao.all()

suspend fun deleteMatch(matchId: Long) {
    matchDao.deleteById(matchId)   // FK CASCADE räumt ThrowEvents/MatchTeams/Results auf
}

suspend fun updateThrowFix(id: Long, lat: Double?, lon: Double?, acc: Float?, dist: Double?) =
    throwDao.updateFix(id, lat, lon, acc, dist)


    // ===== Throws & Aggregationen =====
    suspend fun addEvent(ev: ThrowEvent): ThrowEvent { val id = throwDao.insert(ev); return ev.copy(id = id) }
    suspend fun nextSeq(matchId: Long): Int = (throwDao.maxSeq(matchId) ?: 0) + 1
    suspend fun throwsForMatch(matchId: Long): List<ThrowEvent> = throwDao.forMatch(matchId)

    suspend fun countThrowsForTeam(matchId: Long, teamId: Long): Int =
        throwDao.countThrowsForTeam(matchId, teamId)

    suspend fun totalDistanceByTeam(matchId: Long, teamIds: List<Long>): Map<Long, Double> =
        throwDao.totalDistanceByTeam(matchId, teamIds).associate { row -> row.teamId to row.total }

    // ===== Counter Adjustments =====
    suspend fun getOffsets(matchId: Long): Map<Long, Int> =
        adjDao.forMatch(matchId).associate { it.teamId to it.offset }

    suspend fun setOffset(matchId: Long, teamId: Long, offset: Int) {
        adjDao.upsert(CounterAdjustment(matchId = matchId, teamId = teamId, offset = offset))
    }

    // ===== Results / History =====
    suspend fun resultsForMatch(matchId: Long): List<MatchResult> = resultDao.forMatch(matchId)

    suspend fun endMatchAndPersistSummary(match: Match) {
        val now = System.currentTimeMillis()
        matchDao.endMatch(match.id, now)

        val teamIds = participatingTeamIds(match.id)
        if (teamIds.isEmpty()) return

        val distances = totalDistanceByTeam(match.id, teamIds)
        val results = teamIds.map { tid ->
            val throws = countThrowsForTeam(match.id, tid)
            val dist = distances[tid] ?: 0.0
            MatchResult(matchId = match.id, teamId = tid, throwsCount = throws, totalDistanceM = dist)
        }
        resultDao.insertAll(results)
    }
}
