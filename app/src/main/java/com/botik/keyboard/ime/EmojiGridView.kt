package com.botik.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/**
 * Emoji grid meant to live inside a ScrollView. Only the rows inside the visible clip are
 * drawn, so long categories scroll smoothly.
 */
class EmojiGridView(context: Context) : View(context) {

    var onPick: ((String) -> Unit)? = null

    var items: List<String> = emptyList()
        set(value) {
            field = value
            pressed = -1
            requestLayout()
            invalidate()
        }

    var theme: KeyboardTheme = KeyboardTheme.AMETHYST
        set(value) {
            field = value
            pressPaint.color = value.keyPressed
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val targetCell = 44f * density
    private var columns = 8
    private var cell = targetCell

    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.keyPressed }
    private val clip = Rect()
    private val tmp = RectF()
    private var pressed = -1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        columns = max(6, (width / targetCell).toInt())
        cell = width / columns.toFloat()
        val rows = (items.size + columns - 1) / columns
        setMeasuredDimension(width, (rows * cell).toInt())
        emojiPaint.textSize = cell * 0.58f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.getClipBounds(clip)
        val firstRow = max(0, (clip.top / cell).toInt())
        val lastRow = (clip.bottom / cell).toInt() + 1
        val baseline = cell / 2f - (emojiPaint.descent() + emojiPaint.ascent()) / 2f
        for (row in firstRow..lastRow) {
            for (col in 0 until columns) {
                val i = row * columns + col
                if (i >= items.size) return
                val left = col * cell
                val top = row * cell
                if (i == pressed) {
                    tmp.set(left + 3, top + 3, left + cell - 3, top + cell - 3)
                    canvas.drawRoundRect(tmp, cell / 4f, cell / 4f, pressPaint)
                }
                canvas.drawText(items[i], left + cell / 2f, top + baseline, emojiPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = indexAt(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val i = indexAt(event.x, event.y)
                if (i >= 0 && i == pressed) onPick?.invoke(items[i])
                pressed = -1
                invalidate()
            }
            // The ScrollView took over the gesture: it was a scroll, not a tap.
            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                invalidate()
            }
        }
        return true
    }

    private fun indexAt(x: Float, y: Float): Int {
        val col = (x / cell).toInt()
        val row = (y / cell).toInt()
        if (col !in 0 until columns || row < 0) return -1
        val i = row * columns + col
        return if (i < items.size) i else -1
    }
}
