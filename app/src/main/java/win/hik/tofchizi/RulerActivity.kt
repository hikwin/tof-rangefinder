package win.hik.tofchizi

import android.content.SharedPreferences
import kotlin.math.abs


import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class RulerActivity : BaseActivity() {

    private lateinit var rulerView: RulerView
    private val prefs: SharedPreferences by lazy { getSharedPreferences("tof_prefs", MODE_PRIVATE) }

    private var currentRefMm = 85.60f
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruler)

        rulerView = findViewById(R.id.rulerView)
        btnSave = findViewById(R.id.btnSaveCalibrate)
        
        // Load saved PPM
        val savedPpm = prefs.getFloat("ruler_ppm", 0f)
        if (savedPpm > 0) {
            rulerView.setPpm(savedPpm)
        }

        findViewById<Button>(R.id.btnCalibrate).setOnClickListener {
            showRefSelectionDialog()
        }
        
        btnSave.setOnClickListener {
            val distPx = abs(rulerView.calibrationLine2Y - rulerView.calibrationLine1Y)
            if (distPx > 10) {
                val newPpm = distPx / currentRefMm
                prefs.edit().putFloat("ruler_ppm", newPpm).apply()
                rulerView.setPpm(newPpm)
                Toast.makeText(this, getString(R.string.ruler_msg_saved), Toast.LENGTH_SHORT).show()
                
                // Exit calibration mode
                rulerView.isCalibrationMode = false
                rulerView.invalidate()
                btnSave.visibility = View.GONE
            }
        }
    }

    private fun showRefSelectionDialog() {
        val items = arrayOf(
            getString(R.string.ruler_calib_card),
            getString(R.string.ruler_calib_ruler),
            getString(R.string.ruler_calib_reset)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ruler_calib_title))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> { currentRefMm = 85.60f; startCalibration(getString(R.string.ruler_calib_msg_card)) }
                    1 -> { currentRefMm = 50.0f; startCalibration(getString(R.string.ruler_calib_msg_ruler)) }
                    2 -> {
                        prefs.edit().remove("ruler_ppm").apply()
                        val defaultPpm = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 1f, resources.displayMetrics)
                        rulerView.setPpm(defaultPpm)
                        Toast.makeText(this, getString(R.string.ruler_msg_reset_success), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }
    
    private fun startCalibration(msg: String) {
        // Line 1 at logical 3cm (30mm)
        // If current PPM is unknown (initial), assume a default ~400dpi or whatever TypedValue gives
        // Line 2 at logical 30mm + ref
        
        val startY = 30f * rulerView.pixelsPerMm
        val endY = (30f + currentRefMm) * rulerView.pixelsPerMm
        
        rulerView.calibrationLine1Y = startY
        rulerView.calibrationLine2Y = endY
        rulerView.isCalibrationMode = true
        rulerView.invalidate()
        
        btnSave.visibility = View.VISIBLE
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
