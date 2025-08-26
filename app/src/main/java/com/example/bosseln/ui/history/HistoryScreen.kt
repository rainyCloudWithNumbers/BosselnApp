package com.example.bosseln.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bosseln.data.db.Match
import com.example.bosseln.data.db.MatchResult
import com.example.bosseln.vm.GameViewModel
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.views.MapView
import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import java.text.DateFormat
import java.util.*


private fun Dp.toPx(context: android.content.Context): Float =
    this.value * context.resources.displayMetrics.density
@Composable
fun MiniMap(matchId: Long, vm: GameViewModel) {
    val state by vm.state.collectAsState()

    val throws by produceState(
        initialValue = emptyList<com.example.bosseln.data.db.ThrowEvent>(),
        key1 = matchId
    ) {
        value = vm.getThrowsForMatch(matchId)
    }

    // Team-Farben (Long ARGB -> Int)
    val teamColor: Map<Long, Int> = remember(state.teams) {
        state.teams.associate { it.id to it.colorArgb.toInt() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clipToBounds()
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            factory = { ctx ->
                MapView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (150.dp.toPx(ctx)).toInt()
                    )
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    controller.setZoom(15.0)
                    setMultiTouchControls(false)
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, _ -> false }
                    isTilesScaledToDpi = true
                }
            },
            update = { map ->
                map.overlays.clear()

                // Punkte je Team sammeln (nur gültige Koordinaten)
                val pointsByTeam: Map<Long, List<GeoPoint>> =
                    throws.groupBy { it.teamId }
                        .mapValues { (_, list) ->
                            list
                                .sortedBy { it.sequence } // Route in Wurfreihenfolge
                                .filter { it.lat != null && it.lon != null }
                                .map { GeoPoint(it.lat!!, it.lon!!) }
                        }
                        .filterValues { it.isNotEmpty() }

                // Alle Punkte für Bounding Box
                val allPoints = pointsByTeam.values.flatten()

                // Eine Polyline je Team, mit Team-Farbe
                pointsByTeam.forEach { (teamId, pts) ->
                    val line = Polyline().apply {
                        setPoints(pts)
                        setWidth(6f)
                        // Farbe wählen (fallback, falls Team nicht gefunden)
                        val c = teamColor[teamId] ?: Color.BLUE
                        outlinePaint.color = c
                        outlinePaint.isAntiAlias = true
                    }
                    map.overlays.add(line)
                }

                // auf die gesamte Strecke zoomen
                if (allPoints.isNotEmpty()) {
                    val bb: BoundingBox = BoundingBox.fromGeoPoints(allPoints)
                    map.zoomToBoundingBox(bb, true, 48) // padding = 48px
                }

                map.invalidate()
            }
        )
    }
}


@Composable
fun HistoryScreen(vm: GameViewModel) {
    val state by vm.state.collectAsState()

    val matches by produceState(
    initialValue = emptyList<Match>(),
    key1 = state.historyVersion  // <-- statt state.currentMatch
) {
    value = vm.getAllMatches()
}

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val finished = matches.filter { it.endedAt != null }
        items(finished.size) { i ->
            val m = finished[i]
            var expanded by remember { mutableStateOf(false) }
            var askDelete by remember { mutableStateOf(false) }

            val results by produceState(initialValue = emptyList<MatchResult>(), key1 = m.id) {
                value = vm.getMatchResults(m.id)
            }
            val winnerTeamId = results.minByOrNull { it.throwsCount }?.teamId

            Card(
                colors = CardDefaults.cardColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                    .format(Date(m.endedAt!!)),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "Details zuklappen" else "Details anzeigen")
                            }
                            OutlinedButton(
                                onClick = { askDelete = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) { Text("Löschen") }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    // Team-Zeilen mit Gewinner-Highlight
                    val nameById = state.teams.associateBy({ it.id }, { it.name })
                    results.forEach { r ->
                        val isWinner = r.teamId == winnerTeamId
                        val line = "• ${nameById[r.teamId] ?: "Team ${r.teamId}"} – " +
                                "Würfe: ${r.throwsCount}, Strecke: ${"%.1f".format(r.totalDistanceM)} m"
                        val bg = if (isWinner) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        val fg = if (isWinner) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(color = bg, tonalElevation = if (isWinner) 2.dp else 0.dp, shape = MaterialTheme.shapes.small) {
                            Text(line, color = fg, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                    }



                    if (expanded) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        // Detail: Spieler + durchschnittliche Distanz
                        val teamIds = results.map { it.teamId }
                        teamIds.forEach { teamId ->
                            val teamName = nameById[teamId] ?: "Team $teamId"
                            Text(teamName, style = MaterialTheme.typography.titleSmall)
                            val stats by produceState(initialValue = emptyList<Triple<Long, String, com.example.bosseln.data.db.PlayerThrowStat>>(), key1 = m.id, key2 = teamId) {
                                value = vm.playerStatsForTeam(m.id, teamId)
                            }
                            if (stats.isEmpty()) {
                                Text("– keine Spieler-Daten –", style = MaterialTheme.typography.bodySmall)
                            } else {
                                stats.forEach { (_, pname, s) ->
                                    val avg = s.avgDistance?.let { "${"%.1f".format(it)} m" } ?: "—"
                                    Text("   · $pname – Würfe: ${s.throws}, Ø: $avg")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }

                        MiniMap(m.id, vm)
                    }
                }
            }

            if (askDelete) {
                AlertDialog(
                    onDismissRequest = { askDelete = false },
                    title = { Text("Match löschen?") },
                    text = { Text("Dieser Eintrag wird dauerhaft gelöscht (inkl. Würfe/Ergebnisse). Fortfahren?") },
                    confirmButton = {
                        TextButton(onClick = {
                            askDelete = false
                            vm.deleteMatch(m.id)
                        }) { Text("Ja, löschen", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { askDelete = false }) { Text("Abbrechen") }
                    }
                )
            }
        }
    }
}
