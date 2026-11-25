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

import org.isoron.uhabits.core.commands.AddPauseCommand
import org.isoron.uhabits.core.commands.ArchiveHabitsCommand
import org.isoron.uhabits.core.commands.ChangeHabitCategoryCommand
import org.isoron.uhabits.core.commands.ChangeHabitColorCommand
import org.isoron.uhabits.core.commands.ChangePauseEndCommand
import org.isoron.uhabits.core.commands.Command
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.commands.DeleteHabitsCommand
import org.isoron.uhabits.core.commands.EditHabitCommand
import org.isoron.uhabits.core.commands.RemovePauseCommand
import org.isoron.uhabits.core.commands.RenameCategoryCommand
import org.isoron.uhabits.core.commands.ReorderHabitCommand
import org.isoron.uhabits.core.commands.UnarchiveHabitsCommand
import org.isoron.uhabits.core.models.Habit

/**
 * Listens for finished commands and publishes to the given [transport]:
 *
 * - a [HabitEvent] for every entry change. All entry changes (list, habit
 *   screen, widgets, notifications, automation) go through
 *   [CreateRepetitionCommand], so this is the single hook point for them.
 * - a [HabitLifecycleEvent] when a habit is edited, paused, archived,
 *   unarchived or deleted. [HabitLifecycleEvent.Updated] is only sent when the habit's
 *   [HabitSnapshot] differs from the one last sent, so that e.g. reordering
 *   does not produce events. Imports change habits without commands and
 *   produce no events.
 *
 * @param habitFilter decides which habits are published (e.g. the user's
 *   selection in the integration settings). All habits by default.
 */
class HabitEventPublisher(
    private val commandRunner: CommandRunner,
    private val transport: HabitEventTransport,
    private val habitFilter: (Habit) -> Boolean = { true }
) : CommandRunner.Listener {

    fun startListening() {
        commandRunner.addListener(this)
    }

    fun stopListening() {
        commandRunner.removeListener(this)
    }

    private val lastSnapshots = mutableMapOf<String, HabitSnapshot>()

    override fun onCommandFinished(command: Command) {
        when (command) {
            is CreateRepetitionCommand -> publishEntryChange(command)
            is EditHabitCommand ->
                publishUpdates(listOfNotNull(command.habitList.getById(command.habitId)))
            is ChangeHabitColorCommand -> publishUpdates(command.selected)
            is ChangeHabitCategoryCommand -> publishUpdates(command.selected)
            is RenameCategoryCommand ->
                publishUpdates(command.habitList.filter { it.category == command.newCategory })
            is ReorderHabitCommand -> publishUpdates(listOf(command.habit))
            is AddPauseCommand -> publishUpdates(listOf(command.habit))
            is ChangePauseEndCommand -> publishUpdates(listOf(command.habit))
            is RemovePauseCommand -> publishUpdates(listOf(command.habit))
            is ArchiveHabitsCommand ->
                publishEach(command.selected) { HabitLifecycleEvent.Archived(it) }
            is UnarchiveHabitsCommand ->
                publishEach(command.selected) { HabitLifecycleEvent.Unarchived(it) }
            is DeleteHabitsCommand -> publishEach(command.selected) { uuid ->
                lastSnapshots.remove(uuid)
                HabitLifecycleEvent.Deleted(uuid)
            }
            else -> {}
        }
    }

    private fun publishEntryChange(command: CreateRepetitionCommand) {
        if (!habitFilter(command.habit)) return
        val event = HabitEventFactory.entryChanged(command.habit, command.date, command.source)
        transport.send(event)
    }

    private fun publishUpdates(habits: List<Habit>) {
        for (habit in habits) {
            val uuid = habit.uuid ?: continue
            if (!habitFilter(habit)) continue
            val snapshot = HabitEventFactory.snapshot(habit)
            if (lastSnapshots[uuid] == snapshot) continue
            lastSnapshots[uuid] = snapshot
            transport.send(HabitLifecycleEvent.Updated(snapshot))
        }
    }

    private fun publishEach(habits: List<Habit>, event: (String) -> HabitLifecycleEvent) {
        for (habit in habits) {
            val uuid = habit.uuid ?: continue
            if (!habitFilter(habit)) continue
            transport.send(event(uuid))
        }
    }
}
