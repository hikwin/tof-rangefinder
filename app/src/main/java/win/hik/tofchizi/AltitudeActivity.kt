package win.hik.tofchizi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.pow

class AltitudeActivity : BaseActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var barometer: Sensor? = null
    private lateinit var locationManager: LocationManager
    
    private lateinit var tvBarometerAlt: TextView
    private lateinit var tvPressure: TextView
    private lateinit var tvGpsAlt: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvBoilingPoint: TextView
    private lateinit var btnGrantGps: Button

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            btnGrantGps.visibility = View.GONE
            startGpsUpdates()
        } else {
            Toast.makeText(this, getString(R.string.alt_toast_permission), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_altitude)

        tvBarometerAlt = findViewById(R.id.tvBarometerAltitude)
        tvPressure = findViewById(R.id.tvPressure)
        tvGpsAlt = findViewById(R.id.tvGpsAltitude)
        tvLocation = findViewById(R.id.tvLocation)
        tvBoilingPoint = findViewById(R.id.tvBoilingPoint)
        btnGrantGps = findViewById(R.id.btnGrantGps)
        
        // Removed deleted ivGpsIcon view finding and setting

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        if (barometer == null) {
            tvBarometerAlt.text = getString(R.string.status_no_sensor)
            tvPressure.text = getString(R.string.status_not_supported)
        }

        btnGrantGps.setOnClickListener {
            checkAndRequestGps()
        }
        
        checkAndRequestGps()
    }

    private fun checkAndRequestGps() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            btnGrantGps.visibility = View.GONE
            startGpsUpdates()
        } else {
            btnGrantGps.visibility = View.VISIBLE
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startGpsUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        // Request updates from both GPS and NetworkProvider for best results
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                5f,
                gpsLocationListener
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                2000L,
                5f,
                gpsLocationListener
            )
            
            val lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastLoc != null) {
                updateGpsUI(lastLoc)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_gps, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private val gpsLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            updateGpsUI(location)
        }
        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }
    
    private fun updateGpsUI(location: Location) {
        val alt = location.altitude
        tvGpsAlt.text = String.format(Locale.US, "%.1f m", alt)
        tvLocation.text = String.format(
            Locale.US, 
            "Lat: %.4f\nLon: %.4f", 
            location.latitude, 
            location.longitude
        )
        
        // If barometer is missing, estimate pressure from GPS altitude and calculate boiling point
        if (barometer == null) {
            // Standard Atmosphere Model: P = P0 * (1 - 2.25577e-5 * h)^5.25588
            // P0 = 1013.25 hPa
            val estimatedPressure = 1013.25 * (1 - 2.25577e-5 * alt).pow(5.25588)
            updateBoilingPointFromPressure(estimatedPressure)
        }
    }

    override fun onResume() {
        super.onResume()
        barometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(gpsLocationListener)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0]
            tvPressure.text = String.format(Locale.US, "%.1f hPa", pressure)
            
            // Calculate Altitude from Pressure
            // Standard generic formula: h = 44330 * (1 - (p/p0)^(1/5.255))
            val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
            tvBarometerAlt.text = String.format(Locale.US, "%.1f m", altitude)
            
            // Use Direct Pressure for Boiling Point (Most Accurate)
            updateBoilingPointFromPressure(pressure.toDouble())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
    
    private fun updateBoilingPointFromPressure(pressureHpa: Double) {
        // Using Antoine Equation for Water: T = B / (A - log10(P)) - C
        // A=8.07131, B=1730.63, C=233.426 (for P in mmHg, T in Celsius)
        // 1 hPa = 0.75006156130264 mmHg
        
        val pressureMmHg = pressureHpa * 0.75006156130264
        
        // Avoid log of negative or zero
        if (pressureMmHg > 0.1) {
            val logP = kotlin.math.log10(pressureMmHg)
            val tempC = (1730.63 / (8.07131 - logP)) - 233.426
            
            tvBoilingPoint.text = String.format(Locale.US, "%.1f °C", tempC)
        } else {
            tvBoilingPoint.text = "-- °C"
        }
    }
}
