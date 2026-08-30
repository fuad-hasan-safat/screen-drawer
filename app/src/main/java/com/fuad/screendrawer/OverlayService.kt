package com.fuad.screendrawer

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar

/**
 * Hosts three overlay windows on top of whatever app the user is in:
 *  1. [drawingView]  - full-screen transparent canvas that captures the pen/eraser strokes
 *  2. [toolbarView]  - small draggable pill: pen/move toggle, eraser, color, undo, clear, exit
 *  3. [styleView]    - hidden by default; opens when the color swatch is tapped,
 *                      holds the HSV color wheel, brightness slider, quick presets,
 *                      a brush/eraser size slider, and the stylus-only toggle
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
    private lateinit var panelTitle: android.widget.TextView
    private lateinit var btnToolPen: ImageButton
    private lateinit var btnToolMarker: ImageButton
    private lateinit var btnToolEraser: ImageButton
    private lateinit var colorGroup: LinearLayout
    private lateinit var opacityGroup: LinearLayout
    private lateinit var colorWheel: ColorWheelView
    private lateinit var brightnessSeek: SeekBar
    private lateinit var strokeSeek: SeekBar
    private lateinit var opacitySeek: SeekBar
    private lateinit var btnStylusOnly: ImageButton
    private val presetViews = mutableListOf<ColorSwatchView>()
    private var stylePanelVisible = false

    private var drawModeOn = true
    private var penPulseAnimator: ObjectAnimator? = null

    private val accentColor = Color.parseColor("#6C5CE7")
    private val mutedColor = Color.parseColor("#9AA0B4")

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

    /** Shared visual language for every toggle-style icon button: tint + glow background. */
    private fun setToggleActive(button: ImageButton, active: Boolean) {
        button.setColorFilter(if (active) accentColor else mutedColor)
        button.setBackgroundResource(
            if (active) R.drawable.bg_icon_button_active else R.drawable.bg_icon_button
        )
    }

    /** Highlights exactly one of the three panel tool buttons - whichever is active. */
    private fun updateToolSelectorUi() {
        val tool = drawingView.getTool()
        setToggleActive(btnToolPen, tool == DrawingView.Tool.PEN)
        setToggleActive(btnToolMarker, tool == DrawingView.Tool.MARKER)
        setToggleActive(btnToolEraser, tool == DrawingView.Tool.ERASER)
    }

    /**
     * A tiny, cheap breathing-scale pulse (property animation only, no
     * redraw/relayout work) that draws attention to the pen icon while draw
     * mode is live - stopped immediately once draw mode turns off.
     */
    private fun startPenPulse(view: View) {
        if (penPulseAnimator?.isRunning == true) return
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f)
        penPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPenPulse(view: View) {
        penPulseAnimator?.cancel()
        penPulseAnimator = null
        view.scaleX = 1f
        view.scaleY = 1f
    }

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
        val btnRedo = toolbarView.findViewById<ImageButton>(R.id.btnRedo)
        val btnClear = toolbarView.findViewById<ImageButton>(R.id.btnClear)
        val btnExit = toolbarView.findViewById<ImageButton>(R.id.btnExit)

        colorSwatch.setColorValue(drawingView.getActiveColor())

        btnToggleMode.setOnClickListener {
            drawModeOn = !drawModeOn
            applyDrawMode()
        }
        colorSwatch.setOnClickListener { toggleStylePanel() }
        btnUndo.setOnClickListener { drawingView.undo() }
        btnRedo.setOnClickListener { drawingView.redo() }
        btnClear.setOnClickListener { drawingView.clearAll() }
        btnExit.setOnClickListener { stopSelf() }

        drawingView.onHistoryChanged = {
            btnUndo.alpha = if (drawingView.canUndo()) 1f else 0.4f
            btnRedo.alpha = if (drawingView.canRedo()) 1f else 0.4f
        }
        drawingView.onHistoryChanged?.invoke()

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
        setToggleActive(btnToggleMode, drawModeOn)

        if (drawModeOn) {
            startPenPulse(btnToggleMode)
        } else {
            stopPenPulse(btnToggleMode)
        }

        // The color swatch only matters while the overlay is actively
        // capturing touches, so dim it during pass-through to signal that.
        colorSwatch.alpha = if (drawModeOn) 1f else 0.4f
    }

    // ---------------------------------------------------------------------
    // Layer 3: the style panel (color wheel + brightness + presets + brush/eraser size + stylus toggle)
    // ---------------------------------------------------------------------

    private fun setupStylePanel() {
        // IMPORTANT: this window's width must be a fixed pixel value, not
        // WRAP_CONTENT. The panel's content uses match_parent widths (for
        // sliders, dividers, etc.), and on several OEM skins a WRAP_CONTENT
        // overlay window combined with match_parent descendants measures
        // those descendants against an undefined/near-zero width - which is
        // exactly the "squeezed into a thin strip" bug. Giving the window a
        // concrete width removes that ambiguity entirely.
        val panelWidthPx = (300 * resources.displayMetrics.density).toInt()
        styleParams = WindowManager.LayoutParams(
            panelWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        styleView = LayoutInflater.from(this).inflate(R.layout.panel_style, null)
        styleView.visibility = View.GONE
        windowManager.addView(styleView, styleParams)

        panelTitle = styleView.findViewById(R.id.panelTitle)
        btnToolPen = styleView.findViewById(R.id.btnToolPen)
        btnToolMarker = styleView.findViewById(R.id.btnToolMarker)
        btnToolEraser = styleView.findViewById(R.id.btnToolEraser)
        colorGroup = styleView.findViewById(R.id.colorGroup)
        opacityGroup = styleView.findViewById(R.id.opacityGroup)
        colorWheel = styleView.findViewById(R.id.colorWheel)
        brightnessSeek = styleView.findViewById(R.id.brightnessSeek)
        strokeSeek = styleView.findViewById(R.id.strokeSeek)
        opacitySeek = styleView.findViewById(R.id.opacitySeek)
        btnStylusOnly = styleView.findViewById(R.id.btnStylusOnly)
        val presetRow = styleView.findViewById<LinearLayout>(R.id.presetRow)
        val btnPanelClose = styleView.findViewById<ImageButton>(R.id.btnPanelClose)

        colorWheel.setColorExternally(drawingView.getActiveColor())

        colorWheel.onColorChanged = { color ->
            drawingView.setActiveColor(color)
            colorSwatch.setColorValue(color)
            refreshPresetSelection(color)
        }

        brightnessSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                colorWheel.value = progress / 100f
                colorWheel.invalidate()
                if (fromUser) {
                    val color = colorWheel.currentColor()
                    drawingView.setActiveColor(color)
                    colorSwatch.setColorValue(color)
                    refreshPresetSelection(color)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // The brush-size slider always controls whichever tool is currently selected.
        strokeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val width = progress.coerceAtLeast(4).toFloat()
                when (drawingView.getTool()) {
                    DrawingView.Tool.PEN -> drawingView.setStrokeWidth(width)
                    DrawingView.Tool.MARKER -> drawingView.setMarkerWidth(width)
                    DrawingView.Tool.ERASER -> drawingView.setEraserWidth(width)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Opacity only ever applies to the marker (see opacityGroup visibility).
        opacitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                drawingView.setMarkerOpacity(progress / 100f)
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
                drawingView.setActiveColor(c)
                colorSwatch.setColorValue(c)
                colorWheel.setColorExternally(c)
                brightnessSeek.progress = (colorWheel.value * 100).toInt()
                refreshPresetSelection(c)
            }
            presetRow.addView(swatch)
            presetViews.add(swatch)
        }
        refreshPresetSelection(drawingView.getActiveColor())

        setToggleActive(btnStylusOnly, drawingView.isStylusOnlyMode())
        btnStylusOnly.setOnClickListener {
            val turningOn = !drawingView.isStylusOnlyMode()
            drawingView.setStylusOnlyMode(turningOn)
            setToggleActive(btnStylusOnly, turningOn)
        }

        updateToolSelectorUi()
        val onToolPicked: (DrawingView.Tool) -> Unit = { tool ->
            drawingView.setTool(tool)
            if (!drawModeOn) {
                // Picking a tool from the panel means "I want to use this
                // now", so make sure the overlay is actively capturing touches.
                drawModeOn = true
                applyDrawMode()
            }
            colorSwatch.setColorValue(drawingView.getActiveColor())
            updateToolSelectorUi()
            refreshPanelForActiveTool()
        }
        btnToolPen.setOnClickListener { onToolPicked(DrawingView.Tool.PEN) }
        btnToolMarker.setOnClickListener { onToolPicked(DrawingView.Tool.MARKER) }
        btnToolEraser.setOnClickListener { onToolPicked(DrawingView.Tool.ERASER) }

        btnPanelClose.setOnClickListener { toggleStylePanel() }
    }

    /** Lights up whichever preset swatch (if any) exactly matches the given color. */
    private fun refreshPresetSelection(color: Int) {
        for (v in presetViews) {
            v.setPicked(v.getColorValue() == color)
        }
    }

    /** Shows/hides and re-syncs panel sections to match whichever tool is active. */
    private fun refreshPanelForActiveTool() {
        val tool = drawingView.getTool()
        updateToolSelectorUi()

        panelTitle.text = when (tool) {
            DrawingView.Tool.PEN -> "Pen settings"
            DrawingView.Tool.MARKER -> "Marker settings"
            DrawingView.Tool.ERASER -> "Eraser settings"
        }

        // Color has no meaning for the eraser; opacity only means anything for the marker.
        colorGroup.visibility = if (tool == DrawingView.Tool.ERASER) View.GONE else View.VISIBLE
        opacityGroup.visibility = if (tool == DrawingView.Tool.MARKER) View.VISIBLE else View.GONE

        if (tool != DrawingView.Tool.ERASER) {
            val color = drawingView.getActiveColor()
            colorWheel.setColorExternally(color)
            brightnessSeek.progress = (colorWheel.value * 100).toInt()
            refreshPresetSelection(color)
        }

        strokeSeek.progress = when (tool) {
            DrawingView.Tool.PEN -> drawingView.getStrokeWidth().toInt()
            DrawingView.Tool.MARKER -> drawingView.getMarkerWidth().toInt()
            DrawingView.Tool.ERASER -> drawingView.getEraserWidth().toInt()
        }

        if (tool == DrawingView.Tool.MARKER) {
            opacitySeek.progress = (drawingView.getMarkerOpacity() * 100).toInt()
        }
    }

    private fun toggleStylePanel() {
        stylePanelVisible = !stylePanelVisible
        styleView.visibility = if (stylePanelVisible) View.VISIBLE else View.GONE

        if (stylePanelVisible) {
            refreshPanelForActiveTool()
        }

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
            .setContentText("Tap the floating toolbar to draw, erase, undo, or exit.")
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
        penPulseAnimator?.cancel()
        windowManager.removeView(drawingView)
        windowManager.removeView(toolbarView)
        windowManager.removeView(styleView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
