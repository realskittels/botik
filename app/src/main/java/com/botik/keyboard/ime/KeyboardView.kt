package com.botik.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.min

enum class ShiftState { OFF, ONCE, LOCKED }

/**
 * The key grid. Everything is drawn on one Canvas with preallocated paints and rects, so a
 * frame costs a few dozen rounded rects and glyphs and nothing is allocated while typing.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        /** A character, space, enter or mode key was released. */
        fun onKey(key: Key)
        /** A long-press alternative was chosen. */
        fun onAltChar(text: String)
        /** Fired on press and then repeatedly while backspace is held. */
        fun onDelete()
        fun onShift()
        /** Horizontal drag on the space bar; negative moves left. */
        fun onCursorMove(steps: Int)
        /** Every key press, for haptics and sound. */
        fun onPressFeedback(key: Key)
        /** Swipe left from backspace. */
        fun onDeleteWord()
        /** Long press on the space bar. */
        fun onSpaceLongPress()
    }

    var listener: Listener? = null

    var theme: KeyboardTheme = KeyboardTheme.AMETHYST
        set(value) {
            field = value
            rebuildShaders()
            invalidate()
        }

    var layout: KeyboardLayout = KeyboardLayouts.russian
        set(value) {
            if (field === value) return
            field = value
            cancelAllPointers()
            buildGeometry()
            requestLayout()
            invalidate()
        }

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Glyph on the enter key, depends on the editor's IME action. */
    var enterLabel: String = "⏎"
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Caption on the space bar. */
    var spaceLabel: String = ""
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Space above this view (the translation bar) that key previews may draw into. */
    var previewHeadroom: Float = 0f

    var keyHeightScale: Float = 1f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val baseRowHeight = dp(48f)
    private val gapX = dp(3f)
    private val gapY = dp(5f)
    private val padTop = dp(4f)
    private val padBottom = dp(6f)
    private val padSide = dp(3f)
    private val radius = dp(8f)
    private val shadowOffset = dp(1.5f)

    private class KeyGeom(val key: Key, val rect: RectF, val hit: RectF) {
        val upper: String = key.label.uppercase()
        var pressed = false
        var releasedAt = 0L
    }

    private var geoms: Array<KeyGeom> = emptyArray()
    private var rowHeight = baseRowHeight

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmpRect = RectF()

    private var letterSize = 0f
    private var modifierSize = 0f
    private var hintSize = 0f

    // ---- touch state ----

    private class Pointer(val id: Int) {
        var index = -1
        var downX = 0f
        var lastDragX = 0f
        var dragging = false
        var consumed = false
        var altShown: String? = null
        var longPress: Runnable? = null
    }

    private val pointers = SparseArray<Pointer>()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val cursorStep = dp(11f)
    private val deleteSwipe = dp(36f)
    private val longPressDelay = 300L
    private var deleteRepeat: Runnable? = null

    // ---- layout ----

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        rowHeight = baseRowHeight * keyHeightScale
        val height = (padTop + padBottom + rowHeight * layout.rows.size).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        buildGeometry()
        rebuildShaders()
    }

    private fun buildGeometry() {
        val w = width.toFloat()
        if (w <= 0f) return
        val unit = (w - 2 * padSide) / layout.columns
        val list = ArrayList<KeyGeom>(40)
        layout.rows.forEachIndexed { r, row ->
            val fixed = row.sumOf { if (it.weight == Key.FILL) 0.0 else it.weight.toDouble() }.toFloat()
            val fill = (layout.columns - fixed).coerceAtLeast(1f)
            val rowWidth = row.sumOf { (if (it.weight == Key.FILL) fill else it.weight).toDouble() }.toFloat() * unit
            var x = (w - rowWidth) / 2f
            val top = padTop + r * rowHeight
            row.forEachIndexed { i, key ->
                val kw = (if (key.weight == Key.FILL) fill else key.weight) * unit
                val rect = RectF(x + gapX / 2, top + gapY / 2, x + kw - gapX / 2, top + rowHeight - gapY / 2)
                // Hit areas tile the row with no gaps and extend to the screen edges.
                val hit = RectF(
                    if (i == 0) 0f else x,
                    if (r == 0) 0f else top,
                    if (i == row.lastIndex) w else x + kw,
                    if (r == layout.rows.lastIndex) height.toFloat() else top + rowHeight,
                )
                list += KeyGeom(key, rect, hit)
                x += kw
            }
        }
        geoms = list.toTypedArray()
        val keyWidth = unit - gapX
        letterSize = min(rowHeight * 0.42f, keyWidth * 0.62f)
        modifierSize = letterSize * 0.72f
        hintSize = letterSize * 0.46f
    }

    private fun rebuildShaders() {
        if (height <= 0) return
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            theme.backgroundTop, theme.backgroundBottom, Shader.TileMode.CLAMP,
        )
        accentPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            theme.accent, theme.accentEnd, Shader.TileMode.CLAMP,
        )
        shadowPaint.color = theme.keyShadow
        bubblePaint.color = theme.previewBubble
        bubbleShadowPaint.color = 0x40000000
    }

    // ---- drawing ----

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val now = SystemClock.uptimeMillis()
        var animating = false
        for (g in geoms) {
            val press = pressAmount(g, now)
            if (press > 0f && !g.pressed) animating = true
            drawKey(canvas, g, press)
        }
        // Preview bubbles are drawn last so they sit above neighbouring keys (and above
        // the translation bar for the top row: parents disable child clipping).
        for (i in 0 until pointers.size()) {
            val p = pointers.valueAt(i)
            val g = geoms.getOrNull(p.index) ?: continue
            if (g.key.type == KeyType.CHAR && !p.dragging) drawBubble(canvas, g, p.altShown)
        }
        if (animating) postInvalidateOnAnimation()
    }

    private fun pressAmount(g: KeyGeom, now: Long): Float {
        if (g.pressed) return 1f
        val t = (now - g.releasedAt) / RELEASE_MS.toFloat()
        return if (t >= 1f) 0f else 1f - t
    }

    private fun drawKey(canvas: Canvas, g: KeyGeom, press: Float) {
        val key = g.key
        val r = g.rect
        val special = key.isModifier
        val base = if (special) theme.keySpecial else theme.key
        val isEnter = key.type == KeyType.ENTER
        val shiftActive = key.type == KeyType.SHIFT && shiftState != ShiftState.OFF

        tmpRect.set(r.left, r.top + shadowOffset, r.right, r.bottom + shadowOffset)
        canvas.drawRoundRect(tmpRect, radius, radius, shadowPaint)
        // Pressed keys sink onto their shadow, like a physical key.
        val sink = shadowOffset * press
        tmpRect.set(r.left, r.top + sink, r.right, r.bottom + sink)
        if (isEnter || (key.type == KeyType.SHIFT && shiftState == ShiftState.LOCKED)) {
            accentPaint.alpha = (255 - 60 * press).toInt()
            canvas.drawRoundRect(tmpRect, radius, radius, accentPaint)
        } else {
            keyPaint.color = blend(base, theme.keyPressed, press)
            canvas.drawRoundRect(tmpRect, radius, radius, keyPaint)
        }

        val label = when (key.type) {
            KeyType.ENTER -> enterLabel
            KeyType.SPACE -> spaceLabel
            KeyType.SHIFT -> if (shiftState == ShiftState.LOCKED) "⇪" else "⇧"
            KeyType.CHAR -> if (shiftState != ShiftState.OFF) g.upper else key.label
            else -> key.label
        }
        if (label.isEmpty()) return

        labelPaint.color = when {
            isEnter || (key.type == KeyType.SHIFT && shiftState == ShiftState.LOCKED) -> theme.onAccent
            shiftActive -> theme.accent
            key.type == KeyType.SPACE -> theme.hint
            else -> theme.text
        }
        labelPaint.textSize = when {
            key.type == KeyType.CHAR && label.length == 1 -> letterSize
            key.type == KeyType.SPACE -> modifierSize * 0.85f
            label.length > 2 -> modifierSize * 0.8f
            else -> modifierSize * 1.15f
        }
        val cy = r.centerY() + sink - (labelPaint.descent() + labelPaint.ascent()) / 2f
        canvas.drawText(label, r.centerX(), cy, labelPaint)

        val alt = key.alt
        if (alt != null) {
            hintPaint.color = theme.hint
            hintPaint.textSize = hintSize
            canvas.drawText(alt, r.right - dp(5f), r.top + hintSize + dp(2f), hintPaint)
        }
    }

    private fun drawBubble(canvas: Canvas, g: KeyGeom, alt: String?) {
        val r = g.rect
        val w = r.width() * 1.25f
        val h = rowHeight * 1.15f
        val left = (r.centerX() - w / 2f).coerceIn(dp(2f), width - w - dp(2f))
        // Top-row previews would leave the window: pin them to the headroom and let them
        // overlap their own key instead.
        val top = (r.top - dp(4f) - h).coerceAtLeast(-previewHeadroom)
        tmpRect.set(left, top, left + w, top + h)
        canvas.drawRoundRect(tmpRect.left, tmpRect.top + dp(2f), tmpRect.right, tmpRect.bottom + dp(2f), radius * 1.3f, radius * 1.3f, bubbleShadowPaint)
        val text = alt ?: if (shiftState != ShiftState.OFF) g.upper else g.key.label
        if (alt != null) {
            accentPaint.alpha = 255
            canvas.drawRoundRect(tmpRect, radius * 1.3f, radius * 1.3f, accentPaint)
            labelPaint.color = theme.onAccent
        } else {
            canvas.drawRoundRect(tmpRect, radius * 1.3f, radius * 1.3f, bubblePaint)
            labelPaint.color = theme.text
        }
        labelPaint.textSize = letterSize * 1.3f
        val cy = tmpRect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f
        canvas.drawText(text, tmpRect.centerX(), cy, labelPaint)
    }

    // ---- touch ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                onPointerDown(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    onPointerMove(event.getPointerId(i), event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                onPointerUp(event.getPointerId(event.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> cancelAllPointers()
        }
        return true
    }

    private fun findKey(x: Float, y: Float): Int {
        for (i in geoms.indices) if (geoms[i].hit.contains(x, y)) return i
        return -1
    }

    private fun onPointerDown(id: Int, x: Float, y: Float) {
        // Fast two-thumb typing: a new touch commits the key still held by the other thumb.
        for (i in 0 until pointers.size()) {
            val other = pointers.valueAt(i)
            val g = geoms.getOrNull(other.index) ?: continue
            if (!other.consumed && g.key.type == KeyType.CHAR) {
                other.consumed = true
                cancelLongPress(other)
                listener?.onKey(g.key)
                release(g)
            }
        }

        val index = findKey(x, y)
        if (index < 0) return
        val p = Pointer(id).also {
            it.index = index
            it.downX = x
            it.lastDragX = x
        }
        pointers.put(id, p)
        val g = geoms[index]
        press(g)
        listener?.onPressFeedback(g.key)

        when (g.key.type) {
            KeyType.DELETE -> {
                p.consumed = true
                listener?.onDelete()
                startDeleteRepeat()
            }
            KeyType.SHIFT -> {
                p.consumed = true
                listener?.onShift()
            }
            else -> scheduleLongPress(p)
        }
    }

    private fun onPointerMove(id: Int, x: Float, y: Float) {
        val p = pointers.get(id) ?: return
        val g = geoms.getOrNull(p.index) ?: return
        when (g.key.type) {
            KeyType.SPACE -> {
                if (!p.dragging && abs(x - p.downX) > touchSlop) {
                    p.dragging = true
                    cancelLongPress(p)
                }
                if (p.dragging) {
                    val steps = ((x - p.lastDragX) / cursorStep).toInt()
                    if (steps != 0) {
                        p.lastDragX += steps * cursorStep
                        listener?.onCursorMove(steps)
                    }
                }
            }
            KeyType.DELETE -> {
                if (!p.dragging && p.downX - x > deleteSwipe) {
                    p.dragging = true
                    stopDeleteRepeat()
                    listener?.onDeleteWord()
                }
            }
            KeyType.CHAR -> {
                if (p.consumed || p.altShown != null) return
                val index = findKey(x, y)
                if (index >= 0 && index != p.index && geoms[index].key.type == KeyType.CHAR) {
                    // Finger slid onto a neighbour before lifting: follow it.
                    release(g)
                    p.index = index
                    press(geoms[index])
                    scheduleLongPress(p)
                    invalidate()
                }
            }
            else -> Unit
        }
    }

    private fun onPointerUp(id: Int) {
        val p = pointers.get(id) ?: return
        pointers.remove(id)
        cancelLongPress(p)
        val g = geoms.getOrNull(p.index) ?: return
        if (g.key.type == KeyType.DELETE) stopDeleteRepeat()
        if (!p.consumed && !p.dragging && p.altShown == null) listener?.onKey(g.key)
        release(g)
    }

    private fun scheduleLongPress(p: Pointer) {
        cancelLongPress(p)
        val g = geoms.getOrNull(p.index) ?: return
        if (g.key.type == KeyType.SPACE) {
            val r = Runnable {
                if (pointers.get(p.id) !== p || p.dragging) return@Runnable
                p.consumed = true
                listener?.onSpaceLongPress()
            }
            p.longPress = r
            postDelayed(r, longPressDelay * 2)
            return
        }
        val alt = g.key.alt ?: return
        val r = Runnable {
            if (pointers.get(p.id) !== p || p.consumed) return@Runnable
            p.altShown = alt
            p.consumed = true
            listener?.onPressFeedback(g.key)
            listener?.onAltChar(alt)
            invalidate()
        }
        p.longPress = r
        postDelayed(r, longPressDelay)
    }

    private fun cancelLongPress(p: Pointer) {
        p.longPress?.let { removeCallbacks(it) }
        p.longPress = null
    }

    private fun startDeleteRepeat() {
        stopDeleteRepeat()
        var interval = 70L
        val r = object : Runnable {
            override fun run() {
                listener?.onDelete()
                interval = (interval - 5).coerceAtLeast(30L)
                postDelayed(this, interval)
            }
        }
        deleteRepeat = r
        postDelayed(r, 380L)
    }

    private fun stopDeleteRepeat() {
        deleteRepeat?.let { removeCallbacks(it) }
        deleteRepeat = null
    }

    private fun press(g: KeyGeom) {
        g.pressed = true
        invalidate()
    }

    private fun release(g: KeyGeom) {
        if (!g.pressed) return
        g.pressed = false
        g.releasedAt = SystemClock.uptimeMillis()
        invalidate()
    }

    fun cancelAllPointers() {
        for (i in 0 until pointers.size()) cancelLongPress(pointers.valueAt(i))
        pointers.clear()
        stopDeleteRepeat()
        for (g in geoms) g.pressed = false
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelAllPointers()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val RELEASE_MS = 140L

        private fun blend(from: Int, to: Int, t: Float): Int {
            if (t <= 0f) return from
            if (t >= 1f) return to
            val a = ((from ushr 24) + (((to ushr 24) - (from ushr 24)) * t)).toInt()
            val r = (((from shr 16) and 0xFF) + ((((to shr 16) and 0xFF) - ((from shr 16) and 0xFF)) * t)).toInt()
            val g = (((from shr 8) and 0xFF) + ((((to shr 8) and 0xFF) - ((from shr 8) and 0xFF)) * t)).toInt()
            val b = ((from and 0xFF) + (((to and 0xFF) - (from and 0xFF)) * t)).toInt()
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
