package win.hik.tofchizi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.content.SharedPreferences

class CrosshairOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            24f,
            resources.displayMetrics
        )
    }
    private val dp10 = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        10f,
        resources.displayMetrics
    )
    private var text: String = ""
    private var pitchDeg: Float = 0f
    private var yawDeg: Float = 0f
    private val anglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            16f,
            resources.displayMetrics
        )
    }
    private val compassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            16f,
            resources.displayMetrics
        )
    }
    private var compassText: String = ""
    private var centerX: Float = -1f
    private var centerY: Float = -1f
    private var dragging: Boolean = false
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            dragging = true
        }
    })
    private val crossRadius = 70f
    private val prefs: SharedPreferences = context.getSharedPreferences("tof_prefs", Context.MODE_PRIVATE)
    private var movementLocked: Boolean = false

    fun setDistanceText(t: String) {
        if (text != t) {
            text = t
            invalidate()
        }
    }

    fun setAngles(pitch: Float, yaw: Float) {
        if (pitchDeg != pitch || yawDeg != yaw) {
            pitchDeg = pitch
            yawDeg = yaw
            invalidate()
        }
    }

    fun setCompassHeading(azimuthDeg: Float) {
        val az = ((azimuthDeg % 360f) + 360f) % 360f
        val dir = when {
            az < 22.5f || az >= 337.5f -> "N"
            az < 67.5f -> "NE"
            az < 112.5f -> "E"
            az < 157.5f -> "SE"
            az < 202.5f -> "S"
            az < 247.5f -> "SW"
            az < 292.5f -> "W"
            else -> "NW"
        }
        val ct = String.format("%s %.0f°", dir, az)
        if (compassText != ct) {
            compassText = ct
            invalidate()
        }
    }
    fun setCrosshairColor(color: Int) {
        if (crossPaint.color != color) {
            crossPaint.color = color
            prefs.edit().putInt("crosshair_color", color).apply()
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (centerX < 0f || centerY < 0f) {
            centerX = w / 2f
            centerY = h / 2f
        }
        val nx = prefs.getFloat("crosshair_norm_x", -1f)
        val ny = prefs.getFloat("crosshair_norm_y", -1f)
        if (nx in 0f..1f && ny in 0f..1f) {
            centerX = nx * w
            centerY = ny * h
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (dragging && !movementLocked) {
                    val r = crossRadius
                    val nx = event.x.coerceIn(r, width - r)
                    val ny = event.y.coerceIn(r, height - r)
                    if (nx != centerX || ny != centerY) {
                        centerX = nx
                        centerY = ny
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                val nx = (centerX / width).coerceIn(0f, 1f)
                val ny = (centerY / height).coerceIn(0f, 1f)
                prefs.edit().putFloat("crosshair_norm_x", nx).putFloat("crosshair_norm_y", ny).apply()
            }
        }
        return true
    }
    fun setMovementLocked(locked: Boolean) {
        movementLocked = locked
    }

    override fun onDraw(canvas: Canvas) {
        val cx = if (centerX >= 0f) centerX else width / 2f
        val cy = if (centerY >= 0f) centerY else height / 2f
        val radius = crossRadius
        canvas.drawCircle(cx, cy, radius, crossPaint)
        val extend = radius + 20f
        canvas.drawLine(cx - extend, cy, cx + extend, cy, crossPaint)
        canvas.drawLine(cx, cy - extend, cx, cy + extend, crossPaint)
        if (text.isNotEmpty()) {
            val fm = textPaint.fontMetrics
            val ty = cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(text, cx + radius + dp10, ty, textPaint)
        }
        val angleText = String.format("V:%.1f°  H:%.1f°", pitchDeg, yawDeg)
        val fm2 = anglePaint.fontMetrics
        val topY = dp10 - fm2.ascent
        canvas.drawText(angleText, dp10, topY, anglePaint)
        if (compassText.isNotEmpty()) {
            val fm3 = compassPaint.fontMetrics
            val ty2 = dp10 - fm3.ascent
            val tw = compassPaint.measureText(compassText)
            canvas.drawText(compassText, width - dp10 - tw, ty2, compassPaint)
        }
    }
}
