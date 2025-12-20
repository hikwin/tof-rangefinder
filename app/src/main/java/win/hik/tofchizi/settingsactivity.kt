package win.hik.tofchizi

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.Toast
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import win.hik.tofchizi.db.AppDatabaseHelper
import win.hik.tofchizi.db.MeasurementRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.dhatim.fastexcel.Workbook
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : BaseActivity() {
    private lateinit var dbHelper: AppDatabaseHelper
    private val prefs by lazy { getSharedPreferences("tof_prefs", MODE_PRIVATE) }
    private lateinit var etHistoryLimit: EditText
    private var exportType = "json" // json, md, csv, xlsx

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                exportData(uri, exportType)
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importData(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        dbHelper = AppDatabaseHelper(this)
        etHistoryLimit = findViewById(R.id.etHistoryLimit)
        
        val limit = prefs.getInt("history_limit", 200)
        etHistoryLimit.setText(limit.toString())
        
        setupLanguageSpinner()
        setupSaveMode()
        setupThemeSpinner()
        
        findViewById<Button>(R.id.btnSaveLimit).setOnClickListener {
            val v = etHistoryLimit.text.toString().toIntOrNull()
            if (v != null && v >= 0) {
                prefs.edit().putInt("history_limit", v).apply()
                Toast.makeText(this, getString(R.string.msg_save_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.error_invalid_number), Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnExportMulti).setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            val strMd = getString(R.string.export_md)
            val strCsv = getString(R.string.export_csv)
            val strXlsx = getString(R.string.export_xlsx)
            
            popup.menu.add(strMd)
            popup.menu.add(strCsv)
            popup.menu.add(strXlsx)
            
            popup.setOnMenuItemClickListener { item ->
                val (type, mime, ext) = when (item.title) {
                    strMd -> Triple("md", "text/markdown", "md")
                    strCsv -> Triple("csv", "text/csv", "csv")
                    strXlsx -> Triple("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
                    else -> Triple("txt", "text/plain", "txt")
                }
                exportType = type
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    this.type = mime
                    putExtra(Intent.EXTRA_TITLE, "tof_export_${System.currentTimeMillis()}.$ext")
                }
                exportLauncher.launch(intent)
                true
            }
            popup.show()
        }

        findViewById<Button>(R.id.btnShareMulti).setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            val strMd = getString(R.string.export_md)
            val strCsv = getString(R.string.export_csv)
            val strXlsx = getString(R.string.export_xlsx)
            
            popup.menu.add(strMd)
            popup.menu.add(strCsv)
            popup.menu.add(strXlsx)
            
            popup.setOnMenuItemClickListener { item ->
                val (type, mime, ext) = when (item.title) {
                    strMd -> Triple("md", "text/markdown", "md")
                    strCsv -> Triple("csv", "text/csv", "csv")
                    strXlsx -> Triple("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
                    else -> Triple("txt", "text/plain", "txt")
                }
                shareData(type, mime, ext)
                true
            }
            popup.show()
        }

        findViewById<Button>(R.id.btnClearAll).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_clear_title))
                .setMessage(getString(R.string.dialog_clear_msg))
                .setPositiveButton(getString(R.string.btn_confirm_clear)) { _, _ ->
                    dbHelper.deleteAllRecords()
                    Toast.makeText(this, getString(R.string.msg_records_cleared), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }

        findViewById<Button>(R.id.btnBackup).setOnClickListener {
            exportType = "json"
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "tof_backup_${System.currentTimeMillis()}.json")
            }
            exportLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnRestore).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            importLauncher.launch(intent)
        }
    }

    private fun setupLanguageSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerLanguage)
        val options = listOf(
            getString(R.string.pref_language_auto),
            getString(R.string.pref_language_zh),
            getString(R.string.pref_language_en)
        )
        val values = listOf("auto", "zh", "en")
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        
        val currentLang = win.hik.tofchizi.util.LocaleHelper.getLanguage(this)
        val index = values.indexOf(currentLang).let { if (it < 0) 0 else it }
        spinner.setSelection(index)
        
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = values[position]
                if (selected != currentLang) {
                    win.hik.tofchizi.util.LocaleHelper.setLocale(this@SettingsActivity, selected)
                    // Restart App
                    val i = Intent(this@SettingsActivity, ModeSelectActivity::class.java)
                    i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(i)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun exportData(uri: Uri, type: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                writeDataToStream(type, os)
            }
            Toast.makeText(this, getString(R.string.msg_export_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, String.format(getString(R.string.msg_export_fail), e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun shareData(type: String, mime: String, ext: String) {
        try {
            // Create a file in the cache directory
            val shareDir = File(cacheDir, "share")
            if (!shareDir.exists()) shareDir.mkdirs()
            
            val file = File(shareDir, "tof_share_${System.currentTimeMillis()}.$ext")
            FileOutputStream(file).use { fos ->
                writeDataToStream(type, fos)
            }
            
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                this.type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, getString(R.string.title_share)))
            
        } catch (e: Exception) {
            Toast.makeText(this, String.format(getString(R.string.msg_share_fail), e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun generateMarkdown(list: List<MeasurementRecord>): String {
        val sb = StringBuilder()
        sb.append("| Time | Distance(mm) | Pitch(V) | Yaw(H) | Azimuth | Note |\n")
        sb.append("|---|---|---|---|---|---|\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (r in list) {
            sb.append("| ${sdf.format(Date(r.timestamp))} | ${r.distance} | ${r.pitch} | ${r.yaw} | ${r.azimuth} | ${r.note} |\n")
        }
        return sb.toString()
    }

    private fun generateCsv(list: List<MeasurementRecord>): String {
        val sb = StringBuilder()
        sb.append("Time,Distance(mm),Pitch,Yaw,Azimuth,Note\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (r in list) {
            // Escape CSV fields if necessary
            val note = r.note.replace("\"", "\"\"").let { if (it.contains(",")) "\"$it\"" else it }
            sb.append("${sdf.format(Date(r.timestamp))},${r.distance},${r.pitch},${r.yaw},${r.azimuth},$note\n")
        }
        return sb.toString()
    }

    private fun generateXlsx(list: List<MeasurementRecord>): ByteArray {
        val bos = ByteArrayOutputStream()
        val wb = Workbook(bos, "TofChizi", "1.0")
        val ws = wb.newWorksheet("Records")
        
        ws.value(0, 0, "Time")
        ws.value(0, 1, "Distance(mm)")
        ws.value(0, 2, "Pitch(V)")
        ws.value(0, 3, "Yaw(H)")
        ws.value(0, 4, "Azimuth")
        ws.value(0, 5, "Note")
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for ((i, r) in list.withIndex()) {
            val row = i + 1
            ws.value(row, 0, sdf.format(Date(r.timestamp)))
            ws.value(row, 1, r.distance)
            ws.value(row, 2, r.pitch)
            ws.value(row, 3, r.yaw)
            ws.value(row, 4, r.azimuth)
            ws.value(row, 5, r.note)
        }
        wb.finish()
        return bos.toByteArray()
    }

    private fun writeDataToStream(type: String, os: java.io.OutputStream) {
        val records = dbHelper.getAllRecords()
        if (type == "xlsx") {
            val bytes = generateXlsx(records)
            os.write(bytes)
        } else {
            val content = when (type) {
                "json" -> Gson().toJson(records)
                "md" -> generateMarkdown(records)
                "csv" -> generateCsv(records)
                else -> ""
            }
            OutputStreamWriter(os).use { it.write(content) }
        }
    }

    private fun importData(uri: Uri) {
        try {
            val sb = StringBuilder()
            contentResolver.openInputStream(uri)?.use { ins ->
                BufferedReader(InputStreamReader(ins)).use { br ->
                    var line: String?
                    while (br.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                }
            }
            val json = sb.toString()
            val listType = object : TypeToken<List<MeasurementRecord>>() {}.type
            val list: List<MeasurementRecord> = Gson().fromJson(json, listType)
            
            // Validate data format
            if (list.isNotEmpty()) {
                val item = list[0]
                if (item.timestamp <= 0) throw Exception("无效的时间戳")
            }

            var count = 0
            for (r in list) {
                val newRecord = r.copy(id = 0) 
                dbHelper.addRecord(newRecord)
                count++
            }
            Toast.makeText(this, String.format(getString(R.string.msg_import_success), count), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.msg_import_fail), Toast.LENGTH_LONG).show()
        }
    }

    private fun setupSaveMode() {
        val rg = findViewById<RadioGroup>(R.id.rgSaveMode)
        val rbAuto = findViewById<RadioButton>(R.id.rbAutoSave)
        val rbManual = findViewById<RadioButton>(R.id.rbManualSave)
        
        val mode = prefs.getString("save_mode", "auto")
        if (mode == "manual") {
            rbManual.isChecked = true
        } else {
            rbAuto.isChecked = true
        }
        
        rg.setOnCheckedChangeListener { _, checkedId ->
            val value = if (checkedId == R.id.rbManualSave) "manual" else "auto"
            prefs.edit().putString("save_mode", value).apply()
        }
    }

    private fun setupThemeSpinner() {
        val themeSpinner = findViewById<Spinner>(R.id.spinnerTheme)
        val themeOptions = listOf(
            getString(R.string.theme_follow_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themeOptions)
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        themeSpinner.adapter = themeAdapter

        val currentMode = prefs.getInt("app_theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val selectedIndex = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }
        themeSpinner.setSelection(selectedIndex)

        themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val mode = when (position) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                
                val savedMode = prefs.getInt("app_theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                
                if (mode != savedMode) {
                    prefs.edit().putInt("app_theme_mode", mode).apply()
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
