package com.example.bosseln

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.bosseln.nav.AppNav
import com.example.bosseln.vm.GameViewModel

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Laufzeitberechtigungen (Location + Kamera)
        val permLauncher = registerForActivityResult(RequestMultiplePermissions()) { /* ignore */ }
        permLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        ))

        setContent {
            MaterialTheme {
                Surface { AppNav(vm) }
            }
        }
    }
}
