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

import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Goals "X times per year", counted per rolling or calendar year (see #79). */
class YearlyHabitTest : BaseUnitTest() {

    // Fixed "today" is 2015-01-25
    private fun booleanHabit(calendarPeriod: Boolean, num: Int, vararg days: LocalDate): Habit {
        val h = modelFactory.buildHabit()
        h.frequency = Frequency(num, Frequency.YEAR_DAYS)
        h.calendarPeriod = calendarPeriod
        days.forEach { h.originalEntries.add(Entry(it, Entry.YES_MANUAL)) }
        h.recompute()
        return h
    }

    @Test
    fun testYearlyFrequency() {
        assertTrue(Frequency(12, 365).isYearly)
        assertFalse(Frequency(12, 30).isYearly)
        assertTrue(EntryList.hasCalendarChoice(Frequency(1, 365)))
    }

    @Test
    fun testCalendarYearFillsTheWholeYearOnceGoalIsReached() {
        val h = booleanHabit(true, 2, LocalDate(2014, 3, 1), LocalDate(2014, 9, 10))
        assertTrue(h.isCompletedOn(LocalDate(2014, 1, 1)))
        assertTrue(h.isCompletedOn(LocalDate(2014, 12, 31)))
        assertFalse(h.isCompletedOn(LocalDate(2015, 1, 2)))
    }

    @Test
    fun testChecksAcrossNewYearDoNotReachTheCalendarGoal() {
        val calendar = booleanHabit(true, 2, LocalDate(2014, 12, 30), LocalDate(2015, 1, 2))
        assertFalse(calendar.isCompletedOn(LocalDate(2015, 1, 3)))

        val rolling = booleanHabit(false, 2, LocalDate(2014, 12, 30), LocalDate(2015, 1, 2))
        assertTrue(rolling.isCompletedOn(LocalDate(2015, 1, 3)))
    }

    @Test
    fun testRollingYearCoversTheGoalForOneYear() {
        val h = booleanHabit(false, 1, LocalDate(2014, 1, 10))
        // Rolling year: the check on 10 Jan 2014 covers 365 days, i.e. up to 9 Jan 2015
        assertTrue(h.isCompletedOn(LocalDate(2015, 1, 9)))
        assertFalse(h.isCompletedOn(LocalDate(2015, 1, 10)))
    }

    @Test
    fun testLeapYearIsCoveredCompletelyInCalendarMode() {
        val h = booleanHabit(true, 1, LocalDate(2012, 2, 29))
        assertTrue(h.isCompletedOn(LocalDate(2012, 12, 31)))
        assertFalse(h.isCompletedOn(LocalDate(2013, 1, 1)))
    }

    @Test
    fun testScoreRestartsWithEachCalendarYear() {
        val h = booleanHabit(true, 2, LocalDate(2014, 11, 1), LocalDate(2014, 12, 1))
        assertTrue(h.scores[LocalDate(2015, 1, 1)].value < h.scores[LocalDate(2014, 12, 31)].value)
    }

    @Test
    fun testNumericalYearlyGoalCountsPerCalendarYear() {
        val h = modelFactory.buildHabit()
        h.type = HabitType.NUMERICAL
        h.frequency = Frequency(1, Frequency.YEAR_DAYS)
        h.targetValue = 100.0
        h.calendarPeriod = true
        h.originalEntries.add(Entry(LocalDate(2014, 2, 1), 60000))
        h.originalEntries.add(Entry(LocalDate(2014, 11, 1), 50000))
        h.originalEntries.add(Entry(LocalDate(2015, 1, 5), 30000))
        h.recompute()
        assertTrue(h.computedEntries.get(LocalDate(2014, 2, 1)).completed)
        assertFalse(h.computedEntries.get(LocalDate(2015, 1, 5)).completed)
    }
}
