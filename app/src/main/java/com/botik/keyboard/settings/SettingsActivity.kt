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
import com.botik.keyboard.translate.ClaudeModel
import com.botik.keyboard.translate.Languages
import com.botik.keyboard.translate.OfflineTranslator
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

        bindClaude()
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

    private fun bindClaude() {
        val apiKey = findViewById<TextInputEditText>(R.id.api_key)
        apiKey.setText(prefs.apiKey)
        apiKey.doAfterTextChanged { prefs.apiKey = it?.toString().orEmpty() }

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
        val themeGroup = findViewById<MaterialButtonToggleGroup>(R.id.theme_group)
        themeGroup.check(
            when (prefs.theme) {
                "dark" -> R.id.theme_dark
                "light" -> R.id.theme_light
                else -> R.id.theme_system
            },
        )
        themeGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            prefs.theme = when (id) {
                R.id.theme_dark -> "dark"
                R.id.theme_light -> "light"
                else -> "system"
            }
        }

        val height = findViewById<Slider>(R.id.key_height)
        height.value = (Math.round(prefs.keyHeightScale * 20f) / 20f).coerceIn(height.valueFrom, height.valueTo)
        height.addOnChangeListener { _, value, _ -> prefs.keyHeightScale = value }

        val haptics = findViewById<MaterialSwitch>(R.id.haptics)
        haptics.isChecked = prefs.haptics
        haptics.setOnCheckedChangeListener { _, v -> prefs.haptics = v }

        val sound = findViewById<MaterialSwitch>(R.id.sound)
        sound.isChecked = prefs.sound
        sound.setOnCheckedChangeListener { _, v -> prefs.sound = v }
    }
}
