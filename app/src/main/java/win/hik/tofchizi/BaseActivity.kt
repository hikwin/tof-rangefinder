package win.hik.tofchizi

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import win.hik.tofchizi.util.LocaleHelper

open class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }
}
