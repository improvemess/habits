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
import org.isoron.uhabits.core.commands.ArchiveHabitsCommand
import org.isoron.uhabits.core.commands.ChangeHabitCategoryCommand
import org.isoron.uhabits.core.commands.ChangeHabitColorCommand
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.commands.DeleteHabitsCommand
import org.isoron.uhabits.core.commands.EditHabitCommand
import org.isoron.uhabits.core.commands.EntrySource
import org.isoron.uhabits.core.commands.RenameCategoryCommand
import org.isoron.uhabits.core.commands.ReorderHabitCommand
import org.isoron.uhabits.core.commands.ReorderTarget
import org.isoron.uhabits.core.commands.UnarchiveHabitsCommand
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.core.models.NumericalHabitType
import org.isoron.uhabits.core.models.PaletteColor
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HabitLifecycleEventPublisherTest : BaseUnitTest() {
    private lateinit var habit: Habit
    private lateinit var other: Habit
    private val entryEvents = mutableListOf<HabitEvent>()
    private val events = mutableListOf<HabitLifecycleEvent>()

    private val transport = object : HabitEventTransport {
        override fun send(event: HabitEvent) {
            entryEvents.add(event)
        }

        override fun send(event: HabitLifecycleEvent) {
            events.add(event)
        }
    }

    @BeforeTest
    override fun setUp() {
        super.setUp()
        habit = fixtures.createEmptyHabit()
        habitList.add(habit)
        other = fixtures.createEmptyHabit()
        habitList.add(other)
        entryEvents.clear()
        events.clear()
    }

    private fun publisher(filter: (Habit) -> Boolean = { true }) =
        HabitEventPublisher(commandRunner, transport, filter).apply { startListening() }

    private fun edit(block: Habit.() -> Unit) {
        val modified = modelFactory.buildHabit()
        modified.copyFrom(habit)
        modified.block()
        commandRunner.run(EditHabitCommand(habitList, habit.id!!, modified))
    }

    private fun updates() = events.filterIsInstance<HabitLifecycleEvent.Updated>()

    @Test
    fun testEditPublishesSnapshot() {
        publisher()
        edit {
            name = "Run"
            description = "Outside"
            question = "Did you run?"
            frequency = Frequency(3, 7)
        }
        val snapshot = updates().single().snapshot
        assertEquals(habit.uuid, snapshot.habitUuid)
        assertEquals("Run", snapshot.name)
        assertEquals("Outside", snapshot.description)
        assertEquals("Did you run?", snapshot.question)
        assertEquals(3, snapshot.frequencyNumerator)
        assertEquals(7, snapshot.frequencyDenominator)
        assertEquals(HabitEvent.Type.YES_NO, snapshot.habitType)
        assertNull(snapshot.target)
        assertNull(snapshot.targetType)
    }

    @Test
    fun testSnapshotOfNumericalHabit() {
        publisher()
        edit {
            type = HabitType.NUMERICAL
            unit = "km"
            targetValue = 5.0
            targetType = NumericalHabitType.AT_MOST
        }
        val snapshot = updates().single().snapshot
        assertEquals(HabitEvent.Type.NUMERICAL, snapshot.habitType)
        assertEquals("km", snapshot.unit)
        assertEquals(5.0, snapshot.target)
        assertEquals(HabitEvent.TargetType.AT_MOST, snapshot.targetType)
    }

    @Test
    fun testUnchangedSnapshotIsNotPublishedAgain() {
        publisher()
        edit { name = "Run" }
        edit { name = "Run" }
        assertEquals(1, updates().size)
    }

    @Test
    fun testChangeColorPublishesUpdate() {
        publisher()
        commandRunner.run(ChangeHabitColorCommand(habitList, listOf(habit, other), PaletteColor(5)))
        assertEquals(listOf(habit.uuid, other.uuid), updates().map { it.habitUuid })
        assertTrue(updates().all { it.snapshot.color == 5 })
    }

    @Test
    fun testChangeCategoryPublishesUpdate() {
        publisher()
        commandRunner.run(ChangeHabitCategoryCommand(habitList, listOf(habit), "Sport"))
        assertEquals("Sport", updates().single().snapshot.category)
    }

    @Test
    fun testRenameCategoryPublishesUpdatesForAffectedHabits() {
        commandRunner.run(ChangeHabitCategoryCommand(habitList, listOf(habit), "Sport"))
        publisher()
        commandRunner.run(RenameCategoryCommand(habitList, "Sport", "Fitness"))
        val update = updates().single()
        assertEquals(habit.uuid, update.habitUuid)
        assertEquals("Fitness", update.snapshot.category)
    }

    @Test
    fun testReorderWithinCategoryPublishesNothingAfterFirstSnapshot() {
        publisher()
        commandRunner.run(ChangeHabitColorCommand(habitList, listOf(habit), PaletteColor(5)))
        events.clear()
        commandRunner.run(
            ReorderHabitCommand(habitList, habit, ReorderTarget.After(other), habit.category)
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun testArchiveAndUnarchive() {
        publisher()
        commandRunner.run(ArchiveHabitsCommand(habitList, listOf(habit)))
        commandRunner.run(UnarchiveHabitsCommand(habitList, listOf(habit)))
        assertEquals<List<HabitLifecycleEvent>>(
            listOf(
                HabitLifecycleEvent.Archived(habit.uuid!!),
                HabitLifecycleEvent.Unarchived(habit.uuid!!)
            ),
            events
        )
    }

    @Test
    fun testDelete() {
        publisher()
        commandRunner.run(DeleteHabitsCommand(habitList, listOf(habit, other)))
        assertEquals<List<HabitLifecycleEvent>>(
            listOf(
                HabitLifecycleEvent.Deleted(habit.uuid!!),
                HabitLifecycleEvent.Deleted(other.uuid!!)
            ),
            events
        )
    }

    @Test
    fun testRespectsHabitFilter() {
        publisher { it.uuid == other.uuid }
        commandRunner.run(ChangeHabitColorCommand(habitList, listOf(habit, other), PaletteColor(5)))
        commandRunner.run(ArchiveHabitsCommand(habitList, listOf(habit)))
        commandRunner.run(DeleteHabitsCommand(habitList, listOf(habit)))
        assertEquals(listOf(other.uuid), events.map { it.habitUuid })
    }

    @Test
    fun testEntryChangesAreNotLifecycleEvents() {
        publisher()
        commandRunner.run(
            CreateRepetitionCommand(habitList, habit, getToday(), Entry.YES_MANUAL, "", EntrySource.APP)
        )
        assertEquals(1, entryEvents.size)
        assertTrue(events.isEmpty())
    }

    @Test
    fun testTransportsWithoutLifecycleSupportIgnoreThem() {
        val received = mutableListOf<HabitEvent>()
        HabitEventPublisher(commandRunner, { received.add(it) }).startListening()
        commandRunner.run(ChangeHabitColorCommand(habitList, listOf(habit), PaletteColor(5)))
        commandRunner.run(DeleteHabitsCommand(habitList, listOf(habit)))
        assertTrue(received.isEmpty())
    }

    @Test
    fun testUpdatedEventCarriesUuid() {
        publisher()
        edit { name = "Read" }
        assertIs<HabitLifecycleEvent.Updated>(events.single())
        assertEquals(habit.uuid, events.single().habitUuid)
    }
}
