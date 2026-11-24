package org.isoron.uhabits.core.commands

import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.Entry
import kotlin.test.Test
import kotlin.test.assertEquals

class PauseHabitCommandTest : BaseUnitTest() {
    @Test
    fun testPauseSetsSkipButKeepsRealEntries() {
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        habit.originalEntries.add(Entry(today.minus(1), Entry.YES_MANUAL))
        habit.recompute()

        PauseHabitCommand(habitList, habit, today.minus(2), today).run()

        assertEquals(Entry.SKIP, habit.originalEntries.get(today.minus(2)).value)
        assertEquals(Entry.YES_MANUAL, habit.originalEntries.get(today.minus(1)).value)
        assertEquals(Entry.SKIP, habit.originalEntries.get(today).value)
    }

    @Test
    fun testResumeClearsSkipOnly() {
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        PauseHabitCommand(habitList, habit, today.minus(2), today).run()
        habit.originalEntries.add(Entry(today, Entry.YES_MANUAL))

        PauseHabitCommand(habitList, habit, today.minus(2), today, paused = false).run()

        assertEquals(Entry.UNKNOWN, habit.originalEntries.get(today.minus(2)).value)
        assertEquals(Entry.YES_MANUAL, habit.originalEntries.get(today).value)
    }
}
