package win.hik.tofchizi.db

data class MeasurementRecord(
    val id: Long = 0,
    val distance: Int, // mm
    val pitch: Float,
    val yaw: Float,
    val azimuth: Float = 0f, // New field for compass
    val timestamp: Long,
    val note: String = ""
)
