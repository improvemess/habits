/*
 * Copyright (C) 2026 Christoph Schmidtmeier
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
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakUnitTest {
    private val monday = DayOfWeek.MONDAY

    @Test
    fun testDefaultDependsOnFrequency() {
        assertEquals(StreakUnit.DAYS, StreakUnit.defaultFor(Frequency.DAILY))
        assertEquals(StreakUnit.DAYS, StreakUnit.defaultFor(Frequency(3, 4)))
        assertEquals(StreakUnit.WEEKS, StreakUnit.defaultFor(Frequency.THREE_TIMES_PER_WEEK))
        assertEquals(StreakUnit.WEEKS, StreakUnit.defaultFor(Frequency.WEEKLY))
        assertEquals(StreakUnit.MONTHS, StreakUnit.defaultFor(Frequency(1, 30)))
        assertEquals(StreakUnit.MONTHS, StreakUnit.defaultFor(Frequency(2, 31)))
    }

    @Test
    fun testFromId() {
        assertEquals(StreakUnit.WEEKS, StreakUnit.fromId(1))
        assertEquals(null, StreakUnit.fromId(StreakUnit.AUTO_ID))
    }

    @Test
    fun testRollingConvertsByLength() {
        val from = LocalDate(2026, 3, 4)
        val to = from.plus(29) // 30 days
        assertEquals(30, StreakUnit.convert(from, to, StreakUnit.DAYS, false, monday))
        assertEquals(4, StreakUnit.convert(from, to, StreakUnit.WEEKS, false, monday))
        assertEquals(1, StreakUnit.convert(from, to, StreakUnit.MONTHS, false, monday))
        assertEquals(0, StreakUnit.convert(from, from.plus(5), StreakUnit.WEEKS, false, monday))
    }

    @Test
    fun testCalendarCountsOnlyCompletePeriods() {
        // Mon 2 Mar 2026 .. Sun 22 Mar 2026 = exactly 3 calendar weeks
        assertEquals(3, StreakUnit.convert(LocalDate(2026, 3, 2), LocalDate(2026, 3, 22), StreakUnit.WEEKS, true, monday))
        // Starting on a Wednesday, the first, incomplete week does not count
        assertEquals(2, StreakUnit.convert(LocalDate(2026, 3, 4), LocalDate(2026, 3, 22), StreakUnit.WEEKS, true, monday))
        // A week that is still running does not count
        assertEquals(2, StreakUnit.convert(LocalDate(2026, 3, 2), LocalDate(2026, 3, 18), StreakUnit.WEEKS, true, monday))
        // February 2026 has 28 days and March 31
        assertEquals(2, StreakUnit.convert(LocalDate(2026, 2, 1), LocalDate(2026, 3, 31), StreakUnit.MONTHS, true, monday))
        assertEquals(1, StreakUnit.convert(LocalDate(2026, 2, 10), LocalDate(2026, 3, 31), StreakUnit.MONTHS, true, monday))
    }
}
