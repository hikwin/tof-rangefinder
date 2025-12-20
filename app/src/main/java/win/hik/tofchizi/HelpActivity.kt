package win.hik.tofchizi

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AppCompatActivity
import win.hik.tofchizi.databinding.ActivityHelpBinding

class HelpActivity : BaseActivity() {
    private lateinit var binding: ActivityHelpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.setText(R.string.title_help)
        val content = getString(R.string.home_intro_body)

        binding.tvContent.text = Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT)
        binding.tvContent.movementMethod = LinkMovementMethod.getInstance()

        binding.btnBack.setOnClickListener {
            finish()
        }
        binding.btnBackIcon.setOnClickListener {
            finish()
        }
    }
}
