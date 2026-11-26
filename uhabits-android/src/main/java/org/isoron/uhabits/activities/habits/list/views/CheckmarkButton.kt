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
import android.view.MotionEvent
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
import org.isoron.uhabits.core.ui.screens.habits.list.ZzState
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
    val isNegative: Boolean = false,
    val zz: ZzState = ZzState.NONE,
    /** The day lies before the habit was created; only there a tap can reset the day (see #70). */
    val beforeCreation: Boolean = false
)

/**
 * A day that is passed over without affecting the streak: shown as "zz". Once the user checks it
 * (the value changes), it is a normal day again until the list is refreshed.
 */
private val CheckmarkButtonState.isResting: Boolean
    get() = zz != ZzState.NONE && (value == SKIP || value == YES_AUTO)

internal val NEGATIVE_RED = Color.rgb(0xD3, 0x2F, 0x2F)

class CheckmarkButtonView(
    context: Context,
    val preferences: Preferences
) : View(context) {

    private var state = CheckmarkButtonState(Color.BLACK, 0, "")

    var onToggle: (Int, String) -> Unit = { _, _ -> }

    var onEdit: () -> Unit = { }

    private var drawer = Drawer()

    fun setState(newState: CheckmarkButtonState) {
        state = newState
        invalidate()
    }

    val currentValue: Int get() = state.value

    private val gestures = EntryButtonGestures(this) { preferences.isShortToggleEnabled }

    init {
        isClickable = true
        isLongClickable = true
    }

    fun performToggle() {
        // Tapping a resting day ("zz") checks it; unchecking returns it to the resting state
        val v = if (state.isResting) {
            Entry.YES_MANUAL
        } else {
            Entry.nextToggleValue(
                value = state.value,
                isSkipEnabled = preferences.isSkipEnabled,
                isBeforeCreation = state.beforeCreation
            )
        }
        state = state.copy(value = v)
        onToggle(v, state.notes)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

    /** Main action: toggles the checkmark. */
    override fun performClick(): Boolean {
        super.performClick()
        performToggle()
        return true
    }

    /** Opens the dialog for notes and corrections. */
    override fun performLongClick(): Boolean {
        onEdit()
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
            val isZz = state.isResting
            paint.color = when {
                // "zz" is the habit color if it continues a streak, otherwise gray
                isZz -> if (state.zz == ZzState.STREAK) state.color else lowContrastColor
                else -> when (state.value) {
                    YES_MANUAL, YES_AUTO, SKIP -> state.color
                    NO -> {
                        if (state.isNegative) {
                            NEGATIVE_RED
                        } else if (state.beforeCreation) {
                            mediumContrastColor
                        } else {
                            lowContrastColor
                        }
                    }
                    else -> lowContrastColor
                }
            }
            val id = when (state.value) {
                SKIP -> R.string.fa_skipped
                NO -> R.string.fa_times
                UNKNOWN -> R.string.fa_times
                else -> R.string.fa_check
            }
            paint.typeface = if (isZz) Typeface.DEFAULT_BOLD else getFontAwesome()
            paint.textSize = when {
                state.value == YES_AUTO && !isZz -> sp(13.0f)
                else -> sp(14.0f)
            }
            if (state.value == YES_AUTO && !isZz) {
                paint.strokeWidth = 5f
                paint.style = Paint.Style.STROKE
            } else {
                paint.strokeWidth = 0f
                paint.style = Paint.Style.FILL
            }

            // Every day that does not affect the streak shows "zz" (sleeping) instead of a symbol
            val label = if (isZz) "zz" else resources.getString(id)
            val em = paint.measureText("m")

            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            rect.offset(0f, 0.4f * em)
            if (isZz) {
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

            if (state.value == YES_AUTO && !isZz) {
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
