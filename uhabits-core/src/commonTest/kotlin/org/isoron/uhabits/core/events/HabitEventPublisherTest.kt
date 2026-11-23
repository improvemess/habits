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

import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.commands.ChangeHabitColorCommand
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.commands.EntrySource
import org.isoron.uhabits.core.events.HabitEvent.EntryState
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.PaletteColor
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HabitEventPublisherTest : BaseUnitTest() {
    private lateinit var habit: Habit
    private val events = mutableListOf<HabitEvent>()

    @BeforeTest
    override fun setUp() {
        super.setUp()
        habit = fixtures.createEmptyHabit()
        habitList.add(habit)
        events.clear()
    }

    private fun publisher(filter: (Habit) -> Boolean = { true }) =
        HabitEventPublisher(commandRunner, { events.add(it) }, filter).apply { startListening() }

    private fun check(source: EntrySource = EntrySource.APP) {
        commandRunner.run(
            CreateRepetitionCommand(habitList, habit, getToday(), Entry.YES_MANUAL, "", source)
        )
    }

    @Test
    fun testPublishesEntryChange() {
        publisher()
        check(EntrySource.EXTERNAL)
        assertEquals(1, events.size)
        assertEquals(habit.uuid, events[0].habitUuid)
        assertEquals(EntryState.YES, events[0].state)
        assertTrue(events[0].completed)
        assertEquals(EntrySource.EXTERNAL, events[0].source)
    }

    @Test
    fun testDefaultSourceIsApp() {
        publisher()
        check()
        assertEquals(EntrySource.APP, events.single().source)
    }

    @Test
    fun testIgnoresOtherCommands() {
        publisher()
        commandRunner.run(ChangeHabitColorCommand(habitList, listOf(habit), PaletteColor(5)))
        assertTrue(events.isEmpty())
    }

    @Test
    fun testRespectsHabitFilter() {
        publisher { it.uuid != habit.uuid }
        check()
        assertTrue(events.isEmpty())
    }

    @Test
    fun testStopListening() {
        publisher().stopListening()
        check()
        assertTrue(events.isEmpty())
    }
}
