package com.fuad.screendrawer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 120, 60, 60)
        }

        val info = TextView(this).apply {
            text = "Screen Drawer\n\n" +
                "1) Grant the overlay (draw over other apps) permission.\n" +
                "2) Tap Start. A small floating toolbar appears.\n" +
                "3) Draw anywhere on screen with your finger.\n" +
                "4) Use the toolbar to change color, undo, clear, toggle " +
                "draw/move mode, or exit."
            textSize = 16f
            setPadding(0, 0, 0, 48)
        }

        val grantButton = Button(this).apply {
            text = "1. Grant overlay permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
        }

        val startButton = Button(this).apply {
            text = "2. Start drawing overlay"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    val serviceIntent = Intent(this@MainActivity, OverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    moveTaskToBack(true)
                } else {
                    Settings.canDrawOverlays(this@MainActivity)
                }
            }
        }

        val stopButton = Button(this).apply {
            text = "Stop overlay"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
            }
        }

        root.addView(info)
        root.addView(grantButton)
        root.addView(startButton)
        root.addView(stopButton)
        setContentView(root)
    }
}
