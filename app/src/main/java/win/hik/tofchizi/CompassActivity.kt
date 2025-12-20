package win.hik.tofchizi

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle

import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class CompassActivity : BaseActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    
    private lateinit var compassView: CompassView
    private lateinit var tvDirection: TextView
    private lateinit var tvField: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compass)

        compassView = findViewById(R.id.compassView)
        tvDirection = findViewById(R.id.tvDirection)
        tvField = findViewById(R.id.tvMagneticField)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private val ALPHA = 0.1f // Smoother filter
    private var lastUpdateTimestamp: Long = 0
    private val UPDATE_INTERVAL_MS = 200 // Update text 5 times per second

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            gravity = lowPass(event.values.clone(), gravity)
        }
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = lowPass(event.values.clone(), geomagnetic)
            
            // Only update field strength text occasionally or it flickers too much? 
            // Actually usually field strength is stable. We can leave it or throttle it too.
            // Let's filter it slightly for display or just compute from smoothed vector.
        }

        if (gravity != null && geomagnetic != null) {
            val rotationMatrix = FloatArray(9)
            val I = FloatArray(9)
            if (SensorManager.getRotationMatrix(rotationMatrix, I, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                
                // Azimuth in radians
                var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                // azimuth is -180 to 180. Normalize to 0-360
                azimuth = (azimuth + 360) % 360

                // Rotate dial (View handles its own smooth animation, pass unfiltered or filtered?
                // Passing filtered value to View is better so the target doesn't jump around.)
                compassView.setAzimuth(azimuth)

            // Throttle Text Update
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTimestamp > UPDATE_INTERVAL_MS) {
                    val directionText = getDirectionText(azimuth)
                    tvDirection.text = "${azimuth.roundToInt()}° $directionText"
                    
                    // Update field strength based on smoothed vector
                    val fieldStrength = kotlin.math.sqrt(
                        (geomagnetic!![0] * geomagnetic!![0] + 
                         geomagnetic!![1] * geomagnetic!![1] + 
                         geomagnetic!![2] * geomagnetic!![2]).toDouble()
                    )
                    tvField.text = getString(R.string.compass_field_fmt, fieldStrength.roundToInt())

                    lastUpdateTimestamp = currentTime
                }
            }
        }
    }

    private fun lowPass(input: FloatArray, output: FloatArray?): FloatArray {
        if (output == null) return input
        for (i in input.indices) {
            output[i] = output[i] + ALPHA * (input[i] - output[i])
        }
        return output
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun getDirectionText(azimuth: Float): String {
        return when {
            azimuth >= 337.5 || azimuth < 22.5 -> getString(R.string.compass_n)
            azimuth >= 22.5 && azimuth < 67.5 -> getString(R.string.compass_ne)
            azimuth >= 67.5 && azimuth < 112.5 -> getString(R.string.compass_e)
            azimuth >= 112.5 && azimuth < 157.5 -> getString(R.string.compass_se)
            azimuth >= 157.5 && azimuth < 202.5 -> getString(R.string.compass_s)
            azimuth >= 202.5 && azimuth < 247.5 -> getString(R.string.compass_sw)
            azimuth >= 247.5 && azimuth < 292.5 -> getString(R.string.compass_w)
            azimuth >= 292.5 && azimuth < 337.5 -> getString(R.string.compass_nw)
            else -> ""
        }
    }
}
