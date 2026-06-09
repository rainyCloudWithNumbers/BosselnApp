package com.example.bosseln.ui.team

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.example.bosseln.data.db.Player
import com.example.bosseln.data.db.Team
import com.example.bosseln.vm.GameViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamManagerScreen(vm: GameViewModel, onContinue: () -> Unit) {
    val state by vm.state.collectAsState()
    var newTeam by remember { mutableStateOf("") }
    var newPlayer by remember { mutableStateOf("") }
    var selectedTeamForPlayer by remember { mutableStateOf<Team?>(null) }

    val teamColors = remember {
        listOf(
            "Blau" to 0xFF2196F3,
            "Rot" to 0xFFF44336,
            "Gelb" to 0xFFFFEB3B,
            "Pink" to 0xFFE91E63,
            "Grün" to 0xFF4CAF50
        )
    }
    var selectedColor by remember { mutableStateOf(teamColors[0]) }
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.padding(16.dp)) {
        Text("Teams verwalten", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = newTeam, onValueChange = { newTeam = it },
            label = { Text("Neues Team Name") }, modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = selectedColor.first,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Farbe") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Box(
                            Modifier
                                .size(24.dp)
                                .background(Color(selectedColor.second.toInt()), RoundedCornerShape(4.dp))
                        )
                    }
                )
                // Overlay to catch clicks
                Box(Modifier.matchParentSize().clickable { expanded = true })

                DropdownMenu(
                    expanded = expanded, 
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.5f)
                ) {
                    teamColors.forEach { colorPair ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(16.dp)
                                            .background(Color(colorPair.second.toInt()), RoundedCornerShape(2.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(colorPair.first)
                                }
                            },
                            onClick = {
                                selectedColor = colorPair
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                modifier = Modifier.height(56.dp),
                onClick = {
                    if (newTeam.isNotBlank()) {
                        vm.createTeam(newTeam.trim(), selectedColor.second)
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .background(Color(t.colorArgb.toInt()))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(t.name, style = MaterialTheme.typography.titleMedium)
                            }
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
