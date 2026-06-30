package com.etext.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.max

/**
 * A monospaced editor with a JetBrains-style line-number gutter and a
 * current-line highlight, both drawn directly in [onDraw] so they stay
 * perfectly aligned with the text while scrolling.
 */
class CodeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterCurrentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val currentLinePaint = Paint()
    private val dividerPaint = Paint()

    private val tmpRect = Rect()

    private var gutterBackground = 0
    private var currentLineColor = 0

    /** Horizontal padding (px) inside the gutter, on each side of the digits. */
    private val gutterPadding = dp(8f)
    private val basePadding = dp(6f)

    /** Whether to paint the gutter + current-line highlight. */
    var showGutter = true
        set(value) {
            field = value
            updatePadding()
            invalidate()
        }

    init {
        gutterTextPaint.textAlign = Paint.Align.RIGHT
        gutterCurrentTextPaint.textAlign = Paint.Align.RIGHT
        gutterCurrentTextPaint.isFakeBoldText = true
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        includeFontPadding = false
    }

    fun applyTheme(theme: EditorTheme) {
        setBackgroundColor(theme.background)
        setTextColor(theme.text)
        setHintTextColor(theme.gutterText)
        highlightColor = theme.selection
        // Caret / handle tint follows the accent.
        textCursorDrawable?.let { /* keep system default shape, tint below */ }

        gutterBackground = theme.gutterBackground
        currentLineColor = theme.currentLine
        gutterPaint.color = theme.gutterBackground
        gutterTextPaint.color = theme.gutterText
        gutterCurrentTextPaint.color = theme.gutterCurrentText
        currentLinePaint.color = theme.currentLine
        dividerPaint.color = adjustAlpha(theme.gutterText, 0.4f)

        val size = textSize
        gutterTextPaint.textSize = size * 0.82f
        gutterCurrentTextPaint.textSize = size * 0.82f

        // Tint the caret using the accent color (API 29+).
        try {
            textCursorDrawable?.let { d ->
                val tinted = d.mutate()
                tinted.setTint(theme.accent)
                textCursorDrawable = tinted
            }
        } catch (_: Throwable) {
        }

        updatePadding()
        invalidate()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        // Repaint so the current-line highlight follows the caret.
        invalidate()
    }

    override fun onTextChanged(
        text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        // A change in line count can change the gutter width.
        updatePadding()
    }

    private fun gutterWidth(): Int {
        if (!showGutter) return basePadding
        val lines = max(1, lineCount)
        val digits = lines.toString().length.coerceAtLeast(2)
        val sample = "0".repeat(digits)
        val w = gutterTextPaint.measureText(sample)
        return (w + gutterPadding * 2).toInt()
    }

    private fun updatePadding() {
        val left = gutterWidth()
        if (paddingLeft != left || paddingTop != basePadding) {
            setPadding(left, basePadding, basePadding, basePadding)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val layout: Layout? = layout
        if (showGutter && layout != null) {
            drawCurrentLine(canvas, layout)
            super.onDraw(canvas)
            drawGutter(canvas, layout)
        } else {
            super.onDraw(canvas)
        }
    }

    private fun currentLineIndex(layout: Layout): Int {
        val sel = selectionStart.coerceAtLeast(0)
        return layout.getLineForOffset(sel)
    }

    private fun drawCurrentLine(canvas: Canvas, layout: Layout) {
        if (!hasFocus()) return
        val line = currentLineIndex(layout)
        val top = layout.getLineTop(line) + paddingTop
        val bottom = layout.getLineBottom(line) + paddingTop
        canvas.drawRect(0f, top.toFloat(), width.toFloat() + scrollX, bottom.toFloat(), currentLinePaint)
    }

    private fun drawGutter(canvas: Canvas, layout: Layout) {
        val scrollY = scrollY
        val gWidth = gutterWidth()
        val left = scrollX.toFloat()

        // Gutter strip background, fixed to the viewport left edge.
        canvas.drawRect(left, scrollY.toFloat(), left + gWidth, (scrollY + height).toFloat(), gutterPaint)
        // Thin divider line.
        canvas.drawRect(left + gWidth - dp(0.5f), scrollY.toFloat(), left + gWidth, (scrollY + height).toFloat(), dividerPaint)

        val content = text ?: return
        val currentVisualLine = if (hasFocus()) currentLineIndex(layout) else -1
        val firstVisible = layout.getLineForVertical(scrollY)
        val lastVisible = layout.getLineForVertical(scrollY + height)

        // Logical (1-based) line number of the first visible visual line.
        // Counted once here, then carried forward as we walk down — O(visible).
        var logicalNumber = 1 + countNewlines(content, 0, layout.getLineStart(firstVisible))

        val textX = left + gWidth - gutterPadding
        val fm = gutterTextPaint.fontMetrics
        for (i in firstVisible..lastVisible) {
            val lineStart = layout.getLineStart(i)
            val isParagraphStart = lineStart == 0 || content.getOrNull(lineStart - 1) == '\n'
            if (i > firstVisible && isParagraphStart) logicalNumber++

            // Only the first visual row of a wrapped logical line is numbered.
            if (!isParagraphStart && i != firstVisible) continue
            // If the first visible row is a wrapped continuation, skip its number.
            if (i == firstVisible && !isParagraphStart) continue

            val top = layout.getLineTop(i) + paddingTop
            val bottom = layout.getLineBottom(i) + paddingTop
            val baseline = (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f
            val onCurrent = currentVisualLine in lineStart.let { _ ->
                paragraphRange(content, layout, i)
            }
            val paint = if (onCurrent) gutterCurrentTextPaint else gutterTextPaint
            canvas.drawText(logicalNumber.toString(), textX, baseline, paint)
        }
    }

    /** Range of visual lines that belong to the same logical paragraph as [visualLine]. */
    private fun paragraphRange(content: CharSequence, layout: Layout, visualLine: Int): IntRange {
        var start = visualLine
        while (start > 0) {
            val s = layout.getLineStart(start)
            if (s == 0 || content.getOrNull(s - 1) == '\n') break
            start--
        }
        var end = visualLine
        while (end < layout.lineCount - 1) {
            val nextStart = layout.getLineStart(end + 1)
            if (content.getOrNull(nextStart - 1) == '\n') break
            end++
        }
        return start..end
    }

    private fun countNewlines(s: CharSequence, from: Int, to: Int): Int {
        var n = 0
        var i = from
        while (i < to) {
            if (s[i] == '\n') n++
            i++
        }
        return n
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = (android.graphics.Color.alpha(color) * factor).toInt()
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()
}
