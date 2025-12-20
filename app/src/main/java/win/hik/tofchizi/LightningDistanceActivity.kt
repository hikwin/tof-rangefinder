package win.hik.tofchizi

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.log10

class LightningDistanceActivity : BaseActivity(), SensorEventListener {

    private lateinit var tvLightValue: TextView
    private lateinit var graphLight: RealtimeGraphView
    private lateinit var tvSoundValue: TextView
    private lateinit var graphSound: RealtimeGraphView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var layoutResult: View
    private lateinit var tvResultDistance: TextView
    private lateinit var tvResultTime: TextView
    private lateinit var tvNoiseLevel: TextView

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    
    // Data storage
    data class DataPoint(val timestamp: Long, val value: Float)
    private val lightData = CopyOnWriteArrayList<DataPoint>()
    private val soundData = CopyOnWriteArrayList<DataPoint>()

    // Audio
    private var audioRecord: AudioRecord? = null
    private var bufferSize = 0
    private var audioThread: Thread? = null

    // Polling for graph updates
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                updateUI()
                handler.postDelayed(this, 50) // 20Hz refresh
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startMonitoring()
        } else {
            Toast.makeText(this, R.string.error_no_mic_permission, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lightning_distance)

        // Bind Views
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        tvLightValue = findViewById(R.id.tvLightValue)
        graphLight = findViewById(R.id.graphLight)
        tvSoundValue = findViewById(R.id.tvSoundValue)
        graphSound = findViewById(R.id.graphSound)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        layoutResult = findViewById(R.id.layoutResult)
        tvResultDistance = findViewById(R.id.tvResultDistance)
        tvResultTime = findViewById(R.id.tvResultTime)
        tvNoiseLevel = findViewById(R.id.tvNoiseLevel)

        findViewById<View>(R.id.btnHelp).setOnClickListener {
            showHelpDialog()
        }

        // Resolve colors based on theme
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val lightColor = if (isNightMode) Color.YELLOW else Color.parseColor("#FFD700") // Darker yellow for light mode? Or just Gold
        val soundColor = if (isNightMode) Color.CYAN else Color.BLUE

        graphLight.lineColor = lightColor
        graphSound.lineColor = soundColor

        sensorManager = getSystemService(SensorManager::class.java)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        btnStart.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startMonitoring()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        btnStop.setOnClickListener {
            stopMonitoring()
        }
    }

    private fun startMonitoring() {
        isMonitoring = true
        lightData.clear()
        soundData.clear()
        graphLight.clear()
        graphSound.clear()
        layoutResult.visibility = View.INVISIBLE

        btnStart.isEnabled = false
        btnStop.isEnabled = true
        btnStart.setBackgroundColor(Color.GRAY)
        btnStop.setBackgroundColor(0xFFFF4444.toInt()) // Red

        // Start Light Sensor
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Start Audio Recording
        startAudioRecording()

        // Start UI updates
        handler.post(updateRunnable)
        
        Toast.makeText(this, R.string.msg_monitoring_started, Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        isMonitoring = false
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        btnStart.setBackgroundColor(getColor(R.color.brand_primary))
        btnStop.setBackgroundColor(Color.GRAY)

        sensorManager.unregisterListener(this)
        stopAudioRecording()
        handler.removeCallbacks(updateRunnable)

        calculateResult()
        
        Toast.makeText(this, R.string.msg_monitoring_stopped, Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        // Just refresh graphs if needed, or graphs refresh themselves when data added?
        // Actually, we add data in background threads (sensor, audio), so we should invalidate graphs here if they don't auto-invalidate?
        // My GraphView invalidates on addDataPoint.
        // We are adding data in sensor callback (main thread usually) and audio thread (bg).
        // UI updates for TextViews should happen here or in callbacks.
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (isMonitoring && event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            val timestamp = System.currentTimeMillis()
            lightData.add(DataPoint(timestamp, lux))
            
            graphLight.addDataPoint(lux)
            tvLightValue.text = String.format("%.1f Lux", lux)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @SuppressLint("MissingPermission")
    private fun startAudioRecording() {
        val sampleRate = 44100
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) return

        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)

        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        audioThread = Thread {
            val buffer = ShortArray(bufferSize)
            while (isMonitoring) {
                val readResult = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (readResult > 0) {
                    // Calculate Amplitude / dB
                    // Root Mean Square? Or Peak?
                    // Peak is simpler.
                    var maxAmplitude = 0
                    for (i in 0 until readResult) {
                        val absVal = abs(buffer[i].toInt())
                        if (absVal > maxAmplitude) maxAmplitude = absVal
                    }
                    
                    // Simple dB calculation (approximate)
                    // Reference max for 16bit is 32767
                    // dB = 20 * log10(amp / ref)? No, usually relative to 1 or something.
                    // For display, raw amplitude is fine, or simple SPL.
                    // Let's use 20 * log10(maxAmplitude).
                    
                    val db = if (maxAmplitude > 0) 20 * log10(maxAmplitude.toDouble()) else 0.0
                    val finalDb = if (db < 0) 0.0 else db
                    
                    val timestamp = System.currentTimeMillis()
                    val floatDb = finalDb.toFloat()
                    
                    soundData.add(DataPoint(timestamp, floatDb))
                    
                    runOnUiThread {
                        if (isMonitoring) {
                            graphSound.addDataPoint(floatDb)
                            tvSoundValue.text = String.format("%.1f dB", floatDb)
                            updateNoiseLevel(floatDb)
                        }
                    }
                    
                    // Sleep a tiny bit to not flood?
                    // AudioRecord read blocks until buffer filled usually.
                    // 44100Hz, bufferSize maybe 4000 samples -> ~100ms.
                }
            }
        }.apply { start() }
    }

    private fun stopAudioRecording() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            audioThread = null // loop condition checked by isMonitoring
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calculateResult() {
        if (lightData.isEmpty() || soundData.isEmpty()) {
            runOnUiThread {
                Toast.makeText(this, "数据不足，请重试", Toast.LENGTH_SHORT).show() 
            }
            return
        }

        // Find max peaks
        val maxLight = lightData.maxByOrNull { it.value }
        val maxSound = soundData.maxByOrNull { it.value }
        
        // Basic threshold check (optional, but good to avoid noise)
        // For now, just check if we have data. User said "no value", so maybe max was null?
        
        if (maxLight == null || maxSound == null) {
             runOnUiThread {
                Toast.makeText(this, R.string.error_calc_failed, Toast.LENGTH_SHORT).show()
            }
            return
        }

        val tLight = maxLight.timestamp
        val tSound = maxSound.timestamp
        val deltaMs = abs(tSound - tLight)
        
        // V_sound ~ 340 m/s = 0.34 m/ms
        val distanceM = 0.34 * deltaMs
        
        runOnUiThread {
            tvResultDistance.text = String.format("%.1f m", distanceM)
            tvResultTime.text = String.format("时间间隔：%.2f s (%d ms)", deltaMs / 1000.0, deltaMs)
            
            // Ensure visibility is set on UI thread
            layoutResult.visibility = View.VISIBLE
            
            // Scroll to bottom to show result
            val scrollView = findViewById<android.widget.ScrollView>(R.id.scrollView) // Need ID in xml
            scrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    private fun showHelpDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.lightning_help_title)
            .setMessage(R.string.lightning_help_content)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun updateNoiseLevel(db: Float) {
        val levelText = when {
            db < 20 -> getString(R.string.noise_level_0)
            db < 40 -> getString(R.string.noise_level_1)
            db < 60 -> getString(R.string.noise_level_2)
            db < 80 -> getString(R.string.noise_level_3)
            db < 100 -> getString(R.string.noise_level_4)
            else -> getString(R.string.noise_level_5)
        }
        tvNoiseLevel.text = levelText
    }

    override fun onPause() {
        super.onPause()
        if (isMonitoring) stopMonitoring()
    }
}
