package com.example.bosseln.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.bosseln.data.db.Team
import com.example.bosseln.vm.GameViewModel

@Composable
fun MatchSetupScreen(vm: GameViewModel, onStart: () -> Unit) {
    val state by vm.state.collectAsState()
    var title by remember { mutableStateOf(state.matchTitle) }

    Column(Modifier.padding(16.dp)) {
        Text("Match Setup", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = title,
            onValueChange = { value -> title = value; vm.setTitle(value) },
            label = { Text("Titel") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        Text("Teams wählen (mindestens 1)", modifier = Modifier.padding(top = 12.dp))

        LazyColumn(Modifier.weight(1f, fill = true)) {
            items(state.teams.size) { i ->
                val t: Team = state.teams[i]
                val checked = state.selectedTeams.contains(t.id)
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .background(Color(t.colorArgb.toInt()), RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(t.name)
                        }
                    },
                    trailingContent = {
                        Checkbox(checked = checked, onCheckedChange = { vm.toggleTeamSelection(t) })
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggleTeamSelection(t) }
                        .padding(vertical = 2.dp)
                )
                HorizontalDivider()
            }
        }

        Button(
            onClick = { vm.startMatch(); onStart() },
            enabled = state.selectedTeams.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Match starten") }
    }
}
