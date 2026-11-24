package org.isoron.uhabits.core.commands

import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Pause
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PauseCommandsTest : BaseUnitTest() {
    private val today get() = getToday()

    @Test
    fun testAddPauseMarksFutureDaysAsSkip() {
        val habit = fixtures.createEmptyHabit()
        AddPauseCommand(habitList, habit, today.plus(1), today.plus(3)).run()
        assertEquals(listOf(Pause(today.plus(1), today.plus(3))), habit.pauses.getAll())
        assertEquals(Entry.SKIP, habit.computedEntries.get(today.plus(2)).value)
        // The pause is not stored as entries
        assertEquals(Entry.UNKNOWN, habit.originalEntries.get(today.plus(2)).value)
    }

    @Test
    fun testPauseKeepsTitle() {
        val habit = fixtures.createEmptyHabit()
        AddPauseCommand(habitList, habit, today.plus(1), today.plus(3), "Vacation").run()
        assertEquals("Vacation", habit.pauses.getAll().single().title)
    }

    @Test
    fun testPauseStartingTodayMarksTodayAsSkip() {
        for (habit in listOf(fixtures.createEmptyHabit(), fixtures.createShortHabit())) {
            habit.originalEntries.add(Entry(today, Entry.NO, "note"))
            AddPauseCommand(habitList, habit, today, today.plus(2)).run()
            assertEquals(Entry.SKIP, habit.computedEntries.get(today).value)
            assertEquals(Entry.SKIP, habit.computedEntries.get(today.plus(1)).value)
        }
    }

    @Test
    fun testCannotPauseMoreThanAMonthAhead() {
        val habit = fixtures.createEmptyHabit()
        assertFailsWith<IllegalArgumentException> {
            AddPauseCommand(habitList, habit, today.plus(20), today.plus(32)).run()
        }
        AddPauseCommand(habitList, habit, today.plus(20), today.plus(31)).run()
    }

    @Test
    fun testChangeEndExtendsAndShortens() {
        val habit = fixtures.createEmptyHabit()
        val pause = Pause(today.minus(2), today.plus(2), "Trip")
        habit.pauses.add(pause)
        ChangePauseEndCommand(habitList, habit, pause, today.plus(6)).run()
        assertEquals(listOf(Pause(today.minus(2), today.plus(6), "Trip")), habit.pauses.getAll())
        val extended = habit.pauses.getAll().single()
        ChangePauseEndCommand(habitList, habit, extended, today).run()
        assertEquals(today, habit.pauses.getAll().single().end)
        // the past days stay paused, the days after the new end are released
        assertEquals(Entry.SKIP, habit.computedEntries.get(today.minus(1)).value)
        assertEquals(Entry.UNKNOWN, habit.computedEntries.get(today.plus(1)).value)
    }

    @Test
    fun testChangeEndCannotGoIntoThePastOrOverlapNextPause() {
        val habit = fixtures.createEmptyHabit()
        val first = Pause(today.minus(2), today.plus(2))
        val second = Pause(today.plus(10), today.plus(12))
        habit.pauses.add(first)
        habit.pauses.add(second)
        assertFailsWith<IllegalArgumentException> {
            ChangePauseEndCommand(habitList, habit, first, today.minus(1)).run()
        }
        assertFailsWith<IllegalArgumentException> {
            ChangePauseEndCommand(habitList, habit, first, today.plus(10)).run()
        }
        ChangePauseEndCommand(habitList, habit, first, today.plus(9)).run()
        assertEquals(today.plus(9), habit.pauses.getAll().first().end)
    }

    @Test
    fun testCannotPauseRetroactively() {
        val habit = fixtures.createEmptyHabit()
        assertFailsWith<IllegalArgumentException> {
            AddPauseCommand(habitList, habit, today.minus(1), today.plus(1)).run()
        }
    }

    @Test
    fun testMultiplePauses() {
        val habit = fixtures.createEmptyHabit()
        AddPauseCommand(habitList, habit, today.plus(1), today.plus(2)).run()
        AddPauseCommand(habitList, habit, today.plus(10), today.plus(12)).run()
        assertEquals(2, habit.pauses.getAll().size)
        assertTrue(habit.pauses.isPaused(today.plus(11)))
        assertFalse(habit.pauses.isPaused(today.plus(5)))
    }

    @Test
    fun testOverlappingPausesAreRejected() {
        val habit = fixtures.createEmptyHabit()
        AddPauseCommand(habitList, habit, today.plus(5), today.plus(10)).run()
        // overlaps at the start, at the end, inside and around the existing pause
        for ((from, to) in listOf(3 to 5, 10 to 12, 6 to 8, 2 to 12)) {
            assertFailsWith<IllegalArgumentException> {
                AddPauseCommand(habitList, habit, today.plus(from), today.plus(to)).run()
            }
        }
        // directly adjacent pauses are fine
        AddPauseCommand(habitList, habit, today.plus(11), today.plus(12)).run()
        assertEquals(2, habit.pauses.getAll().size)
    }

    @Test
    fun testRemoveRunningPauseReleasesAllItsDays() {
        val habit = fixtures.createEmptyHabit()
        val pause = Pause(today.minus(2), today.plus(2))
        habit.pauses.add(pause)
        habit.recompute()
        assertEquals(Entry.SKIP, habit.computedEntries.get(today.minus(1)).value)
        RemovePauseCommand(habitList, habit, pause).run()
        assertTrue(habit.pauses.isEmpty())
        assertEquals(Entry.UNKNOWN, habit.computedEntries.get(today.minus(1)).value)
        assertEquals(Entry.UNKNOWN, habit.computedEntries.get(today.plus(1)).value)
    }

    @Test
    fun testRemoveFuturePauseDeletesIt() {
        val habit = fixtures.createEmptyHabit()
        val pause = Pause(today.plus(3), today.plus(5))
        habit.pauses.add(pause)
        RemovePauseCommand(habitList, habit, pause).run()
        assertTrue(habit.pauses.isEmpty())
    }

    @Test
    fun testRemovePastPauseIsAllowed() {
        val habit = fixtures.createEmptyHabit()
        val pause = Pause(today.minus(5), today.minus(3))
        habit.pauses.add(pause)
        RemovePauseCommand(habitList, habit, pause).run()
        assertTrue(habit.pauses.isEmpty())
    }

    @Test
    fun testUncheckingOnPausedDayRestoresPause() {
        val habit = fixtures.createEmptyHabit()
        habit.pauses.add(Pause(today, today.plus(2)))
        habit.recompute()
        // choosing "no" on an empty paused day keeps it paused (does not check it)
        CreateRepetitionCommand(habitList, habit, today, Entry.NO, "").run()
        assertEquals(Entry.UNKNOWN, habit.originalEntries.get(today).value)
        assertEquals(Entry.SKIP, habit.computedEntries.get(today).value)
        // checking the day works, and "no" afterwards brings back the pause (no cross)
        CreateRepetitionCommand(habitList, habit, today, Entry.YES_MANUAL, "").run()
        assertEquals(Entry.YES_MANUAL, habit.computedEntries.get(today).value)
        CreateRepetitionCommand(habitList, habit, today, Entry.NO, "").run()
        assertEquals(Entry.UNKNOWN, habit.originalEntries.get(today).value)
        assertEquals(Entry.SKIP, habit.computedEntries.get(today).value)
    }
}
