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
package org.isoron.uhabits.core.events

import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.commands.EntrySource
import org.isoron.uhabits.core.events.HabitEvent.EntryState
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.NumericalHabitType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HabitEventFactoryTest : BaseUnitTest() {
    private lateinit var today: LocalDate

    @BeforeTest
    override fun setUp() {
        super.setUp()
        today = getToday()
    }

    private fun yesNoHabit(vararg entries: Pair<Int, Int>): Habit {
        val habit = fixtures.createEmptyHabit(name = "Meditate", category = "Health")
        for ((daysAgo, value) in entries) habit.originalEntries.add(Entry(today.minus(daysAgo), value))
        habit.recompute()
        return habit
    }

    private fun numericalHabit(vararg entries: Pair<Int, Int>): Habit {
        val habit = fixtures.createEmptyNumericalHabit(NumericalHabitType.AT_LEAST)
        for ((daysAgo, value) in entries) habit.originalEntries.add(Entry(today.minus(daysAgo), value))
        habit.recompute()
        return habit
    }

    @Test
    fun testYesNoHabitChecked() {
        val habit = yesNoHabit(0 to Entry.YES_MANUAL, 1 to Entry.YES_MANUAL)
        val event = HabitEventFactory.entryChanged(habit, today, EntrySource.APP)
        assertEquals(habit.uuid, event.habitUuid)
        assertEquals("Meditate", event.habitName)
        assertEquals("Health", event.category)
        assertEquals("2015-01-25", event.date)
        assertEquals(EntryState.YES, event.state)
        assertEquals(1.0, event.value)
        assertEquals(HabitEvent.Type.YES_NO, event.habitType)
        assertNull(event.target)
        assertNull(event.targetType)
        assertTrue(event.completed)
        assertEquals(2, event.currentStreak)
        assertTrue(event.score > 0.0)
        assertEquals(EntrySource.APP, event.source)
    }

    @Test
    fun testYesNoHabitUnchecked() {
        val habit = yesNoHabit(0 to Entry.NO, 1 to Entry.YES_MANUAL)
        val event = HabitEventFactory.entryChanged(habit, today, EntrySource.WIDGET)
        assertEquals(EntryState.NO, event.state)
        assertEquals(0.0, event.value)
        assertFalse(event.completed)
        // Streak ending yesterday is still considered current
        assertEquals(1, event.currentStreak)
        assertEquals(EntrySource.WIDGET, event.source)
    }

    @Test
    fun testSkip() {
        val event = HabitEventFactory.entryChanged(yesNoHabit(0 to Entry.SKIP), today, EntrySource.APP)
        assertEquals(EntryState.SKIP, event.state)
        assertNull(event.value)
    }

    @Test
    fun testUnknown() {
        val event = HabitEventFactory.entryChanged(yesNoHabit(), today, EntrySource.APP)
        assertEquals(EntryState.UNKNOWN, event.state)
        assertNull(event.value)
        assertFalse(event.completed)
        assertEquals(0, event.currentStreak)
    }

    @Test
    fun testYesAuto() {
        val habit = fixtures.createEmptyHabit()
        habit.frequency = Frequency.WEEKLY
        habit.originalEntries.add(Entry(today.minus(1), Entry.YES_MANUAL))
        habit.recompute()
        val event = HabitEventFactory.entryChanged(habit, today, EntrySource.APP)
        assertEquals(EntryState.YES_AUTO, event.state)
        assertEquals(0.0, event.value)
        assertTrue(event.completed)
    }

    @Test
    fun testPastDate() {
        val habit = yesNoHabit(3 to Entry.YES_MANUAL)
        val event = HabitEventFactory.entryChanged(habit, today.minus(3), EntrySource.APP)
        assertEquals("2015-01-22", event.date)
        assertEquals(EntryState.YES, event.state)
        assertTrue(event.completed)
        // The streak is evaluated as of today, so an old streak is not current
        assertEquals(0, event.currentStreak)
    }

    @Test
    fun testNumericalHabit() {
        val habit = numericalHabit(0 to 2500)
        val event = HabitEventFactory.entryChanged(habit, today, EntrySource.NOTIFICATION)
        assertEquals(EntryState.VALUE, event.state)
        assertEquals(2.5, event.value)
        assertEquals(HabitEvent.Type.NUMERICAL, event.habitType)
        assertEquals("miles", event.unit)
        assertEquals(2.0, event.target)
        assertEquals(HabitEvent.TargetType.AT_LEAST, event.targetType)
        assertTrue(event.completed)
        assertEquals(1, event.currentStreak)
    }

    @Test
    fun testNumericalHabitBelowTarget() {
        val event = HabitEventFactory.entryChanged(numericalHabit(0 to 1000), today, EntrySource.APP)
        assertEquals(EntryState.VALUE, event.state)
        assertEquals(1.0, event.value)
        assertFalse(event.completed)
    }

    @Test
    fun testNumericalHabitSkipAndUnknown() {
        val skipped = HabitEventFactory.entryChanged(numericalHabit(0 to Entry.SKIP), today, EntrySource.APP)
        assertEquals(EntryState.SKIP, skipped.state)
        assertNull(skipped.value)

        val unknown = HabitEventFactory.entryChanged(numericalHabit(), today, EntrySource.APP)
        assertEquals(EntryState.UNKNOWN, unknown.state)
        assertNull(unknown.value)
    }
}
