package win.hik.tofchizi

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ToolsActivity : BaseActivity() {

    data class ToolItem(val nameRes: Int, val iconRes: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tools)

        val tools = listOf(
            ToolItem(R.string.tool_ruler, R.drawable.ic_tool_ruler),
            ToolItem(R.string.tool_protractor, R.drawable.ic_tool_protractor),
            ToolItem(R.string.tool_level, R.drawable.ic_tool_level),
            ToolItem(R.string.tool_compass, R.drawable.ic_tool_compass),
            ToolItem(R.string.tool_altitude, R.drawable.ic_tool_altitude),
            ToolItem(R.string.tool_frame_alignment, R.drawable.ic_tool_frame),
            ToolItem(R.string.tool_two_step, R.drawable.ic_tool_two_step),
            ToolItem(R.string.tool_lightning, R.drawable.ic_tool_lightning),
            ToolItem(R.string.tool_pressure, R.drawable.ic_tool_pressure),
            ToolItem(R.string.tool_nfc_title, R.drawable.ic_tool_nfc)
        )

        val gridView = findViewById<GridView>(R.id.gridViewTools)
        gridView.adapter = ToolsAdapter(this, tools)

        gridView.setOnItemClickListener { _, _, position, _ ->
            val tool = tools[position]
            when (tool.nameRes) {
                R.string.tool_ruler -> startActivity(android.content.Intent(this, RulerActivity::class.java))
                R.string.tool_protractor -> startActivity(android.content.Intent(this, ProtractorActivity::class.java))
                R.string.tool_level -> startActivity(android.content.Intent(this, LevelActivity::class.java))
                R.string.tool_frame_alignment -> startActivity(android.content.Intent(this, FrameAlignmentActivity::class.java))
                R.string.tool_compass -> startActivity(android.content.Intent(this, CompassActivity::class.java))
                R.string.tool_altitude -> startActivity(android.content.Intent(this, AltitudeActivity::class.java))
                R.string.tool_two_step -> startActivity(android.content.Intent(this, TwoStepMeasurementActivity::class.java))
                R.string.tool_lightning -> startActivity(android.content.Intent(this, LightningDistanceActivity::class.java))
                R.string.tool_pressure -> startActivity(android.content.Intent(this, AirtightnessActivity::class.java))
                R.string.tool_nfc_title -> startActivity(android.content.Intent(this, NfcActivity::class.java))
                else -> {
                     val name = getString(tool.nameRes)
                     if (name.isNotEmpty()) {
                        Toast.makeText(this, getString(R.string.msg_opening_tool, name), Toast.LENGTH_SHORT).show()
                     }
                }
            }
        }
    }

    class ToolsAdapter(private val context: Context, private val items: List<ToolItem>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_tool, parent, false)
            
            val item = items[position]
            val iv = view.findViewById<ImageView>(R.id.ivToolIcon)
            val tv = view.findViewById<TextView>(R.id.tvToolName)

            if (item.nameRes != 0) {
                iv.setImageResource(item.iconRes)
                tv.setText(item.nameRes)
                iv.setColorFilter(context.getColor(R.color.brand_primary)) 
                view.visibility = View.VISIBLE
                
                // Reset background if needed (optional)
                // view.setBackgroundResource(R.drawable.bg_glass_button)
            } else {
                view.visibility = View.INVISIBLE
            }

            return view
        }
    }
}
