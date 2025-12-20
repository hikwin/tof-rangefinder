package win.hik.tofchizi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min

import androidx.core.content.ContextCompat

class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var azimuth: Float = 0f
    private var targetAzimuth: Float = 0f

    // Paints
    private val mainTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    
    private val secondaryTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        alpha = 128
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 40f
        textAlign = Paint.Align.CENTER
        // Load a custom font if available, or just standard bold
        isFakeBoldText = true 
    }

    private val cardinalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 60f // Larger for N, S, E, W
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val trianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    
    // For smooth animation
    private val ANIMATION_SPEED = 0.2f 

    fun setAzimuth(newAzimuth: Float) {
        // Handle wrapping 359 -> 0
        var diff = newAzimuth - this.azimuth
        while (diff < -180) diff += 360
        while (diff > 180) diff -= 360
        
        targetAzimuth = this.azimuth + diff
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Smoothly interpolate current azimuth to target
        val diff = targetAzimuth - azimuth
        if (Math.abs(diff) > 0.1f) {
            azimuth += diff * ANIMATION_SPEED
            invalidate() // Continue animation
        } else {
            azimuth = targetAzimuth
        }
        
        // Normalize for drawing (optional, but good for cleanliness)
        val drawAzimuth = (azimuth % 360 + 360) % 360

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - 40f // Padding

        // Draw center indicator (Fixed at top)
        drawIndicator(canvas)

        // Rotate the dial
        canvas.save()
        canvas.rotate(-drawAzimuth, cx, cy)

        // Draw Ticks and Text
        for (i in 0 until 360) {
            val angleRad = Math.toRadians((i - 90).toDouble()) // -90 to start at top (North)
            
            // Coordinates
            val cosAngle = cos(angleRad).toFloat()
            val sinAngle = sin(angleRad).toFloat()
            
            // Major cardinal points (0, 90, 180, 270)
            if (i % 90 == 0) {
                val startX = cx + radius * cosAngle
                val startY = cy + radius * sinAngle
                val endX = cx + (radius - 50f) * cosAngle
                val endY = cy + (radius - 50f) * sinAngle
                canvas.drawLine(startX, startY, endX, endY, mainTickPaint)

                // Draw N, E, S, W
                val labelRadius = radius - 90f
                val labelX = cx + labelRadius * cosAngle
                val labelY = cy + labelRadius * sinAngle + (cardinalTextPaint.textSize / 3)
                
                val label = when(i) {
                    0 -> "N"
                    90 -> "E"
                    180 -> "S"
                    270 -> "W"
                    else -> ""
                }
                
                // Highlight North in Red? Or standard White? usually White/Red. 
                // Let's keep it White for cleanliness or Red for North.
                val originalColor = cardinalTextPaint.color
                if (i == 0) cardinalTextPaint.color = Color.RED
                canvas.drawText(label, labelX, labelY, cardinalTextPaint)
                if (i == 0) cardinalTextPaint.color = originalColor // Reset

            } 
            // Major degree markers (30, 60, 120...)
            else if (i % 30 == 0) {
                val startX = cx + radius * cosAngle
                val startY = cy + radius * sinAngle
                val endX = cx + (radius - 40f) * cosAngle
                val endY = cy + (radius - 40f) * sinAngle
                canvas.drawLine(startX, startY, endX, endY, mainTickPaint)
                
                val labelRadius = radius - 75f
                val labelX = cx + labelRadius * cosAngle
                val labelY = cy + labelRadius * sinAngle + (textPaint.textSize / 3)
                canvas.drawText(i.toString(), labelX, labelY, textPaint)
            }
            // Decimals (10, 20, 40...)
            else if (i % 10 == 0) {
                val startX = cx + radius * cosAngle
                val startY = cy + radius * sinAngle
                val endX = cx + (radius - 30f) * cosAngle
                val endY = cy + (radius - 30f) * sinAngle
                canvas.drawLine(startX, startY, endX, endY, secondaryTickPaint)
            }
            // Minor ticks (every 2 degrees)
            else if (i % 2 == 0) {
                 val startX = cx + radius * cosAngle
                val startY = cy + radius * sinAngle
                val endX = cx + (radius - 15f) * cosAngle
                val endY = cy + (radius - 15f) * sinAngle
                canvas.drawLine(startX, startY, endX, endY, minorTickPaint)
            }
        }
        
        // Draw centered crosshair or plus 
        val plusSize = 20f
        canvas.drawLine(cx - plusSize, cy, cx + plusSize, cy, secondaryTickPaint)
        canvas.drawLine(cx, cy - plusSize, cx, cy + plusSize, secondaryTickPaint)

        canvas.restore()
    }

    private fun drawIndicator(canvas: Canvas) {
        val path = Path()
        // Draw a small triangle pointing down to the dial
        val indicatorSize = 30f
        // Position it at Top Center of the View, pointing down
        // However, standard UI usually has a fixed line or triangle at the top of the dial.
        // Let's draw it just slightly above the radius.
        
        val centerX = width / 2f
        val topY = height / 2f - (min(width, height) / 2f) + 10f // Top margin
        
        path.moveTo(centerX, topY + indicatorSize)
        path.lineTo(centerX - indicatorSize / 2, topY)
        path.lineTo(centerX + indicatorSize / 2, topY)
        path.close()
        
        canvas.drawPath(path, trianglePaint)
        
        // Also draw a static line?
        val paint = Paint()
        paint.color = ContextCompat.getColor(context, R.color.text_primary)
        paint.strokeWidth = 4f
        canvas.drawLine(centerX, topY + indicatorSize, centerX, topY + indicatorSize + 30f, paint)
    }
}
