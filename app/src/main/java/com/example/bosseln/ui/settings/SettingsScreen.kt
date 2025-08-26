package com.example.bosseln.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
//import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bosseln.vm.GameViewModel

@Composable
fun SettingsScreen(vm: GameViewModel) {
    val state by vm.state.collectAsState()
    val match = state.currentMatch

    Column(Modifier.padding(16.dp)) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        if (match == null || state.selectedTeams.isEmpty()) {
            Text("Kein laufendes Match oder keine Teams ausgewählt.")
            return
        }

        val teams = state.teams.filter { state.selectedTeams.contains(it.id) }
        teams.forEach { team ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(team.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    var input by remember(team.id) { mutableStateOf("") }
                    val current by produceState(initialValue = 0, key1 = state.throws, key2 = state.counterOffsets) {
                        value = vm.getDisplayCount(team.id)
                    }

                    Text("Aktueller Zähler: $current")
                    Spacer(Modifier.height(8.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Zähler setzen auf") },
                           // keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            val v = input.toIntOrNull()
                            if (v != null) vm.setCounter(team.id, v)
                            input = ""
                        }) { Text("Setzen") }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.resetCounter(team.id) }

                    ) { Text("Zähler auf 0 zurücksetzen") }

                }
            }
        }
    }
}
