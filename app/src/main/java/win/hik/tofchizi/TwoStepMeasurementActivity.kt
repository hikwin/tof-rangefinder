package win.hik.tofchizi

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.tan
import kotlin.math.PI

class TwoStepMeasurementActivity : BaseActivity(), SensorEventListener {

    private lateinit var textureView: TextureView
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val cameraOpenCloseLock = Semaphore(1)

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // UI Elements
    private lateinit var etCameraHeight: EditText
    private lateinit var tvInstruction: TextView
    private lateinit var btnAction: Button
    private lateinit var btnReset: TextView
    private lateinit var tvResultDistance: TextView
    private lateinit var tvResultHeight: TextView
    private lateinit var tvAngleDebug: TextView
    
    // Crosshair Views
    private lateinit var ivCrosshair: ImageView
    private lateinit var lineHorizontal: View
    private lateinit var lineVertical: View
    
    // Settings State
    private var crosshairStyle = 0 // 0: Icon, 1: Lines, 2: Both
    private var guideLineColor = 0 // 0: White(Default), 1: Red, 2: Green, 3: Blue, 4: Yellow, 5: Cyan

    // State
    private enum class State {
        WAITING_BOTTOM,
        WAITING_TOP,
        RESULT
    }
    private var currentState = State.WAITING_BOTTOM
    private var currentPitch: Double = 0.0 // Degrees
    private var calculatedDistance: Double = 0.0
    private var calculatedHeight: Double = 0.0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCameraIfReady() else Toast.makeText(this, "需要相机权限", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_two_step_measurement)

        textureView = findViewById(R.id.viewFinder)
        etCameraHeight = findViewById(R.id.etCameraHeight)
        tvInstruction = findViewById(R.id.tvInstruction)
        btnAction = findViewById(R.id.btnAction)
        btnReset = findViewById(R.id.btnReset)
        tvResultDistance = findViewById(R.id.tvResultDistance)
        tvResultHeight = findViewById(R.id.tvResultHeight)
        tvAngleDebug = findViewById(R.id.tvAngleDebug)
        
        ivCrosshair = findViewById(R.id.ivCrosshair)
        lineHorizontal = findViewById(R.id.lineHorizontal)
        lineVertical = findViewById(R.id.lineVertical)
        
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        cameraManager = getSystemService(CameraManager::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationSensor == null) {
            Toast.makeText(this, "设备不支持旋转矢量传感器", Toast.LENGTH_LONG).show()
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                startCameraIfReady()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        btnAction.setOnClickListener { handleAction() }
        btnReset.setOnClickListener { resetState() }
        findViewById<android.view.View>(R.id.btnEyeHeightHelper).setOnClickListener { showEyeHeightDialog() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }
        
        val prefs = getSharedPreferences("tof_prefs", MODE_PRIVATE)
        val savedH = prefs.getFloat("saved_camera_height", 150f)
        etCameraHeight.setText(String.format("%.1f", savedH))
        
        crosshairStyle = prefs.getInt("key_crosshair_style", 0)
        guideLineColor = prefs.getInt("key_guide_line_color", 0)
        applySettings()

        resetState()
    }
    
    private fun startCameraIfReady() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        startBackgroundThread()
        if (textureView.isAvailable) {
            openCamera()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) return
            val cameraId = findBackCameraId() ?: return
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
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
            }, backgroundHandler)
        } catch (e: Exception) {
            cameraOpenCloseLock.release()
        }
    }

    private fun findBackCameraId(): String? {
        // Reuse logic roughly from MainActivity
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) { null }
    }

    private fun createPreviewSession() {
        try {
            val texture = textureView.surfaceTexture ?: return
            texture.setDefaultBufferSize(1920, 1080) // Simplified, usually should pick best size
            val surface = Surface(texture)
            val requestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) ?: return
            requestBuilder.addTarget(surface)
            
            // Ensure background handler is ready
            val bgHandler = backgroundHandler ?: return
            
            val outputConfig = android.hardware.camera2.params.OutputConfiguration(surface)
            val executor = java.util.concurrent.Executor { command -> bgHandler.post(command) }
            
            val sessionConfig = android.hardware.camera2.params.SessionConfiguration(
                android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                listOf(outputConfig),
                executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            session.setRepeatingRequest(requestBuilder.build(), null, bgHandler)
                        } catch (_: Exception) {}
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }
            )
            
            cameraDevice?.createCaptureSession(sessionConfig)
        } catch (_: Exception) {}
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBg").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun resetState() {
        currentState = State.WAITING_BOTTOM
        calculatedDistance = 0.0
        calculatedHeight = 0.0
        tvInstruction.text = getString(R.string.step_1_instruction)
        btnAction.text = getString(R.string.btn_record_bottom)
        btnAction.isEnabled = true
        tvResultDistance.text = "-- m"
        tvResultHeight.text = "-- m"
        tvResultDistance.setTextColor(0xFFCCCCCC.toInt())
        tvResultHeight.setTextColor(0xFFCCCCCC.toInt())
        btnReset.visibility = View.INVISIBLE
        btnAction.visibility = View.VISIBLE
    }

    private fun handleAction() {
        val hStr = etCameraHeight.text.toString()
        val hCm = hStr.toDoubleOrNull() ?: 150.0
        
        // Auto-save height
        getSharedPreferences("tof_prefs", MODE_PRIVATE).edit().putFloat("saved_camera_height", hCm.toFloat()).apply()
        
        val hM = hCm / 100.0 // Height in meters

        when (currentState) {
            State.WAITING_BOTTOM -> {
                // Step 1: Record Bottom
                // User should be looking down (negative pitch)
                // Pitch is angle from horizon. 
                // Distance = H / tan(|pitch|)
                
                val pitchRad = Math.toRadians(currentPitch)
                
                if (currentPitch >= 0) {
                     Toast.makeText(this, "请对准地面上的物体底部 (需俯视)", Toast.LENGTH_SHORT).show()
                     return
                }
                
                // Calculate horizontal distance
                // tan(theta_depression) = H / D  => D = H / tan(theta_depression)
                val depression = abs(pitchRad)
                if (depression < 0.01) {
                    Toast.makeText(this, "角度过小，无法计算", Toast.LENGTH_SHORT).show()
                    return
                }

                calculatedDistance = hM / tan(depression)
                
                // Update UI
                tvResultDistance.text = String.format("%.2f m", calculatedDistance)
                tvResultDistance.setTextColor(0xFFFFFFFF.toInt())
                
                // Next State
                currentState = State.WAITING_TOP
                tvInstruction.text = getString(R.string.step_2_instruction)
                btnAction.text = getString(R.string.btn_record_top)
            }
            State.WAITING_TOP -> {
                // Step 2: Record Top
                // Height = H_cam + D * tan(pitch)
                // If pitch is positive (looking up), tan(pitch) > 0, adds to camera height.
                // If pitch is negative (looking down but higher than bottom), tan(pitch) < 0, subtracts.
                // This formula holds true for coordinate system where Up is positive pitch?
                // Wait, my pitch definition: Looking UP is positive.
                // Let's verify.
                
                val pitchRad = Math.toRadians(currentPitch)
                val deltaH = calculatedDistance * tan(pitchRad)
                val objHeight = hM + deltaH
                
                calculatedHeight = abs(objHeight) // Should be positive
                
                tvResultHeight.text = String.format("%.2f m", calculatedHeight)
                tvResultHeight.setTextColor(0xFFFFFFFF.toInt())
                
                currentState = State.RESULT
                tvInstruction.text = getString(R.string.step_done_instruction)
                btnAction.visibility = View.GONE
                btnReset.visibility = View.VISIBLE
            }
            State.RESULT -> {
                // Done
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (rotationSensor != null) {
            val success = sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            if (!success) Toast.makeText(this, "传感器初始化失败", Toast.LENGTH_SHORT).show()
        }
        if (textureView.isAvailable) startCameraIfReady()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        closeCamera()
        try {
            backgroundThread?.quitSafely()
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (_: Exception) {}
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            
            // Calculate Pitch using the Z-component of the Device's Z-axis mapped to World coordinates.
            // R[8] represents the dot product of Device Z and World Z.
            // Device Z points out of the screen. Camera points opposite (-Z).
            // Angle of Camera vector relative to Horizon = arcsin(-R[8]) (in degrees)
            // Range: -90 (Down) to +90 (Up).
            
            // Note: rotationMatrix is a flattened 3x3 matrix.
            // Index 8 is element (2,2) 
            
            val axisZ_dot_worldZ = rotationMatrix[8]
            val pitchDeg = Math.toDegrees(Math.asin(-axisZ_dot_worldZ.toDouble()))
            
            currentPitch = pitchDeg
            
            runOnUiThread {
                tvAngleDebug.text = String.format("%.1f°", pitchDeg)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}


    private fun showEyeHeightDialog() {
        val view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_eye_height_calculator, null)
        val rgGender = view.findViewById<android.widget.RadioGroup>(R.id.rgGender)
        val etPersonHeight = view.findViewById<android.widget.EditText>(R.id.etPersonHeight)
        val rgShoe = view.findViewById<android.widget.RadioGroup>(R.id.rgShoe)
        val etShoeHeight = view.findViewById<android.widget.EditText>(R.id.etShoeHeight)
        val btnCalc = view.findViewById<android.widget.Button>(R.id.btnCalc)
        val tvCalcResult = view.findViewById<android.widget.TextView>(R.id.tvCalcResult)
        
        rgShoe.setOnCheckedChangeListener { _, checkedId ->
            etShoeHeight.visibility = if (checkedId == R.id.rbShoeCustom) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        var calculatedEyeHeightCm = 0.0
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.eye_calc_title)
            .setView(view)
            .setNegativeButton(R.string.btn_close, null)
            .setPositiveButton(R.string.btn_quick_input, null) // Override later to prevent closing if invalid
            .create()
            
        dialog.show()
        
        // Handle Calc
        btnCalc.setOnClickListener {
            val hStr = etPersonHeight.text.toString()
            val h = hStr.toDoubleOrNull()
            if (h == null) {
                android.widget.Toast.makeText(this, getString(R.string.two_step_msg_height_invalid), android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val isMale = (rgGender.checkedRadioButtonId == R.id.rbMale)
            val coeff = if (isMale) 0.936 else 0.926
            
            var shoeMm = 36.0
            if (rgShoe.checkedRadioButtonId == R.id.rbShoeCustom) {
                val s = etShoeHeight.text.toString().toDoubleOrNull()
                if (s != null) shoeMm = s
            }
            
            // h_eye_mm = coeff * H_mm + shoe_mm
            // Input H is cm.
            val eyeMm = (coeff * h * 10.0) + shoeMm
            val eyeCm = eyeMm / 10.0
            calculatedEyeHeightCm = eyeCm
            
            tvCalcResult.text = String.format(getString(R.string.calc_result_format), eyeCm)
        }
        
        // Handle Quick Input Button
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (calculatedEyeHeightCm > 0) {
                etCameraHeight.setText(String.format("%.1f", calculatedEyeHeightCm))
                getSharedPreferences("tof_prefs", MODE_PRIVATE).edit().putFloat("saved_camera_height", calculatedEyeHeightCm.toFloat()).apply()
                dialog.dismiss()
                android.widget.Toast.makeText(this, getString(R.string.two_step_msg_height_set), android.widget.Toast.LENGTH_LONG).show()
            } else {
                // Try to calc just in case user forgot to click calc
                btnCalc.performClick()
                if (calculatedEyeHeightCm > 0) {
                    etCameraHeight.setText(String.format("%.1f", calculatedEyeHeightCm))
                    getSharedPreferences("tof_prefs", MODE_PRIVATE).edit().putFloat("saved_camera_height", calculatedEyeHeightCm.toFloat()).apply()
                    dialog.dismiss()
                    android.widget.Toast.makeText(this, getString(R.string.two_step_msg_height_set), android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(this, getString(R.string.two_step_msg_calc_first), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_two_step_settings, null)
        val rgStyle = view.findViewById<android.widget.RadioGroup>(R.id.rgCrosshairStyle)
        val layoutColors = view.findViewById<android.widget.LinearLayout>(R.id.layoutColors)
        
        // Init Style
        when (crosshairStyle) {
            0 -> rgStyle.check(R.id.rbStyleIcon)
            1 -> rgStyle.check(R.id.rbStyleLines)
            2 -> rgStyle.check(R.id.rbStyleBoth)
            3 -> rgStyle.check(R.id.rbStyleSniper)
        }
        
        rgStyle.setOnCheckedChangeListener { _, checkedId ->
            crosshairStyle = when (checkedId) {
                R.id.rbStyleIcon -> 0
                R.id.rbStyleLines -> 1
                R.id.rbStyleBoth -> 2
                R.id.rbStyleSniper -> 3
                else -> 0
            }
            applySettings()
            getSharedPreferences("tof_prefs", MODE_PRIVATE).edit().putInt("key_crosshair_style", crosshairStyle).apply()
        }
        
        // Init Colors Click Listeners
        for (i in 0 until layoutColors.childCount) {
            val child = layoutColors.getChildAt(i)
            child.setOnClickListener {
                val tag = it.tag.toString().toIntOrNull() ?: 0
                guideLineColor = tag
                applySettings()
                getSharedPreferences("tof_prefs", MODE_PRIVATE).edit().putInt("key_guide_line_color", guideLineColor).apply()
                // Visual feedback could be added here (e.g., border)
                Toast.makeText(this, "颜色已更新", Toast.LENGTH_SHORT).show()
            }
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.title_settings_crosshair)
            .setView(view)
            .setPositiveButton("完成", null)
            .show()
    }
    
    private fun applySettings() {
        // Reset all visibilities
        ivCrosshair.visibility = View.INVISIBLE
        lineHorizontal.visibility = View.INVISIBLE
        lineVertical.visibility = View.INVISIBLE
        val ivSniper = findViewById<ImageView>(R.id.ivSniperScope)
        if (ivSniper != null) ivSniper.visibility = View.INVISIBLE

        // Apply Crosshair Style
        when (crosshairStyle) {
            0 -> { // Icon Only
                ivCrosshair.visibility = View.VISIBLE
            }
            1 -> { // Lines Only
                lineHorizontal.visibility = View.VISIBLE
                lineVertical.visibility = View.VISIBLE
            }
            2 -> { // Both
                ivCrosshair.visibility = View.VISIBLE
                lineHorizontal.visibility = View.VISIBLE
                lineVertical.visibility = View.VISIBLE
            }
            3 -> { // Sniper Scope
                if (ivSniper != null) ivSniper.visibility = View.VISIBLE
            }
        }
        
        // Apply Color
        // 0: White, 1: Red, 2: Green, 3: Blue, 4: Yellow, 5: Cyan
        // Use approx 75% alpha (0xC0) for lines, distinct color for scope
        val color = when (guideLineColor) {
            1 -> 0xC0FF0000.toInt() // Red
            2 -> 0xC000FF00.toInt() // Green
            3 -> 0xC00000FF.toInt() // Blue
            4 -> 0xC0FFFF00.toInt() // Yellow
            5 -> 0xC000FFFF.toInt() // Cyan
            else -> 0xC0FFFFFF.toInt() // White
        }
        
        lineHorizontal.setBackgroundColor(color)
        lineVertical.setBackgroundColor(color)
        if (ivSniper != null) {
            // Apply tint to the sniper scope drawable
            // Since the drawable is white, tinting it works perfectly.
            ivSniper.setColorFilter(color)
             // Also need to set alpha potentially if not handled by color?
            // The color includes alpha 0xC0, so it should apply transparency too.
        }
    }
}
