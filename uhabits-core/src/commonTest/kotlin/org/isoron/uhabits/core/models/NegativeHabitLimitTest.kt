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
import org.isoron.platform.time.getToday
import org.isoron.platform.time.resetFirstWeekday
import org.isoron.platform.time.setFirstWeekday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.ui.screens.habits.show.views.BarCardPresenter
import org.isoron.uhabits.core.ui.screens.habits.show.views.FrequencyCardPresenter
import org.isoron.uhabits.core.ui.screens.habits.show.views.HistoryCardPresenter
import org.isoron.uhabits.core.ui.screens.habits.show.views.TargetCardPresenter
import org.isoron.uhabits.core.ui.views.HistoryChart.Square
import org.isoron.uhabits.core.ui.views.LightTheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Limits of avoided measurable habits per day, week and month, and how the calendar shows them. */
class NegativeHabitLimitTest : BaseUnitTest() {

    @BeforeTest
    override fun setUp() {
        super.setUp()
        // Fixed "today" is Sunday, 2015-01-25; weeks start on Monday
        setFirstWeekday(DayOfWeek.MONDAY)
    }

    @AfterTest
    fun tearDown() {
        resetFirstWeekday()
    }

    private fun limitHabit(limit: Double, days: Int): Habit {
        val h = modelFactory.buildHabit()
        h.type = HabitType.NUMERICAL
        h.targetType = NumericalHabitType.AT_MOST
        h.targetValue = limit
        h.frequency = Frequency(1, days)
        h.negativeSince = getToday().minus(40)
        return h
    }

    @Test
    fun testDailyLimit() {
        val h = limitHabit(3.0, 1)
        h.originalEntries.add(Entry(getToday(), 4000))
        h.originalEntries.add(Entry(getToday().minus(1), 3000))
        h.recompute()
        assertFalse(h.isCompletedOn(getToday()))
        assertTrue(h.isCompletedOn(getToday().minus(1)))
    }

    @Test
    fun testWeeklyLimitSumsTheWholeWeek() {
        val h = limitHabit(3.0, 7)
        // Monday 19th, Wednesday 21st and today (Sunday 25th) fall into the same week
        h.originalEntries.add(Entry(LocalDate(2015, 1, 19), 2000))
        h.originalEntries.add(Entry(LocalDate(2015, 1, 21), 1000))
        h.recompute()
        assertTrue(h.isCompletedOn(LocalDate(2015, 1, 21)))
        h.originalEntries.add(Entry(getToday(), 1000))
        h.recompute()
        // 2 + 1 + 1 = 4 > 3: the whole week is over the limit
        assertFalse(h.isCompletedOn(LocalDate(2015, 1, 21)))
        assertFalse(h.isCompletedOn(getToday()))
        // The previous week is unaffected
        h.originalEntries.add(Entry(LocalDate(2015, 1, 14), 3000))
        h.recompute()
        assertTrue(h.isCompletedOn(LocalDate(2015, 1, 14)))
    }

    @Test
    fun testMonthlyLimitSumsTheWholeMonth() {
        val h = limitHabit(5.0, 30)
        h.originalEntries.add(Entry(LocalDate(2015, 1, 3), 3000))
        h.originalEntries.add(Entry(LocalDate(2015, 1, 20), 2000))
        h.recompute()
        assertTrue(h.isCompletedOn(LocalDate(2015, 1, 3)))
        assertTrue(h.isCompletedOn(LocalDate(2015, 1, 20)))
        h.originalEntries.add(Entry(LocalDate(2015, 1, 21), 1000))
        h.recompute()
        assertFalse(h.isCompletedOn(LocalDate(2015, 1, 3)))
        assertFalse(h.isCompletedOn(LocalDate(2015, 1, 20)))
        // December had only 3
        h.originalEntries.add(Entry(LocalDate(2014, 12, 30), 3000))
        h.recompute()
        assertTrue(h.isCompletedOn(LocalDate(2014, 12, 30)))
    }

    @Test
    fun testCalendarMarksExceededLimitAndRelapseInRed() {
        val limit = limitHabit(3.0, 1)
        limit.originalEntries.add(Entry(getToday(), 5000))
        limit.originalEntries.add(Entry(getToday().minus(1), 2000))
        limit.recompute()
        var series = HistoryCardPresenter.buildState(limit, DayOfWeek.MONDAY, LightTheme()).series
        // The series is newest-first: index 0 is today
        assertEquals(Square.FAILED, series[0])
        assertEquals(Square.ON, series[1])

        val yesNo = modelFactory.buildHabit()
        yesNo.negativeSince = getToday().minus(3)
        yesNo.originalEntries.add(Entry(getToday().minus(1), Entry.NO))
        yesNo.recompute()
        series = HistoryCardPresenter.buildState(yesNo, DayOfWeek.MONDAY, LightTheme()).series
        assertEquals(Square.ON, series[0])
        assertEquals(Square.FAILED, series[1])

        val normal = modelFactory.buildHabit()
        normal.originalEntries.add(Entry(getToday().minus(1), Entry.NO))
        normal.originalEntries.add(Entry(getToday().minus(2), Entry.YES_MANUAL))
        normal.recompute()
        series = HistoryCardPresenter.buildState(normal, DayOfWeek.MONDAY, LightTheme()).series
        assertEquals(Square.OFF, series[0])
        assertFalse(series.contains(Square.FAILED))
    }

    @Test
    fun testFrequencyCardCountsRelapsesOfNegativeHabits() {
        val h = modelFactory.buildHabit()
        h.negativeSince = getToday().minus(10)
        // Today is Sunday 2015-01-25; Friday the 23rd and Friday the 16th are relapses
        h.originalEntries.add(Entry(LocalDate(2015, 1, 23), Entry.NO))
        h.originalEntries.add(Entry(LocalDate(2015, 1, 16), Entry.NO))
        h.recompute()
        val state = FrequencyCardPresenter.buildState(h, DayOfWeek.MONDAY, LightTheme())
        assertTrue(state.isNegative)
        val january = state.frequency[LocalDate(2015, 1, 1)]!!
        assertEquals(2, january.sum())
        assertEquals(2, january[(LocalDate(2015, 1, 23).dayOfWeek.daysSinceSunday + 1) % 7])

        val normal = modelFactory.buildHabit()
        normal.originalEntries.add(Entry(LocalDate(2015, 1, 23), Entry.NO))
        normal.recompute()
        assertFalse(FrequencyCardPresenter.buildState(normal, DayOfWeek.MONDAY, LightTheme()).isNegative)
    }

    @Test
    fun testBarAndTargetCardsFlagValuesOverTheLimit() {
        val h = limitHabit(3.0, 7)
        h.originalEntries.add(Entry(getToday(), 4000))
        h.recompute()
        val bars = BarCardPresenter.buildState(h, 2, 1, 0, LightTheme())
        // Weekly buckets, newest first: this week holds 4 > 3
        // 4 against a limit of 3: one unit of the bar is red
        assertEquals(1.0, bars.redParts.first())
        val target = TargetCardPresenter.buildState(h, 2, LightTheme())
        assertEquals(true, target.exceeded.first())

        val normal = limitHabit(3.0, 7)
        normal.negativeSince = null
        normal.originalEntries.add(Entry(getToday(), 4000))
        normal.recompute()
        assertTrue(BarCardPresenter.buildState(normal, 2, 1, 0, LightTheme()).redParts.isEmpty())
        assertTrue(TargetCardPresenter.buildState(normal, 2, LightTheme()).exceeded.isEmpty())
    }

    @Test
    fun testBarCardSplitsKeptDaysAndRelapsesOfNegativeHabits() {
        val h = modelFactory.buildHabit()
        h.negativeSince = LocalDate(2015, 1, 15)
        h.originalEntries.add(Entry(LocalDate(2015, 1, 23), Entry.NO))
        h.recompute()
        // Weekly buckets, newest first: the 19th to the 25th, then the 15th to the 18th
        val bars = BarCardPresenter.buildState(h, 2, 1, 0, LightTheme())
        assertEquals(7.0, bars.entries[0].value / 1000.0)
        assertEquals(1.0, bars.redParts[0])
        assertEquals("6", bars.labels[0])
        assertEquals("1", bars.redLabels[0])
        assertEquals(4.0, bars.entries[1].value / 1000.0)
        assertEquals(0.0, bars.redParts[1])
        assertEquals("4", bars.labels[1])
        assertEquals("", bars.redLabels[1])
    }

    @Test
    fun testScoreOfNegativeHabitEqualsScoreOfSameDaysDoneByHand() {
        val since = LocalDate(2015, 1, 16)
        val relapses = listOf(LocalDate(2015, 1, 20), LocalDate(2015, 1, 23))

        val negative = modelFactory.buildHabit()
        negative.negativeSince = since
        relapses.forEach { negative.originalEntries.add(Entry(it, Entry.NO)) }
        negative.recompute()

        // Same days, but done by hand on a normal daily habit
        val normal = modelFactory.buildHabit()
        var day = since
        while (!day.isNewerThan(getToday())) {
            if (day !in relapses) normal.originalEntries.add(Entry(day, Entry.YES_MANUAL))
            day = day.plus(1)
        }
        normal.recompute()

        day = since
        while (!day.isNewerThan(getToday())) {
            assertEquals(normal.scores[day].value, negative.scores[day].value, 1e-9)
            day = day.plus(1)
        }
        assertTrue(negative.scores[getToday()].value > 0.0)
        assertTrue(negative.scores[LocalDate(2015, 1, 20)].value < negative.scores[LocalDate(2015, 1, 19)].value)
        assertTrue(negative.scores[LocalDate(2015, 1, 22)].value > negative.scores[LocalDate(2015, 1, 20)].value)
    }

    @Test
    fun testTappingACrossOnAnAvoidedHabitAlwaysComesBackToTheCheck() {
        for (skipEnabled in listOf(false, true)) {
            for (questionMarks in listOf(false, true)) {
                val h = modelFactory.buildHabit()
                h.negativeSince = getToday().minus(3)
                h.recompute()
                val day = getToday().minus(1)
                var seenCross = false
                var taps = 0
                do {
                    val shown = h.computedEntries.get(day).value
                    val next = Entry.nextToggleValue(shown, skipEnabled, questionMarks)
                    h.originalEntries.add(Entry(day, next))
                    h.recompute()
                    if (h.computedEntries.get(day).value == Entry.NO) seenCross = true
                    taps++
                } while (h.computedEntries.get(day).value != Entry.YES_MANUAL && taps < 6)
                assertTrue(seenCross, "skip=$skipEnabled questionMarks=$questionMarks")
                assertEquals(Entry.YES_MANUAL, h.computedEntries.get(day).value)
                assertTrue(h.isCompletedOn(day))
            }
        }
    }
}
