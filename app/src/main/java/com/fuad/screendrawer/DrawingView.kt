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
 * Full-screen transparent canvas.
 *
 * Internally this draws onto an offscreen [Bitmap] in real time (both pen
 * strokes and eraser strokes), which is what makes a *true* pixel eraser
 * possible: an eraser stroke is drawn with PorterDuff.Mode.CLEAR straight
 * onto the bitmap, punching a transparent hole wherever it goes, exactly
 * like a real eraser rather than just deleting a whole shape.
 *
 * A lightweight history of strokes (path + color + width + eraser flag) is
 * kept alongside the bitmap so Undo/Redo can rebuild the bitmap correctly -
 * erasing isn't simply "invertible" once it has punched through earlier ink,
 * so undo replays every remaining stroke in order from scratch, while redo
 * can cheaply re-apply the single stroke it just restored.
 */
class DrawingView(context: Context) : View(context) {

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

    private var currentColor = Color.parseColor("#FF3B30")
    private var currentWidth = 12f
    private var eraserWidth = 40f
    private var eraserModeOn = false
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

    // --- Pen state -----------------------------------------------------

    fun setColor(color: Int) { currentColor = color }
    fun setStrokeWidth(width: Float) { currentWidth = width }
    fun getColor(): Int = currentColor
    fun getStrokeWidth(): Float = currentWidth

    // --- Eraser state ----------------------------------------------------

    fun setEraserMode(on: Boolean) { eraserModeOn = on }
    fun isEraserMode(): Boolean = eraserModeOn
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
        // The bitmap already reflects everything up to just before this
        // stroke, so redo only needs to re-apply this one stroke - no need
        // for a full history replay.
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
                // Draw an immediate dot so a plain tap (no drag) is still visible.
                drawSegment(x, y, x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                drawSegment(lastX, lastY, x, y)
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_UP -> {
                val color = if (eraserModeOn) Color.TRANSPARENT else currentColor
                val width = if (eraserModeOn) eraserWidth else currentWidth
                strokes.add(Stroke(currentPath, color, width, eraserModeOn))
                redoStack.clear() // a fresh stroke invalidates any pending redo
                currentPath = Path()
                onHistoryChanged?.invoke()
            }
        }
        invalidate()
        return true
    }

    /** Draws one live segment straight onto the bitmap - this is what gives real-time erasing. */
    private fun drawSegment(x1: Float, y1: Float, x2: Float, y2: Float) {
        val canvas = bitmapCanvas ?: return
        paint.xfermode = if (eraserModeOn) clearXfermode else null
        paint.color = currentColor
        paint.strokeWidth = if (eraserModeOn) eraserWidth else currentWidth
        canvas.drawLine(x1, y1, x2, y2, paint)
        paint.xfermode = null
    }
}
