package win.hik.tofchizi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs

class FrameAlignmentView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var roll: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
        
    private var animRoll: Float = 0f
    private val smoothingFactor = 0.1f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_primary)
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 60f
        textAlign = Paint.Align.CENTER
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Shortest path interpolation for angles
        var diff = roll - animRoll
        while (diff < -180) diff += 360
        while (diff > 180) diff -= 360
        
        animRoll += diff * smoothingFactor
        
        val cx = width / 2f
        val cy = height / 2f
        
        // Rotate the canvas opposite to phone rotation to keep lines level/plumb
        canvas.save()
        canvas.rotate(-animRoll, cx, cy)
        
        // Determine "Level-ness"
        // If angle is near 0, 90, 180, 270...
        // We normalize angle to -180..180 or 0..360
        var normAngle = animRoll % 90
        if (normAngle > 45) normAngle -= 90
        if (normAngle < -45) normAngle += 90
        // Now normAngle is deviation from nearest 90-degree step
        
        val isLevel = abs(normAngle) < 1f
        linePaint.color = if (isLevel) Color.GREEN else ContextCompat.getColor(context, R.color.brand_primary)
        
        // Draw huge lines covering beyond screen
        val len = Math.max(width, height) * 2f
        
        // Horizontal (World)
        canvas.drawLine(cx - len, cy, cx + len, cy, linePaint)
        
        // Vertical (World)
        canvas.drawLine(cx, cy - len, cx, cy + len, linePaint)
        
        canvas.restore()
        
        // Draw deviation angle in center?
        // Or hidden
        // User requested removing bottom strip.
        // Let's show angle near center if needed, or keeping it clean.
        // "十字水平、竖直线" requested.
        // Displaying angle is useful feedback.
        
        textPaint.color = if (isLevel) Color.GREEN else Color.WHITE
        canvas.drawText("${"%.1f".format(abs(normAngle))}°", cx, cy + 200f, textPaint)
        
        // Trigger redraw
        invalidate()
    }
}
