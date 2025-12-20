package win.hik.tofchizi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class ProtractorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var angle = 90f // Default to 90 degrees (center)
    
    // Paints
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_accent)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        alpha = 200
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_primary)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_primary)
        style = Paint.Style.FILL
        alpha = 180
    }
    
    private val resultTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_accent)
        textSize = 120f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val pixelsPerMm: Float = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_MM, 1f, resources.displayMetrics
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        
        // Add 2mm padding offset from left
        val offsetMm = 2f
        val startOffset = offsetMm * pixelsPerMm
        val cx = startOffset
        
        val cy = h / 2f
        val radius = (w * 0.85f).coerceAtMost(h * 0.8f) // Use plenty of width
        
        // Rect for Arc: Centered at (cx, cy)
        // We want arc from -90 (Top) to 90 (Bottom), extending to Right.
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        
        canvas.drawArc(rect, -90f, 180f, false, arcPaint)
        
        // Base Line (Vertical along left edge)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, arcPaint)
        
        // Draw Ticks
        for (i in 0..180) {
             val angleRad = Math.toRadians((-90 + i).toDouble())
             
             if (i % 10 == 0) {
                // Major
                val startX = cx + radius * cos(angleRad).toFloat()
                val startY = cy + radius * sin(angleRad).toFloat()
                val endX = cx + (radius - 50f) * cos(angleRad).toFloat()
                val endY = cy + (radius - 50f) * sin(angleRad).toFloat()
                canvas.drawLine(startX, startY, endX, endY, tickPaint)
                
                // Text
                if (i % 30 == 0) {
                     val textRadius = radius - 90f
                     val tx = cx + textRadius * cos(angleRad).toFloat()
                     val ty = cy + textRadius * sin(angleRad).toFloat() + 10f
                     canvas.drawText(i.toString(), tx, ty, textPaint)
                }
             } else if (i % 2 == 0) { // Minor ticks every 2 degrees
                 val tickLen = if (i % 5 == 0) 30f else 15f
                 val startX = cx + radius * cos(angleRad).toFloat()
                 val startY = cy + radius * sin(angleRad).toFloat()
                 val endX = cx + (radius - tickLen) * cos(angleRad).toFloat()
                 val endY = cy + (radius - tickLen) * sin(angleRad).toFloat()
                 canvas.drawLine(startX, startY, endX, endY, tickPaint)
             }
        }
        
        // Needle
        val needleRad = Math.toRadians((-90 + angle).toDouble())
        val needleX = cx + (radius - 20f) * cos(needleRad).toFloat()
        val needleY = cy + (radius - 20f) * sin(needleRad).toFloat()
        canvas.drawLine(cx, cy, needleX, needleY, needlePaint)
        
        // Knob
        canvas.drawCircle(cx, cy, 30f, knobPaint)
        
        // Result Text - Rotated Vertical (90 degrees clockwise)
        // Result Text - Rotated Vertical (90 degrees clockwise)
        canvas.save()
        val textX = w / 2f + 50f 
        val textY = cy
        
        // Draw background circle
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
             color = android.graphics.Color.BLACK
             alpha = 82 // ~32% opacity
             style = Paint.Style.FILL
        }
        val bgRadius = 180f
        canvas.drawCircle(textX, textY, bgRadius, bgPaint)
        
        canvas.rotate(90f, textX, textY)
        
        val angleInt = angle.toInt()
        val supplementary = -(180 - angleInt)
        
        // Calculate text heights to center them vertically (which is horizontally after rotation)
        // Font metrics
        val fmMain = resultTextPaint.fontMetrics
        val hMain = fmMain.descent - fmMain.ascent
        
        val secPaint = Paint(resultTextPaint)
        secPaint.textSize = 80f
        secPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
        val fmSec = secPaint.fontMetrics
        val hSec = fmSec.descent - fmSec.ascent
        
        val totalH = hMain + hSec

        
        // Start Y (which is local X in rotated logic... wait. 
        // drawText(text, x, y).
        // Rotate 90 around (textX, textY).
        // The "Y" coordinate in drawText moves along the X axis of screen.
        // The "X" coordinate in drawText moves along the Y axis of screen.
        // We want to center strictly on textX, textY.
        
        // Main text roughly centered, slightly up
        val yMain = textY - (totalH / 2f) - fmMain.ascent // Baseline
        canvas.drawText("${angleInt}°", textX, yMain - 20f, resultTextPaint)
        
        // Secondary text roughly centered, slightly down
        val ySec = textY + (totalH / 2f) - fmSec.descent
        canvas.drawText("${supplementary}°", textX, ySec + 20f, secPaint)
        
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Recalculate cx (offset)
                val offsetMm = 2f
                val startOffset = offsetMm * pixelsPerMm
                val cx = startOffset
                
                val cy = height / 2f
                val dx = event.x - cx
                val dy = event.y - cy
                
                var theta = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                
                // If dx < 0 (left of offset), angle calculation might jump.
                // Just map theta (-180 to 180) to desired range.
                // Right side (dx>0): -90 (Top) -> 90 (Bottom). 
                // That matches our drawing logic perfectly.
                
                val rawAngle = theta + 90f
                angle = rawAngle.coerceIn(0f, 180f)
                invalidate()
                return true
            }
        }
        return true
    }
}
