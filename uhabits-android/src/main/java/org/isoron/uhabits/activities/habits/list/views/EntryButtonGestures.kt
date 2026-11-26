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

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Gesture scheme shared by all entry buttons (yes/no, negative and numerical habits):
 *
 * - double tap: main action (or a single tap, if [singleTapEnabled] is true)
 * - long press: opens the dialog
 *
 * The main action is dispatched through [View.performClick] and the dialog through
 * [View.performLongClick], so the view only has to override those two methods.
 *
 * In single-tap mode the main action fires right on release, without waiting to see whether
 * a second tap follows.
 */
class EntryButtonGestures(
    private val view: View,
    private val singleTapEnabled: () -> Boolean
) {
    private val detector = GestureDetector(
        view.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (singleTapEnabled()) view.performClick()
                return true
            }

            // A fast second tap must not get lost in single-tap mode, so it acts as a tap, too
            override fun onDoubleTap(e: MotionEvent): Boolean {
                view.performClick()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                view.performLongClick()
            }
        }
    )

    fun onTouchEvent(event: MotionEvent): Boolean = detector.onTouchEvent(event)
}
