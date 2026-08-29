package com.fuad.screendrawer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A full hue/saturation color wheel. Brightness (the HSV "value" component)
 * is controlled externally (e.g. by a SeekBar) via [value].
 *
 * Standard technique: a SweepGradient provides hue around the circle, a
 * RadialGradient (white center -> transparent edge) is composited on top so
 * saturation fades to white toward the center.
 */
class ColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Brightness component, 0f..1f. Set this from an external brightness slider. */
    var value: Float = 1f

    /** Called whenever the user drags a new hue/saturation onto the wheel. */
    var onColorChanged: ((Int) -> Unit)? = null

    private var hue = 0f
    private var sat = 0f
    private var radius = 0f
    private var centerX = 0f
    private var centerY = 0f

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val selectorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.argb(90, 0, 0, 0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = min(w, h) / 2f - 8f
        if (radius <= 0f) return

        val hueColors = IntArray(361)
        for (i in 0..360) {
            hueColors[i] = Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
        }
        val sweep = SweepGradient(centerX, centerY, hueColors, null)
        val radial = RadialGradient(
            centerX, centerY, radius,
            Color.WHITE, Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        wheelPaint.shader = ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0f) return

        canvas.drawCircle(centerX, centerY, radius, wheelPaint)

        val angle = Math.toRadians(hue.toDouble())
        val dist = sat * radius
        val selX = centerX + (dist * cos(angle)).toFloat()
        val selY = centerY + (dist * sin(angle)).toFloat()
        canvas.drawCircle(selX, selY, 11f, selectorShadowPaint)
        canvas.drawCircle(selX, selY, 11f, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                updateFromTouch(event.x, event.y)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(x: Float, y: Float) {
        if (radius <= 0f) return
        val dx = x - centerX
        val dy = y - centerY
        var dist = sqrt(dx * dx + dy * dy)
        if (dist > radius) dist = radius

        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (angle < 0) angle += 360f

        hue = angle
        sat = (dist / radius).coerceIn(0f, 1f)
        invalidate()
        onColorChanged?.invoke(currentColor())
    }

    fun currentColor(): Int = Color.HSVToColor(floatArrayOf(hue, sat, value))

    /** Move the selector to match an externally-chosen color (e.g. a preset swatch). */
    fun setColorExternally(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        invalidate()
    }
}
