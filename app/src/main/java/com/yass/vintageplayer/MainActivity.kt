package com.yass.vintageplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.yass.vintageplayer.ui.MainViewModel
import com.yass.vintageplayer.ui.VintageAppUi
import com.yass.vintageplayer.ui.screens.PermissionGate
import com.yass.vintageplayer.ui.theme.LocalVintage
import com.yass.vintageplayer.ui.theme.VintageTheme
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    /** False when audio permission is already granted at launch: no gate flash. */
    private var gateNeeded by mutableStateOf(false)

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

    // POST_NOTIFICATIONS is non-blocking: result is ignored.
    private val notificationsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val audioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                onAudioGranted()
            }
        }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun onAudioGranted() {
        gateNeeded = false
        lifecycleScope.launch { AppGraph.library.start() }
        requestNotifications()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gateNeeded = !hasAudioPermission()
        if (!gateNeeded) {
            lifecycleScope.launch { AppGraph.library.start() }
            requestNotifications()
        }
        // Restore the last track once the library first emits a non-empty list.
        lifecycleScope.launch {
            val tracks = AppGraph.library.tracks.filter { it.isNotEmpty() }.first()
            AppGraph.connection.restore(tracks)
        }
        enableEdgeToEdge()
        setContent {
            val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
            VintageTheme(dark = settings.dark, accent = androidx.compose.ui.graphics.Color(settings.accent)) {
                val tokens = LocalVintage.current
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !settings.dark
                }
                if (gateNeeded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(tokens.chassisBrush)
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        PermissionGate(onGrant = { audioLauncher.launch(audioPermission) })
                    }
                } else {
                    VintageAppUi(vm)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppGraph.player.connect()
    }
}
