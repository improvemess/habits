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

import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.not
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.BaseAndroidJVMTest
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.NumericalHabitType
import org.isoron.uhabits.core.ui.NotificationTray
import org.isoron.uhabits.homeassistant.SetEntryHandler.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class SetEntryHandlerTest : BaseAndroidJVMTest() {
    private lateinit var handler: SetEntryHandler
    private lateinit var notificationTray: NotificationTray
    private lateinit var yesNo: Habit
    private lateinit var numerical: Habit

    override fun setUp() {
        super.setUp()
        notificationTray = mock()
        handler = SetEntryHandler(habitList, commandRunner, notificationTray)
        yesNo = fixtures.createEmptyHabit().also(habitList::add)
        numerical = fixtures.createEmptyNumericalHabit(NumericalHabitType.AT_LEAST)
            .also(habitList::add)
    }

    private fun handle(
        vararg extras: Pair<String, String>,
        acceptedToken: String? = TOKEN
    ): Result {
        val map = mapOf("token" to TOKEN) + extras
        return handler.handle(map::get, acceptedToken)
    }

    private fun Habit.valueOn(date: LocalDate) = originalEntries.get(date).value

    @Test
    fun setsYesNoEntryForToday() {
        val result = handle("habit_uuid" to yesNo.uuid!!, "value" to "yes")
        assertEquals(Result.Ok(yesNo, getToday(), Entry.YES_MANUAL), result)
        assertEquals(Entry.YES_MANUAL, yesNo.valueOn(getToday()))
        verify { notificationTray.cancel(yesNo) }
    }

    @Test
    fun acceptsAllYesNoSpellings() {
        val expected = mapOf(
            "yes" to Entry.YES_MANUAL,
            "1" to Entry.YES_MANUAL,
            "TRUE" to Entry.YES_MANUAL,
            "no" to Entry.NO,
            "0" to Entry.NO,
            "false" to Entry.NO,
            "skip" to Entry.SKIP,
            "unknown" to Entry.UNKNOWN
        )
        for ((value, entry) in expected) {
            handle("habit_uuid" to yesNo.uuid!!, "value" to value)
            assertEquals("value=$value", entry, yesNo.valueOn(getToday()))
        }
    }

    @Test
    fun setsNumericalEntryInThousandths() {
        val result = handle("habit_uuid" to numerical.uuid!!, "value" to "2.5")
        assertEquals(Result.Ok(numerical, getToday(), 2500), result)
        assertEquals(2500, numerical.valueOn(getToday()))
    }

    @Test
    fun setsEntryForPastDateWithoutCancellingNotification() {
        val date = getToday().minus(3)
        val result = handle(
            "habit_uuid" to yesNo.uuid!!,
            "value" to "yes",
            "date" to date.toCSVString()
        )
        assertEquals(Result.Ok(yesNo, date, Entry.YES_MANUAL), result)
        assertEquals(Entry.YES_MANUAL, yesNo.valueOn(date))
        verify(not) { notificationTray.cancel(any()) }
    }

    @Test
    fun keepsExistingNotes() {
        val today = getToday()
        yesNo.originalEntries.add(Entry(today, Entry.NO, "felt tired"))
        handle("habit_uuid" to yesNo.uuid!!, "value" to "yes")
        assertEquals("felt tired", yesNo.originalEntries.get(today).notes)
    }

    @Test
    fun rejectsWhenDisabled() {
        val result = handle("habit_uuid" to yesNo.uuid!!, "value" to "yes", acceptedToken = null)
        assertEquals(Result.Disabled, result)
        assertEquals(Entry.UNKNOWN, yesNo.valueOn(getToday()))
    }

    @Test
    fun rejectsWrongOrMissingToken() {
        val wrong = handler.handle(
            mapOf("token" to "nope", "habit_uuid" to yesNo.uuid!!, "value" to "yes")::get,
            TOKEN
        )
        val missing = handler.handle(
            mapOf("habit_uuid" to yesNo.uuid!!, "value" to "yes")::get,
            TOKEN
        )
        assertEquals(Result.Unauthorized, wrong)
        assertEquals(Result.Unauthorized, missing)
        assertEquals(Entry.UNKNOWN, yesNo.valueOn(getToday()))
    }

    @Test
    fun rejectsUnknownOrMissingHabit() {
        assertEquals(Result.UnknownHabit, handle("habit_uuid" to "doesnotexist", "value" to "yes"))
        assertEquals(Result.UnknownHabit, handle("value" to "yes"))
    }

    @Test
    fun rejectsInvalidValues() {
        for (value in listOf("maybe", "", "2")) {
            assertEquals(
                "yes/no value=$value",
                Result.InvalidValue,
                handle("habit_uuid" to yesNo.uuid!!, "value" to value)
            )
        }
        for (value in listOf("abc", "-1", "NaN", "1e7", "yes")) {
            assertEquals(
                "numerical value=$value",
                Result.InvalidValue,
                handle("habit_uuid" to numerical.uuid!!, "value" to value)
            )
        }
        assertEquals(Result.InvalidValue, handle("habit_uuid" to yesNo.uuid!!))
        assertEquals(Entry.UNKNOWN, yesNo.valueOn(getToday()))
    }

    @Test
    fun rejectsInvalidAndFutureDates() {
        val future = getToday().plus(1).toCSVString()
        for (date in listOf("2015-02-30", "2015-13-01", "25.01.2015", "2015-1-5", future)) {
            assertEquals(
                "date=$date",
                Result.InvalidDate,
                handle("habit_uuid" to yesNo.uuid!!, "value" to "yes", "date" to date)
            )
        }
    }

    companion object {
        private const val TOKEN = "0123456789abcdef"
    }
}
