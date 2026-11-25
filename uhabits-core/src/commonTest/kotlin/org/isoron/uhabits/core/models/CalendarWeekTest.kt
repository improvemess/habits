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

/** Weekly goals count either the last 7 days (rolling) or a calendar week (see #23). */
class CalendarWeekTest : BaseUnitTest() {

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

    private fun booleanHabit(calendarWeek: Boolean, vararg checkedDays: Int): Habit {
        val h = modelFactory.buildHabit()
        h.frequency = Frequency(3, 7)
        h.calendarWeek = calendarWeek
        checkedDays.forEach { h.originalEntries.add(Entry(day(it), Entry.YES_MANUAL)) }
        h.recompute()
        return h
    }

    private fun numericalHabit(calendarWeek: Boolean, target: Double, vararg values: Pair<Int, Int>): Habit {
        val h = modelFactory.buildHabit()
        h.type = HabitType.NUMERICAL
        h.frequency = Frequency(1, 7)
        h.targetValue = target
        h.calendarWeek = calendarWeek
        values.forEach { (d, v) -> h.originalEntries.add(Entry(day(d), v)) }
        h.recompute()
        return h
    }

    @Test
    fun testDefaultIsRolling() {
        assertFalse(modelFactory.buildHabit().calendarWeek)
    }

    @Test
    fun testGoalReachedWithinOneCalendarWeekFillsTheWholeWeek() {
        val h = booleanHabit(true, 23, 24, 25)
        // Monday of the same week counts as done, although it lies 4 days before the first check
        assertEquals(Entry.YES_AUTO, h.computedEntries.get(day(19)).value)
        assertTrue(h.isCompletedOn(day(19)))
        assertEquals(Entry.UNKNOWN, h.computedEntries.get(day(18)).value)

        // Rolling week: only the 7 days from the oldest check on
        val rolling = booleanHabit(false, 23, 24, 25)
        assertEquals(Entry.UNKNOWN, rolling.computedEntries.get(day(19)).value)
    }

    @Test
    fun testChecksAcrossWeekChangeDoNotReachTheCalendarGoal() {
        // Three checks within 3 days, but split over two calendar weeks (Sat, Sun | Mon)
        val calendar = booleanHabit(true, 17, 18, 19)
        assertFalse(calendar.isCompletedOn(day(20)))
        assertEquals(Entry.UNKNOWN, calendar.computedEntries.get(day(20)).value)

        val rolling = booleanHabit(false, 17, 18, 19)
        assertTrue(rolling.isCompletedOn(day(20)))
    }

    @Test
    fun testGoalIsEvaluatedPerWeekWithMondayAndSundayBoundaries() {
        // Sunday 18 closes the week 12-18 with three checks, Monday 19 starts a new one
        val h = booleanHabit(true, 16, 17, 18, 19)
        assertTrue(h.isCompletedOn(day(12)))
        assertTrue(h.isCompletedOn(day(18)))
        assertFalse(h.isCompletedOn(day(20)))
    }

    @Test
    fun testFirstWeekdayChangesTheWeekBoundary() {
        setFirstWeekday(DayOfWeek.SUNDAY)
        // With Sunday as first day, Sun 18 belongs to the week 18-24
        val h = booleanHabit(true, 18, 19, 20)
        assertTrue(h.isCompletedOn(day(18)))
        assertTrue(h.isCompletedOn(day(24)))
        assertFalse(h.isCompletedOn(day(17)))
    }

    @Test
    fun testStreakCountsCompletedCalendarWeeks() {
        // Two consecutive full weeks: 12-18 and 19-25
        val h = booleanHabit(true, 13, 15, 17, 20, 22, 24)
        val best = h.streaks.getBest(1).first()
        assertEquals(day(12), best.start)
        assertEquals(day(25), best.end)

        // A week below the goal in between breaks the streak
        val broken = booleanHabit(true, 13, 15, 17, 21, 23)
        assertFalse(broken.isCompletedOn(day(25)))
        assertTrue(broken.streaks.getBest(5).none { it.start <= day(18) && it.end >= day(19) })
    }

    @Test
    fun testScoreRestartsWithEachCalendarWeek() {
        // Goal reached in the week 12-18, then the new week 19-25 starts without any check
        val calendar = booleanHabit(true, 13, 15, 17)
        assertTrue(calendar.scores[day(19)].value < calendar.scores[day(18)].value)

        // Rolling: the three checks stay in the window of 7 days a bit longer
        val rolling = booleanHabit(false, 13, 15, 17)
        assertTrue(rolling.scores[day(19)].value >= rolling.scores[day(18)].value)

        // Within a week, every check raises the score again
        val full = booleanHabit(true, 19, 20, 21)
        assertTrue(full.scores[day(21)].value > full.scores[day(19)].value)
    }

    @Test
    fun testNumericalRollingWeekSumsTheLastSevenDays() {
        // 50 on Fri 16 and 30 on Mon 19: the 7 days ending on Monday add up to 80
        val rolling = numericalHabit(false, 70.0, 16 to 50000, 19 to 30000)
        assertTrue(rolling.isCompletedOn(day(19)))
        // ... until the 50 falls out of the window on Fri 23
        assertTrue(rolling.isCompletedOn(day(22)))
        assertFalse(rolling.isCompletedOn(day(23)))
    }

    @Test
    fun testNumericalCalendarWeekSumsOnlyTheCalendarWeek() {
        val calendar = numericalHabit(true, 70.0, 16 to 50000, 19 to 30000)
        assertFalse(calendar.isCompletedOn(day(19)))
        assertFalse(calendar.isCompletedOn(day(16)))

        val met = numericalHabit(true, 70.0, 19 to 30000, 25 to 50000)
        assertTrue(met.isCompletedOn(day(19)))
        assertTrue(met.isCompletedOn(day(25)))
    }

    @Test
    fun testNumericalRollingWeekReducesTargetBySkippedDays() {
        // 70 per week = 10 per day; one skipped day in the window lowers the target to 60
        val h = numericalHabit(false, 70.0, 20 to Entry.SKIP, 21 to 60000)
        assertTrue(h.isCompletedOn(day(21)))
        val without = numericalHabit(false, 70.0, 21 to 60000)
        assertFalse(without.isCompletedOn(day(21)))
    }

    @Test
    fun testCopyFromAndEqualsIncludeTheWeekMode() {
        val a = modelFactory.buildHabit()
        val b = modelFactory.buildHabit()
        b.uuid = a.uuid
        a.calendarWeek = true
        assertFalse(a == b)
        b.copyFrom(a)
        assertTrue(b.calendarWeek)
        assertTrue(a == b)
    }
}
