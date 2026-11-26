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
import android.view.View
import android.view.View.MeasureSpec.EXACTLY
import android.widget.LinearLayout
import org.isoron.uhabits.R
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.utils.dim
import org.isoron.uhabits.utils.isRTL
import org.isoron.uhabits.utils.toMeasureSpec

/**
 * Row of day buttons. Holds one more button than is visible: while the list is scrolled between
 * two days, the outermost columns are only partly in view, and that extra button fills the gap.
 * Columns are shifted pixel-wise by [setScrollFraction], and the two partly visible columns fade
 * in proportion to how much of them is visible.
 */
abstract class ButtonPanelView<T : View>(
    context: Context,
    val preferences: Preferences
) : LinearLayout(context),
    Preferences.Listener {

    protected var buttonCount = 0

    private var scrollFraction = 0f

    var buttons = mutableListOf<T>()

    override fun onCheckmarkSequenceChanged() {
        inflateButtons()
    }

    @Synchronized
    protected fun inflateButtons() {
        buttons.clear()
        repeat(if (buttonCount > 0) buttonCount + 1 else 0) { buttons.add(createButton()) }

        removeAllViews()
        buttons.forEach { addView(it) }
        setupButtons()
        applyScroll()
        requestLayout()
    }

    /** Shifts the columns by [fraction] (0 until 1) of a column towards the newest day. */
    fun setScrollFraction(fraction: Float) {
        if (fraction == scrollFraction) return
        scrollFraction = fraction
        applyScroll()
    }

    /** Index of the button (0 = newest day) as a slot from the start edge, see [slotOf]. */
    private fun slotOf(index: Int): Int {
        val reversed = preferences.isCheckmarkSequenceReversed
        return if (reversed) buttonCount - 1 - index else index
    }

    private fun applyScroll() {
        val shift = scrollFraction * dim(R.dimen.checkmarkWidth)
        // Newest day is at the start edge unless the sequence is reversed; RTL mirrors everything.
        var translation = if (preferences.isCheckmarkSequenceReversed) shift else -shift
        if (isRTL()) translation = -translation
        buttons.forEachIndexed { index, button ->
            button.translationX = translation
            button.alpha = when (index) {
                0 -> 1f - scrollFraction
                buttonCount -> scrollFraction
                else -> 1f
            }
        }
    }

    public override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferences.addListener(this)
    }

    public override fun onDetachedFromWindow() {
        preferences.removeListener(this)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val buttonWidth = dim(R.dimen.checkmarkWidth)
        val buttonHeight = dim(R.dimen.checkmarkHeight)
        val width = (buttonWidth * buttonCount)
        buttons.forEach {
            it.measure(buttonWidth.toMeasureSpec(EXACTLY), buttonHeight.toMeasureSpec(EXACTLY))
        }
        setMeasuredDimension(width.toInt(), buttonHeight.toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val buttonWidth = dim(R.dimen.checkmarkWidth).toInt()
        val panelWidth = r - l
        val rtl = isRTL()
        buttons.forEachIndexed { index, button ->
            // The extra button sits just outside the oldest visible column.
            val slot = if (index == buttonCount) {
                if (preferences.isCheckmarkSequenceReversed) -1 else buttonCount
            } else {
                slotOf(index)
            }
            val left = slot * buttonWidth
            val x = if (rtl) panelWidth - left - buttonWidth else left
            button.layout(x, 0, x + buttonWidth, b - t)
        }
        applyScroll()
    }

    protected abstract fun setupButtons()
    protected abstract fun createButton(): T
}
