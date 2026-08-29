package com.fuad.screendrawer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * A small circular swatch that previews a color. Used both as the "current
 * color" indicator in the floating toolbar, and as a quick-pick preset in
 * the style panel - where [setPicked] draws a soft accent ring around
 * whichever preset currently matches the active drawing color.
 */
class ColorSwatchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var color = Color.RED
    private var picked = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }
    private val pickedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#6C5CE7")
    }

    fun setColorValue(c: Int) {
        color = c
        invalidate()
    }

    fun getColorValue(): Int = color

    fun setPicked(p: Boolean) {
        if (picked != p) {
            picked = p
            invalidate()
        }
    }

    fun isPicked(): Boolean = picked

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 5f

        if (picked) {
            canvas.drawCircle(cx, cy, r + 4f, pickedGlowPaint)
        }

        fillPaint.color = color
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, ringPaint)
    }
}
