package com.example.bosseln.ui.play

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bosseln.data.db.Team
import com.example.bosseln.vm.GameViewModel

@Composable
fun PlayScreen(vm: GameViewModel) {
    val state by vm.state.collectAsState()
    val match = state.currentMatch
    val selectedTeams = remember(state.selectedTeams, state.teams) {
        state.teams.filter { state.selectedTeams.contains(it.id) }
    }

    Column(Modifier.padding(12.dp).fillMaxSize()) {
        Text(match?.title ?: "Match", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        if (selectedTeams.isEmpty()) {
            Text("Keine Teams ausgewählt.")
            return
        }

        val twoCols = selectedTeams.size == 2

        if (twoCols) {
            // nebeneinander
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                selectedTeams.forEach { team ->
                    TeamSegment(vm, team, modifier = Modifier.weight(1f))
                }
            }
        } else {
            // untereinander
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(selectedTeams.size) { i ->
                    TeamSegment(vm, selectedTeams[i], modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TeamSegment(vm: GameViewModel, team: Team, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    val throws = state.throws.filter { it.teamId == team.id }
    val count = throws.count { it.kind == "ABWURF" } // Zähler = Anzahl Abwürfe
    val lastDist = throws.lastOrNull { it.distanceSinceLastM != null }?.distanceSinceLastM
    val players = state.players[team.id].orEmpty()

    // Nächster Index: ABWURF-Zahl modulo Spieleranzahl (falls >0)
    val nextIdx = remember(players.size, count) {
        if (players.isNotEmpty()) count % players.size else -1
    }

    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(team.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))

            // Große Zählanzeige
            Box(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            if (lastDist != null) {
                Text("Letzte Distanz: ${"%.1f".format(lastDist)} m")
                Spacer(Modifier.height(4.dp))
            }

            Button(
                onClick = { vm.recordAbwurf(team.id) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Abwurf") }

            Spacer(Modifier.height(10.dp))
            Text("Wer ist dran:", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))

            if (players.isEmpty()) {
                Text("— (keine Spieler eingetragen)")
            } else {
                // Liste mit optischem Highlight für den nächsten
                players.forEachIndexed { idx, p ->
                    val isNext = idx == nextIdx
                    val container = if (isNext) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                    val content = if (isNext) MaterialTheme.colorScheme.onSecondaryContainer
                                  else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        tonalElevation = if (isNext) 4.dp else 0.dp,
                        color = container,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isNext) {
                                Text("▶", color = content, modifier = Modifier.padding(end = 8.dp))
                            } else {
                                Text("  ", modifier = Modifier.padding(end = 8.dp)) // Platzhalter
                            }
                            Text(
                                text = "${idx + 1}. ${p.name}",
                                color = content,
                                fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Spieler-Liste einmalig laden
            LaunchedEffect(team.id) { vm.loadPlayers(team.id) }
        }
    }
}
