package win.hik.tofchizi

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import win.hik.tofchizi.databinding.ActivityMainBinding
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import win.hik.tofchizi.db.AppDatabaseHelper
import win.hik.tofchizi.db.MeasurementRecord

class MainActivity : BaseActivity(), ToFSensorHelper.Listener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val cameraOpenCloseLock = Semaphore(1)
    private var cameraId: String? = null
    private var tofHelper: ToFSensorHelper? = null
    private var mode: SensorMode = SensorMode.TOF
    private var angleHelper: AngleSensorHelper? = null
    @Volatile
    private var isPaused: Boolean = false
    private var countdownSeconds: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pauseCountdownRunnable: Runnable? = null
    private var locked: Boolean = false
    private val prefs by lazy { getSharedPreferences("tof_prefs", MODE_PRIVATE) }
    private var countdownRemaining: Int = 0
    private lateinit var dbHelper: AppDatabaseHelper
    
    // Refresh Rate Control
    private var distRefreshIntervalMs: Long = 0
    private var lastDistUpdate: Long = 0
    
    // Cache current values for saving
    // Cache current values for saving
    @Volatile private var currentDistance: Int = 0
    @Volatile private var currentPitch: Float = 0f
    @Volatile private var currentYaw: Float = 0f
    @Volatile private var currentAzimuth: Float = 0f
    
    // Display Unit
    private var displayUnit: String = "mm" // mm, cm, m

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCameraIfReady() else binding.overlay.setDistanceText(getString(R.string.error_permission))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        dbHelper = AppDatabaseHelper(this)
        
        cameraManager = getSystemService(CameraManager::class.java)
        binding.textureView.surfaceTextureListener = surfaceListener
        val fromIntent = intent.getStringExtra("mode")
        mode = try { SensorMode.valueOf(fromIntent ?: "TOF") } catch (_: Exception) { SensorMode.TOF }
        val extraCandidates = intent.getStringArrayListExtra("candidates") ?: arrayListOf()
        val crosshairColor = intent.getIntExtra("crosshairColor", prefs.getInt("crosshair_color", android.graphics.Color.RED))
        binding.overlay.setCrosshairColor(crosshairColor)
        countdownSeconds = prefs.getInt("countdown_seconds", 0)
        binding.overlay.setCrosshairColor(crosshairColor)
        countdownSeconds = prefs.getInt("countdown_seconds", 0)
        distRefreshIntervalMs = prefs.getLong("refresh_interval_ms", 0L)
        displayUnit = prefs.getString("display_unit", "mm") ?: "mm"
        locked = prefs.getBoolean("locked", false)
        updateCountdownLabel()
        updatePauseLabel()
        updateLockLabel()
        binding.tvCountdown.setOnClickListener { showCountdownDialog() }
        binding.tvPause.setOnClickListener { togglePause() }
        binding.tvLock.setOnClickListener {
            locked = !locked
            updateLockLabel()
            prefs.edit().putBoolean("locked", locked).apply()
        }
        val currentCand = if (extraCandidates.isNotEmpty()) listOf(extraCandidates[0]) else null
        tofHelper = ToFSensorHelper(this, this, mode, currentCand)
        angleHelper = AngleSensorHelper(this) { p, y ->
            if (isPaused) return@AngleSensorHelper
            currentPitch = p
            currentYaw = y
            currentAzimuth = y // yaw is used as azimuth here
            runOnUiThread {
                binding.overlay.setAngles(p, y)
                binding.overlay.setCompassHeading(y)
            }
        }
        
        binding.btnDirectSave.setOnClickListener { saveRecord("") }
        binding.btnNoteSave.setOnClickListener { showNoteDialog() }
        binding.btnSettings.setOnClickListener { showRefreshRateDialog() }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (binding.textureView.isAvailable) startCameraIfReady() else binding.textureView.surfaceTextureListener = surfaceListener
        binding.overlay.setDistanceText(getString(R.string.status_initializing))
        tofHelper?.start()
        angleHelper?.start()
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        tofHelper?.stop()
        angleHelper?.stop()
        super.onPause()
    }

    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            startCameraIfReady()
        }
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return true
        }
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private fun startCameraIfReady() {
        if (cameraDevice != null) return
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        openCamera()
    }

    private fun openCamera() {
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) return
            cameraId = findBackCameraId()
            val id = cameraId ?: run {
                binding.overlay.setDistanceText(getString(R.string.error_no_camera))
                cameraOpenCloseLock.release()
                return
            }
            cameraManager.openCamera(id, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            binding.overlay.setDistanceText(getString(R.string.error_no_camera))
            cameraOpenCloseLock.release()
        }
    }

    private fun findBackCameraId(): String? {
        val ids = cameraManager.cameraIdList
        
        // Prioritize ID "0" (Standard Main Camera)
        if (ids.contains("0")) {
             val chars = cameraManager.getCameraCharacteristics("0")
             val facing = chars.get(CameraCharacteristics.LENS_FACING)
             if (facing == CameraCharacteristics.LENS_FACING_BACK) return "0"
        }
        
        for (id in ids) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return ids.firstOrNull()
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice = camera
            createPreviewSession()
        }
        override fun onDisconnected(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
        }
        override fun onError(camera: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
        }
    }

    @Suppress("DEPRECATION")
    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val texture = binding.textureView.surfaceTexture ?: return
        
        // Find best 16:9 size
        val map = cameraManager.getCameraCharacteristics(device.id)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()
        
        // 1. Try 16:9 close to 1920x1080
        var bestSize = sizes
            .filter { 
                val ratio = it.width.toFloat() / it.height.toFloat()
                Math.abs(ratio - (16.0/9.0)) < 0.05 || Math.abs(ratio - (9.0/16.0)) < 0.05
            }
            .minByOrNull { Math.abs(it.width - 1920) }
            
        // 2. Fallback: Try 4:3 close to 1920x1080 (e.g. 1440x1080)
        if (bestSize == null) {
            bestSize = sizes
                .filter { 
                    val ratio = it.width.toFloat() / it.height.toFloat()
                    Math.abs(ratio - (4.0/3.0)) < 0.05 || Math.abs(ratio - (3.0/4.0)) < 0.05
                }
                .minByOrNull { Math.abs(it.width - 1920) }
        }
        
        // 3. Last Result: Closest to 1920 regardless of ratio
        if (bestSize == null) {
            bestSize = sizes.minByOrNull { Math.abs(it.width - 1920) }
        }
        
        if (bestSize != null) {
            texture.setDefaultBufferSize(bestSize.width, bestSize.height)
        } else {
            texture.setDefaultBufferSize(binding.textureView.width, binding.textureView.height)
        }
        
        val surface = Surface(texture)
        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            previewRequestBuilder = builder
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, backgroundHandler)
        } catch (_: Exception) {}
    }

    private fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (_: Exception) {}
        cameraOpenCloseLock.release()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    override fun onDistanceMeters(value: Float) {
        if (isPaused) return
        currentDistance = value.toInt()
        
        val now = System.currentTimeMillis()
        if (now - lastDistUpdate >= distRefreshIntervalMs) {
            lastDistUpdate = now
            lastDistUpdate = now
            
            val t = when (displayUnit) {
                "cm" -> String.format("%.1f cm", currentDistance / 10f)
                "m"  -> String.format("%.3f m", currentDistance / 1000f)
                else -> String.format("%d mm", currentDistance)
            }
            // Use just the number for big display? Or with unit? 
            // Previous code used just number for mm. But User requested "formatting mm into cm".
            // Let's assume user wants to see the unit or at least the converted value. 
            // The overlay probably handles text size.
            // If I pass "12.5 cm", it might be too long? CrosshairOverlay draws simple text.
            // Let's stick to compact representation if possible, but unit is important.
            // Actually, previously it was just `String.format("%d", currentDistance)` - NO unit shown in overlay?
            // Checking CrosshairOverlay: `canvas.drawText(text, ...)`
            
            runOnUiThread {
                binding.overlay.setDistanceText(t)
            }
        }
    }

    override fun onOutOfRange() {
        if (isPaused) return
        currentDistance = -1
        runOnUiThread { binding.overlay.setDistanceText(getString(R.string.error_out_of_range)) }
    }

    override fun onInvalidData() {
        if (isPaused) return
        currentDistance = -1
        runOnUiThread { binding.overlay.setDistanceText(getString(R.string.error_invalid_data)) }
    }

    override fun onUnsupported() {
        runOnUiThread { binding.overlay.setDistanceText(getString(R.string.error_tof_unsupport)) }
    }



    private fun showRefreshRateDialog() {
        // Dialog with SeekBar for Refresh Rate + Unit Selection
        val dialogView = android.widget.LinearLayout(this)
        dialogView.orientation = android.widget.LinearLayout.VERTICAL
        dialogView.setPadding(50, 50, 50, 50)
        
        // 1. Refresh Rate
        val tvLabel = android.widget.TextView(this)
        tvLabel.text = getString(R.string.label_refresh_delay, distRefreshIntervalMs)
        dialogView.addView(tvLabel)
        
        val seekBar = android.widget.SeekBar(this)
        seekBar.max = 1000 // Max 1 second delay
        seekBar.progress = distRefreshIntervalMs.toInt()
        dialogView.addView(seekBar)
        
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                tvLabel.text = getString(R.string.label_refresh_delay, progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // 2. Unit Selection
        val tvUnitLabel = android.widget.TextView(this)
        tvUnitLabel.text = "\n" + getString(R.string.label_display_unit)
        tvUnitLabel.setPadding(0, 30, 0, 10)
        dialogView.addView(tvUnitLabel)

        val radioGroup = android.widget.RadioGroup(this)
        radioGroup.orientation = android.widget.RadioGroup.HORIZONTAL
        
        val rbMm = android.widget.RadioButton(this)
        rbMm.text = getString(R.string.unit_label_mm)
        rbMm.tag = "mm"
        
        val rbCm = android.widget.RadioButton(this)
        rbCm.text = getString(R.string.unit_label_cm)
        rbCm.tag = "cm"
        
        val rbM = android.widget.RadioButton(this)
        rbM.text = getString(R.string.unit_label_m)
        rbM.tag = "m"
        
        radioGroup.addView(rbMm)
        radioGroup.addView(rbCm)
        radioGroup.addView(rbM)
        
        when (displayUnit) {
            "cm" -> rbCm.isChecked = true
            "m" -> rbM.isChecked = true
            else -> rbMm.isChecked = true
        }
        
        dialogView.addView(radioGroup)

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_realtime_settings))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                // Save Refresh Rate
                distRefreshIntervalMs = seekBar.progress.toLong()
                prefs.edit().putLong("refresh_interval_ms", distRefreshIntervalMs).apply()
                
                // Save Unit
                val selectedId = radioGroup.checkedRadioButtonId
                if (selectedId != -1) {
                    val rb = dialogView.findViewById<android.widget.RadioButton>(selectedId)
                    displayUnit = rb.tag.toString()
                    prefs.edit().putString("display_unit", displayUnit).apply()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showCountdownDialog() {
        val edit = android.widget.EditText(this)
        edit.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        edit.hint = getString(R.string.label_seconds)
        edit.setText(countdownSeconds.toString())
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_countdown_title))
            .setView(edit)
            .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                val v = edit.text.toString().toIntOrNull() ?: 0
                countdownSeconds = v.coerceAtLeast(0)
                updateCountdownLabel()
                prefs.edit().putInt("countdown_seconds", countdownSeconds).apply()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun togglePause() {
        if (!isPaused) {
            if (countdownSeconds > 0) {
                startCountdownOverlay()
            } else {
                pauseNow()
            }
        } else {
            resumeNow()
        }
    }

    private fun pauseNow() {
        try {
            captureSession?.stopRepeating()
            captureSession?.abortCaptures()
        } catch (_: Exception) {}
        binding.countdownOverlay.visibility = android.view.View.GONE
        isPaused = true
        updatePauseLabel()
        
        val mode = prefs.getString("save_mode", "auto")
        if (mode == "manual") {
            binding.manualSaveContainer.visibility = android.view.View.VISIBLE
        } else {
            saveRecord("")
        }
    }

    private fun showNoteDialog() {
        val input = android.widget.EditText(this)
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_note_title))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                 saveRecord(input.text.toString())
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun saveRecord(note: String) {
        if (currentDistance > 0) {
            val record = MeasurementRecord(
                distance = currentDistance,
                pitch = currentPitch,
                yaw = currentYaw,
                azimuth = currentAzimuth,
                timestamp = System.currentTimeMillis(),
                note = note
            )
            // Run on background thread
            backgroundHandler?.post {
                val limit = prefs.getInt("history_limit", 200)
                dbHelper.addRecord(record)
                dbHelper.trimRecords(limit) // Auto-trim
                runOnUiThread {
                    android.widget.Toast.makeText(this, getString(R.string.msg_record_saved), android.widget.Toast.LENGTH_SHORT).show()
                    binding.manualSaveContainer.visibility = android.view.View.GONE
                }
            }
        } else {
            android.widget.Toast.makeText(this, getString(R.string.msg_invalid_data_save), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun resumeNow() {
        val builder = previewRequestBuilder ?: return
        try {
            captureSession?.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (_: Exception) {}
        binding.countdownOverlay.visibility = android.view.View.GONE
        binding.manualSaveContainer.visibility = android.view.View.GONE
        isPaused = false
        updatePauseLabel()
    }

    private fun updateCountdownLabel() {
        binding.tvCountdown.text = "⏱️ ${countdownSeconds}s"
    }
    private fun updatePauseLabel() {
        binding.tvPause.text = if (isPaused) "▶️" else "⏸︎"
    }
    private fun updateLockLabel() {
        binding.tvLock.text = if (locked) "🔒" else "🔓"
        binding.overlay.setMovementLocked(locked)
    }

    private fun startCountdownOverlay() {
        countdownRemaining = countdownSeconds
        binding.countdownOverlay.visibility = android.view.View.VISIBLE
        binding.countdownOverlay.text = countdownRemaining.toString()
        pauseCountdownRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                countdownRemaining -= 1
                if (countdownRemaining > 0) {
                    binding.countdownOverlay.text = countdownRemaining.toString()
                    mainHandler.postDelayed(this, 1000L)
                } else {
                    binding.countdownOverlay.visibility = android.view.View.GONE
                    pauseNow()
                }
            }
        }
        pauseCountdownRunnable = r
        mainHandler.postDelayed(r, 1000L)
    }
}
