package com.example.bosseln.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    LazyColumn(
        Modifier.padding(12.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(match?.title ?: "Match", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
        }

        if (selectedTeams.isEmpty()) {
            item {
                Text("Keine Teams ausgewählt.")
            }
        } else {
            val twoCols = selectedTeams.size == 2

            if (twoCols) {
                // nebeneinander - als EIN Item in der LazyColumn
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        selectedTeams.forEach { team ->
                            TeamSegment(vm, team, modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // untereinander - jedes Team ein Item
                items(selectedTeams.size) { i ->
                    TeamSegment(vm, selectedTeams[i], modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))

            var showConfirm by remember { mutableStateOf(false) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = { showConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors()
                ) { Text("Spielrunde beenden") }
            }

            if (showConfirm) {
                AlertDialog(
                    onDismissRequest = { showConfirm = false },
                    title = { Text("Runde beenden?") },
                    text = { Text("Die aktuelle Spielrunde wird abgeschlossen und in der Historie gespeichert. Fortfahren?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showConfirm = false
                            vm.finishMatch()
                        }) { Text("Ja, beenden") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirm = false }) { Text("Abbrechen") }
                    }
                )
            }
        }
    }
}

@Composable
private fun TeamSegment(vm: GameViewModel, team: Team, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    val throws = state.throws.filter { it.teamId == team.id }
    val count by produceState(initialValue = 0, key1 = state.throws, key2 = state.counterOffsets) {
        value = vm.getDisplayCount(team.id)
    }
    val lastDist = throws.lastOrNull { it.distanceSinceLastM != null }?.distanceSinceLastM
    val players = state.players[team.id].orEmpty()

    val teamColor = Color(team.colorArgb.toInt())

    // Nächster Index: ABWURF-Zahl modulo Spieleranzahl (falls >0)
    val nextIdx = remember(players.size, count) {
        if (players.isNotEmpty()) count % players.size else -1
    }

    val isRefining = state.refiningTeams.contains(team.id)

    Card(
        modifier = modifier.border(2.dp, teamColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = teamColor.copy(alpha = 0.1f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).background(teamColor, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(8.dp))
                Text(team.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (isRefining) {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = teamColor
                    )
                }
            }
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
                    color = teamColor,
                    letterSpacing = 2.sp
                )
            }

            if (lastDist != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Letzte Distanz: ${"%.1f".format(lastDist)} m")
                    if (isRefining) {
                        Text(" (wird verfeinert...)", style = MaterialTheme.typography.bodySmall, color = teamColor)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Button(
                onClick = { vm.recordAbwurf(team.id) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRefining,
                colors = ButtonDefaults.buttonColors(containerColor = teamColor)
            ) {
                if (isRefining) {
                    Text("Suche GPS...")
                } else {
                    Text("Abwurf")
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Wer ist dran:", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))

            if (players.isEmpty()) {
                Text("— (keine Spieler eingetragen)")
            } else {
                // Liste mit optischem Highlight für den nächsten
                players.forEachIndexed { idx, p ->
                    val isNext = idx == nextIdx
                    val container = if (isNext) teamColor.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                    val content = if (isNext) MaterialTheme.colorScheme.onSurface
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
                                Text("▶", color = teamColor, modifier = Modifier.padding(end = 8.dp))
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
