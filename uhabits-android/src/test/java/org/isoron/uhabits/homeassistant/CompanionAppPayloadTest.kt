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

import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.commands.EntrySource
import org.isoron.uhabits.core.events.HabitEvent
import org.isoron.uhabits.core.events.HabitEvent.EntryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionAppPayloadTest {
    private val numerical = HabitEvent(
        habitUuid = "abc123",
        habitName = "Run",
        category = "Sport",
        date = LocalDate(2026, 3, 7),
        state = EntryState.VALUE,
        value = 2.5,
        habitType = HabitEvent.Type.NUMERICAL,
        unit = "km",
        target = 5.0,
        targetType = HabitEvent.TargetType.AT_LEAST,
        completed = false,
        currentStreak = 3,
        score = 0.25,
        source = EntrySource.WIDGET
    )

    @Test
    fun mapsNumericalEvent() {
        assertEquals(
            mapOf(
                "v" to "1",
                "type" to "entry_changed",
                "habit_uuid" to "abc123",
                "habit_name" to "Run",
                "category" to "Sport",
                "habit_type" to "numerical",
                "date" to "2026-03-07",
                "state" to "value",
                "value" to "2.5",
                "unit" to "km",
                "target" to "5",
                "target_type" to "at_least",
                "completed" to "false",
                "streak" to "3",
                "score" to "0.25",
                "source" to "widget"
            ),
            CompanionAppPayload.extras(numerical)
        )
    }

    @Test
    fun leavesOutNulls() {
        val event = numerical.copy(
            state = EntryState.SKIP,
            value = null,
            habitType = HabitEvent.Type.YES_NO,
            target = null,
            targetType = null,
            source = EntrySource.EXTERNAL
        )
        val extras = CompanionAppPayload.extras(event)
        assertFalse("value" in extras)
        assertFalse("target" in extras)
        assertFalse("target_type" in extras)
        assertEquals("skip", extras["state"])
        assertEquals("yes_no", extras["habit_type"])
        assertEquals("external", extras["source"])
    }

    @Test
    fun formatsNumbersWithoutLocale() {
        assertEquals("2", CompanionAppPayload.number(2.0))
        assertEquals("0", CompanionAppPayload.number(0.0))
        assertEquals("0.123", CompanionAppPayload.number(0.123))
        assertEquals("1234.5", CompanionAppPayload.number(1234.5))
    }

    @Test
    fun generatedTokenIsSafeForIntentExtras() {
        val token = HomeAssistantPreferences.generateToken()
        assertEquals(64, token.length)
        assertTrue(token.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
