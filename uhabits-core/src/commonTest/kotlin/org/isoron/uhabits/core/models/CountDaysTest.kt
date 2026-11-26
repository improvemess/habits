/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.core.models

import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.resetFirstWeekday
import org.isoron.platform.time.setFirstWeekday
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Measurable goals "at least Y on X days per week or month" (see #81). */
class CountDaysTest : BaseUnitTest() {

    // Fixed "today" is Sunday, 2015-01-25; weeks start on Monday, so the current week is 19-25
    private fun day(d: Int) = LocalDate(2015, 1, d)

    @BeforeTest
    override fun setUp() {
        super.setUp()
        setFirstWeekday(DayOfWeek.MONDAY)
    }

    @AfterTest
    fun tearDown() {
        resetFirstWeekday()
    }

    private fun habit(
        days: Int,
        period: Int,
        threshold: Double,
        calendar: Boolean,
        type: NumericalHabitType = NumericalHabitType.AT_LEAST,
        vararg values: Pair<Int, Int>
    ): Habit {
        val h = modelFactory.buildHabit()
        h.type = HabitType.NUMERICAL
        h.frequency = Frequency(days, period)
        h.targetValue = threshold
        h.targetType = type
        h.calendarPeriod = calendar
        h.countDays = true
        values.forEach { (d, v) -> h.originalEntries.add(Entry(day(d), v)) }
        h.recompute()
        return h
    }

    @Test
    fun testDefaultIsSumPerPeriod() {
        assertFalse(modelFactory.buildHabit().countDays)
    }

    @Test
    fun testOnlyDaysAtOrAboveTheThresholdCount() {
        // 6, 5 and 4.999 km: the last day stays below 5 km
        val three = habit(3, 7, 5.0, false, values = arrayOf(23 to 6000, 24 to 5000, 25 to 4999))
        assertFalse(three.isCompletedOn(day(25)))
        val two = habit(2, 7, 5.0, false, values = arrayOf(23 to 6000, 24 to 5000, 25 to 4999))
        assertTrue(two.isCompletedOn(day(25)))
        // The value itself is kept for display
        assertEquals(4999, two.computedEntries.get(day(25)).value)
    }

    @Test
    fun testSumAcrossDaysDoesNotCount() {
        // 3 x 3 km is 9 km in total, but no day reaches 5 km
        val h = habit(3, 7, 5.0, false, values = arrayOf(23 to 3000, 24 to 3000, 25 to 3000))
        assertFalse(h.isCompletedOn(day(25)))
        assertEquals(0, h.streaks.getBest(10).size)
    }

    @Test
    fun testRollingWeekIsReachedAcrossAWeekChange() {
        // Sat 17, Sun 18, Mon 19: three days within 7 days, but in two calendar weeks
        val rolling = habit(3, 7, 5.0, false, values = arrayOf(17 to 5000, 18 to 5000, 19 to 5000))
        assertTrue(rolling.isCompletedOn(day(20)))
        val calendar = habit(3, 7, 5.0, true, values = arrayOf(17 to 5000, 18 to 5000, 19 to 5000))
        assertFalse(calendar.isCompletedOn(day(20)))
    }

    @Test
    fun testCalendarWeekFillsTheWholeWeekOnceReached() {
        val h = habit(3, 7, 5.0, true, values = arrayOf(23 to 5000, 24 to 6000, 25 to 7000))
        assertTrue(h.isCompletedOn(day(19)))
        assertTrue(h.isCompletedOn(day(25)))
        assertFalse(h.isCompletedOn(day(18)))
    }

    @Test
    fun testCalendarWeekStartsOverOnMonday() {
        // Mon 12 to Sun 18 has three days, Mon 19 starts a new week with only one
        val h = habit(3, 7, 5.0, true, values = arrayOf(12 to 5000, 14 to 5000, 18 to 5000, 19 to 5000))
        assertTrue(h.isCompletedOn(day(12)))
        assertTrue(h.isCompletedOn(day(18)))
        assertFalse(h.isCompletedOn(day(20)))
    }

    @Test
    fun testCalendarMonthChange() {
        val h = modelFactory.buildHabit()
        h.type = HabitType.NUMERICAL
        h.frequency = Frequency(3, 30)
        h.targetValue = 30.0
        h.calendarPeriod = true
        h.countDays = true
        listOf(LocalDate(2014, 12, 30), LocalDate(2014, 12, 31), LocalDate(2015, 1, 2), LocalDate(2015, 1, 3), LocalDate(2015, 1, 4))
            .forEach { h.originalEntries.add(Entry(it, 45000)) }
        h.recompute()
        // December has two days (not reached), January three (reached)
        assertFalse(h.isCompletedOn(LocalDate(2014, 12, 29)))
        assertTrue(h.isCompletedOn(LocalDate(2015, 1, 1)))
        assertTrue(h.isCompletedOn(LocalDate(2015, 1, 20)))
    }

    @Test
    fun testAtMostCountsDaysUnderTheLimit() {
        // At most 60 minutes: 30 and 60 are fine, 90 is not
        val two = habit(2, 7, 60.0, false, NumericalHabitType.AT_MOST, 23 to 30000, 24 to 60000, 25 to 90000)
        assertTrue(two.isCompletedOn(day(25)))
        val three = habit(3, 7, 60.0, false, NumericalHabitType.AT_MOST, 23 to 30000, 24 to 60000, 25 to 90000)
        assertFalse(three.isCompletedOn(day(25)))
    }

    @Test
    fun testStreakAndScoreFollowTheDays() {
        val h = habit(3, 7, 5.0, true, values = arrayOf(20 to 5000, 21 to 5000, 22 to 5000))
        assertEquals(1, h.streaks.getBest(10).size)
        val none = habit(3, 7, 5.0, true, values = arrayOf(20 to 1000, 21 to 1000, 22 to 1000))
        assertEquals(0, none.streaks.getBest(10).size)
        assertTrue(h.scores.get(day(25)).value > none.scores.get(day(25)).value)
    }

    @Test
    fun testDailyFrequencyIgnoresTheFlag() {
        // "Every day" has no days to count: the target stays one sum per day
        val h = habit(1, 1, 5.0, false, values = arrayOf(25 to 5000))
        assertFalse(h.countsDays)
        assertTrue(h.isCompletedOn(day(25)))
    }
}
