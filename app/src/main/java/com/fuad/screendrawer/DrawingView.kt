package com.fuad.screendrawer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

/**
 * Full-screen transparent canvas. Holds a list of finished strokes (each with
 * its own color + width so undo/redo history stays accurate even if the user
 * changes color or brush size mid-session) plus the stroke currently being drawn.
 */
class DrawingView(context: Context) : View(context) {

    private data class Stroke(val path: Path, val color: Int, val width: Float)

    private val strokes = mutableListOf<Stroke>()
    private var currentPath = Path()

    private var currentColor = Color.parseColor("#FF3B30")
    private var currentWidth = 12f

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun setColor(color: Int) {
        currentColor = color
    }

    fun setStrokeWidth(width: Float) {
        currentWidth = width
    }

    fun getColor(): Int = currentColor

    fun getStrokeWidth(): Float = currentWidth

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.size - 1)
            invalidate()
        }
    }

    fun clearAll() {
        strokes.clear()
        currentPath = Path()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (stroke in strokes) {
            paint.color = stroke.color
            paint.strokeWidth = stroke.width
            canvas.drawPath(stroke.path, paint)
        }
        paint.color = currentColor
        paint.strokeWidth = currentWidth
        canvas.drawPath(currentPath, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path()
                currentPath.moveTo(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                strokes.add(Stroke(currentPath, currentColor, currentWidth))
                currentPath = Path()
            }
        }
        invalidate()
        return true
    }
}
