package win.hik.tofchizi

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AirtightnessActivity : BaseActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null
    private var isMonitoring = false

    private lateinit var graph: RealtimeGraphView
    private lateinit var btnStartStop: Button
    private lateinit var tvCurrent: TextView
    private lateinit var tvMax: TextView
    private lateinit var tvMin: TextView
    private lateinit var tvRange: TextView

    private var maxVal = Float.MIN_VALUE
    private var minVal = Float.MAX_VALUE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_airtightness)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        if (pressureSensor == null) {
            Toast.makeText(this, R.string.status_no_sensor, Toast.LENGTH_LONG).show()
        }

        graph = findViewById(R.id.pressureGraph)
        graph.showYAxis = true
        graph.lockToMaxRange = true
        
        btnStartStop = findViewById(R.id.btnStartStop)
        tvCurrent = findViewById(R.id.tvCurrent)
        tvMax = findViewById(R.id.tvMax)
        tvMin = findViewById(R.id.tvMin)
        tvRange = findViewById(R.id.tvRange)

        // Init text
        updateStats(0f, reset=true)

        btnStartStop.setOnClickListener {
            if (isMonitoring) {
                stopMonitoring()
            } else {
                startMonitoring()
            }
        }
    }

    private fun startMonitoring() {
        if (pressureSensor == null) {
            Toast.makeText(this, R.string.status_no_sensor, Toast.LENGTH_SHORT).show()
            return
        }
        isMonitoring = true
        btnStartStop.text = getString(R.string.airtight_stop)
        
        // When starting, allow continuing or start fresh?
        // User said: "Click Stop button then clear all"
        // This implies Start just starts (recording). Logic is simpler if we just start.
        
        sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI)
    }

    private fun stopMonitoring() {
        isMonitoring = false
        btnStartStop.text = getString(R.string.airtight_start)
        sensorManager.unregisterListener(this)
        
        // "Click Stop button then clear all"
        graph.clear()
        maxVal = Float.MIN_VALUE
        minVal = Float.MAX_VALUE
        updateStats(0f, reset=true)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0]
            
            // Update Graph
            graph.addDataPoint(pressure)
            
            // Update Stats
            if (pressure > maxVal) maxVal = pressure
            if (pressure < minVal) minVal = pressure
            
            updateStats(pressure)
        }
    }

    private fun updateStats(current: Float, reset: Boolean = false) {
        if (reset) {
            val empty = "--"
            tvCurrent.text = getString(R.string.airtight_current, 0f).replace("0.00", empty)
            tvMax.text = getString(R.string.airtight_max, 0f).replace("0.00", empty)
            tvMin.text = getString(R.string.airtight_min, 0f).replace("0.00", empty)
            tvRange.text = getString(R.string.airtight_range, 0f).replace("0.00", empty)
        } else {
            tvCurrent.text = getString(R.string.airtight_current, current)
            tvMax.text = getString(R.string.airtight_max, maxVal)
            tvMin.text = getString(R.string.airtight_min, minVal)
            tvRange.text = getString(R.string.airtight_range, maxVal - minVal)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
    
    override fun onPause() {
        super.onPause()
        if (isMonitoring) {
            sensorManager.unregisterListener(this)
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (isMonitoring && pressureSensor != null) {
             sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }
}
