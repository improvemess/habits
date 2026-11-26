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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixed weekdays of a habit (see #19). Fixed "today" is Sunday, 2015-01-25. */
class DueDaysTest : BaseUnitTest() {
    private fun day(d: Int) = LocalDate(2015, 1, d)

    private val monWedFri = DueDays.bit(DayOfWeek.MONDAY) or DueDays.bit(DayOfWeek.WEDNESDAY) or DueDays.bit(DayOfWeek.FRIDAY)

    @AfterTest
    fun tearDown() {
        resetFirstWeekday()
    }

    @Test
    fun testEmptyHistoryIsDueEveryDay() {
        val due = DueDays()
        assertNull(due.current)
        assertTrue((19..25).all { due.isDueOn(day(it)) })
    }

    @Test
    fun testWeekdaysAreDue() {
        val due = DueDays().changedFrom(day(1), monWedFri)
        assertTrue(due.isDueOn(day(19))) // Monday
        assertFalse(due.isDueOn(day(20)))
        assertTrue(due.isDueOn(day(21)))
        assertFalse(due.isDueOn(day(24))) // Saturday
        assertFalse(due.isDueOn(day(25))) // Sunday
    }

    @Test
    fun testChangeNeverAppliesToThePast() {
        val old = DueDays().changedFrom(day(1), monWedFri)
        val tuesday = DueDays.bit(DayOfWeek.TUESDAY)
        val changed = old.changedFrom(day(20), tuesday)
        assertEquals(old.isDueOn(day(19)), changed.isDueOn(day(19)))
        assertTrue(changed.isDueOn(day(19)))
        assertFalse(changed.isDueOn(day(21)))
        assertTrue(changed.isDueOn(day(27)))
    }

    @Test
    fun testSwitchingBackToFrequencyKeepsPastWeekdays() {
        val due = DueDays().changedFrom(day(1), monWedFri).changedFrom(day(20), null)
        assertNull(due.current)
        assertFalse(due.isDueOn(day(18)) && due.isDueOn(day(17))) // old rule: Sunday/Saturday rest
        assertTrue(due.isDueOn(day(24)))
    }

    @Test
    fun testUnchangedSelectionAddsNothing() {
        val due = DueDays().changedFrom(day(1), monWedFri)
        assertEquals(due, due.changedFrom(day(20), monWedFri))
        assertEquals(DueDays(), DueDays().changedFrom(day(20), null))
    }

    @Test
    fun testEncodeAndDecode() {
        val due = DueDays().changedFrom(day(1), monWedFri).changedFrom(day(20), null)
        assertEquals(due, DueDays.decode(due.encode()))
        assertEquals(DueDays(), DueDays.decode(""))
        assertEquals(DueDays(), DueDays.decode("garbage"))
    }

    @Test
    fun testWeekdayListConversion() {
        assertEquals(monWedFri, DueDays.fromWeekdayList(DueDays.toWeekdayList(monWedFri)))
        val saturdayFirst = DueDays.toWeekdayList(DueDays.bit(DayOfWeek.SATURDAY)).toArray()
        assertTrue(saturdayFirst[0])
    }

    private fun fixedHabit(vararg checked: Int): Habit {
        val h = modelFactory.buildHabit()
        h.dueDays = DueDays().changedFrom(day(1), monWedFri)
        checked.forEach { h.originalEntries.add(Entry(day(it), Entry.YES_MANUAL)) }
        h.recompute()
        return h
    }

    @Test
    fun testRestDaysDoNotBreakTheStreak() {
        setFirstWeekday(DayOfWeek.MONDAY)
        // Mon, Wed, Fri done; Tuesday and Thursday are rest days
        val h = fixedHabit(19, 21, 23)
        assertEquals(Entry.SKIP, h.computedEntries.get(day(20)).value)
        assertEquals(Entry.SKIP, h.computedEntries.get(day(22)).value)
        val streak = h.streaks.getOngoingStreak(day(23))!!
        // The leading weekend (17, 18) are rest days and belong to the streak, which then starts there
        assertEquals(day(17), streak.start)
        assertEquals(day(25), streak.end) // the weekend after it is rest, too
    }

    @Test
    fun testMissedDueDayBreaksTheStreak() {
        val h = fixedHabit(19, 23)
        // Wednesday (21) was due and missed
        val streak = h.streaks.getOngoingStreak(day(23))!!
        // Wednesday was missed; the rest day after it (22) is where the new streak begins
        assertEquals(day(22), streak.start)
    }

    @Test
    fun testVoluntaryCheckOnRestDayStays() {
        val h = fixedHabit(19, 20, 21, 23)
        assertEquals(Entry.YES_MANUAL, h.computedEntries.get(day(20)).value)
        val streak = h.streaks.getOngoingStreak(day(23))!!
        // The leading weekend (17, 18) are rest days and belong to the streak, which then starts there
        assertEquals(day(17), streak.start)
    }

    @Test
    fun testRestDayDoesNotLowerScore() {
        val h = fixedHabit(19, 21, 23)
        val beforeRest = h.scores[day(21)].value
        val afterRest = h.scores[day(22)].value
        assertEquals(beforeRest, afterRest)
    }

    @Test
    fun testIsRestDay() {
        val h = fixedHabit()
        assertTrue(h.isRestDay(day(20)))
        assertFalse(h.isRestDay(day(19)))
        assertFalse(modelFactory.buildHabit().isRestDay(day(20)))
    }

    @Test
    fun testPastKeepsTheOldWeekdaysAfterAChange() {
        val h = modelFactory.buildHabit()
        h.dueDays = DueDays().changedFrom(day(1), monWedFri).changedFrom(day(23), DueDays.bit(DayOfWeek.SATURDAY))
        h.originalEntries.add(Entry(day(19), Entry.YES_MANUAL))
        h.originalEntries.add(Entry(day(21), Entry.YES_MANUAL))
        h.recompute()
        // Friday the 23rd is a rest day now, but the 19th and 21st were judged by the old days
        assertEquals(Entry.SKIP, h.computedEntries.get(day(23)).value)
        assertEquals(Entry.SKIP, h.computedEntries.get(day(20)).value)
        assertEquals(day(17), h.streaks.getOngoingStreak(day(23))?.start)
    }
}
