package com.example.bosseln.data

import com.example.bosseln.data.db.*

class GameRepository(
    private val teamDao: TeamDao,
    private val matchDao: MatchDao,
    private val throwDao: ThrowDao,
    private val playerDao: PlayerDao
) {
    suspend fun upsertTeam(name: String, color: Long = 0xFF2196F3): Team {
        val id = teamDao.insert(Team(name = name, colorArgb = color))
        return Team(id, name, color)
    }

    suspend fun deleteTeam(team: Team) = teamDao.delete(team)

     suspend fun addPlayer(teamId: Long, name: String): Player {
        // immer dichte Indizes sicherstellen
        resequence(teamId)
        val idx = playerDao.countForTeam(teamId)
        val id = playerDao.insert(Player(teamId = teamId, name = name, orderIndex = idx))
        return Player(id = id, teamId = teamId, name = name, orderIndex = idx)
    }

    suspend fun removePlayer(player: Player) {
        playerDao.delete(player)
        resequence(player.teamId)
    }
    suspend fun players(teamId: Long) = playerDao.forTeam(teamId)

    suspend fun rotatePlayers(teamId: Long) {
        val size = playerDao.countForTeam(teamId)
        if (size <= 1) return
        playerDao.rotateOnce(teamId)
    }

    // *** NEU: Indizes auf 0..n-1 schließen (bei Lücken nach Löschungen) ***
    suspend fun resequence(teamId: Long) {
        val list = playerDao.forTeam(teamId) // bereits nach orderIndex sortiert
        list.forEachIndexed { i, p -> playerDao.updateOrder(p.id, i) }
    }

    suspend fun createMatch(title: String): Match {
        val id = matchDao.insert(Match(title = title))
        return Match(id = id, title = title)
    }

    suspend fun addEvent(ev: ThrowEvent): ThrowEvent {
        val id = throwDao.insert(ev)
        return ev.copy(id = id)
    }

    suspend fun nextSeq(matchId: Long): Int =
        (throwDao.maxSeq(matchId) ?: 0) + 1

    suspend fun getThrows(matchId: Long) = throwDao.forMatch(matchId)
    suspend fun teams() = teamDao.all()
    suspend fun matches() = matchDao.all()
}

