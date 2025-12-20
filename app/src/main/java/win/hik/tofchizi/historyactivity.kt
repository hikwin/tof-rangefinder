package win.hik.tofchizi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import win.hik.tofchizi.db.AppDatabaseHelper
import win.hik.tofchizi.db.MeasurementRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : BaseActivity() {
    private lateinit var dbHelper: AppDatabaseHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HistoryAdapter
    private val records = mutableListOf<MeasurementRecord>()
    private val selectedIds = mutableSetOf<Long>()
    private val prefs by lazy { getSharedPreferences("tof_prefs", MODE_PRIVATE) }
    private var currentUnit: Int = 0 // 0:mm, 1:cm, 2:m

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        
        currentUnit = prefs.getInt("history_unit", 0)
        
        dbHelper = AppDatabaseHelper(this)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter()
        recyclerView.adapter = adapter
        
        findViewById<View>(R.id.btnDelete).setOnClickListener {
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, getString(R.string.msg_long_press_delete), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_delete_title))
                .setMessage(getString(R.string.dialog_delete_msg, selectedIds.size))
                .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                    dbHelper.deleteRecords(selectedIds.toList())
                    selectedIds.clear()
                    loadRecords()
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }

        findViewById<View>(R.id.btnUnit).setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 0, 0, getString(R.string.unit_mm))
            popup.menu.add(0, 1, 1, getString(R.string.unit_cm))
            popup.menu.add(0, 2, 2, getString(R.string.unit_m))
            popup.setOnMenuItemClickListener { item ->
                currentUnit = item.itemId
                prefs.edit().putInt("history_unit", currentUnit).apply()
                adapter.notifyDataSetChanged()
                true
            }
            popup.show()
        }
        
        loadRecords()
    }

    private fun loadRecords() {
        records.clear()
        records.addAll(dbHelper.getAllRecords())
        adapter.notifyDataSetChanged()
    }

    inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDistance: TextView = view.findViewById(R.id.tvDistance)
            val tvTime: TextView = view.findViewById(R.id.tvTime)
            val tvAngles: TextView = view.findViewById(R.id.tvAngles)
            val tvNote: TextView = view.findViewById(R.id.tvNote)
            val cardView: View = view
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_record, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = records[position]
            
            val distText = when (currentUnit) {
                1 -> String.format("%.1f cm", item.distance / 10f)
                2 -> String.format("%.3f m", item.distance / 1000f)
                else -> "${item.distance} mm"
            }
            holder.tvDistance.text = distText
            
            holder.tvTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
            
            val az = ((item.azimuth % 360f) + 360f) % 360f
            val dir = when {
                az < 22.5f || az >= 337.5f -> "N"
                az < 67.5f -> "NE"
                az < 112.5f -> "E"
                az < 157.5f -> "SE"
                az < 202.5f -> "S"
                az < 247.5f -> "SW"
                az < 292.5f -> "W"
                else -> "NW"
            }
            holder.tvAngles.text = String.format("V:%.1f° H:%.1f° %s%.0f°", item.pitch, item.yaw, dir, az)
            
            if (item.note.isNotEmpty()) {
                holder.tvNote.text = item.note
                holder.tvNote.visibility = View.VISIBLE
            } else {
                holder.tvNote.visibility = View.GONE
            }

            val isSelected = selectedIds.contains(item.id)
            val context = holder.cardView.context
            val surfaceColor = if (isSelected) {
                // Using a darker shade for selection or an overlay
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorControlHighlight, typedValue, true)
                typedValue.data
            } else {
                 // Get cardBackgroundColor from theme or just use default because CardView handles it
                 // However, we need to reset it if it was changed.
                 // Better to rely on CardView's default background but we are overriding it here.
                 // Let's resolve 'brand_surface' color from resources
                 androidx.core.content.ContextCompat.getColor(context, R.color.brand_surface)
            }
            holder.cardView.setBackgroundColor(surfaceColor)

            holder.cardView.setOnClickListener {
                if (selectedIds.isNotEmpty()) {
                    toggleSelection(item.id)
                } else {
                    showEditNoteDialog(item)
                }
            }

            holder.cardView.setOnLongClickListener {
                toggleSelection(item.id)
                true
            }
        }

        override fun getItemCount() = records.size

        private fun toggleSelection(id: Long) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id)
            } else {
                selectedIds.add(id)
            }
            notifyDataSetChanged()
        }
    }

    private fun showEditNoteDialog(record: MeasurementRecord) {
        val input = EditText(this)
        input.setText(record.note)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_note_title))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val newNote = input.text.toString()
                dbHelper.updateNote(record.id, newNote)
                loadRecords()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }
}
