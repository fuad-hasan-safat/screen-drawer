package com.fuad.screendrawer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.MotionEvent
import android.view.View

/**
 * Full-screen transparent canvas supporting three mutually-exclusive tools:
 * Pen (opaque), Marker (translucent highlighter), and Eraser.
 *
 * Internally this draws onto an offscreen [Bitmap] in real time, which is
 * what makes a *true* pixel eraser possible: an eraser stroke is drawn with
 * PorterDuff.Mode.CLEAR straight onto the bitmap, punching a transparent
 * hole wherever it goes.
 *
 * Pen and eraser strokes are safe to draw incrementally, segment by segment,
 * straight onto the permanent bitmap - pen is fully opaque and eraser's
 * CLEAR mode is idempotent, so overlapping segments within one stroke never
 * look different from a single continuous stroke. A translucent marker
 * stroke is NOT safe to draw that way: compositing many overlapping
 * semi-transparent segments would double-darken wherever they overlap along
 * the same stroke. So the in-progress marker stroke is instead rendered
 * fresh each frame as a *preview* on top of the bitmap (never written into
 * it) and only committed to the bitmap once, as a single whole-path draw,
 * when the stroke finishes.
 *
 * A lightweight history of strokes (path + final color + width + eraser
 * flag) is kept alongside the bitmap so Undo/Redo can rebuild it correctly.
 */
class DrawingView(context: Context) : View(context) {

    enum class Tool { PEN, MARKER, ERASER }

    private data class Stroke(
        val path: Path,
        val color: Int,
        val width: Float,
        val isEraser: Boolean
    )

    private val strokes = mutableListOf<Stroke>()
    private val redoStack = mutableListOf<Stroke>()
    private var currentPath = Path()
    private var lastX = 0f
    private var lastY = 0f
    private var movedWhileDrawing = false

    private var currentTool = Tool.PEN

    // Pen
    private var penColor = Color.parseColor("#FF3B30")
    private var penWidth = 12f

    // Marker (highlighter): translucent, flatter/thicker by default
    private var markerColor = Color.parseColor("#FFEB3B")
    private var markerWidth = 26f
    private var markerOpacity = 0.4f // 0f..1f

    // Eraser
    private var eraserWidth = 40f

    private var stylusOnlyMode = false

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private val clearXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Notified whenever a stroke is added, undone, redone, or cleared. */
    var onHistoryChanged: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val newCanvas = Canvas(newBitmap)
        bitmap?.let { newCanvas.drawBitmap(it, 0f, 0f, null) }
        bitmap = newBitmap
        bitmapCanvas = newCanvas
    }

    // --- Tool selection ----------------------------------------------------

    fun setTool(tool: Tool) { currentTool = tool }
    fun getTool(): Tool = currentTool

    // --- Pen state -----------------------------------------------------

    fun setStrokeWidth(width: Float) { penWidth = width }
    fun getStrokeWidth(): Float = penWidth

    // --- Marker state -----------------------------------------------------

    fun setMarkerWidth(width: Float) { markerWidth = width }
    fun getMarkerWidth(): Float = markerWidth
    fun setMarkerOpacity(opacity: Float) { markerOpacity = opacity.coerceIn(0.05f, 1f) }
    fun getMarkerOpacity(): Float = markerOpacity

    // --- Color: applies to whichever of Pen/Marker is currently active ----

    fun setActiveColor(color: Int) {
        when (currentTool) {
            Tool.PEN -> penColor = color
            Tool.MARKER -> markerColor = color
            Tool.ERASER -> { /* eraser has no color */ }
        }
    }

    fun getActiveColor(): Int = when (currentTool) {
        Tool.PEN -> penColor
        Tool.MARKER -> markerColor
        Tool.ERASER -> penColor
    }

    // --- Eraser state ----------------------------------------------------

    fun setEraserWidth(width: Float) { eraserWidth = width }
    fun getEraserWidth(): Float = eraserWidth

    // --- Palm rejection --------------------------------------------------

    fun setStylusOnlyMode(on: Boolean) { stylusOnlyMode = on }
    fun isStylusOnlyMode(): Boolean = stylusOnlyMode

    // --- History (undo / redo) --------------------------------------------

    fun canUndo(): Boolean = strokes.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        if (strokes.isEmpty()) return
        val removed = strokes.removeAt(strokes.size - 1)
        redoStack.add(removed)
        redrawFromHistory()
        onHistoryChanged?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val stroke = redoStack.removeAt(redoStack.size - 1)
        strokes.add(stroke)
        val canvas = bitmapCanvas
        if (canvas != null) {
            paint.xfermode = if (stroke.isEraser) clearXfermode else null
            paint.color = stroke.color
            paint.strokeWidth = stroke.width
            canvas.drawPath(stroke.path, paint)
            paint.xfermode = null
        }
        invalidate()
        onHistoryChanged?.invoke()
    }

    fun clearAll() {
        strokes.clear()
        redoStack.clear()
        currentPath = Path()
        bitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
        onHistoryChanged?.invoke()
    }

    private fun redrawFromHistory() {
        val bm = bitmap ?: return
        val canvas = bitmapCanvas ?: return
        bm.eraseColor(Color.TRANSPARENT)
        for (stroke in strokes) {
            paint.xfermode = if (stroke.isEraser) clearXfermode else null
            paint.color = stroke.color
            paint.strokeWidth = stroke.width
            canvas.drawPath(stroke.path, paint)
        }
        paint.xfermode = null
        invalidate()
    }

    // --- Rendering ---------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        // The marker's in-progress stroke is rendered fresh each frame here,
        // never written into the bitmap until release - see class doc.
        if (currentTool == Tool.MARKER && !currentPath.isEmpty) {
            paint.xfermode = null
            paint.color = markerColorWithOpacity()
            paint.strokeWidth = markerWidth
            canvas.drawPath(currentPath, paint)
        }
    }

    private fun markerColorWithOpacity(): Int {
        val a = (markerOpacity * 255f).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(markerColor), Color.green(markerColor), Color.blue(markerColor))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (stylusOnlyMode) {
            val toolType = event.getToolType(0)
            val isPenInput = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                toolType == MotionEvent.TOOL_TYPE_ERASER
            if (!isPenInput) {
                // Finger (or anything else) touched the screen - swallow it
                // silently so a resting palm never leaves a mark.
                return true
            }
        }

        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path()
                currentPath.moveTo(x, y)
                lastX = x
                lastY = y
                movedWhileDrawing = false
                if (currentTool != Tool.MARKER) {
                    // Immediate dot so a plain tap (no drag) is still visible.
                    drawSegment(x, y, x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                movedWhileDrawing = true
                if (currentTool == Tool.MARKER) {
                    invalidate() // just repaint the live preview, bitmap untouched
                } else {
                    drawSegment(lastX, lastY, x, y)
                }
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_UP -> {
                if (currentTool == Tool.MARKER) {
                    if (!movedWhileDrawing) {
                        currentPath.lineTo(x + 0.01f, y) // tiny nudge so a tap still renders a dot
                    }
                    val color = markerColorWithOpacity()
                    strokes.add(Stroke(currentPath, color, markerWidth, false))
                    bitmapCanvas?.let { canvas ->
                        paint.xfermode = null
                        paint.color = color
                        paint.strokeWidth = markerWidth
                        canvas.drawPath(currentPath, paint)
                    }
                } else {
                    val isEraser = currentTool == Tool.ERASER
                    val color = if (isEraser) Color.TRANSPARENT else penColor
                    val width = if (isEraser) eraserWidth else penWidth
                    strokes.add(Stroke(currentPath, color, width, isEraser))
                }
                redoStack.clear() // a fresh stroke invalidates any pending redo
                currentPath = Path()
                onHistoryChanged?.invoke()
            }
        }
        invalidate()
        return true
    }

    /** Draws one live segment straight onto the bitmap (pen/eraser only - see class doc). */
    private fun drawSegment(x1: Float, y1: Float, x2: Float, y2: Float) {
        val canvas = bitmapCanvas ?: return
        val isEraser = currentTool == Tool.ERASER
        paint.xfermode = if (isEraser) clearXfermode else null
        paint.color = if (isEraser) Color.TRANSPARENT else penColor
        paint.strokeWidth = if (isEraser) eraserWidth else penWidth
        canvas.drawLine(x1, y1, x2, y2, paint)
        paint.xfermode = null
    }
}
