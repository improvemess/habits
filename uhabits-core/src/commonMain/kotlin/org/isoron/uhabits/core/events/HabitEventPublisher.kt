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

import org.isoron.uhabits.core.commands.Command
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.models.Habit

/**
 * Listens for finished commands and publishes a [HabitEvent] for every entry
 * change to the given [transport].
 *
 * All entry changes (list, habit screen, widgets, notifications, automation)
 * go through [CreateRepetitionCommand], so this is the single hook point.
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

    override fun onCommandFinished(command: Command) {
        if (command !is CreateRepetitionCommand) return
        if (!habitFilter(command.habit)) return
        val event = HabitEventFactory.entryChanged(command.habit, command.date, command.source)
        transport.send(event)
    }
}
