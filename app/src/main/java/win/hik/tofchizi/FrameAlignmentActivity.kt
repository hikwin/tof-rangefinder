package win.hik.tofchizi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class FrameAlignmentActivity : BaseActivity(), SensorEventListener {

    private lateinit var textureView: TextureView
    private lateinit var frameView: FrameAlignmentView
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, getString(R.string.frame_toast_permission), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_frame_alignment)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        textureView = findViewById(R.id.textureView)
        frameView = findViewById(R.id.frameView)
        cameraManager = getSystemService(CameraManager::class.java)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // Always register Accelerometer for gravity vector calculation
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                checkPermissionAndStart()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCamera() {
        if (cameraDevice != null) return
        try {
            // Prefer camera ID "0" (usually main back camera), otherwise first back-facing found
            val list = cameraManager.cameraIdList
            var selectedId = list.firstOrNull()
            
            // Try to find ID "0" first
            if (list.contains("0")) {
                 val chars = cameraManager.getCameraCharacteristics("0")
                 val facing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                 if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                     selectedId = "0"
                 }
            }
            
            val cameraId = selectedId ?: return
            
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION")
    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val surfaceTexture = textureView.surfaceTexture ?: return
        
        // Find best 16:9 size
        val map = cameraManager.getCameraCharacteristics(device.id)
            .get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()
        
        // 1. Try 16:9 close to 1920x1080
        var bestSize = sizes
            .filter { 
                val ratio = it.width.toFloat() / it.height.toFloat()
                Math.abs(ratio - (16.0/9.0)) < 0.05 || Math.abs(ratio - (9.0/16.0)) < 0.05 
            }
            .minByOrNull { Math.abs(it.width - 1920) }
            
        // 2. Fallback: Try 4:3 close to 1920x1080
        if (bestSize == null) {
            bestSize = sizes
                .filter { 
                    val ratio = it.width.toFloat() / it.height.toFloat()
                    Math.abs(ratio - (4.0/3.0)) < 0.05 || Math.abs(ratio - (3.0/4.0)) < 0.05 
                }
                .minByOrNull { Math.abs(it.width - 1920) }
        }

        // 3. Fallback: Closest to 1920
        if (bestSize == null) {
            bestSize = sizes.minByOrNull { Math.abs(it.width - 1920) }
        }
        
        if (bestSize != null) {
            surfaceTexture.setDefaultBufferSize(bestSize.width, bestSize.height)
        } else {
            surfaceTexture.setDefaultBufferSize(textureView.width, textureView.height)
        }
        
        val surface = Surface(surfaceTexture)

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)
            
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        session.setRepeatingRequest(builder.build(), null, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
        sensorManager.unregisterListener(this)
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            processOrientation()
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, event.values.size)
            updateOrientationAuth()
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
            updateOrientationAuth()
        }
    }
    
    private fun updateOrientationAuth() {
        if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
            processOrientation()
        }
    }
    
    private fun processOrientation() {
        // Use gravity vector to calculate rotation angle
        val ax = gravity[0]
        val ay = gravity[1]
        
        // Calculate angle from Upright (-Y axis standard).
        // atan2(y, x). Upright (y=9.8, x=0) -> 90 deg.
        // We subtract 90 to make Upright = 0.
        // CW 90 (Right Down): x=-9.8, y=0. atan2(0, -9.8) = 180. 180-90 = 90. Correct.
        // CCW 90 (Left Down): x=9.8, y=0. atan2(0, 9.8) = 0. 0-90 = -90. Correct.
        
        val angleRad = Math.atan2(ay.toDouble(), ax.toDouble())
        val angleDeg = Math.toDegrees(angleRad).toFloat() - 90f
        
        frameView.roll = angleDeg
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
