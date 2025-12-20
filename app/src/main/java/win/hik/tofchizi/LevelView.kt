package win.hik.tofchizi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class LevelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode {
        FLAT, SIDE, LEFT_SIDE
    }

    var currentMode: Mode = Mode.FLAT
        set(value) {
            field = value
            invalidate()
        }

    // Sensor values in degrees
    var pitch: Float = 0f
    var roll: Float = 0f

    // Smoothed values for animation
    private var animPitch: Float = 0f
    private var animRoll: Float = 0f
    private val smoothingFactor = 0.2f // Simple LPF

    // Captured values for Face-Down mode
    var capturedPitch: Float? = null
    var capturedRoll: Float? = null
    
    // Paints
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = ContextCompat.getColor(context, R.color.text_primary)
    }

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.brand_primary)
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 100f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 50f
        textAlign = Paint.Align.CENTER
    }
    
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.text_primary)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Smooth animation math
        animPitch = animPitch + (pitch - animPitch) * smoothingFactor
        animRoll = animRoll + (roll - animRoll) * smoothingFactor
        
        val displayPitch = capturedPitch ?: animPitch
        val displayRoll = capturedRoll ?: animRoll

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        when (currentMode) {
            Mode.FLAT -> drawFlatMode(canvas, cx, cy, displayPitch, displayRoll)
            Mode.SIDE -> drawSideMode(canvas, cx, cy, displayPitch, displayRoll)
            Mode.LEFT_SIDE -> drawLeftSideMode(canvas, cx, cy, displayPitch, displayRoll)
        }

        invalidate()
    }

    private fun drawFlatMode(canvas: Canvas, cx: Float, cy: Float, p: Float, r: Float) {
        // Flat Mode: Twin Circles.
        val maxDeg = 10f
        val radius = min(cx, cy) * 0.8f
        val pixelsPerDeg = radius / maxDeg
        
        // Clamp bubble to circle
        var dx = r * pixelsPerDeg
        var dy = -p * pixelsPerDeg
        
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist > radius) {
            val scale = radius / dist
            dx *= scale
            dy *= scale
        }
        
        // Draw Outer Circle
        canvas.drawCircle(cx, cy, radius, circlePaint)
        
        // Draw Center Target (1 degree tolerance)
        val targetRadius = pixelsPerDeg * 1f
        canvas.drawCircle(cx, cy, targetRadius, circlePaint)
        
        // Check if level
        val isLevel = abs(p) < 1f && abs(r) < 1f
        bubblePaint.color = if (isLevel) Color.GREEN else ContextCompat.getColor(context, R.color.brand_primary)
        
        // Draw Bubble
        val bubbleRadius = radius * 0.1f
        canvas.drawCircle(cx + dx, cy + dy, bubbleRadius, bubblePaint)
        
        // Draw Text
        val degText = String.format("X: %.1f°  Y: %.1f°", p, r)
        canvas.drawText(degText, cx, cy + radius + 120f, subTextPaint)
    }

    private fun drawSideMode(canvas: Canvas, cx: Float, cy: Float, @Suppress("UNUSED_PARAMETER") p: Float, r: Float) {
        val angle = r // Degrees
        val radius = width * 0.4f
        
        val isLevel = abs(angle) < 1f
        linePaint.color = if (isLevel) Color.GREEN else ContextCompat.getColor(context, R.color.text_primary)
        
        canvas.save()
        canvas.rotate(-angle, cx, cy)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, linePaint)
        canvas.restore()
        
        // Draw Angle
        textPaint.color = if (isLevel) Color.GREEN else ContextCompat.getColor(context, R.color.text_primary)
        canvas.drawText(String.format("%.1f°", abs(angle)), cx, cy - 200f, textPaint)
        
        // Helper circle
        canvas.drawCircle(cx, cy, radius, circlePaint)
        
        // Multi-line instruction text
        subTextPaint.textSize = 40f
        canvas.drawText(context.getString(R.string.level_instr_bottom), cx, cy + 300f, subTextPaint)
        canvas.drawText(context.getString(R.string.level_instr_tilt), cx, cy + 360f, subTextPaint)
    }

    private fun drawLeftSideMode(canvas: Canvas, cx: Float, cy: Float, p: Float, @Suppress("UNUSED_PARAMETER") r: Float) {
        // Left Side Mode: Screen is Vertical, Phone on Left Edge.
        // We draw a VERTICAL line relative to screen (which is Horizontal in real world).
        
        val angle = p // Pitch
        val radius = width * 0.4f
        
        val isLevel = abs(angle) < 1f
        linePaint.color = if (isLevel) Color.GREEN else ContextCompat.getColor(context, R.color.text_primary)
        
        canvas.save()
        // Rotate -angle. Initial line is Vertical.
        canvas.rotate(-angle, cx, cy)
        // Draw Vertical Line: (cx, cy - r) to (cx, cy + r)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, linePaint)
        canvas.restore()
        
        textPaint.color = if (isLevel) Color.GREEN else ContextCompat.getColor(context, R.color.text_primary)
        
        // Rotate text to match the "Side" view orientation (Text runs up/down screen)
        canvas.save()
        canvas.rotate(90f, cx, cy)
        canvas.drawText(String.format("%.1f°", abs(angle)), cx, cy - 200f, textPaint)
        
        subTextPaint.textSize = 40f
        canvas.drawText(context.getString(R.string.level_instr_left), cx, cy + 300f, subTextPaint)
        canvas.drawText(context.getString(R.string.level_instr_tilt), cx, cy + 360f, subTextPaint)
        canvas.restore()
        
        canvas.drawCircle(cx, cy, radius, circlePaint)
    }
}
