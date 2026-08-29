package com.fuad.screendrawer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var drawingView: DrawingView
    private lateinit var controlPanel: LinearLayout
    private lateinit var drawParams: WindowManager.LayoutParams
    private lateinit var controlParams: WindowManager.LayoutParams
    private var drawModeOn = true

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        drawParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        drawParams.gravity = Gravity.TOP or Gravity.START

        drawingView = DrawingView(this)
        windowManager.addView(drawingView, drawParams)

        controlParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        controlParams.gravity = Gravity.TOP or Gravity.START
        controlParams.x = 20
        controlParams.y = 150

        controlPanel = buildControlPanel()
        windowManager.addView(controlPanel, controlParams)

        setDrawMode(true)
    }

    private fun buildControlPanel(): LinearLayout {
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.HORIZONTAL
        panel.setBackgroundColor(Color.parseColor("#CC222222"))
        panel.setPadding(16, 16, 16, 16)

        val toggleBtn = Button(this).apply {
            text = "Pen"
            setOnClickListener {
                drawModeOn = !drawModeOn
                setDrawMode(drawModeOn)
                text = if (drawModeOn) "Pen" else "Move"
            }
        }
        val colorBtn = Button(this).apply {
            text = "Color"
            setOnClickListener { drawingView.nextColor() }
        }
        val undoBtn = Button(this).apply {
            text = "Undo"
            setOnClickListener { drawingView.undo() }
        }
        val clearBtn = Button(this).apply {
            text = "Clear"
            setOnClickListener { drawingView.clearAll() }
        }
        val exitBtn = Button(this).apply {
            text = "Exit"
            setOnClickListener { stopSelf() }
        }

        panel.addView(toggleBtn)
        panel.addView(colorBtn)
        panel.addView(undoBtn)
        panel.addView(clearBtn)
        panel.addView(exitBtn)

        // Let the user drag the toolbar anywhere on screen.
        panel.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = controlParams.x
                        initialY = controlParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        controlParams.x = initialX + (event.rawX - touchX).toInt()
                        controlParams.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(controlPanel, controlParams)
                        return false
                    }
                }
                return false
            }
        })

        return panel
    }

    private fun setDrawMode(on: Boolean) {
        drawParams.flags = if (on) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        windowManager.updateViewLayout(drawingView, drawParams)
    }

    private fun startForegroundNotification() {
        val channelId = "screen_drawer_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Screen Drawer", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Screen Drawer is running")
            .setContentText("Use the floating toolbar to draw, undo, clear, or exit.")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(drawingView)
        windowManager.removeView(controlPanel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
