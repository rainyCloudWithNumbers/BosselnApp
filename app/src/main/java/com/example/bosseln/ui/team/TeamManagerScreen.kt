package com.example.bosseln.ui.team

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bosseln.data.db.Player
import com.example.bosseln.data.db.Team
import com.example.bosseln.vm.GameViewModel

@Composable
fun TeamManagerScreen(vm: GameViewModel, onContinue: () -> Unit) {
    val state by vm.state.collectAsState()
    var newTeam by remember { mutableStateOf("") }
    var newPlayer by remember { mutableStateOf("") }
    var selectedTeamForPlayer by remember { mutableStateOf<Team?>(null) }

    Column(Modifier.padding(16.dp)) {
        Text("Teams verwalten", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newTeam, onValueChange = { newTeam = it },
                label = { Text("Neues Team") }, modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newTeam.isNotBlank()) {
                        vm.createTeam(newTeam.trim())
                        newTeam = ""
                    }
                }
            ) { Text("Hinzufügen") }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.weight(1f, true)) {
            items(state.teams.size) { i ->
                val t = state.teams[i]
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { vm.deleteTeam(t) }) { Text("Löschen") }
                        }

                        // Spieler
                        val players = state.players[t.id] ?: emptyList()
                        if (players.isEmpty()) {
                            Text("Keine Spieler (optional).", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Spieler (Reihenfolge = Wurf-Reihenfolge):")
                            players.forEachIndexed { idx, p ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${idx+1}. ${p.name}")
                                    TextButton(onClick = { vm.removePlayer(p) }) { Text("Entfernen") }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = if (selectedTeamForPlayer?.id == t.id) newPlayer else "",
                                onValueChange = { value ->
                                    selectedTeamForPlayer = t
                                    newPlayer = value
                                },
                                label = { Text("Spielername") },
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                if (selectedTeamForPlayer?.id == t.id && newPlayer.isNotBlank()) {
                                    vm.addPlayer(t.id, newPlayer.trim())
                                    newPlayer = ""
                                }
                            }) { Text("Hinzufügen") }
                        }
                    }
                }

                // ensure players for each team are loaded
                LaunchedEffect(t.id) { vm.loadPlayers(t.id) }
            }
        }

        Button(
            onClick = onContinue,
            enabled = state.teams.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Weiter") }
    }
}
