package com.example.bosseln.vm

import android.app.Application
import android.content.ContentValues
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bosseln.data.GameRepository
import com.example.bosseln.data.db.*
import com.example.bosseln.domain.LocationHelper
import com.example.bosseln.domain.haversineMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = GameRepository(db.teamDao(), db.matchDao(), db.throwDao(), db.playerDao())
    private val locator = LocationHelper(app)

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val teams = repo.teams()
            _state.value = _state.value.copy(teams = teams)
            if (teams.isEmpty()) {
                // leeres System: Hinweis-UI handled im TeamManager
            }
        }
    }

    // ========== Team Management ==========
    fun refreshTeams() = viewModelScope.launch(Dispatchers.IO) {
        _state.value = _state.value.copy(teams = repo.teams())
    }

    fun createTeam(name: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.upsertTeam(name)
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

    fun addPlayer(teamId: Long, name: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.addPlayer(teamId, name)
        loadPlayers(teamId)
    }

    fun removePlayer(player: Player) = viewModelScope.launch(Dispatchers.IO) {
        repo.removePlayer(player)
        loadPlayers(player.teamId)
    }

    // ========== Match Setup ==========
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
        val match = repo.createMatch(_state.value.matchTitle.ifBlank { "Bosseln" })
        _state.value = _state.value.copy(currentMatch = match, throws = emptyList())
        selected.forEach { loadPlayers(it) }
        refreshThrows()
    }

    // ========== Zählen / Abwurf ==========
    fun recordAbwurf(teamId: Long) = viewModelScope.launch(Dispatchers.IO) {
        val match = _state.value.currentMatch ?: return@launch
        val seq = repo.nextSeq(match.id)

        // Standort versuchen (kann null sein)
        val loc = locator.getAccurateOnce()

        // Letzten ABWURF für dieses Team im aktuellen Match suchen
        val all = repo.getThrows(match.id)
        val lastTeamAbwurf = all.lastOrNull { it.teamId == teamId && it.kind == "ABWURF" }

        val dist = if (
            loc != null &&
            lastTeamAbwurf?.lat != null && lastTeamAbwurf.lon != null
        ) {
            haversineMeters(
                lastTeamAbwurf.lat,
                lastTeamAbwurf.lon,
                loc.lat,
                loc.lon
            )
        } else null

        // Event immer speichern – auch ohne Standort
        repo.addEvent(
            ThrowEvent(
                matchId = match.id,
                teamId = teamId,
                sequence = seq,
                kind = "ABWURF",
                lat = loc?.lat,
                lon = loc?.lon,
                accuracyM = loc?.accuracyM,
                distanceSinceLastM = dist
            )
        )

        // KEINE Rotation mehr, nur Refresh
        refreshThrows()
    }

    private fun refreshThrows() = viewModelScope.launch(Dispatchers.IO) {
        val match = _state.value.currentMatch ?: return@launch
        _state.value = _state.value.copy(throws = repo.getThrows(match.id))
    }

    // Foto-URI bleibt, falls später wieder Fotos rein sollen
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

    data class UiState(
        val teams: List<Team> = emptyList(),
        val players: Map<Long, List<Player>> = emptyMap(), // teamId -> Spieler
        val selectedTeams: Set<Long> = emptySet(),
        val matchTitle: String = "",
        val currentMatch: Match? = null,
        val throws: List<ThrowEvent> = emptyList()
    )
}