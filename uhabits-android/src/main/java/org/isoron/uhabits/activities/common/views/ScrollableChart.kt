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
package org.isoron.uhabits.activities.common.views

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Scroller
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

abstract class ScrollableChart : View, GestureDetector.OnGestureListener, AnimatorUpdateListener {
    var dataOffset = 0
        private set
    private var scrollerBucketSize = 1
    private var direction = 1
    private lateinit var detector: GestureDetector
    private lateinit var scroller: Scroller
    private lateinit var scrollAnimator: ValueAnimator
    private lateinit var scrollController: ScrollController
    private var maxDataOffset = 12 * 200

    /**
     * Fraction (0 until 1) of a bucket by which the content is scrolled beyond [dataOffset]. Lets
     * subclasses draw a smooth, pixel-wise scroll instead of jumping from bucket to bucket.
     */
    var scrollFraction = 0f
        private set

    /** If true, the scroll position settles on a whole bucket after the user lets go. */
    var snapToBucket = false
    private var userScrolling = false

    constructor(context: Context?) : super(context) {
        init(context)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        if (!scroller.isFinished) {
            scroller.computeScrollOffset()
            updateDataOffset()
        } else if (!userScrolling && snapIfNeeded()) {
            // Fling finished between two buckets: settle on the nearest one.
        } else {
            scrollAnimator.cancel()
        }
    }

    /**
     * Called when the finger leaves the screen. If [snapToBucket] is set and no fling is running,
     * animates to the nearest whole bucket.
     */
    fun settle() {
        userScrolling = false
        if (scroller.isFinished) snapIfNeeded()
    }

    private fun snapIfNeeded(): Boolean {
        if (!snapToBucket || scrollerBucketSize <= 0) return false
        val rest = scroller.currX % scrollerBucketSize
        if (rest == 0) return false
        val target = if (rest * 2 >= scrollerBucketSize) {
            scroller.currX + scrollerBucketSize - rest
        } else {
            scroller.currX - rest
        }
        val clamped = min(max(target, 0), maxX)
        scroller.forceFinished(true)
        scroller.startScroll(scroller.currX, scroller.currY, clamped - scroller.currX, 0, SNAP_DURATION_MS)
        scrollAnimator.cancel()
        scrollAnimator.duration = SNAP_DURATION_MS.toLong()
        scrollAnimator.start()
        invalidate()
        return true
    }

    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        userScrolling = false
        scroller.fling(
            scroller.currX,
            scroller.currY,
            direction * velocityX.toInt() / 2,
            0,
            0,
            maxX,
            0,
            0
        )
        invalidate()
        scrollAnimator.duration = scroller.duration.toLong()
        scrollAnimator.start()
        return false
    }

    private val maxX: Int
        get() = maxDataOffset * scrollerBucketSize

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is BundleSavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        val x = state.bundle!!.getInt("x")
        val y = state.bundle.getInt("y")
        direction = state.bundle.getInt("direction")
        dataOffset = state.bundle.getInt("dataOffset")
        maxDataOffset = state.bundle.getInt("maxDataOffset")
        scroller.startScroll(0, 0, x, y, 0)
        scroller.computeScrollOffset()
        super.onRestoreInstanceState(state.superState)
    }

    public override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val bundle = Bundle().apply {
            putInt("x", scroller.currX)
            putInt("y", scroller.currY)
            putInt("dataOffset", dataOffset)
            putInt("direction", direction)
            putInt("maxDataOffset", maxDataOffset)
        }
        return BundleSavedState(superState, bundle)
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
        var dx = dx
        if (scrollerBucketSize == 0) return false
        if (abs(dx) > abs(dy)) {
            val parent = parent
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        userScrolling = true
        dx *= -direction
        // Never scroll past today (negative) or past the oldest day.
        dx = min(max(dx, -scroller.currX.toFloat()), (maxX - scroller.currX).toFloat())
        scroller.startScroll(
            scroller.currX,
            scroller.currY,
            dx.toInt(),
            dy.toInt(),
            0
        )
        scroller.computeScrollOffset()
        updateDataOffset()
        return true
    }

    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            settle()
        }
        return handled
    }

    fun setScrollDirection(direction: Int) {
        require(!(direction != 1 && direction != -1))
        this.direction = direction
    }

    override fun onLongPress(e: MotionEvent) {}
    fun setMaxDataOffset(maxDataOffset: Int) {
        this.maxDataOffset = maxDataOffset
        dataOffset = min(dataOffset, maxDataOffset)
        scrollController.onDataOffsetChanged(dataOffset)
        postInvalidate()
    }

    fun setScrollController(scrollController: ScrollController) {
        this.scrollController = scrollController
    }

    fun setScrollerBucketSize(scrollerBucketSize: Int) {
        this.scrollerBucketSize = scrollerBucketSize
    }

    private fun init(context: Context?) {
        detector = GestureDetector(context, this)
        scroller = Scroller(context, null, true)
        val newScrollAnimator = ValueAnimator.ofFloat(0f, 1f)
        newScrollAnimator.addUpdateListener(this)
        scrollAnimator = newScrollAnimator
        scrollController = object : ScrollController {}
    }

    /** Animates back to the newest data (offset 0). */
    fun scrollToStart() {
        if (scroller.currX == 0) return
        scroller.forceFinished(true)
        scroller.startScroll(scroller.currX, scroller.currY, -scroller.currX, 0, 300)
        scrollAnimator.cancel()
        scrollAnimator.duration = 300
        scrollAnimator.start()
        invalidate()
    }

    fun reset() {
        scroller.finalX = 0
        scroller.computeScrollOffset()
        updateDataOffset()
    }

    private fun updateDataOffset() {
        var newDataOffset = scroller.currX / scrollerBucketSize
        newDataOffset = max(0, newDataOffset)
        newDataOffset = min(maxDataOffset, newDataOffset)
        val newFraction = if (!snapToBucket || newDataOffset >= maxDataOffset) {
            0f
        } else {
            (scroller.currX % scrollerBucketSize).toFloat() / scrollerBucketSize
        }
        if (newFraction != scrollFraction) {
            scrollFraction = newFraction
            scrollController.onScrollFractionChanged(newFraction)
            postInvalidate()
        }
        if (newDataOffset != dataOffset) {
            dataOffset = newDataOffset
            scrollController.onDataOffsetChanged(dataOffset)
            postInvalidate()
        }
    }

    interface ScrollController {
        fun onDataOffsetChanged(newDataOffset: Int) {}

        /** Pixel-wise remainder (0 until 1 bucket) on top of the data offset. */
        fun onScrollFractionChanged(fraction: Float) {}
    }

    private companion object {
        const val SNAP_DURATION_MS = 150
    }
}
