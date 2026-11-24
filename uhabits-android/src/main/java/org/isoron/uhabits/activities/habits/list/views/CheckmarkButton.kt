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
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.View.MeasureSpec.EXACTLY
import me.tatarka.inject.annotations.Inject
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Entry.Companion.NO
import org.isoron.uhabits.core.models.Entry.Companion.SKIP
import org.isoron.uhabits.core.models.Entry.Companion.UNKNOWN
import org.isoron.uhabits.core.models.Entry.Companion.YES_AUTO
import org.isoron.uhabits.core.models.Entry.Companion.YES_MANUAL
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.inject.ActivityContext
import org.isoron.uhabits.utils.drawNotesIndicator
import org.isoron.uhabits.utils.getFontAwesome
import org.isoron.uhabits.utils.sp
import org.isoron.uhabits.utils.sres
import org.isoron.uhabits.utils.toMeasureSpec

data class CheckmarkButtonState(
    val color: Int,
    val value: Int,
    val notes: String,
    val paused: Boolean = false,
    val isNegative: Boolean = false
)

internal val NEGATIVE_RED = Color.rgb(0xD3, 0x2F, 0x2F)

class CheckmarkButtonView(
    context: Context,
    val preferences: Preferences
) : View(context),
    View.OnClickListener,
    View.OnLongClickListener {

    private var state = CheckmarkButtonState(Color.BLACK, 0, "")

    var onToggle: (Int, String) -> Unit = { _, _ -> }

    var onEdit: () -> Unit = { }

    private var drawer = Drawer()

    fun setState(newState: CheckmarkButtonState) {
        state = newState
        invalidate()
    }

    val currentValue: Int get() = state.value

    init {
        setOnClickListener(this)
        setOnLongClickListener(this)
    }

    fun performToggle() {
        // Tapping a paused day ("zz") checks it; unchecking returns it to the paused state
        val v = if (state.value == SKIP && state.paused) {
            Entry.YES_MANUAL
        } else {
            Entry.nextToggleValue(
                value = state.value,
                isSkipEnabled = preferences.isSkipEnabled,
                areQuestionMarksEnabled = preferences.areQuestionMarksEnabled
            )
        }
        state = state.copy(value = v)
        onToggle(v, state.notes)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    override fun onClick(v: View) {
        if (preferences.isShortToggleEnabled) {
            performToggle()
        } else {
            onEdit()
        }
    }

    override fun onLongClick(v: View): Boolean {
        if (preferences.isShortToggleEnabled) {
            onEdit()
        } else {
            performToggle()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawer.draw(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = resources.getDimensionPixelSize(R.dimen.checkmarkHeight)
        val width = resources.getDimensionPixelSize(R.dimen.checkmarkWidth)
        super.onMeasure(
            width.toMeasureSpec(EXACTLY),
            height.toMeasureSpec(EXACTLY)
        )
    }

    private inner class Drawer {
        private val rect = RectF()
        private val bgColor = sres.getColor(R.attr.cardBgColor)
        private val lowContrastColor = sres.getColor(R.attr.contrast40)
        private val mediumContrastColor = sres.getColor(R.attr.contrast60)
        private val pNotesIndicator = Paint()

        private val paint = TextPaint().apply {
            typeface = getFontAwesome()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        fun draw(canvas: Canvas) {
            paint.color = when (state.value) {
                YES_MANUAL, YES_AUTO, SKIP -> state.color
                NO -> {
                    if (state.isNegative) {
                        NEGATIVE_RED
                    } else if (preferences.areQuestionMarksEnabled) {
                        mediumContrastColor
                    } else {
                        lowContrastColor
                    }
                }
                else -> lowContrastColor
            }
            val id = when (state.value) {
                SKIP -> R.string.fa_skipped
                NO -> R.string.fa_times
                UNKNOWN -> {
                    if (preferences.areQuestionMarksEnabled) {
                        R.string.fa_question
                    } else {
                        R.string.fa_times
                    }
                }
                else -> R.string.fa_check
            }
            paint.typeface = if (state.value == SKIP && state.paused) Typeface.DEFAULT_BOLD else getFontAwesome()
            paint.textSize = when {
                id == R.string.fa_question -> sp(12.0f)
                state.value == YES_AUTO -> sp(13.0f)
                else -> sp(14.0f)
            }
            if (state.value == YES_AUTO) {
                paint.strokeWidth = 5f
                paint.style = Paint.Style.STROKE
            } else {
                paint.strokeWidth = 0f
                paint.style = Paint.Style.FILL
            }

            // A day that belongs to a pause shows "zz" (sleeping) instead of the skip symbol
            val isPaused = state.value == SKIP && state.paused
            val label = if (isPaused) "zz" else resources.getString(id)
            val em = paint.measureText("m")

            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            rect.offset(0f, 0.4f * em)
            if (isPaused) {
                // Comic style: a normal "z" with a smaller, raised "z" to its upper right
                val big = paint.textSize
                val gap = 0.35f * em
                val smallSize = 0.7f * big
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("z", rect.centerX() - gap, rect.centerY() + 0.1f * em, paint)
                paint.textSize = smallSize
                canvas.drawText("z", rect.centerX() + gap, rect.centerY() - 0.5f * em, paint)
                paint.textSize = big
            } else {
                canvas.drawText(label, rect.centerX(), rect.centerY(), paint)
            }

            if (state.value == YES_AUTO) {
                paint.color = bgColor
                paint.style = Paint.Style.FILL
                canvas.drawText(label, rect.centerX(), rect.centerY(), paint)
            }

            drawNotesIndicator(
                pNotesIndicator = pNotesIndicator,
                canvas = canvas,
                color = state.color,
                size = em,
                notes = state.notes
            )
        }
    }
}

@Inject
class CheckmarkButtonViewFactory(
    @ActivityContext val context: Context,
    val preferences: Preferences
) {
    fun create() = CheckmarkButtonView(context, preferences)
}
