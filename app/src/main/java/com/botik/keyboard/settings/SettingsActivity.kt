package com.botik.keyboard.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.botik.keyboard.Prefs
import com.botik.keyboard.R
import com.botik.keyboard.ime.KeyboardTheme
import com.botik.keyboard.translate.ClaudeModel
import com.botik.keyboard.translate.Languages
import com.botik.keyboard.translate.OfflineTranslator
import com.botik.keyboard.translate.Provider
import com.botik.keyboard.translate.TranslationStyle
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var setupStatus: TextView
    private lateinit var btnEnable: MaterialButton
    private lateinit var btnChoose: MaterialButton
    private lateinit var downloadStatus: TextView
    private var offline: OfflineTranslator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        setupStatus = findViewById(R.id.setup_status)
        btnEnable = findViewById(R.id.btn_enable)
        btnChoose = findViewById(R.id.btn_choose)
        downloadStatus = findViewById(R.id.download_status)

        btnEnable.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        btnChoose.setOnClickListener { imm().showInputMethodPicker() }

        bindTranslation()
        bindOffline()
        bindLook()
    }

    override fun onResume() {
        super.onResume()
        refreshSetupStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The IME picker is a dialog: refresh when it closes.
        if (hasFocus) refreshSetupStatus()
    }

    override fun onDestroy() {
        offline?.close()
        super.onDestroy()
    }

    private fun imm() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private fun refreshSetupStatus() {
        val enabled = imm().enabledInputMethodList.any { it.packageName == packageName }
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
        setupStatus.setText(
            when {
                !enabled -> R.string.setup_not_enabled
                !selected -> R.string.setup_not_selected
                else -> R.string.setup_done
            },
        )
        btnEnable.visibility = if (enabled) View.GONE else View.VISIBLE
        btnChoose.visibility = if (enabled && !selected) View.VISIBLE else View.GONE
    }

    private fun bindTranslation() {
        val providers = Provider.entries
        val provider = findViewById<MaterialAutoCompleteTextView>(R.id.provider)
        provider.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, providers.map { it.title }))
        provider.setText(prefs.provider.title, false)
        provider.setOnItemClickListener { _, _, position, _ ->
            prefs.provider = providers[position]
            showProvider(providers[position])
        }
        showProvider(prefs.provider)

        bindKey(R.id.api_key, prefs.apiKey) { prefs.apiKey = it }
        bindKey(R.id.gemini_key, prefs.geminiKey) { prefs.geminiKey = it }
        bindKey(R.id.deepl_key, prefs.deeplKey) { prefs.deeplKey = it }

        val models = ClaudeModel.entries
        val model = findViewById<MaterialAutoCompleteTextView>(R.id.model)
        model.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, models.map { it.title }))
        model.setText(prefs.model.title, false)
        model.setOnItemClickListener { _, _, position, _ -> prefs.model = models[position] }

        val languages = Languages.ALL
        val target = findViewById<MaterialAutoCompleteTextView>(R.id.target)
        target.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, languages.map { "${it.flag}  ${it.nativeName}" }),
        )
        target.setText(prefs.target.let { "${it.flag}  ${it.nativeName}" }, false)
        target.setOnItemClickListener { _, _, position, _ ->
            prefs.target = languages[position]
            downloadStatus.text = ""
        }

        val styleGroup = findViewById<MaterialButtonToggleGroup>(R.id.style_group)
        styleGroup.check(
            when (prefs.style) {
                TranslationStyle.AUTO -> R.id.style_auto
                TranslationStyle.CASUAL -> R.id.style_casual
                TranslationStyle.FORMAL -> R.id.style_formal
            },
        )
        styleGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            prefs.style = when (id) {
                R.id.style_casual -> TranslationStyle.CASUAL
                R.id.style_formal -> TranslationStyle.FORMAL
                else -> TranslationStyle.AUTO
            }
        }

        val autoReplace = findViewById<MaterialSwitch>(R.id.auto_replace)
        autoReplace.isChecked = prefs.autoReplace
        autoReplace.setOnCheckedChangeListener { _, v -> prefs.autoReplace = v }
    }

    private fun bindKey(id: Int, value: String, save: (String) -> Unit) {
        val field = findViewById<TextInputEditText>(id)
        field.setText(value)
        field.doAfterTextChanged { save(it?.toString().orEmpty()) }
    }

    /** Shows the help text and key fields for the chosen provider only. */
    private fun showProvider(p: Provider) {
        findViewById<TextView>(R.id.provider_help).setText(
            when (p) {
                Provider.FREE -> R.string.help_free
                Provider.GEMINI -> R.string.help_gemini
                Provider.DEEPL -> R.string.help_deepl
                Provider.CLAUDE -> R.string.help_claude
                Provider.OFFLINE -> R.string.help_offline
            },
        )
        findViewById<View>(R.id.claude_group).visibility = if (p == Provider.CLAUDE) View.VISIBLE else View.GONE
        findViewById<View>(R.id.gemini_group).visibility = if (p == Provider.GEMINI) View.VISIBLE else View.GONE
        findViewById<View>(R.id.deepl_group).visibility = if (p == Provider.DEEPL) View.VISIBLE else View.GONE
    }

    private fun bindOffline() {
        val live = findViewById<MaterialSwitch>(R.id.live_preview)
        live.isChecked = prefs.livePreview
        live.setOnCheckedChangeListener { _, v -> prefs.livePreview = v }

        findViewById<MaterialButton>(R.id.btn_download).setOnClickListener { button ->
            val target = prefs.target
            val translator = offline ?: OfflineTranslator().also { offline = it }
            if (!translator.isSupported(target)) {
                downloadStatus.setText(R.string.offline_unsupported)
                return@setOnClickListener
            }
            button.isEnabled = false
            downloadStatus.setText(R.string.offline_downloading)
            translator.prepare(target, wifiOnly = false) { ok ->
                button.isEnabled = true
                downloadStatus.setText(if (ok) R.string.offline_ready else R.string.offline_failed)
            }
        }
    }

    private fun bindLook() {
        // "system" first, then every named theme.
        val themeIds = listOf("system") + KeyboardTheme.ALL.map { it.id }
        val themeTitles = listOf(getString(R.string.theme_system)) + KeyboardTheme.ALL.map { it.title }
        val theme = findViewById<MaterialAutoCompleteTextView>(R.id.theme)
        theme.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, themeTitles))
        theme.setText(themeTitles[themeIds.indexOf(prefs.theme).coerceAtLeast(0)], false)
        theme.setOnItemClickListener { _, _, position, _ -> prefs.theme = themeIds[position] }

        val height = findViewById<Slider>(R.id.key_height)
        height.value = (Math.round(prefs.keyHeightScale * 20f) / 20f).coerceIn(height.valueFrom, height.valueTo)
        height.addOnChangeListener { _, value, _ -> prefs.keyHeightScale = value }

        val haptics = findViewById<MaterialSwitch>(R.id.haptics)
        haptics.isChecked = prefs.haptics
        haptics.setOnCheckedChangeListener { _, v -> prefs.haptics = v }

        val sound = findViewById<MaterialSwitch>(R.id.sound)
        sound.isChecked = prefs.sound
        sound.setOnCheckedChangeListener { _, v -> prefs.sound = v }

        val numberRow = findViewById<MaterialSwitch>(R.id.number_row)
        numberRow.isChecked = prefs.numberRow
        numberRow.setOnCheckedChangeListener { _, v -> prefs.numberRow = v }

        val clipboard = findViewById<MaterialSwitch>(R.id.clipboard_history)
        clipboard.isChecked = prefs.clipboardHistory
        clipboard.setOnCheckedChangeListener { _, v -> prefs.clipboardHistory = v }
    }
}
