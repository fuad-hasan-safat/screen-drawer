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
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar

/**
 * Hosts three overlay windows on top of whatever app the user is in:
 *  1. [drawingView]  - full-screen transparent canvas that captures the pen strokes
 *  2. [toolbarView]  - small draggable pill: pen/move toggle, color, undo, clear, exit
 *  3. [styleView]    - hidden by default; opens when the color swatch is tapped,
 *                      holds the HSV color wheel, brightness slider, quick presets
 *                      and brush size slider
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    private lateinit var drawingView: DrawingView
    private lateinit var drawParams: WindowManager.LayoutParams

    private lateinit var toolbarView: View
    private lateinit var toolbarParams: WindowManager.LayoutParams
    private lateinit var colorSwatch: ColorSwatchView
    private lateinit var btnToggleMode: ImageButton

    private lateinit var styleView: View
    private lateinit var styleParams: WindowManager.LayoutParams
    private var stylePanelVisible = false

    private var drawModeOn = true

    private val presetColors = intArrayOf(
        Color.parseColor("#FF3B30"), Color.parseColor("#FF9500"),
        Color.parseColor("#FFCC00"), Color.parseColor("#34C759"),
        Color.parseColor("#00C7BE"), Color.parseColor("#32ADE6"),
        Color.parseColor("#007AFF"), Color.parseColor("#AF52DE"),
        Color.parseColor("#FF2D55"), Color.parseColor("#FFFFFF"),
        Color.parseColor("#8E8E93"), Color.parseColor("#000000")
    )

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        setupDrawingLayer()
        setupToolbar()
        setupStylePanel()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ---------------------------------------------------------------------
    // Layer 1: the drawing canvas
    // ---------------------------------------------------------------------

    private fun setupDrawingLayer() {
        drawParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        drawingView = DrawingView(this)
        windowManager.addView(drawingView, drawParams)
    }

    // ---------------------------------------------------------------------
    // Layer 2: the floating toolbar
    // ---------------------------------------------------------------------

    private fun setupToolbar() {
        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 160
        }

        toolbarView = LayoutInflater.from(this).inflate(R.layout.overlay_toolbar, null)
        windowManager.addView(toolbarView, toolbarParams)

        val dragHandle = toolbarView.findViewById<View>(R.id.dragHandle)
        btnToggleMode = toolbarView.findViewById(R.id.btnToggleMode)
        colorSwatch = toolbarView.findViewById(R.id.colorSwatch)
        val btnUndo = toolbarView.findViewById<ImageButton>(R.id.btnUndo)
        val btnClear = toolbarView.findViewById<ImageButton>(R.id.btnClear)
        val btnExit = toolbarView.findViewById<ImageButton>(R.id.btnExit)

        colorSwatch.setColorValue(drawingView.getColor())

        btnToggleMode.setOnClickListener {
            drawModeOn = !drawModeOn
            applyDrawMode()
        }
        colorSwatch.setOnClickListener { toggleStylePanel() }
        btnUndo.setOnClickListener { drawingView.undo() }
        btnClear.setOnClickListener { drawingView.clearAll() }
        btnExit.setOnClickListener { stopSelf() }

        // Only the drag handle moves the toolbar, so the icon buttons stay
        // perfectly clickable (no gesture conflict between drag and tap).
        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = toolbarParams.x
                        startY = toolbarParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        toolbarParams.x = startX + (event.rawX - touchX).toInt()
                        toolbarParams.y = startY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(toolbarView, toolbarParams)
                        return true
                    }
                }
                return false
            }
        })

        applyDrawMode()
    }

    private fun applyDrawMode() {
        drawParams.flags = if (drawModeOn) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        windowManager.updateViewLayout(drawingView, drawParams)

        btnToggleMode.setImageResource(if (drawModeOn) R.drawable.ic_pen else R.drawable.ic_pan)
        btnToggleMode.setColorFilter(
            if (drawModeOn) Color.parseColor("#6C5CE7") else Color.parseColor("#9AA0B4")
        )
    }

    // ---------------------------------------------------------------------
    // Layer 3: the style panel (color wheel + brightness + presets + brush size)
    // ---------------------------------------------------------------------

    private fun setupStylePanel() {
        styleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        styleView = LayoutInflater.from(this).inflate(R.layout.panel_style, null)
        styleView.visibility = View.GONE
        windowManager.addView(styleView, styleParams)

        val wheel = styleView.findViewById<ColorWheelView>(R.id.colorWheel)
        val brightnessSeek = styleView.findViewById<SeekBar>(R.id.brightnessSeek)
        val strokeSeek = styleView.findViewById<SeekBar>(R.id.strokeSeek)
        val presetRow = styleView.findViewById<LinearLayout>(R.id.presetRow)
        val btnPanelClose = styleView.findViewById<ImageButton>(R.id.btnPanelClose)

        wheel.setColorExternally(drawingView.getColor())
        strokeSeek.progress = drawingView.getStrokeWidth().toInt()

        wheel.onColorChanged = { color ->
            drawingView.setColor(color)
            colorSwatch.setColorValue(color)
        }

        brightnessSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                wheel.value = progress / 100f
                wheel.invalidate()
                if (fromUser) {
                    val color = wheel.currentColor()
                    drawingView.setColor(color)
                    colorSwatch.setColorValue(color)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        strokeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                drawingView.setStrokeWidth(progress.coerceAtLeast(4).toFloat())
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val density = resources.displayMetrics.density
        val swatchSize = (28 * density).toInt()
        val swatchMargin = (6 * density).toInt()

        for (c in presetColors) {
            val swatch = ColorSwatchView(this)
            swatch.layoutParams = LinearLayout.LayoutParams(swatchSize, swatchSize).apply {
                marginStart = swatchMargin
                marginEnd = swatchMargin
            }
            swatch.setColorValue(c)
            swatch.setOnClickListener {
                drawingView.setColor(c)
                colorSwatch.setColorValue(c)
                wheel.setColorExternally(c)
                brightnessSeek.progress = (wheel.value * 100).toInt()
            }
            presetRow.addView(swatch)
        }

        btnPanelClose.setOnClickListener { toggleStylePanel() }
    }

    private fun toggleStylePanel() {
        stylePanelVisible = !stylePanelVisible
        styleView.visibility = if (stylePanelVisible) View.VISIBLE else View.GONE

        styleParams.flags = if (stylePanelVisible) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        styleParams.dimAmount = 0.45f
        windowManager.updateViewLayout(styleView, styleParams)
    }

    // ---------------------------------------------------------------------
    // Foreground service plumbing
    // ---------------------------------------------------------------------

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
            .setContentText("Tap the floating toolbar to draw, undo, clear, or exit.")
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
        windowManager.removeView(toolbarView)
        windowManager.removeView(styleView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
