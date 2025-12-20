package win.hik.tofchizi

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build


class ToFSensorHelper(
    private val context: Context,
    private val listener: Listener,
    private val mode: SensorMode,
    private val candidates: List<String>? = null
) : SensorEventListener {
    interface Listener {
        fun onDistanceMeters(value: Float)
        fun onOutOfRange()
        fun onInvalidData()
        fun onUnsupported()
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var sensor: Sensor? = null

    fun start() {
        sensor = findToFSensor()
        val s = sensor
        if (s == null) {
            listener.onUnsupported()
            return
        }
        val ok = sensorManager.registerListener(this, s, 100000)
        if (!ok) {
            listener.onUnsupported()
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        sensor = null
    }

    private fun findToFSensor(): Sensor? {
        if (mode == SensorMode.AR) return null
        val list = sensorManager.getSensorList(Sensor.TYPE_ALL)
        if (!candidates.isNullOrEmpty()) {
            for (cand in candidates) {
                val c = cand.lowercase()
                val m = list.firstOrNull {
                    val nm = it.name.lowercase()
                    nm == c
                }
                if (m != null) return m
            }
        }
        val byTypeDistance = try { sensorManager.getDefaultSensor(55) } catch (_: Throwable) { null }
        if (byTypeDistance != null) {
            val st = try { byTypeDistance.stringType.lowercase() } catch (_: Throwable) { "" }
            if (st.contains("distance") || st.contains("tof")) return byTypeDistance
        }
        val exactTof = list.firstOrNull {
            val st = try { it.stringType.lowercase() } catch (_: Throwable) { "" }
            st == "android.sensor.tof"
        }
        if (exactTof != null) return exactTof
        if (isXiaomi()) {
            val miTof = list.firstOrNull {
                val name = it.name.lowercase()
                val st = try { it.stringType.lowercase() } catch (_: Throwable) { "" }
                name.contains("laser") || name.contains("tof") || name.contains("mi") || st.contains("xiaomi") || st.contains("tof") || st.contains("distance")
            }
            if (miTof != null) return miTof
        }
        val vendorTof = list.firstOrNull {
            val st = try { it.stringType.lowercase() } catch (_: Throwable) { "" }
            st.contains("tof")
        }
        if (vendorTof != null) return vendorTof
        val byNameLaser = list.firstOrNull {
            val name = it.name.lowercase()
            name.contains("laser") || name.contains("tof") || name.contains("time of flight") || name.contains("lidar") || name.contains("depth") || name.contains("ultrasonic") || name.contains("距离") || name.contains("激光")
        }
        if (byNameLaser != null) return byNameLaser
        val byNameDistance = list.firstOrNull {
            val name = it.name.lowercase()
            name.contains("distance") && !name.contains("proximity")
        }
        if (byNameDistance != null) return byNameDistance
        return null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values.firstOrNull() ?: return
        listener.onDistanceMeters(v)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun isXiaomi(): Boolean {
        return Build.MANUFACTURER.equals("xiaomi", true) || Build.BRAND.equals("xiaomi", true) || Build.MODEL.lowercase().contains("mi") || Build.MODEL.lowercase().contains("xiaomi")
    }
}
