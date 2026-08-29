package com.fuad.screendrawer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var statusDot: ImageView
    private lateinit var statusText: TextView
    private lateinit var btnStart: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }

        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        btnStart = findViewById(R.id.btnStart)
        val btnGrant = findViewById<MaterialButton>(R.id.btnGrant)
        val btnStop = findViewById<MaterialButton>(R.id.btnStop)

        btnGrant.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        btnStart.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                val serviceIntent = Intent(this, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                moveTaskToBack(true)
            }
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun refreshPermissionStatus() {
        val granted = Settings.canDrawOverlays(this)

        statusDot.setColorFilter(
            if (granted) Color.parseColor("#34C759") else Color.parseColor("#FF9500")
        )
        statusText.text = if (granted)
            "Overlay permission granted — you're ready to draw"
        else
            "Overlay permission needed before you can start"

        btnStart.isEnabled = granted
        btnStart.alpha = if (granted) 1f else 0.5f
    }
}
