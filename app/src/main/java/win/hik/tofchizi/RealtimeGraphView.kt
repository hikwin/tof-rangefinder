package win.hik.tofchizi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.util.LinkedList

class RealtimeGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dataPoints = LinkedList<Float>()
    private val maxPoints = 200 // Max points to keep on screen
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val path = Path()

    // Configuration
    var lineColor: Int = Color.GREEN
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }
        
    var lockToMaxRange: Boolean = false
    private var globalMin = Float.MAX_VALUE
    private var globalMax = Float.MIN_VALUE

    fun addDataPoint(value: Float) {
        dataPoints.add(value)
        if (value > globalMax) globalMax = value
        if (value < globalMin) globalMin = value
        
        if (dataPoints.size > maxPoints) {
            dataPoints.removeFirst()
        }
        invalidate()
    }
    
    fun clear() {
        dataPoints.clear()
        globalMin = Float.MAX_VALUE
        globalMax = Float.MIN_VALUE
        invalidate()
    }

    var showYAxis = false
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 30f
        textAlign = Paint.Align.RIGHT
    }
    
    // Add margin for labels if enabled
    private val Y_AXIS_WIDTH = 100f 

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dataPoints.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        
        // Auto scale Y
        var min: Float
        var max: Float
        
        if (lockToMaxRange && globalMax > globalMin) {
            min = globalMin
            max = globalMax
        } else {
            min = dataPoints.minOrNull() ?: 0f
            max = dataPoints.maxOrNull() ?: 1f
        }
        
        if (max == min) {
            max = min + 1f
            min = min - 1f
        }
        
        val range = max - min
        
        // Adjust drawing area
        val startX = if (showYAxis) Y_AXIS_WIDTH else 0f
        val drawWidth = width - startX
        
        val dx = drawWidth / (maxPoints - 1)
        
        // Draw Axis Labels
        if (showYAxis) {
            val labelX = Y_AXIS_WIDTH - 10f
            // Top (Max)
            canvas.drawText(String.format("%.1f", max), labelX, 40f, textPaint)
            // Middle
            canvas.drawText(String.format("%.1f", (max+min)/2), labelX, height/2, textPaint)
            // Bottom (Min)
            canvas.drawText(String.format("%.1f", min), labelX, height - 10f, textPaint)
            
            // Vertical Line
            canvas.drawLine(startX, 0f, startX, height, textPaint)
        }

        path.reset()
        
        for (i in dataPoints.indices) {
            val value = dataPoints[i]
            // Normalize value to 0..1
            val normalized = (value - min) / range
            // Flip Y (canvas Y is 0 at top)
            val y = height - (normalized * height)
            val x = startX + i * dx
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, paint)
    }
}
