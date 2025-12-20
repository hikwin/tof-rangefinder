package win.hik.tofchizi

import android.content.Context
import kotlin.math.abs
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

import androidx.core.content.ContextCompat

class RulerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        strokeWidth = 2f
        textSize = 30f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 40f
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    var pixelsPerMm: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_MM, 1f, resources.displayMetrics
    )
    
    fun setPpm(ppm: Float) {
        pixelsPerMm = ppm
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Reset paint to default state for ticks
        paint.color = ContextCompat.getColor(context, R.color.text_primary)
        paint.strokeWidth = 2f

        val width = width.toFloat()
        val height = height.toFloat()
        
        // Draw vertical scale on the left
        var mm = 0
        var y = 0f
        val topPaddingMm = 2f
        val startOffset = topPaddingMm * pixelsPerMm
        
        while (y < height) {
            y = startOffset + mm * pixelsPerMm
            
            // Major tick every 10mm (1cm)
            val lineLength = if (mm % 10 == 0) {
                100f // 1cm tick
            } else if (mm % 5 == 0) {
                70f  // 0.5cm tick
            } else {
                40f  // 1mm tick
            }
            
            canvas.drawLine(0f, y, lineLength, y, paint)
            
            if (mm % 10 == 0) {
                val cm = mm / 10
                canvas.drawText("${cm}cm", 110f, y + 15f, textPaint)
            }
            
            mm++
        }
        
    // Right side (optional, mirrored or specific logic)
        // For now just left side vertical ruler
        
        if (isCalibrationMode) {
            // Draw Line 1 (Fixed)
            paint.color = Color.GREEN
            paint.strokeWidth = 5f
            canvas.drawLine(0f, calibrationLine1Y, width, calibrationLine1Y, paint)
            // canvas.drawText("Start", width - 100f, calibrationLine1Y - 10f, textPaint)
            
            // Draw Line 2 (Draggable)
            paint.color = Color.RED
            canvas.drawLine(0f, calibrationLine2Y, width, calibrationLine2Y, paint)
            // canvas.drawText("Drag me", width - 200f, calibrationLine2Y - 10f, textPaint)
            
            // Draw Double Arrow on Line 2 (Right side)
            val arrowX = width - 60f
            val arrowY = calibrationLine2Y
            val arrowSize = 30f
            val halfHead = 15f
            
            // Vertical shaft
            canvas.drawLine(arrowX, arrowY - arrowSize, arrowX, arrowY + arrowSize, paint)
            
            // Top head (pointing up)
            canvas.drawLine(arrowX, arrowY - arrowSize, arrowX - halfHead, arrowY - arrowSize + halfHead, paint)
            canvas.drawLine(arrowX, arrowY - arrowSize, arrowX + halfHead, arrowY - arrowSize + halfHead, paint)
            
            // Bottom head (pointing down)
            canvas.drawLine(arrowX, arrowY + arrowSize, arrowX - halfHead, arrowY + arrowSize - halfHead, paint)
            canvas.drawLine(arrowX, arrowY + arrowSize, arrowX + halfHead, arrowY + arrowSize - halfHead, paint)
            
            // Restore paint
            paint.color = Color.WHITE
            paint.strokeWidth = 2f
        }
    }

    var isCalibrationMode = false
    var calibrationLine1Y = 0f
    var calibrationLine2Y = 0f
    private var isDraggingLine2 = false

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (!isCalibrationMode) return super.onTouchEvent(event)
        
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                if (abs(event.y - calibrationLine2Y) < 150f) {
                    isDraggingLine2 = true
                    return true
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (isDraggingLine2) {
                    calibrationLine2Y = event.y
                    invalidate()
                    return true
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                isDraggingLine2 = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
