package win.hik.tofchizi

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
 

class AngleSensorHelper(private val context: Context, private val listener: Listener) : SensorEventListener {
    fun interface Listener {
        fun onAngles(pitchDeg: Float, yawDeg: Float)
    }
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var rotation: Sensor? = null
    private var lastEmitNs = 0L

    fun start() {
        rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val r = rotation ?: return
        sm.registerListener(this, r, SensorManager.SENSOR_DELAY_GAME)
        lastEmitNs = 0L
    }

    fun stop() {
        sm.unregisterListener(this)
        rotation = null
        lastEmitNs = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val R = FloatArray(9)

        try {
            SensorManager.getRotationMatrixFromVector(R, event.values)
        } catch (_: Exception) {
            return
        }
        val orientation = FloatArray(3)
        SensorManager.getOrientation(R, orientation)
        val yaw = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val now = System.nanoTime()
        if (now - lastEmitNs >= 100_000_000L) {
            lastEmitNs = now
            listener.onAngles(pitch, yaw)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
