package win.hik.tofchizi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.CheckBox
import android.widget.PopupMenu
import androidx.activity.ComponentActivity
import win.hik.tofchizi.databinding.ActivityModeSelectBinding
import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog

class ModeSelectActivity : BaseActivity() {
    private lateinit var binding: ActivityModeSelectBinding
    private lateinit var sensorManager: SensorManager
    private val rawCandidates = mutableListOf<String>()
    private val checkBoxes = mutableListOf<CheckBox>()
    private var selectedColor: Int = android.graphics.Color.RED
    private val prefs by lazy { getSharedPreferences("tof_prefs", MODE_PRIVATE) }

    private lateinit var gestureDetector: android.view.GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        val p = getSharedPreferences("tof_prefs", MODE_PRIVATE)
        val mode = p.getInt("app_theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
        
        super.onCreate(savedInstanceState)
        binding = ActivityModeSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sensorManager = getSystemService(SensorManager::class.java)
        
        setupCrosshairSpinner()
        populateCandidates()

        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                        // Swipe Left or Right -> Open Tools
                        startActivity(Intent(this@ModeSelectActivity, ToolsActivity::class.java))
                        
                        // Set animation based on direction
                        if (diffX < 0) {
                             if (android.os.Build.VERSION.SDK_INT >= 34) {
                                 overrideActivityTransition(androidx.appcompat.app.AppCompatActivity.OVERRIDE_TRANSITION_OPEN, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                             } else {
                                 @Suppress("DEPRECATION")
                                 overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                             }
                        } else {
                             if (android.os.Build.VERSION.SDK_INT >= 34) {
                                 overrideActivityTransition(androidx.appcompat.app.AppCompatActivity.OVERRIDE_TRANSITION_OPEN, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                             } else {
                                 @Suppress("DEPRECATION")
                                 overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                             }
                        }
                        return true
                    }
                }
                return false
            }
        })
        
        binding.btnStart.setOnClickListener {
            val i = Intent(this, MainActivity::class.java)
            i.putExtra("mode", SensorMode.TOF.name)
            val chosen = arrayListOf<String>()
            for (idx in checkBoxes.indices) {
                if (checkBoxes[idx].isChecked) {
                    chosen.add(rawCandidates[idx])
                }
            }
            if (chosen.isEmpty() && rawCandidates.isNotEmpty()) {
                chosen.add(rawCandidates[0])
            }
            if (chosen.isNotEmpty()) {
                i.putStringArrayListExtra("candidates", chosen)
            }
            i.putExtra("crosshairColor", selectedColor)
            startActivity(i)
        }

        binding.btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            val menuTools = getString(R.string.menu_tools)
            val menuHelp = getString(R.string.menu_help)
            val menuSettings = getString(R.string.menu_settings)
            
            popup.menu.add(menuTools)
            popup.menu.add(menuHelp)
            popup.menu.add(menuSettings)
            
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    menuTools -> startActivity(Intent(this, ToolsActivity::class.java))
                    menuHelp -> {
                        val i = Intent(this, HelpActivity::class.java)
                        i.putExtra("type", "Help")
                        startActivity(i)
                    }
                    menuSettings -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                }
                true
            }
            popup.show()
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev != null) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun populateCandidates() {
        val list = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val filtered = list.filter {
            val nm = it.name.lowercase()
            val st = try { it.stringType.lowercase() } catch (_: Throwable) { "" }
            nm.contains("tof") || nm.contains("time of flight") || nm.contains("laser") || nm.contains("lidar") || st.contains("tof") || st.contains("sensor.tof") || st.contains("lidar") || st.contains("laser")
        }
        
        binding.candidateListContainer.removeAllViews()
        rawCandidates.clear()
        checkBoxes.clear()
        
        if (filtered.isEmpty()) {
            val tv = android.widget.TextView(this)
            tv.text = "未找到ToF/激光类传感器"
            tv.setTextColor(resources.getColor(R.color.text_secondary, theme))
            binding.candidateListContainer.addView(tv)
            return
        }

        for (s in filtered) {
            val st = try { s.stringType } catch (_: Throwable) { "" }
            val displayName = if (st.isNullOrEmpty()) s.name else "${s.name} ($st)"
            
            rawCandidates.add(s.name)
            
            val cb = CheckBox(this)
            cb.text = displayName
            cb.setTextColor(resources.getColor(R.color.text_primary, theme))
            cb.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            binding.candidateListContainer.addView(cb)
            checkBoxes.add(cb)
        }
    }

    private fun setupCrosshairSpinner() {
        val names = listOf(
            getString(R.string.color_red),
            getString(R.string.color_green),
            getString(R.string.color_blue),
            getString(R.string.color_yellow),
            getString(R.string.color_cyan),
            getString(R.string.color_white)
        )
        val colors = listOf(
            android.graphics.Color.RED,
            android.graphics.Color.GREEN,
            android.graphics.Color.BLUE,
            android.graphics.Color.YELLOW,
            android.graphics.Color.CYAN,
            android.graphics.Color.WHITE
        )
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCrosshair.adapter = adapter
        val saved = prefs.getInt("crosshair_color", android.graphics.Color.RED)
        val idx = colors.indexOf(saved).let { if (it < 0) 0 else it }
        binding.spinnerCrosshair.setSelection(idx)
        selectedColor = colors[idx]
        binding.spinnerCrosshair.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedColor = colors[position]
                prefs.edit().putInt("crosshair_color", selectedColor).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
}