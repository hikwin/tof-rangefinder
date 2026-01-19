package win.hik.tofchizi

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import java.nio.charset.Charset
import java.util.Locale
import android.widget.Spinner
import android.widget.LinearLayout
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.view.View
import android.text.InputType
import com.google.android.material.textfield.TextInputLayout
import androidx.appcompat.app.AlertDialog
import java.io.ByteArrayOutputStream

class NfcActivity : BaseActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var tvStatus: TextView
    private lateinit var tvWriteStatus: TextView
    private lateinit var tvSecurityStatus: TextView
    private lateinit var etRead: EditText
    private lateinit var etWrite: EditText
    private lateinit var btnWrite: Button
    private lateinit var btnCopy: Button
    private lateinit var btnClone: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnSavedWifi: Button

    private lateinit var spWriteType: Spinner
    private lateinit var layoutTextInput: TextInputLayout
    private lateinit var layoutWifiInput: LinearLayout
    private lateinit var layoutAppInput: LinearLayout
    private lateinit var etWifiSsid: EditText
    private lateinit var etWifiPass: EditText
    private lateinit var tvSelectedApp: TextView
    private lateinit var btnSelectApp: Button

    private lateinit var layoutBtInput: LinearLayout
    private lateinit var etBtMac: EditText
    
    private lateinit var layoutContactInput: LinearLayout
    private lateinit var etContactName: EditText
    private lateinit var etContactPhone: EditText
    private lateinit var etContactEmail: EditText
    
    private lateinit var layoutLocInput: LinearLayout
    private lateinit var etLocLat: EditText
    private lateinit var etLocLon: EditText
    private lateinit var btnCurrentLoc: Button

    private var selectedAppPackage: String? = null
    
    // 0: Text, 1: URL, 2: Phone, 3: App, 4: WiFi
    private var currentTypeIndex = 0 

    private var pendingWriteText: String? = null
    private var pendingWriteMessage: NdefMessage? = null
    private var cachedNdefMessage: NdefMessage? = null
    
    private enum class NfcAction {
        READ, WRITE, LOCK, SET_PWD, REMOVE_PWD
    }
    private var currentAction = NfcAction.READ
    private var pendingPassword: String? = null
    
    // NTAG Page Constants (Simplification, risks incorrect writing on other tags)
    // Dynamic detection usually required.
    // For this demo, let's assume NTAG213/215/216 standard structure if Ultralight.
    
    private lateinit var btnLock: Button
    private lateinit var btnSetPwd: Button
    private lateinit var btnRemovePwd: Button
    private lateinit var btnCancelAction: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc)

        tvStatus = findViewById(R.id.tvNfcStatus)
        tvWriteStatus = findViewById(R.id.tvWriteStatus)
        tvSecurityStatus = findViewById(R.id.tvSecurityStatus)
        etRead = findViewById(R.id.etReadContent)
        etWrite = findViewById(R.id.etWriteContent)
        btnWrite = findViewById(R.id.btnWrite)
        btnCopy = findViewById(R.id.btnCopy)
        btnClone = findViewById(R.id.btnClone)
        btnBack = findViewById(R.id.btnBack)
        
        btnLock = findViewById(R.id.btnLock)
        btnSetPwd = findViewById(R.id.btnSetPwd)
        btnRemovePwd = findViewById(R.id.btnRemovePwd)
        btnCancelAction = findViewById(R.id.btnCancelAction)
        
        spWriteType = findViewById(R.id.spWriteType)
        layoutTextInput = findViewById(R.id.layoutTextInput)
        layoutWifiInput = findViewById(R.id.layoutWifiInput)
        layoutAppInput = findViewById(R.id.layoutAppInput)
        etWifiSsid = findViewById(R.id.etWifiSsid)
        etWifiPass = findViewById(R.id.etWifiPass)
        tvSelectedApp = findViewById(R.id.tvSelectedApp)
        btnSelectApp = findViewById(R.id.btnSelectApp)
        btnSavedWifi = findViewById(R.id.btnSavedWifi)

        layoutBtInput = findViewById(R.id.layoutBtInput)
        etBtMac = findViewById(R.id.etBtMac)
        
        layoutContactInput = findViewById(R.id.layoutContactInput)
        etContactName = findViewById(R.id.etContactName)
        etContactPhone = findViewById(R.id.etContactPhone)
        etContactEmail = findViewById(R.id.etContactEmail)
        
        layoutLocInput = findViewById(R.id.layoutLocInput)
        etLocLat = findViewById(R.id.etLocLat)
        etLocLon = findViewById(R.id.etLocLon)
        btnCurrentLoc = findViewById(R.id.btnCurrentLoc)

        btnBack.setOnClickListener { finish() }

        btnSavedWifi.setOnClickListener { checkAndShowSavedWifi() }
        btnCurrentLoc.setOnClickListener { getCurrentLocation() }

        // Setup Spinner
        val types = resources.getStringArray(R.array.nfc_write_types)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spWriteType.adapter = adapter
        
        spWriteType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTypeIndex = position
                updateWriteUI(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnSelectApp.setOnClickListener { showAppSelectDialog() }

        findViewById<TextView>(R.id.tvGetAlipayRedPacket).setOnClickListener {
            generateAndShowQr("https://qr.alipay.com/11w19205ehy2cxs4cidyk07", R.string.title_author_red_packet_qr, R.string.hint_author_red_packet_qr)
        }

        // Make Read Content field read-only but selectable
        etRead.keyListener = null
        etRead.setTextIsSelectable(true)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, R.string.nfc_msg_not_supported, Toast.LENGTH_LONG).show()
            tvStatus.text = getString(R.string.nfc_msg_not_supported)
            btnWrite.isEnabled = false
            disableProtectionButtons()
        } else if (!nfcAdapter!!.isEnabled) {
            tvStatus.text = getString(R.string.nfc_msg_disabled)
            Toast.makeText(this, R.string.nfc_msg_disabled, Toast.LENGTH_LONG).show()
        }

        btnWrite.setOnClickListener {
            if (validateInput()) {
                pendingWriteText = etWrite.text.toString() // For Text/URL/Phone
                pendingWriteMessage = null 
                currentAction = NfcAction.WRITE
                tvWriteStatus.text = getString(R.string.nfc_status_write_mode)
                tvWriteStatus.setTextColor(getColor(R.color.brand_primary))
                // Clear other statuses if needed
                tvStatus.text = getString(R.string.nfc_status_waiting)
                tvSecurityStatus.text = getString(R.string.nfc_status_waiting)
            }
        }

        btnCopy.setOnClickListener {
            val text = etRead.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("NFC Content", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.msg_copied, Toast.LENGTH_SHORT).show()
            }
        }

        btnClone.setOnClickListener {
            if (cachedNdefMessage != null) {
                pendingWriteMessage = cachedNdefMessage
                pendingWriteText = null
                currentAction = NfcAction.WRITE
                tvWriteStatus.text = getString(R.string.nfc_msg_clone_ready)
                tvWriteStatus.setTextColor(getColor(R.color.brand_primary))
                tvStatus.text = getString(R.string.nfc_status_waiting)
                tvSecurityStatus.text = getString(R.string.nfc_status_waiting)
            } else {
                Toast.makeText(this, getString(R.string.nfc_msg_write_empty), Toast.LENGTH_SHORT).show()
            }
        }
        
        // Protection Buttons
        btnLock.setOnClickListener {
            val input = EditText(this)
            val container = LinearLayout(this)
            container.orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            container.setPadding(padding, 0, padding, 0)
            input.hint = "yes"
            container.addView(input)

            AlertDialog.Builder(this)
                .setTitle(R.string.nfc_security_title)
                .setMessage(R.string.msg_lock_confirm)
                .setView(container)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val text = input.text.toString().trim()
                    if (text.equals("yes", ignoreCase = true)) {
                        currentAction = NfcAction.LOCK
                        tvSecurityStatus.text = getString(R.string.nfc_status_lock_mode)
                        tvSecurityStatus.setTextColor(getColor(R.color.brand_primary))
                        tvStatus.text = getString(R.string.nfc_status_waiting)
                        tvWriteStatus.text = getString(R.string.nfc_status_waiting_write)
                    } else {
                        Toast.makeText(this, getString(R.string.error_lock_confirm_fail), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        
        btnSetPwd.setOnClickListener {
             promptForPassword(true)
        }
        
        btnRemovePwd.setOnClickListener {
             promptForPassword(false)
        }
        
        btnCancelAction.setOnClickListener {
            currentAction = NfcAction.READ
            pendingWriteMessage = null
            pendingWriteText = null
            pendingPassword = null
            
            tvStatus.text = getString(R.string.nfc_status_waiting)
            tvWriteStatus.text = getString(R.string.nfc_status_waiting_write)
            tvSecurityStatus.text = getString(R.string.nfc_status_waiting)
            
            tvStatus.setTextColor(getColor(R.color.text_secondary))
            tvWriteStatus.setTextColor(getColor(R.color.text_secondary))
            tvSecurityStatus.setTextColor(getColor(R.color.text_secondary))
            
            Toast.makeText(this, getString(R.string.msg_action_canceled), Toast.LENGTH_SHORT).show()
        }
        
        // Check if started by NFC intent
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action || 
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            handleIntent(intent)
        }
    }
    
    private fun disableProtectionButtons() {
        btnLock.isEnabled = false
        btnSetPwd.isEnabled = false
        btnRemovePwd.isEnabled = false
    }

    private fun promptForPassword(isSet: Boolean) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.hint = "1234"
        val container = LinearLayout(this)
        container.setPadding(50, 20, 50, 0)
        container.addView(input)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_pwd_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pwd = input.text.toString()
                if (pwd.length != 4) {
                    Toast.makeText(this, R.string.msg_pwd_len_err, Toast.LENGTH_SHORT).show()
                } else {
                    pendingPassword = pwd
                    if (isSet) {
                        currentAction = NfcAction.SET_PWD
                        tvSecurityStatus.text = getString(R.string.nfc_status_pwd_mode)
                    } else {
                        currentAction = NfcAction.REMOVE_PWD
                        tvSecurityStatus.text = getString(R.string.nfc_status_unpwd_mode)
                    }
                    tvSecurityStatus.setTextColor(getColor(R.color.brand_primary))
                    tvStatus.text = getString(R.string.nfc_status_waiting)
                    tvWriteStatus.text = getString(R.string.nfc_status_waiting_write)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        enableForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        disableForegroundDispatch()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == action) {

            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            if (tag != null) {
                when (currentAction) {
                    NfcAction.WRITE -> writeTag(tag)
                    NfcAction.LOCK -> lockTag(tag)
                    NfcAction.SET_PWD -> setTagPassword(tag, pendingPassword)
                    NfcAction.REMOVE_PWD -> removeTagPassword(tag, pendingPassword)
                    NfcAction.READ -> readTag(tag)
                }
            }
        }
    }

    private fun updateWriteUI(index: Int) {
        layoutTextInput.visibility = View.GONE
        layoutWifiInput.visibility = View.GONE
        layoutAppInput.visibility = View.GONE
        layoutBtInput.visibility = View.GONE
        layoutContactInput.visibility = View.GONE
        layoutLocInput.visibility = View.GONE
        
        when (index) {
            0 -> { // Text
                layoutTextInput.visibility = View.VISIBLE
                layoutTextInput.hint = getString(R.string.nfc_hint_text)
                etWrite.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            1 -> { // URL
                layoutTextInput.visibility = View.VISIBLE
                layoutTextInput.hint = getString(R.string.nfc_hint_url)
                etWrite.inputType = InputType.TYPE_TEXT_VARIATION_URI
            }
            2 -> { // Phone
                layoutTextInput.visibility = View.VISIBLE
                layoutTextInput.hint = getString(R.string.nfc_hint_phone)
                etWrite.inputType = InputType.TYPE_CLASS_PHONE
            }
            3 -> { // App
                layoutAppInput.visibility = View.VISIBLE
            }
            4 -> { // WiFi
                layoutWifiInput.visibility = View.VISIBLE
            }
            5 -> { // Bluetooth
                layoutBtInput.visibility = View.VISIBLE
            }
            6 -> { // Contact
                layoutContactInput.visibility = View.VISIBLE
            }
            7 -> { // Location
                layoutLocInput.visibility = View.VISIBLE
            }
        }
    }

    private fun validateInput(): Boolean {
        when (currentTypeIndex) {
            0, 1, 2 -> {
                if (etWrite.text.isNullOrEmpty()) {
                    Toast.makeText(this, getString(R.string.nfc_write_hint), Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            3 -> {
                if (selectedAppPackage.isNullOrEmpty()) {
                    Toast.makeText(this, getString(R.string.label_select_app), Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            4 -> {
                if (etWifiSsid.text.isNullOrEmpty()) {
                    Toast.makeText(this, getString(R.string.nfc_hint_wifi_ssid), Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            5 -> { // Bluetooth
                if (etBtMac.text.isNullOrEmpty() || etBtMac.text.length != 17) {
                    Toast.makeText(this, "MAC地址格式错误", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            6 -> { // Contact
                if (etContactName.text.isNullOrEmpty() && etContactPhone.text.isNullOrEmpty()) {
                    Toast.makeText(this, "请至少输入姓名或电话", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            7 -> { // Location
                if (etLocLat.text.isNullOrEmpty() || etLocLon.text.isNullOrEmpty()) {
                    Toast.makeText(this, "请输入经纬度", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
        }
        return true
    }

    private data class AppItem(val label: String, val packageName: String) {
        override fun toString(): String {
            return label 
        }
    }

    private fun showAppSelectDialog() {
        val pm = packageManager
        
        // Use a progress indicator or thread if blocking, but for now simple enough
        val packages = pm.getInstalledPackages(0)
        val appsOriginal = packages.filter { 
             pm.getLaunchIntentForPackage(it.packageName) != null 
        }.map { 
            AppItem(it.applicationInfo.loadLabel(pm).toString(), it.packageName)
        }.sortedBy { it.label }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_app_select, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchApp)
        val lvApps = dialogView.findViewById<android.widget.ListView>(R.id.lvAppList)
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, appsOriginal.toMutableList())
        lvApps.adapter = adapter
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.nfc_btn_select_app))
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        lvApps.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val app = adapter.getItem(position)
            if (app != null) {
                selectedAppPackage = app.packageName
                tvSelectedApp.text = "${app.label} ($selectedAppPackage)"
                dialog.dismiss()
            }
        }

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().lowercase()
                adapter.clear()
                if (query.isEmpty()) {
                    adapter.addAll(appsOriginal)
                } else {
                    val filtered = appsOriginal.filter { 
                        it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
                    }
                    adapter.addAll(filtered)
                }
            }
        })

        dialog.show()
        // Ensure dialog height is sufficient for list scrolling but not full screen
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            (resources.displayMetrics.heightPixels * 0.7).toInt() // 70% height
        )
    }

    private fun checkAndShowSavedWifi() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            Toast.makeText(this, getString(R.string.nfc_msg_no_wifi_permission), Toast.LENGTH_SHORT).show()
        } else {
            showWifiListDialog()
        }
    }

    private fun showWifiListDialog() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, getString(R.string.nfc_msg_disabled), Toast.LENGTH_SHORT).show()
            return
        }

        // Try getting configured networks (Saved) - limited on Android 10+
        val savedList = try {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                // Suppress deprecation as we try best effort
                @Suppress("DEPRECATION")
                wifiManager.configuredNetworks ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }

        // Also get Scan Results for nearby
        val scanList = try {
             wifiManager.scanResults ?: emptyList()
        } catch (e: Exception) {
             emptyList()
        }
        
        // Combined list
        val wifiNames = mutableSetOf<String>()
        
        // Prioritize saved
        savedList.forEach { 
            @Suppress("DEPRECATION")
            val ssid = it.SSID.trim('"') // Remove quotes
            if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                wifiNames.add(ssid)
            }
        }
        
        scanList.forEach {
            @Suppress("DEPRECATION")
            val ssid = it.SSID
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                 wifiNames.add(ssid)
            }
        }
        
        val finalApiList = wifiNames.sorted().toList()
        
        if (finalApiList.isEmpty()) {
             Toast.makeText(this, getString(R.string.nfc_msg_no_wifi_found), Toast.LENGTH_SHORT).show()
             return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_app_select, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchApp)
        val lvList = dialogView.findViewById<android.widget.ListView>(R.id.lvAppList)
        
        etSearch.hint = "Search WiFi..."
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, finalApiList.toMutableList())
        lvList.adapter = adapter
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.nfc_title_saved_wifi))
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        lvList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val wifiName = adapter.getItem(position)
            if (wifiName != null) {
                etWifiSsid.setText(wifiName)
                dialog.dismiss()
            }
        }

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().lowercase()
                adapter.clear()
                if (query.isEmpty()) {
                    adapter.addAll(finalApiList)
                } else {
                    val filtered = finalApiList.filter { 
                        it.lowercase().contains(query)
                    }
                    adapter.addAll(filtered)
                }
            }
        })
        
        dialog.show()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            (resources.displayMetrics.heightPixels * 0.6).toInt() 
        )
    }

    private fun readTag(tag: Tag) {
        // First try to parse NDEF from Intent extras if available (usually in NDEF_DISCOVERED)
        // usage of intent properties on newIntent is tricky, better use the objects
        
        // Let's rely on reading from the tag object for consistency, or check raw msgs
        val rawMsgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }

        if (rawMsgs != null && rawMsgs.isNotEmpty()) {
            val msgs = rawMsgs.map { it as NdefMessage }
            displayNdefMessages(msgs, tag)
        } else {
            // Try to connect
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                try {
                    ndef.connect()
                    val msg = ndef.ndefMessage
                    ndef.close()
                    if (msg != null) {
                        displayNdefMessages(listOf(msg), tag)
                    } else {
                       val extraInfo = getTagInfo(tag)
                       tvStatus.text = getString(R.string.msg_tag_empty, extraInfo)
                       etRead.setText("")
                       cachedNdefMessage = null
                       btnClone.isEnabled = false
                    }
                } catch (e: Exception) {
                    tvStatus.text = getString(R.string.nfc_msg_read_error)
                    etRead.setText("")
                }
            } else {
                val extraInfo = getTagInfo(tag)
                tvStatus.text = getString(R.string.msg_tag_non_ndef, extraInfo)
                etRead.setText("")
            }
        }
    }

    private fun displayNdefMessages(msgs: List<NdefMessage>, tag: Tag? = null) {
        if (msgs.isNotEmpty()) {
            cachedNdefMessage = msgs[0]
            btnClone.isEnabled = true
        }
    
        val builder = StringBuilder()
        for (msg in msgs) {
            for (record in msg.records) {
                val payload = parseTextRecord(record)
                if (payload != null) {
                    builder.append(payload).append("\n")
                } else {
                    // Try to detect other types
                    if (record.toUri() != null) {
                         builder.append(record.toUri().toString()).append("\n")
                    } else if (String(record.type) == "application/vnd.wfa.wsc") {
                        builder.append("WiFi Config Record\n")
                    } else {
                        builder.append("[Type: ${String(record.type)}]\n")
                    }
                }
            }
        }
        
        var statusText = getString(R.string.msg_read_success)
        if (tag != null) {
            statusText += "\n${getTagInfo(tag)}"
        }
        
        tvStatus.text = statusText
        tvStatus.setTextColor(getColor(R.color.text_primary))
        val finalContent = builder.toString().trim()
        etRead.setText(finalContent)
        checkAndPromptAlipayDecode(finalContent)
    }

    private fun getTagInfo(tag: Tag): String {
        val sb = StringBuilder()
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                sb.append(if (ndef.isWritable) getString(R.string.msg_tag_writable) else getString(R.string.msg_tag_locked_readonly))
                sb.append(" | ")
                sb.append(ndef.type.substringAfterLast(".")) // Compact type name
                sb.append(" | ")
                sb.append("${ndef.maxSize}字节")
            } else {
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    sb.append(getString(R.string.msg_tag_unformatted))
                } else {
                     sb.append(getString(R.string.msg_tag_readonly_non_ndef))
                }
            }
            // Add prominent tech info if generic
            if (sb.isEmpty()) {
                 sb.append(tag.techList.joinToString { it.substringAfterLast('.') })
            }
        } catch (e: Exception) {
            sb.append(getString(R.string.msg_tag_unavailable_info))
        }
        return sb.toString()
    }

    private fun checkAndPromptAlipayDecode(content: String) {
        if (content.contains("https://render.alipay.com/p/s/ulink/")) {
            AlertDialog.Builder(this)
                .setTitle(R.string.title_detect_alipay)
                .setMessage(R.string.msg_detect_alipay)
                .setPositiveButton(R.string.btn_decode) { _, _ ->
                     val decoded = decodeAlipayLink(content)
                     etRead.setText(decoded)
                     
                     val code = extractCodeFromDecoded(decoded)
                     if (code != null) {
                         promptGenerateQr(code)
                     }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun decodeAlipayLink(content: String): String {
        try {
            val urlStart = content.indexOf("https://render.alipay.com")
            if (urlStart == -1) return content
            
            var urlEnd = content.indexOfAny(charArrayOf(' ', '\n', '\t'), urlStart)
            if (urlEnd == -1) urlEnd = content.length
            
            val fullUrl = content.substring(urlStart, urlEnd)
            val uri = android.net.Uri.parse(fullUrl)
            val schemeParam = uri.getQueryParameter("scheme")
            
            if (!schemeParam.isNullOrEmpty()) {
                // Logic from Ali-NFC2QR: Decode scheme twice
                val firstDecode = java.net.URLDecoder.decode(schemeParam, "UTF-8")
                val secondDecode = java.net.URLDecoder.decode(firstDecode, "UTF-8")
                
                val sb = StringBuilder(content)
                sb.append("\n\n").append(getString(R.string.msg_decode_result_header)).append("\n")
                sb.append(getString(R.string.msg_decode_original_link, secondDecode))
                
                // Extract payment code (codeContent)
                try {
                    val schemeUri = android.net.Uri.parse(secondDecode)
                    val codeContent = schemeUri.getQueryParameter("codeContent")
                    
                    if (!codeContent.isNullOrEmpty()) {
                        // Remove 'noT' param if exists
                        var finalCode = codeContent
                        if (finalCode.contains("?")) {
                            val codeUri = android.net.Uri.parse(finalCode)
                            val builder = codeUri.buildUpon()
                            
                            // Rebuild query params without noT
                            builder.clearQuery()
                            for (param in codeUri.queryParameterNames) {
                                if (param != "noT") {
                                    builder.appendQueryParameter(param, codeUri.getQueryParameter(param))
                                }
                            }
                            finalCode = builder.build().toString()
                        }
                        
                        sb.append("\n").append(getString(R.string.msg_decode_payment_code, finalCode))
                    } else {
                         // Fallback check for other params just in case
                         val qrcode = schemeUri.getQueryParameter("qrcode")
                         if (!qrcode.isNullOrEmpty()) {
                             sb.append("\n").append(getString(R.string.msg_decode_payment_qr, qrcode))
                         }
                    }
                } catch (e: Exception) {
                    sb.append("\n").append(getString(R.string.msg_decode_param_fail, e.message))
                }
                
                return sb.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return content
    }

    private fun extractCodeFromDecoded(decoded: String): String? {
        // Simple extraction based on the text format we just built
        val marker = getString(R.string.search_qr_link_marker)
        val start = decoded.indexOf(marker)
        if (start != -1) {
             val lineStart = decoded.indexOf("\n", start) // End of label line
             if (lineStart != -1) {
                 val codeStart = lineStart + 1
                 var codeEnd = decoded.indexOf("\n", codeStart)
                 if (codeEnd == -1) codeEnd = decoded.length
                 return decoded.substring(codeStart, codeEnd).trim()
             }
        }
        return null
    }

    private fun promptGenerateQr(code: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_generate_qr)
            .setMessage(R.string.msg_generate_qr)
            .setPositiveButton(R.string.btn_generate) { _, _ ->
                 generateAndShowQr(code)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun generateAndShowQr(content: String, titleResId: Int = R.string.title_generate_qr, hintResId: Int = R.string.hint_qr_screenshot) {
        try {
            // Increase size for better quality screenshot
            val size = 1024
            val hints = java.util.HashMap<com.google.zxing.EncodeHintType, Any>()
            hints[com.google.zxing.EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[com.google.zxing.EncodeHintType.MARGIN] = 1
            
            val bitMatrix = com.google.zxing.MultiFormatWriter().encode(
                content,
                com.google.zxing.BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * size + x] = android.graphics.Color.BLACK
                    } else {
                        pixels[y * size + x] = android.graphics.Color.WHITE
                    }
                }
            }
            
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            
            // Container layout
            val container = android.widget.LinearLayout(this)
            container.orientation = android.widget.LinearLayout.VERTICAL
            container.gravity = android.view.Gravity.CENTER
            val containerPadding = (16 * resources.displayMetrics.density).toInt()
            container.setPadding(containerPadding, containerPadding, containerPadding, containerPadding)
            
            val imageView = android.widget.ImageView(this)
            imageView.setImageBitmap(bitmap)
            imageView.adjustViewBounds = true
            imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            
            val layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = (12 * resources.displayMetrics.density).toInt()
            
            val textView = TextView(this)
            textView.text = getString(hintResId)
            textView.textSize = 12f
            textView.gravity = android.view.Gravity.CENTER
            textView.setTextColor(getColor(R.color.text_secondary))
            
            container.addView(imageView, layoutParams)
            container.addView(textView)
            
            val dialog = AlertDialog.Builder(this)
                .setTitle(titleResId)
                .setView(container)
                .setPositiveButton(android.R.string.ok, null)
                .create()
            
            dialog.show()
            
            // Try to make dialog wider to show larger image
            val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.msg_gen_qr_fail, e.message), Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun parseTextRecord(record: NdefRecord): String? {
        // TNF_WELL_KNOWN with RTD_TEXT
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && java.util.Arrays.equals(record.type, NdefRecord.RTD_TEXT)) {
            try {
                val payload = record.payload
                val textEncoding = if ((payload[0].toInt() and 128) == 0) Charset.forName("UTF-8") else Charset.forName("UTF-16")
                val languageCodeLength = payload[0].toInt() and 63
                // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
                return String(payload, 1 + languageCodeLength, payload.size - languageCodeLength - 1, textEncoding)
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    private fun writeTag(tag: Tag) {
        val message = try {
            if (pendingWriteMessage != null) {
                pendingWriteMessage!!
            } else {
                 when (currentTypeIndex) {
                    0 -> NdefMessage(arrayOf(createTextRecord(pendingWriteText!!))) // Text
                    1 -> NdefMessage(arrayOf(NdefRecord.createUri(pendingWriteText!!))) // URL
                    2 -> NdefMessage(arrayOf(NdefRecord.createUri("tel:${pendingWriteText!!}"))) // Phone
                    3 -> NdefMessage(arrayOf(NdefRecord.createApplicationRecord(selectedAppPackage!!))) // App
                    4 -> createWifiRecord(etWifiSsid.text.toString(), etWifiPass.text.toString()) // WiFi
                    5 -> createBluetoothRecord(etBtMac.text.toString()) // Bluetooth
                    6 -> createVCardRecord(etContactName.text.toString(), etContactPhone.text.toString(), etContactEmail.text.toString()) // Contact
                    7 -> NdefMessage(arrayOf(NdefRecord.createUri("geo:${etLocLat.text},${etLocLon.text}"))) // Location
                    else -> return
                }
            }
        } catch (e: Exception) {
             runOnUiThread { Toast.makeText(this, getString(R.string.msg_data_format_err, e.message), Toast.LENGTH_SHORT).show() }
             return
        }

        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    runOnUiThread { Toast.makeText(this, getString(R.string.msg_tag_readonly), Toast.LENGTH_SHORT).show() }
                    return
                }
                if (ndef.maxSize < message.byteArrayLength) {
                    runOnUiThread { Toast.makeText(this, getString(R.string.msg_tag_capacity_low), Toast.LENGTH_SHORT).show() }
                    return
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                finishAction(getString(R.string.nfc_msg_write_success))
            } else {
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(message)
                    formatable.close()
                    finishAction(getString(R.string.nfc_msg_write_success))
                } else {
                    runOnUiThread { Toast.makeText(this, getString(R.string.msg_tag_not_writable_ndef), Toast.LENGTH_SHORT).show() }
                }
            }
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, getString(R.string.nfc_msg_write_fail), Toast.LENGTH_SHORT).show() }
            e.printStackTrace()
        }
    }
    
    // Create WiFi Configuration Record (application/vnd.wfa.wsc)
    private fun createWifiRecord(ssid: String, pass: String): NdefMessage {
        // Basic WSC record structure for Android to recognize
        // CRED: https://android.googlesource.com/platform/packages/apps/Nfc/+/master/src/com/android/nfc/NfcWifiProtectedSetup.java
        
        val ssidBytes = ssid.toByteArray(Charset.forName("US-ASCII"))
        // Note: This is simplified. Real WSC requires proper ID attributes.
        
        // Let's use a simpler known working binary structure for WPA2-Personal
        // Attribute ID (2) + Length (2) + Value
        
        // 1. OOB Credential (0x100E) attribute wrapping everything?
        // Actually top level is list of attributes.
        
        // Credential Attribute ID: 0x100E
        // Since we need to calculate length, we write inner attrs to a buffer first.
        val credParams = ByteArrayOutputStream()
        
        // Network Index (0x1026) -> 1
        writeWscAttr(credParams, 0x1026, byteArrayOf(1))
        
        // SSID (0x1045)
        writeWscAttr(credParams, 0x1045, ssidBytes)
        
        // Authentication Type (0x1003) -> 0x20 (WPA2PSK) or 0x01 (Open)
        val authBytes = byteArrayOf(0, if (pass.isEmpty()) 1 else 0x20) // short 0x0020
        writeWscAttr(credParams, 0x1003, authBytes)
        
        // Encryption Type (0x100F) -> 0x08 (AES) or 0x01 (None)
        // 0x04 (TKIP), 0x08 (AES)
        val encBytes = byteArrayOf(0, if (pass.isEmpty()) 1 else 8)
        writeWscAttr(credParams, 0x100F, encBytes)
        
        // Network Key (0x1027) - The password
        if (pass.isNotEmpty()) {
            writeWscAttr(credParams, 0x1027, pass.toByteArray(Charset.forName("US-ASCII")))
        }
        
        // MAC Address (0x1020) - Optional, often FF:FF:FF:FF:FF:FF
        // writeWscAttr(credParams, 0x1020, byteArrayOf(-1, -1, -1, -1, -1, -1))
        
        // Compose final payload: OOB wrapper is often not needed for raw NDEF record on android?
        // Android docs say: protocol: application/vnd.wfa.wsc
        // Expected payload is the "WSC-encoded WLAN Configuration".
        
        // Credential Header
        val credData = credParams.toByteArray()
        val finalStream = ByteArrayOutputStream()
        
        // Credential Attribute (0x100E)
        writeWscAttr(finalStream, 0x100E, credData)
        
        val record = NdefRecord.createMime("application/vnd.wfa.wsc", finalStream.toByteArray())
        return NdefMessage(arrayOf(record))
    }
    
    private fun writeWscAttr(stream: ByteArrayOutputStream, attrId: Int, data: ByteArray) {
        stream.write((attrId shr 8) and 0xFF)
        stream.write(attrId and 0xFF)
        stream.write((data.size shr 8) and 0xFF)
        stream.write(data.size and 0xFF)
        stream.write(data)
    }

    private fun lockTag(tag: Tag) {
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (ndef.canMakeReadOnly()) {
                    if (ndef.makeReadOnly()) {
                        finishAction(getString(R.string.msg_lock_success))
                    } else {
                        finishAction(getString(R.string.msg_lock_fail))
                    }
                } else {
                    finishAction("标签不支持只读锁定")
                }
                ndef.close()
            } else {
                finishAction("非NDEF标签，无法使用标准锁定")
            }
        } catch (e: Exception) {
            finishAction("锁定出错: ${e.message}")
        }
    }

    private fun setTagPassword(tag: Tag, password: String?) {
        if (password == null || password.length != 4) return
        
        val ultralight = android.nfc.tech.MifareUltralight.get(tag)
        if (ultralight != null) {
            try {
                ultralight.connect()
                
                // Very basic NTAG detection based on page count isn't perfect but practical
                // Only support NTAG213/215/216 logic
                // PWD Page addresses
                when {
                    ultralight.type == android.nfc.tech.MifareUltralight.TYPE_ULTRALIGHT_C -> {
                         finishAction("不支持此类标签 (Ultralight C)")
                         return
                    }
                    // Approx detection
                    // Note: This logic assumes standard NTAG21x
                    // Trying to determine based on capacity isn't always available directly in API < 4k?
                    // We can try to guess.
                    // NTAG213 ~ 45 pages. 215 ~ 135. 216 ~ 231.
                    // But we can't easily get max page. 
                    // Let's rely on standard practice or try/catch.
                    // Suggestion: Write to 43 first (213), fail?
                    else -> {} // Default to 213 check logic later?
                }
                
                // Let's implement robust logic: try GET_VERSION command (0x60)? 
                // Response: 00 04 04 02 01 00 11 03 (NTAG213)
                // storage byte (6th, index 5) -> 0x0F (213), 0x11 (215), 0x13 (216)
                
                val version = ultralight.transceive(byteArrayOf(0x60))
                val storage = if (version.size >= 6) version[6] else 0 // version[6] indicates storage size in NTAG
                
                // Values for NTAG: 0x0F=213, 0x11=215, 0x13=216
                val (uPwdPage, uPackPage, uAuth0Page) = when (storage.toInt()) {
                    0x0F -> Triple(43, 44, 41) // NTAG213
                    0x11 -> Triple(133, 134, 131) // NTAG215
                    0x13 -> Triple(229, 230, 227) // NTAG216
                    else -> {
                        finishAction("未知或不支持的NTAG型号")
                        return
                    }
                }
                
                // 1. Write PWD
                val pwdBytes = password.toByteArray(Charset.forName("US-ASCII")) // 4 numeric chars
                // Ensure 4 bytes
                val safePwd = ByteArray(4)
                System.arraycopy(pwdBytes, 0, safePwd, 0, pwdBytes.size.coerceAtMost(4))
                
                ultralight.writePage(uPwdPage, safePwd)
                
                // 2. Write PACK (Password ACK) - Optional but good practice. Using default 00 00
                ultralight.writePage(uPackPage, byteArrayOf(0, 0, 0, 0))
                
                // 3. Configure AUTH0 (Start of protection) and ACCESS (Prot mode)
                // Read current config pages to avoid overwriting other bits
                // AUTH0 is at byte 3 of uAuth0Page
                // ACCESS is at byte 0 of uAuth0Page + 1?
                // NTAG213: Page 41 has AUTH0 at byte 3. Page 42 has ACCESS at byte 0.
                
                // Read Page 41 (and 42, 43, 44 usually come with read(41) -> 4 pages)
                val configPages = ultralight.readPages(uAuth0Page)
                
                // Update AUTH0 (Byte 3 of first page) to 0x04 (Protect from page 4)
                val pageAuth0 = ByteArray(4)
                System.arraycopy(configPages, 0, pageAuth0, 0, 4)
                pageAuth0[3] = 0x04 // Start protection from Page 4
                ultralight.writePage(uAuth0Page, pageAuth0)
                
                // Update ACCESS (Byte 0 of second page - uAuth0Page + 1)
                // Bit 7: 0 = Write Prot, 1 = Read/Write Prot. We want Write Prot (0).
                val pageAccess = ByteArray(4)
                System.arraycopy(configPages, 4, pageAccess, 0, 4)
                pageAccess[0] = (pageAccess[0].toInt() and 0x7F).toByte() // Clear bit 7
                ultralight.writePage(uAuth0Page + 1, pageAccess)
                
                finishAction(getString(R.string.msg_pwd_success))
                ultralight.close()
                
            } catch (e: Exception) {
                finishAction("设置密码失败: ${e.message}")
                e.printStackTrace()
            }
        } else {
             finishAction("不支持的标签技术 (需Ultralight/NTAG)")
        }
    }

    private fun removeTagPassword(tag: Tag, password: String?) {
        if (password == null || password.length != 4) return
        
        val ultralight = android.nfc.tech.MifareUltralight.get(tag)
        if (ultralight != null) {
            try {
                ultralight.connect()
                
                // Authenticate first
                val pwdBytes = password.toByteArray(Charset.forName("US-ASCII"))
                val authCmd = ByteArray(5)
                authCmd[0] = 0x1B.toByte() // PWD_AUTH
                System.arraycopy(pwdBytes, 0, authCmd, 1, 4)
                
                try {
                    ultralight.transceive(authCmd)
                    // If no exception, auth success usually, or check response PACK
                } catch (e: Exception) {
                    finishAction("验证失败或无法移除")
                    return
                }
                
                // Detect version again
                val version = ultralight.transceive(byteArrayOf(0x60))
                val storage = if (version.size >= 6) version[6] else 0
                val (uPwdPage, _, uAuth0Page) = when (storage.toInt()) {
                    0x0F -> Triple(43, 44, 41)
                    0x11 -> Triple(133, 134, 131)
                    0x13 -> Triple(229, 230, 227)
                    else -> {
                       finishAction("未知型号")
                       return
                    }
                }
                
                // To remove protection, set AUTH0 to 0xFF (disabled)
                val configPages = ultralight.readPages(uAuth0Page)
                
                val pageAuth0 = ByteArray(4)
                System.arraycopy(configPages, 0, pageAuth0, 0, 4)
                pageAuth0[3] = 0xFF.toByte() // Disable auth
                ultralight.writePage(uAuth0Page, pageAuth0)
                
                // Optionally clear PWD to 00 00 00 00? Or leave it.
                // Better clear it if removing protection.
                ultralight.writePage(uPwdPage, byteArrayOf(0, 0, 0, 0))
                
                finishAction("密码保护已移除")
                ultralight.close()
                
            } catch (e: Exception) {
                finishAction("操作失败: ${e.message}")
            }
        } else {
             finishAction("不支持的标签技术")
        }
    }
    
    private fun finishAction(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            
            when (currentAction) {
                NfcAction.WRITE -> {
                    tvWriteStatus.text = msg
                    tvWriteStatus.setTextColor(getColor(R.color.brand_primary))
                    // Could enable batch writing here by NOT resetting action?
                    // For now, reset to Read for safety.
                }
                NfcAction.LOCK, NfcAction.SET_PWD, NfcAction.REMOVE_PWD -> {
                    tvSecurityStatus.text = msg
                    tvSecurityStatus.setTextColor(getColor(R.color.brand_primary))
                }
                else -> {
                    tvStatus.text = msg
                    tvStatus.setTextColor(getColor(R.color.brand_primary))
                }
            }

            currentAction = NfcAction.READ
            pendingPassword = null
        }
    }

    private fun createTextRecord(text: String): NdefRecord {
        val lang = Locale.getDefault().language
        val textBytes = text.toByteArray(Charset.forName("UTF-8"))
        val langBytes = lang.toByteArray(Charset.forName("US-ASCII"))
        val langLength = langBytes.size
        val payload = ByteArray(1 + langLength + textBytes.size)

        // Status byte: UTF-8 (bit 7 = 0), len of lang code (bit 5-0)
        payload[0] = (langLength and 0x3F).toByte()

        System.arraycopy(langBytes, 0, payload, 1, langLength)
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textBytes.size)

        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
    }

    private fun enableForegroundDispatch() {
        if (nfcAdapter != null && nfcAdapter!!.isEnabled) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )

            val ndefFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
            try {
                ndefFilter.addDataType("*/*")
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("Fail", e)
            }
            // Also catch TECH and TAG for unformatted or non-NDEF tags if we want to format them
            val filters = arrayOf(
                ndefFilter,
                IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
            )
            
            // Tech lists? Null means all techs? 
            // documentation says: "If techLists is null, the system will look for any tag." for TECH_DISCOVERED? No, null means no tech list matching.
            // But we can pass null for techLists if we don't care about specific tech matching in dispatch.
            
            nfcAdapter!!.enableForegroundDispatch(this, pendingIntent, filters, null)
        }
    }

    private fun disableForegroundDispatch() {
        if (nfcAdapter != null && nfcAdapter!!.isEnabled) {
            nfcAdapter!!.disableForegroundDispatch(this)
        }
    }

    private fun createBluetoothRecord(mac: String): NdefMessage {
        val parts = mac.split(":")
        val macBytes = ByteArray(6)
        for (i in 0 until 6) {
           macBytes[i] = Integer.parseInt(parts[i], 16).toByte()
        }
        // OOB Bluetooth Address is Little Endian
        val reversedMac = macBytes.reversedArray()
        
        // Payload: [Length (2 bytes)] [BD_ADDR (6 bytes)]
        val payload = ByteArray(2 + 6)
        val len = payload.size
        payload[0] = (len and 0xFF).toByte()
        payload[1] = ((len shr 8) and 0xFF).toByte()
        
        System.arraycopy(reversedMac, 0, payload, 2, 6)
        
        val record = NdefRecord.createMime("application/vnd.bluetooth.ep.oob", payload)
        return NdefMessage(arrayOf(record))
    }

    private fun createVCardRecord(name: String, phone: String, email: String): NdefMessage {
        val vcard = StringBuilder()
        vcard.append("BEGIN:VCARD\n")
        vcard.append("VERSION:3.0\n")
        if (name.isNotEmpty()) {
             vcard.append("N:").append(name).append(";;;\n")
             vcard.append("FN:").append(name).append("\n")
        }
        if (phone.isNotEmpty()) {
             vcard.append("TEL;TYPE=CELL:").append(phone).append("\n")
        }
        if (email.isNotEmpty()) {
             vcard.append("EMAIL:").append(email).append("\n")
        }
        vcard.append("END:VCARD")
        
        val record = NdefRecord.createMime("text/vcard", vcard.toString().toByteArray(Charset.forName("UTF-8")))
        return NdefMessage(arrayOf(record))
    }

    private fun getCurrentLocation() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
             androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1002)
             Toast.makeText(this, "需要位置权限", Toast.LENGTH_SHORT).show()
             return
        }
        
        try {
            val validProviders = listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            
            var bestLocation: android.location.Location? = null
            
            for (provider in validProviders) {
                val loc = locationManager.getLastKnownLocation(provider)
                if (loc != null) {
                    if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                        bestLocation = loc
                    }
                }
            }
            
            if (bestLocation != null) {
                 etLocLat.setText(bestLocation.latitude.toString())
                 etLocLon.setText(bestLocation.longitude.toString())
            } else {
                 Toast.makeText(this, "无法获取位置，请确保GPS已开启", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
             Toast.makeText(this, "获取位置出错: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
