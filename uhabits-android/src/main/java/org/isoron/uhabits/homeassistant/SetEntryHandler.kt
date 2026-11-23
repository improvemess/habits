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
package org.isoron.uhabits.homeassistant

import me.tatarka.inject.annotations.Inject
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.commands.EntrySource
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.ui.NotificationTray
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Handles a "set entry" command sent by Home Assistant.
 *
 * Free of Android types, so that the validation can be tested on the JVM:
 * the receiver passes the intent extras in as [extras].
 *
 * Extras (all strings, as the Companion App's `intent_extras` syntax can also
 * send typed values, which arrive via [Any.toString]):
 * - `token`: the shared secret
 * - `habit_uuid`
 * - `value`: for yes/no habits `yes`/`no`/`skip`/`unknown` (or `1`/`0`,
 *   `true`/`false`); for numerical habits a number in the habit's unit
 * - `date` (optional): `YYYY-MM-DD`, today by default; must not be in the future
 */
@Inject
class SetEntryHandler(
    private val habitList: HabitList,
    private val commandRunner: CommandRunner,
    private val notificationTray: NotificationTray
) {
    sealed interface Result {
        data class Ok(val habit: Habit, val date: LocalDate, val value: Int) : Result

        /** The integration is disabled or has no token. */
        data object Disabled : Result
        data object Unauthorized : Result
        data object UnknownHabit : Result
        data object InvalidValue : Result
        data object InvalidDate : Result
    }

    /**
     * @param acceptedToken the token commands must carry, or null if commands
     *   are not accepted at all.
     */
    fun handle(extras: (String) -> String?, acceptedToken: String?): Result {
        if (acceptedToken == null) return Result.Disabled
        if (!tokenMatches(extras(EXTRA_TOKEN), acceptedToken)) return Result.Unauthorized

        val habit = extras(EXTRA_HABIT_UUID)?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { habitList.getByUUID(it) }
            ?: return Result.UnknownHabit

        val today = getToday()
        val date = when (val raw = extras(EXTRA_DATE)?.trim()) {
            null, "" -> today
            else -> parseDate(raw) ?: return Result.InvalidDate
        }
        if (date.isNewerThan(today)) return Result.InvalidDate

        val raw = extras(EXTRA_VALUE)?.trim()?.lowercase() ?: return Result.InvalidValue
        val value = (if (habit.isNumerical) numericalValue(raw) else yesNoValue(raw))
            ?: return Result.InvalidValue

        val notes = habit.originalEntries.get(date).notes
        commandRunner.run(
            CreateRepetitionCommand(habitList, habit, date, value, notes, EntrySource.EXTERNAL)
        )
        if (date == today) notificationTray.cancel(habit)
        return Result.Ok(habit, date, value)
    }

    private fun tokenMatches(given: String?, expected: String): Boolean {
        if (given == null) return false
        return MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())
    }

    private fun yesNoValue(raw: String): Int? = when (raw) {
        "yes", "1", "true" -> Entry.YES_MANUAL
        "no", "0", "false" -> Entry.NO
        "skip" -> Entry.SKIP
        "unknown" -> Entry.UNKNOWN
        else -> null
    }

    /** Numerical entries are stored in thousandths of the habit's unit. */
    private fun numericalValue(raw: String): Int? {
        val number = raw.toDoubleOrNull() ?: return null
        if (number.isNaN() || number < 0 || number > MAX_NUMERICAL_VALUE) return null
        return (number * 1000).roundToInt()
    }

    private fun parseDate(raw: String): LocalDate? {
        val match = DATE_PATTERN.matchEntire(raw) ?: return null
        val (y, m, d) = match.destructured
        val month = m.toInt()
        val day = d.toInt()
        if (month !in 1..12 || day !in 1..31) return null
        val date = LocalDate(y.toInt(), month, day)
        // Rejects days that do not exist in that month, e.g. 2026-02-30.
        return date.takeIf { it.toCSVString() == raw }
    }

    companion object {
        const val ACTION_SET_ENTRY = "org.isoron.uhabits.SET_ENTRY"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_HABIT_UUID = "habit_uuid"
        const val EXTRA_VALUE = "value"
        const val EXTRA_DATE = "date"

        /** Keeps `value * 1000` well within [Int]. */
        private const val MAX_NUMERICAL_VALUE = 1_000_000.0
        private val DATE_PATTERN = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    }
}
