package com.example.bosseln.ui.map

import android.preference.PreferenceManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.bosseln.vm.GameViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(vm: GameViewModel) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    if (state.currentMatch == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Kein aktives Match", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    // OSMdroid einmalig konfigurieren
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = "com.example.bosseln"
    }

    val throws = state.throws.filter { it.lat != null && it.lon != null }
    val last = throws.lastOrNull()

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                controller.setZoom(16.0)
                if (last != null) {
                    controller.setCenter(GeoPoint(last.lat!!, last.lon!!))
                }
            }
        },
        update = { map ->
            // Overlay-Punkte neu aufbauen
            val folder = FolderOverlay()

            throws.forEach { ev ->
                val team = state.teams.firstOrNull { it.id == ev.teamId }
                val teamColorLong = team?.colorArgb ?: 0xFF2196F3
                val teamColorInt = teamColorLong.toInt()
                val teamName = team?.name ?: "Unbekannt"

                val marker = Marker(map).apply {
                    position = GeoPoint(ev.lat!!, ev.lon!!)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = if (ev.kind == "START") "$teamName (Start)" else "$teamName  #${ev.sequence}"
                    // Standard-Icon laden und einfärben (tinten)
                    val base = ContextCompat.getDrawable(ctx, org.osmdroid.library.R.drawable.marker_default)
                    if (base != null) {
                        val wrapped = DrawableCompat.wrap(base.mutate())
                        DrawableCompat.setTint(wrapped, teamColorInt)
                        icon = wrapped
                    }
                }
                folder.add(marker)
            }

            // Alte Overlays raus, neues rein
            map.overlays.clear()
            map.overlays.add(folder)

            // Kamera auf letzten Punkt (falls vorhanden)
            if (last != null) {
                map.controller.setCenter(GeoPoint(last.lat!!, last.lon!!))
            }

            map.invalidate()
        }
    )
}
