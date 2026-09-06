package com.musium.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var bound = false
    private var askedNotifications = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        PlayLog.d("notification permission granted=$granted")
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicService.MusicBinder ?: return
            playerConnection?.release()
            playerConnection = PlayerConnection(binder.service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerConnection?.release()
            playerConnection = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bindPlayer()
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPlayerConnection provides playerConnection) {
                    MusiumApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!bound) bindPlayer()
    }

    override fun onResume() {
        super.onResume()
        askNotificationPermission()
    }

    override fun onDestroy() {
        playerConnection?.release()
        playerConnection = null
        if (bound) {
            runCatching { unbindService(connection) }
            bound = false
        }
        super.onDestroy()
    }

    private fun bindPlayer() {
        bound = bindService(Intent(this, MusicService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || askedNotifications) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        askedNotifications = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
