package com.botik.keyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.botik.keyboard.Prefs
import com.botik.keyboard.R
import com.botik.keyboard.translate.Language
import com.botik.keyboard.translate.Languages
import com.botik.keyboard.translate.TranslationEngine
import com.botik.keyboard.translate.TranslationStyle

class BotikInputMethodService : InputMethodService(), KeyboardView.Listener {

    private lateinit var prefs: Prefs
    private lateinit var engine: TranslationEngine
    private val main = Handler(Looper.getMainLooper())

    private lateinit var root: View
    private lateinit var bar: View
    private lateinit var langChip: TextView
    private lateinit var preview: TextView
    private lateinit var progress: ProgressBar
    private lateinit var undoButton: TextView
    private lateinit var translateButton: TextView
    private lateinit var keyboard: KeyboardView
    private lateinit var langPanel: ScrollView
    private lateinit var langPanelContent: LinearLayout

    private var theme = KeyboardTheme.DARK
    private var vibrator: Vibrator? = null
    private var audio: AudioManager? = null

    private var letters = KeyboardLayouts.RU
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L
    private var lastSpaceTap = 0L
    private var selStart = 0
    private var selEnd = 0
    private var translationAllowed = true

    private enum class BarState { IDLE, DRAFT, TRANSLATING, RESULT, ERROR, REPLACED }

    private var barState = BarState.IDLE
    /** Text currently offered for insertion by tapping the preview. */
    private var offered: String? = null
    private var undo: Undo? = null

    private class FieldText(val text: String, val before: Int, val after: Int, val isSelection: Boolean)
    private class Undo(val original: String, val inserted: String, val isSelection: Boolean)

    private val draftRunnable = Runnable { updateDraft() }

    // ---- lifecycle ----

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        engine = TranslationEngine(prefs)
        letters = prefs.letters
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        engine.release()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        root = layoutInflater.inflate(R.layout.keyboard_view, null)
        bar = root.findViewById(R.id.translation_bar)
        langChip = root.findViewById(R.id.lang_chip)
        preview = root.findViewById(R.id.preview)
        progress = root.findViewById(R.id.progress)
        undoButton = root.findViewById(R.id.undo)
        translateButton = root.findViewById(R.id.translate)
        keyboard = root.findViewById(R.id.keyboard)
        langPanel = root.findViewById(R.id.lang_panel)
        langPanelContent = root.findViewById(R.id.lang_panel_content)

        keyboard.listener = this
        keyboard.previewHeadroom = resources.displayMetrics.density * 48f
        langChip.setOnClickListener { toggleLanguagePanel() }
        translateButton.setOnClickListener { onTranslatePressed() }
        preview.setOnClickListener { insertOffered() }
        undoButton.setOnClickListener { undoTranslation() }

        applyTheme()
        return root
    }

    /** Keeps the translation bar visible in landscape instead of a fullscreen extract editor. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyTheme()
        keyboard.keyHeightScale = prefs.keyHeightScale
        hideLanguagePanel()

        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        val password = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) || cls == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        // Never send passwords or numeric fields anywhere.
        translationAllowed = !password && !numeric && info.inputType != InputType.TYPE_NULL

        setLayout(if (numeric) KeyboardLayouts.SYMBOLS else letters)
        keyboard.enterLabel = enterLabelFor(info)
        selStart = info.initialSelStart
        selEnd = info.initialSelEnd
        shift = ShiftState.OFF
        updateAutoShift()

        if (!restarting) {
            undo = null
            setBarState(BarState.IDLE)
        }
        if (translationAllowed) {
            engine.warmUp()
            scheduleDraft()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        main.removeCallbacks(draftRunnable)
        engine.cancel()
        if (::keyboard.isInitialized) keyboard.cancelAllPointers()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        updateAutoShift()
        if (barState == BarState.TRANSLATING) return
        // Our own replacement also lands here: keep "undo" offered until the user edits the text.
        if (barState == BarState.REPLACED && stillHoldsReplacement()) return
        if (barState == BarState.REPLACED || barState == BarState.RESULT || barState == BarState.ERROR) {
            undo = null
            setBarState(BarState.IDLE)
        }
        scheduleDraft()
    }

    // ---- keys ----

    override fun onKey(key: Key) {
        val ic = currentInputConnection ?: return
        when (key.type) {
            KeyType.CHAR -> {
                ic.commitText(if (shift != ShiftState.OFF) key.label.uppercase() else key.label, 1)
                if (shift == ShiftState.ONCE) setShift(ShiftState.OFF)
            }
            KeyType.SPACE -> onSpace(ic)
            KeyType.ENTER -> onEnter(ic)
            KeyType.SYMBOLS -> setLayout(KeyboardLayouts.SYMBOLS)
            KeyType.SYMBOLS_MORE -> setLayout(KeyboardLayouts.SYMBOLS_MORE)
            KeyType.LETTERS -> setLayout(letters)
            KeyType.LANGUAGE -> {
                letters = if (letters == KeyboardLayouts.RU) KeyboardLayouts.EN else KeyboardLayouts.RU
                prefs.letters = letters
                setLayout(letters)
            }
            KeyType.SHIFT, KeyType.DELETE -> Unit
        }
    }

    override fun onAltChar(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(if (shift != ShiftState.OFF) text.uppercase() else text, 1)
        if (shift == ShiftState.ONCE) setShift(ShiftState.OFF)
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        if (selStart != selEnd) {
            ic.commitText("", 1)
        } else {
            // A key event (not deleteSurroundingText) removes emoji and surrogate pairs whole.
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    override fun onShift() {
        val now = SystemClock.uptimeMillis()
        val next = when {
            shift == ShiftState.LOCKED -> ShiftState.OFF
            shift == ShiftState.ONCE && now - lastShiftTap < 350 -> ShiftState.LOCKED
            shift == ShiftState.ONCE -> ShiftState.OFF
            else -> ShiftState.ONCE
        }
        lastShiftTap = now
        setShift(next)
    }

    override fun onCursorMove(steps: Int) {
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
    }

    override fun onPressFeedback(key: Key) {
        if (prefs.haptics) {
            val v = vibrator
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else {
                    v.vibrate(VibrationEffect.createOneShot(12, 80))
                }
            }
        }
        if (prefs.sound) {
            val fx = when (key.type) {
                KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio?.playSoundEffect(fx, -1f)
        }
    }

    private fun onSpace(ic: InputConnection) {
        val now = SystemClock.uptimeMillis()
        val before = ic.getTextBeforeCursor(2, 0)
        // Double space after a word types ". " like on most phone keyboards.
        if (now - lastSpaceTap < 450 && before != null && before.length == 2 &&
            before[1] == ' ' && before[0].isLetterOrDigit()
        ) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
            lastSpaceTap = 0L
        } else {
            ic.commitText(" ", 1)
            lastSpaceTap = now
        }
    }

    private fun onEnter(ic: InputConnection) {
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = info != null && info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendKeyChar('\n')
        }
    }

    private fun enterLabelFor(info: EditorInfo): String {
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "⏎"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_SEND -> "➤"
            EditorInfo.IME_ACTION_GO -> "→"
            EditorInfo.IME_ACTION_NEXT -> "⇥"
            EditorInfo.IME_ACTION_DONE -> "✓"
            else -> "⏎"
        }
    }

    private fun setLayout(id: String) {
        keyboard.layout = KeyboardLayouts.byId(id)
        keyboard.spaceLabel = when (id) {
            KeyboardLayouts.RU -> "Русский"
            KeyboardLayouts.EN -> "English"
            else -> ""
        }
        if (id != KeyboardLayouts.RU && id != KeyboardLayouts.EN) setShift(ShiftState.OFF) else updateAutoShift()
    }

    private fun setShift(state: ShiftState) {
        shift = state
        if (::keyboard.isInitialized) keyboard.shiftState = state
    }

    /** Capitalises the first letter of a sentence when the editor asks for it. */
    private fun updateAutoShift() {
        if (shift == ShiftState.LOCKED || !::keyboard.isInitialized) return
        val id = keyboard.layout.id
        if (id != KeyboardLayouts.RU && id != KeyboardLayouts.EN) return
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val caps = info.inputType != InputType.TYPE_NULL && ic.getCursorCapsMode(info.inputType) != 0
        setShift(if (caps) ShiftState.ONCE else ShiftState.OFF)
    }

    // ---- translation ----

    private fun readField(): FieldText? {
        val ic = currentInputConnection ?: return null
        if (selStart != selEnd) {
            val selected = ic.getSelectedText(0)?.toString()
            if (!selected.isNullOrBlank()) return FieldText(selected, 0, 0, isSelection = true)
        }
        val before = ic.getTextBeforeCursor(MAX_CHARS, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(MAX_CHARS, 0)?.toString().orEmpty()
        return FieldText(before + after, before.length, after.length, isSelection = false)
    }

    private fun replaceField(field: FieldText, replacement: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (field.isSelection) {
            ic.commitText(replacement, 1)
        } else {
            ic.finishComposingText()
            ic.deleteSurroundingText(field.before, field.after)
            ic.commitText(replacement, 1)
        }
        ic.endBatchEdit()
    }

    private fun onTranslatePressed() {
        hideLanguagePanel()
        if (!translationAllowed) return
        if (barState == BarState.TRANSLATING) {
            engine.cancel()
            setBarState(BarState.IDLE)
            return
        }
        val field = readField()
        val text = field?.text?.trim().orEmpty()
        if (field == null || text.isEmpty()) {
            setBarState(BarState.ERROR, getString(R.string.bar_nothing))
            return
        }
        main.removeCallbacks(draftRunnable)
        setBarState(BarState.TRANSLATING)
        engine.translate(text, prefs.target, object : TranslationEngine.Callback {
            override fun onPartial(text: String) {
                if (barState == BarState.TRANSLATING) preview.text = text
            }

            override fun onDone(text: String) {
                if (prefs.autoReplace) {
                    val current = readField()
                    // Replace only if the user has not edited the field meanwhile.
                    if (current != null && current.isSelection == field.isSelection &&
                        current.text.trim() == field.text.trim()
                    ) {
                        replaceField(current, text)
                        undo = Undo(field.text, text, field.isSelection)
                        setBarState(BarState.REPLACED, text)
                        return
                    }
                }
                offered = text
                setBarState(BarState.RESULT, text)
            }

            override fun onError(message: String) {
                setBarState(BarState.ERROR, message)
            }
        })
    }

    private fun insertOffered() {
        val text = offered ?: return
        val field = readField() ?: return
        replaceField(field, text)
        undo = Undo(field.text, text, field.isSelection)
        offered = null
        setBarState(BarState.REPLACED, text)
    }

    private fun stillHoldsReplacement(): Boolean {
        val u = undo ?: return false
        val ic = currentInputConnection ?: return false
        return if (u.isSelection) {
            ic.getTextBeforeCursor(u.inserted.length, 0)?.toString() == u.inserted
        } else {
            readField()?.text == u.inserted
        }
    }

    private fun undoTranslation() {
        val u = undo ?: return
        val ic = currentInputConnection ?: return
        undo = null
        if (u.isSelection) {
            val before = ic.getTextBeforeCursor(u.inserted.length, 0)?.toString()
            if (before == u.inserted) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(u.inserted.length, 0)
                ic.commitText(u.original, 1)
                ic.endBatchEdit()
            }
        } else {
            val field = readField()
            if (field != null && field.text == u.inserted) replaceField(field, u.original)
        }
        setBarState(BarState.IDLE)
        scheduleDraft()
    }

    private fun scheduleDraft() {
        main.removeCallbacks(draftRunnable)
        if (translationAllowed && ::preview.isInitialized) main.postDelayed(draftRunnable, DRAFT_DELAY_MS)
    }

    /** Instant preview while typing: a cached Claude result or the offline ML Kit draft. */
    private fun updateDraft() {
        if (barState == BarState.TRANSLATING || barState == BarState.REPLACED) return
        val text = readField()?.text?.trim().orEmpty()
        if (text.isEmpty()) {
            offered = null
            setBarState(BarState.IDLE)
            return
        }
        val target = prefs.target
        engine.cached(text, target)?.let {
            offered = it
            setBarState(BarState.RESULT, it)
            return
        }
        // The on-device model translates from Russian only.
        if (!prefs.livePreview || text.length > MAX_DRAFT_CHARS || text.none { it.isCyrillic() }) return
        engine.offline.translate(text, target) { draft ->
            if (draft == null || barState == BarState.TRANSLATING || barState == BarState.REPLACED) return@translate
            // Drop stale drafts if the text changed while the model was working.
            if (readField()?.text?.trim() != text) return@translate
            offered = draft
            setBarState(BarState.DRAFT, draft)
        }
    }

    private fun setBarState(state: BarState, text: String? = null) {
        barState = state
        if (!::preview.isInitialized) return
        progress.visibility = if (state == BarState.TRANSLATING) View.VISIBLE else View.GONE
        undoButton.visibility = if (state == BarState.REPLACED && undo != null) View.VISIBLE else View.GONE
        translateButton.text = if (state == BarState.TRANSLATING) "■" else getString(R.string.translate_short)
        translateButton.isEnabled = translationAllowed
        translateButton.alpha = if (translationAllowed) 1f else 0.4f
        preview.setTextColor(
            when (state) {
                BarState.DRAFT -> theme.barHint
                BarState.ERROR -> 0xFFFF6B6B.toInt()
                BarState.IDLE -> theme.barHint
                else -> theme.barText
            },
        )
        preview.text = when {
            !translationAllowed -> getString(R.string.bar_disabled)
            state == BarState.IDLE ->
                if (engine.hasApiKey) getString(R.string.bar_hint, prefs.target.nativeName)
                else getString(R.string.bar_hint_no_key)
            state == BarState.TRANSLATING -> getString(R.string.bar_translating)
            state == BarState.REPLACED -> getString(R.string.bar_replaced)
            else -> text.orEmpty()
        }
        if (state != BarState.DRAFT && state != BarState.RESULT) offered = null
        updateLangChip()
    }

    // ---- language panel ----

    private fun updateLangChip() {
        val target = prefs.target
        langChip.text = "RU → ${target.flag} ${target.code.uppercase()}"
    }

    private fun toggleLanguagePanel() {
        if (langPanel.visibility == View.VISIBLE) hideLanguagePanel() else showLanguagePanel()
    }

    private fun showLanguagePanel() {
        buildLanguagePanel()
        langPanel.layoutParams = langPanel.layoutParams.apply { height = keyboard.height }
        langPanel.visibility = View.VISIBLE
        keyboard.visibility = View.INVISIBLE
        langPanel.scrollTo(0, 0)
    }

    private fun hideLanguagePanel() {
        if (!::langPanel.isInitialized) return
        langPanel.visibility = View.GONE
        keyboard.visibility = View.VISIBLE
    }

    private fun buildLanguagePanel() {
        val content = langPanelContent
        content.removeAllViews()

        content.addView(sectionTitle(getString(R.string.panel_style)))
        val styles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            TranslationStyle.AUTO to R.string.style_auto,
            TranslationStyle.CASUAL to R.string.style_casual,
            TranslationStyle.FORMAL to R.string.style_formal,
        ).forEach { (style, label) ->
            styles.addView(chip(getString(label), prefs.style == style) {
                prefs.style = style
                buildLanguagePanel()
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        content.addView(styles)

        val recent = prefs.recentCodes.map { Languages.find(it) }.distinct()
        content.addView(sectionTitle(getString(R.string.panel_recent)))
        addLanguageGrid(content, recent)
        content.addView(sectionTitle(getString(R.string.panel_all)))
        addLanguageGrid(content, Languages.ALL)
    }

    private fun addLanguageGrid(parent: LinearLayout, languages: List<Language>) {
        val current = prefs.target
        languages.chunked(3).forEach { rowLangs ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowLangs.forEach { lang ->
                row.addView(chip("${lang.flag} ${lang.nativeName}", lang == current) { selectTarget(lang) },
                    LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            repeat(3 - rowLangs.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(42), 1f))
            }
            parent.addView(row)
        }
    }

    private fun selectTarget(lang: Language) {
        prefs.target = lang
        hideLanguagePanel()
        offered = null
        setBarState(BarState.IDLE)
        scheduleDraft()
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(theme.hint)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(6), dp(8), dp(6), dp(4))
    }

    private fun chip(text: String, selected: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        maxLines = 1
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(if (selected) theme.onAccent else theme.text)
        background = if (selected) accentBackground(dp(10).toFloat()) else roundedBackground(theme.key, dp(10).toFloat())
        setOnClickListener {
            onPressFeedback(Key(text))
            onClick()
        }
    }

    // ---- theme ----

    private fun applyTheme() {
        if (!::root.isInitialized) return
        theme = KeyboardTheme.resolve(this, prefs)
        keyboard.theme = theme
        root.setBackgroundColor(theme.backgroundBottom)
        bar.setBackgroundColor(theme.backgroundTop)
        langPanel.setBackgroundColor(theme.backgroundBottom)
        langChip.background = roundedBackground(theme.chip, dp(17).toFloat())
        langChip.setTextColor(theme.text)
        translateButton.background = accentBackground(dp(18).toFloat())
        translateButton.setTextColor(theme.onAccent)
        undoButton.background = roundedBackground(theme.chip, dp(18).toFloat())
        undoButton.setTextColor(theme.text)
        setBarState(barState)
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun accentBackground(radius: Float) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(theme.accent, theme.accentEnd),
    ).apply { cornerRadius = radius }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun Char.isCyrillic() = this in '\u0400'..'\u04FF'

    companion object {
        private const val MAX_CHARS = 5000
        private const val MAX_DRAFT_CHARS = 1500
        private const val DRAFT_DELAY_MS = 350L
    }
}
