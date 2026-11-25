/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.isoron.uhabits.activities.habits.list.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View.MeasureSpec.EXACTLY
import org.isoron.platform.time.JavaLocalDateFormatter
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.common.views.ScrollableChart
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.utils.MidnightTimer
import org.isoron.uhabits.utils.dim
import org.isoron.uhabits.utils.dp
import org.isoron.uhabits.utils.isRTL
import org.isoron.uhabits.utils.sres
import org.isoron.uhabits.utils.toMeasureSpec
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class HeaderState(
    val buttonCount: Int,
    val maxDataOffset: Int
)

class HeaderView(
    context: Context,
    val prefs: Preferences,
    val midnightTimer: MidnightTimer
) : ScrollableChart(context),
    Preferences.Listener,
    MidnightTimer.MidnightListener {

    private var drawer = Drawer()

    private var state: HeaderState? = null

    fun setState(newState: HeaderState) {
        state = newState
        setMaxDataOffset(newState.maxDataOffset)
        requestLayout()
    }

    init {
        setScrollerBucketSize(dim(R.dimen.checkmarkWidth).toInt())
        setBackgroundColor(sres.getColor(R.attr.headerBackgroundColor))
        elevation = dp(2.0f)
    }

    override fun atMidnight() {
        post { invalidate() }
    }

    override fun onCheckmarkSequenceChanged() {
        updateScrollDirection()
        postInvalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateScrollDirection()
        prefs.addListener(this)
        midnightTimer.addListener(this)
    }

    override fun onDetachedFromWindow() {
        midnightTimer.removeListener(this)
        prefs.removeListener(this)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawer.draw(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = dim(R.dimen.checkmarkHeight)
        setMeasuredDimension(widthMeasureSpec, height.toMeasureSpec(EXACTLY))
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val count = state?.buttonCount ?: 0
        if (dataOffset == 0 || count <= 0) return false
        val columnsWidth = count * dim(R.dimen.checkmarkWidth)
        val inLabelArea = if (isRTL()) e.x > columnsWidth else e.x < width - columnsWidth
        if (!inLabelArea) return false
        scrollToStart()
        return true
    }

    private fun updateScrollDirection() {
        var direction = -1
        if (prefs.isCheckmarkSequenceReversed) direction *= -1
        if (isRTL()) direction *= -1
        setScrollDirection(direction)
    }

    private inner class Drawer {
        private val rect = RectF()
        private val dateFormatter = JavaLocalDateFormatter(Locale.getDefault())
        private val paint = TextPaint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textSize = dim(R.dimen.tinyTextSize)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            color = sres.getColor(R.attr.contrast60)
        }

        private val monthPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = dim(R.dimen.smallTextSize)
            typeface = Typeface.DEFAULT_BOLD
            color = sres.getColor(R.attr.contrast60)
        }

        private fun monthName(month: Int, style: TextStyle): String =
            Month.of(month).getDisplayName(style, Locale.getDefault())

        /**
         * Month (and year) that has the most days among the visible columns, e.g. "Nov '26".
         * On a tie the newer month wins.
         */
        private fun monthLabel(newest: LocalDate, count: Int): String {
            var bestMonth = newest.month
            var bestYear = newest.year
            var bestCount = 0
            var curMonth = newest.month
            var curYear = newest.year
            var curCount = 0
            for (i in 0 until count) {
                val d = newest.minus(i)
                if (d.month == curMonth && d.year == curYear) {
                    curCount++
                } else {
                    curMonth = d.month
                    curYear = d.year
                    curCount = 1
                }
                if (curCount > bestCount) {
                    bestCount = curCount
                    bestMonth = curMonth
                    bestYear = curYear
                }
            }
            val shortMonth = monthName(bestMonth, TextStyle.SHORT_STANDALONE).take(3)
            return "$shortMonth '${(bestYear % 100).toString().padStart(2, '0')}"
        }

        /** Width of the widest possible month label, so the "Today" chip never moves. */
        private val monthSlot: Float by lazy {
            (1..12).maxOf { m ->
                monthPaint.measureText("${monthName(m, TextStyle.SHORT_STANDALONE).take(3)} '00")
            }
        }

        private val chipPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = dim(R.dimen.tinyTextSize)
            typeface = Typeface.DEFAULT_BOLD
            color = sres.getColor(R.attr.contrast60)
        }

        private val chipStroke = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = dp(1.0f)
            color = sres.getColor(R.attr.contrast60)
        }

        private fun drawMonth(canvas: Canvas, count: Int, width: Float) {
            if (count <= 0) return
            val today = getToday()
            val newest = today.minus(dataOffset)
            val label = monthLabel(newest, count)
            val margin = dp(16.0f)
            val gap = dp(8.0f)
            val available = canvas.width - count * width - 2 * margin
            if (available <= 0) return
            val scrolled = dataOffset > 0

            // "Today" chip behind the month, shown only while scrolled away from today; tapping
            // the left part of the header jumps back (see onSingleTapUp).
            val chipText = context.getString(R.string.today)
            val padH = dp(8.0f)
            val padV = dp(3.0f)
            val chipWidth = chipPaint.measureText(chipText) + 2 * padH
            val chipHeight = chipPaint.descent() - chipPaint.ascent() + 2 * padV
            val reserved = if (scrolled) chipWidth + gap else 0f

            val text = TextUtils.ellipsize(
                label,
                monthPaint,
                max(if (scrolled) min(monthSlot, available - reserved) else available, 0f),
                TextUtils.TruncateAt.END
            )
            val slotWidth = monthSlot
            val centerY = canvas.height / 2f
            val baseline = centerY - (monthPaint.ascent() + monthPaint.descent()) / 2
            val rtl = isRTL()
            monthPaint.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            val x = if (rtl) canvas.width - margin else margin
            canvas.drawText(text, 0, text.length, x, baseline, monthPaint)

            if (scrolled) {
                val left = if (rtl) {
                    canvas.width - margin - slotWidth - gap - chipWidth
                } else {
                    margin + slotWidth + gap
                }
                rect.set(left, centerY - chipHeight / 2, left + chipWidth, centerY + chipHeight / 2)
                canvas.drawRoundRect(rect, chipHeight / 2, chipHeight / 2, chipStroke)
                chipPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(
                    chipText,
                    rect.centerX(),
                    rect.centerY() - (chipPaint.ascent() + chipPaint.descent()) / 2,
                    chipPaint
                )
            }
        }

        fun draw(canvas: Canvas) {
            val today = getToday()
            val width = dim(R.dimen.checkmarkWidth)
            val height = dim(R.dimen.checkmarkHeight)
            val isReversed = prefs.isCheckmarkSequenceReversed

            drawMonth(canvas, state?.buttonCount ?: 0, width)

            val em = paint.measureText("m")

            repeat(state?.buttonCount ?: 0) { index ->
                rect.set(0f, 0f, width, height)
                rect.offset(canvas.width.toFloat() - dp(3.0f), 0f)

                if (isReversed) {
                    rect.offset(-(index + 1) * width, 0f)
                } else {
                    rect.offset((index - (state?.buttonCount ?: 0)) * width, 0f)
                }

                if (isRTL()) {
                    rect.set(
                        canvas.width - rect.right,
                        rect.top,
                        canvas.width - rect.left,
                        rect.bottom
                    )
                }

                val date = today.minus(index + dataOffset)
                val dayOfWeek = dateFormatter.shortWeekdayName(date).uppercase()
                val dayOfMonth = date.day.toString()
                val y1 = rect.centerY() - 0.25 * em
                val y2 = rect.centerY() + 1.25 * em
                canvas.drawText(dayOfWeek, rect.centerX(), y1.toFloat(), paint)
                canvas.drawText(dayOfMonth, rect.centerX(), y2.toFloat(), paint)
            }
        }
    }
}
