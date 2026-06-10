package com.example.bosseln.vm

import android.app.Application
import android.content.ContentValues
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bosseln.data.GameRepository
import com.example.bosseln.data.db.AppDatabase
import com.example.bosseln.data.db.Player
import com.example.bosseln.data.db.Team
import com.example.bosseln.data.db.ThrowEvent
import com.example.bosseln.domain.LocationHelper
import com.example.bosseln.domain.haversineMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val repo = GameRepository(
        db.teamDao(),
        db.matchDao(),
        db.throwDao(),
        db.playerDao(),
        db.counterAdjustmentDao(),
        db.matchTeamDao(),
        db.matchResultDao()
    )
    private val locator = LocationHelper(app)

    // ---------- UI-State ----------
    data class UiState(
        val teams: List<com.example.bosseln.data.db.Team> = emptyList(),
        val players: Map<Long, List<com.example.bosseln.data.db.Player>> = emptyMap(),
        val selectedTeams: Set<Long> = emptySet(),
        val matchTitle: String = "",
        val currentMatch: com.example.bosseln.data.db.Match? = null,
        val throws: List<com.example.bosseln.data.db.ThrowEvent> = emptyList(),
        val counterOffsets: Map<Long, Int> = emptyMap(),
        val historyVersion: Int = 0,
        val refiningTeams: Set<Long> = emptySet()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Teams laden & evtl. laufendes Match wiederherstellen
        viewModelScope.launch(Dispatchers.IO) {
            val teams = repo.teams()
            _state.value = _state.value.copy(teams = teams)
            loadAllPlayers()

            val ongoing = repo.ongoingMatch()
            if (ongoing != null) {
                val teamIds = repo.participatingTeamIds(ongoing.id).toSet()
                val offsets = repo.getOffsets(ongoing.id)
                val throws = repo.throwsForMatch(ongoing.id)
                _state.value = _state.value.copy(
                    currentMatch = ongoing,
                    selectedTeams = teamIds,
                    counterOffsets = offsets,
                    throws = throws
                )
                teamIds.forEach { loadPlayers(it) }
            }
        }
    }

    // ---------- History-Helper ----------
    suspend fun getAllMatches() = repo.getAllMatches()
    suspend fun getMatchResults(matchId: Long) = repo.resultsForMatch(matchId)

    suspend fun getThrowsForMatch(matchId: Long) = repo.throwsForMatch(matchId)
    suspend fun playerStatsForTeam(matchId: Long, teamId: Long) =
    repo.playerStatsWithNames(matchId, teamId)

fun deleteMatch(matchId: Long) = viewModelScope.launch(Dispatchers.IO) {
    repo.deleteMatch(matchId)
    // trigger History-Reload
    _state.value = _state.value.copy(historyVersion = _state.value.historyVersion + 1)
}

    // ---------- Team Management ----------
    fun refreshTeams() = viewModelScope.launch(Dispatchers.IO) {
        _state.value = _state.value.copy(teams = repo.teams())
        loadAllPlayers() 
    }

    fun createTeam(name: String, colorArgb: Long) = viewModelScope.launch(Dispatchers.IO) {
        repo.upsertTeam(name, colorArgb)
        refreshTeams()
    }

    fun deleteTeam(team: Team) = viewModelScope.launch(Dispatchers.IO) {
        repo.deleteTeam(team)
        refreshTeams()
    }

    fun loadPlayers(teamId: Long) = viewModelScope.launch(Dispatchers.IO) {
        val map = _state.value.players.toMutableMap()
        map[teamId] = repo.players(teamId)
        _state.value = _state.value.copy(players = map)
    }

    private fun loadAllPlayers() = viewModelScope.launch(Dispatchers.IO) {
        val allTeams = _state.value.teams
        val map = mutableMapOf<Long, List<Player>>()
        allTeams.forEach { t -> map[t.id] = repo.players(t.id) }
        _state.value = _state.value.copy(players = map)
    }

    fun addPlayer(teamId: Long, name: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.addPlayer(teamId, name)
        loadPlayers(teamId)
    }

    fun removePlayer(player: Player) = viewModelScope.launch(Dispatchers.IO) {
        repo.removePlayer(player)
        loadPlayers(player.teamId)
    }

    // ---------- Match Setup ----------
    fun toggleTeamSelection(team: Team) {
        val sel = _state.value.selectedTeams.toMutableSet()
        if (sel.contains(team.id)) sel.remove(team.id) else sel.add(team.id)
        _state.value = _state.value.copy(selectedTeams = sel)
    }

    fun setTitle(title: String) {
        _state.value = _state.value.copy(matchTitle = title)
    }

    fun startMatch() = viewModelScope.launch(Dispatchers.IO) {
        val selected = _state.value.selectedTeams
        if (selected.isEmpty()) return@launch

        // Start-Standort erfassen
        val loc = locator.getAccurateOnce()

        val match = repo.createMatch(_state.value.matchTitle.ifBlank { "Bosseln" }, selected)

        // Für jedes Team einen START-Punkt setzen, damit der erste Abwurf eine Distanz hat
        selected.forEach { tid ->
            val seq = repo.nextSeq(match.id)
            repo.addEvent(
                ThrowEvent(
                    matchId = match.id,
                    teamId = tid,
                    sequence = seq,
                    kind = "START",
                    lat = loc?.lat,
                    lon = loc?.lon,
                    accuracyM = loc?.accuracyM
                )
            )
        }

        val offsets = repo.getOffsets(match.id)
        val throws = repo.throwsForMatch(match.id)

        _state.value = _state.value.copy(
            currentMatch = match,
            throws = throws,
            counterOffsets = offsets
        )
        selected.forEach { loadPlayers(it) }
    }

    fun finishMatch() = viewModelScope.launch(Dispatchers.IO) {
        val match = _state.value.currentMatch ?: return@launch

        // Export as JSON file
        exportMatchToJson(match)

        repo.endMatchAndPersistSummary(match)
        _state.value = _state.value.copy(
            currentMatch = null,
            selectedTeams = emptySet(),
            throws = emptyList(),
            counterOffsets = emptyMap()
        )
    }

    private suspend fun exportMatchToJson(match: com.example.bosseln.data.db.Match) {
        try {
            val teams = _state.value.teams.filter { _state.value.selectedTeams.contains(it.id) }
            val throws = repo.throwsForMatch(match.id)

            val root = org.json.JSONObject()
            root.put("matchId", match.id)
            root.put("title", match.title)
            root.put("startedAt", match.startedAt)
            root.put("endedAt", System.currentTimeMillis())

            val teamsArray = org.json.JSONArray()
            teams.forEach { t ->
                val teamObj = org.json.JSONObject()
                teamObj.put("id", t.id)
                teamObj.put("name", t.name)
                teamObj.put("color", t.colorArgb)
                teamsArray.put(teamObj)
            }
            root.put("teams", teamsArray)

            val throwsArray = org.json.JSONArray()
            throws.forEach { e ->
                val throwObj = org.json.JSONObject()
                throwObj.put("id", e.id)
                throwObj.put("teamId", e.teamId)
                throwObj.put("sequence", e.sequence)
                throwObj.put("timestamp", e.timestamp)
                throwObj.put("lat", e.lat ?: org.json.JSONObject.NULL)
                throwObj.put("lon", e.lon ?: org.json.JSONObject.NULL)
                throwObj.put("accuracy", e.accuracyM?.toDouble() ?: org.json.JSONObject.NULL)
                throwObj.put("distanceSinceLast", e.distanceSinceLastM ?: org.json.JSONObject.NULL)
                throwObj.put("playerId", e.playerId ?: org.json.JSONObject.NULL)
                throwsArray.put(throwObj)
            }
            root.put("throws", throwsArray)

            val fileName = "match_${match.id}_${System.currentTimeMillis()}.json"
            val file = java.io.File(getApplication<Application>().getExternalFilesDir(null), fileName)
            file.writeText(root.toString(4))
            android.util.Log.d("GameViewModel", "Exported match to ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("GameViewModel", "Failed to export match JSON", e)
        }
    }

    // ---------- Zählen / Abwurf ----------
    fun recordAbwurf(teamId: Long) = viewModelScope.launch(Dispatchers.IO) {
        val match = _state.value.currentMatch ?: return@launch
        val seq = repo.nextSeq(match.id)

        // Standort (kann null sein)
        val loc = locator.getAccurateOnce()

        // letzten Team-Event (START oder ABWURF) ermitteln
        val all = repo.throwsForMatch(match.id)
        val lastEvent = all.lastOrNull { it.teamId == teamId }

        val dist = if (
            loc != null &&
            lastEvent?.lat != null && lastEvent.lon != null
        ) {
            haversineMeters(
                lastEvent.lat,
                lastEvent.lon,
                loc.lat,
                loc.lon
            )
        } else null

        val baseCount = repo.countThrowsForTeam(match.id, teamId)
        val players = _state.value.players[teamId].orEmpty()
        val currentPlayerId = if (players.isNotEmpty()) players[baseCount % players.size].id else null


        // Event speichern – auch ohne Standort
        val saved = repo.addEvent(
            ThrowEvent(
                matchId = match.id,
                teamId = teamId,
                sequence = seq,
                kind = "ABWURF",
                lat = loc?.lat,
                lon = loc?.lon,
                accuracyM = loc?.accuracyM,
                distanceSinceLastM = dist,
                playerId = currentPlayerId
            )
        )

        // UI sofort aktualisieren
        refreshThrows()

        // UI-Indikator: setze z. B. state.refiningTeams += teamId
        markRefining(teamId, true)

        viewModelScope.launch(Dispatchers.IO) {
            val better = locator.getBestFixWithin()
            if (better != null) {
                // Distanz zum letzten Team-Event neu berechnen
                val allUpdated = repo.throwsForMatch(match.id)
                val last = allUpdated.lastOrNull { it.teamId == teamId && it.id != saved.id }
                
                val newDist = if (last?.lat != null && last.lon != null)
                    haversineMeters(last.lat, last.lon, better.lat, better.lon)
                else null

                repo.updateThrowFix(saved.id, better.lat, better.lon, better.accuracyM, newDist)
                refreshThrows()
            }
            markRefining(teamId, false)
        }
    }

    private fun markRefining(teamId: Long, on: Boolean) {
        val current = _state.value.refiningTeams.toMutableSet()
        if (on) current.add(teamId) else current.remove(teamId)
        _state.value = _state.value.copy(refiningTeams = current)
    }

    private fun refreshThrows() = viewModelScope.launch(Dispatchers.IO) {
        val match = _state.value.currentMatch ?: return@launch
        _state.value = _state.value.copy(throws = repo.throwsForMatch(match.id))
    }

    // ---------- Foto-URI (für spätere Foto-Funktion) ----------
    fun createPhotoUri(): String? {
        val ctx = getApplication<Application>()
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "bosseln_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Bosseln")
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        return uri?.toString()
    }

    // ---------- Anzeigezähler & Korrektur ----------
    suspend fun getDisplayCount(teamId: Long): Int {
        val match = _state.value.currentMatch ?: return 0
        val base = repo.countThrowsForTeam(match.id, teamId)
        val off = _state.value.counterOffsets[teamId] ?: 0
        return base + off
    }

    fun resetCounter(teamId: Long) = viewModelScope.launch(Dispatchers.IO) {
        val match = _state.value.currentMatch ?: return@launch
        val base = repo.countThrowsForTeam(match.id, teamId)
        repo.setOffset(match.id, teamId, -base)
        val m = _state.value.counterOffsets.toMutableMap()
        m[teamId] = -base
        _state.value = _state.value.copy(counterOffsets = m)
    }

    fun setCounter(teamId: Long, value: Int) = viewModelScope.launch(Dispatchers.IO) {
        val match = _state.value.currentMatch ?: return@launch
        val base = repo.countThrowsForTeam(match.id, teamId)
        val off = value - base
        repo.setOffset(match.id, teamId, off)
        val m = _state.value.counterOffsets.toMutableMap()
        m[teamId] = off
        _state.value = _state.value.copy(counterOffsets = m)
    }
}
