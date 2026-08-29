package com.fuad.screendrawer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

class DrawingView(context: Context) : View(context) {

    private val strokes = mutableListOf<Pair<Path, Int>>()
    private var currentPath = Path()

    private val colors = listOf(
        Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.WHITE, Color.BLACK
    )
    private var colorIndex = 0

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = colors[colorIndex]
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun nextColor() {
        colorIndex = (colorIndex + 1) % colors.size
    }

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
        for ((path, color) in strokes) {
            paint.color = color
            canvas.drawPath(path, paint)
        }
        paint.color = colors[colorIndex]
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
                strokes.add(Pair(currentPath, colors[colorIndex]))
                currentPath = Path()
            }
        }
        invalidate()
        return true
    }
}
