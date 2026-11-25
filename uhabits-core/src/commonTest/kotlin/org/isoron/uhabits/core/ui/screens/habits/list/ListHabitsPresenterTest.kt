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
package org.isoron.uhabits.core.ui.screens.habits.list

import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList.Order
import org.isoron.uhabits.core.models.HabitMatcher
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.Pause
import org.isoron.uhabits.core.models.StreakUnit
import org.isoron.uhabits.core.ui.views.LightTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class ListHabitsPresenterTest : BaseUnitTest() {
    private val theme = LightTheme()
    private val checkmarkCount = 3

    @Test
    fun testBuildState() {
        addHabit("Beta", PaletteColor(1), position = 0, completedOffsets = intArrayOf(0))
        addHabit("Alpha", PaletteColor(1), position = 1, completedOffsets = intArrayOf(1))
        addHabit("Gamma", PaletteColor(0), position = 2, completedOffsets = intArrayOf(0))
        addHabit("Delta", PaletteColor(0), position = 3, archived = true)
        addHabit("Yoga", PaletteColor(2), position = 4, numerical = true)
        addHabit("Zeta", PaletteColor(2), position = 5)
        addHabit("Reading", PaletteColor(3), position = 6, category = "Books")
        addHabit("Meditation", PaletteColor(4), position = 7, category = "Mind")

        val state = ListHabitsPresenter.buildState(
            allHabits = habitList,
            filter = HabitMatcher(searchQuery = "a"),
            primaryOrder = Order.BY_COLOR_ASC,
            secondaryOrder = Order.BY_NAME_ASC,
            checkmarkCount = checkmarkCount,
            theme = theme
        )

        val expected = ListHabitsState(
            items = listOf(
                HabitCardState(
                    id = 2,
                    name = "Gamma",
                    color = PaletteColor(0),
                    unit = "",
                    isNumerical = false,
                    archived = false,
                    score = 0.05192248566082858,
                    checkmarks = listOf(2, -1, -1),
                    completed = listOf(true, false, false),
                    notes = listOf("", "", ""),
                    category = "",
                    streak = 1,
                    streakDoneToday = true
                ),
                HabitCardState(
                    id = 1,
                    name = "Alpha",
                    color = PaletteColor(1),
                    unit = "",
                    isNumerical = false,
                    archived = false,
                    score = 0.04922654114362963,
                    checkmarks = listOf(-1, 2, -1),
                    completed = listOf(false, true, false),
                    notes = listOf("", "", ""),
                    category = "",
                    streak = 1
                ),
                HabitCardState(
                    id = 0,
                    name = "Beta",
                    color = PaletteColor(1),
                    unit = "",
                    isNumerical = false,
                    archived = false,
                    score = 0.05192248566082858,
                    checkmarks = listOf(2, -1, -1),
                    completed = listOf(true, false, false),
                    notes = listOf("", "", ""),
                    category = "",
                    streak = 1,
                    streakDoneToday = true
                ),
                HabitCardState(
                    id = 4,
                    name = "Yoga",
                    color = PaletteColor(2),
                    unit = "miles",
                    isNumerical = true,
                    archived = false,
                    score = 0.06464757443715247,
                    checkmarks = listOf(100, 200, -1),
                    completed = listOf(false, false, false),
                    notes = listOf("", "", ""),
                    category = ""
                ),
                HabitCardState(
                    id = 5,
                    name = "Zeta",
                    color = PaletteColor(2),
                    unit = "",
                    isNumerical = false,
                    archived = false,
                    score = 0.0,
                    checkmarks = listOf(-1, -1, -1),
                    completed = listOf(false, false, false),
                    notes = listOf("", "", ""),
                    category = ""
                ),
                HabitCardState(
                    id = 6,
                    name = "Reading",
                    color = PaletteColor(3),
                    unit = "",
                    isNumerical = false,
                    archived = false,
                    score = 0.0,
                    checkmarks = listOf(-1, -1, -1),
                    completed = listOf(false, false, false),
                    notes = listOf("", "", ""),
                    category = "Books"
                ),
                HabitCardState(
                    id = 7,
                    name = "Meditation",
                    color = PaletteColor(4),
                    unit = "",
                    isNumerical = false,
                    archived = false,
                    score = 0.0,
                    checkmarks = listOf(-1, -1, -1),
                    completed = listOf(false, false, false),
                    notes = listOf("", "", ""),
                    category = "Mind"
                )
            ),
            hasHabits = true,
            primaryOrder = Order.BY_COLOR_ASC,
            secondaryOrder = Order.BY_NAME_ASC,
            isSortable = false,
            checkmarkCount = checkmarkCount,
            theme = theme
        )

        assertEquals(expected, state)
    }

    private fun addHabit(
        name: String,
        color: PaletteColor,
        position: Int,
        category: String = "",
        archived: Boolean = false,
        numerical: Boolean = false,
        completedOffsets: IntArray = intArrayOf()
    ): Habit {
        val habit = if (numerical) fixtures.createNumericalHabit() else fixtures.createEmptyHabit()
        habit.name = name
        habit.color = color
        habit.position = position
        habit.category = category
        habit.isArchived = archived
        val today = getToday()
        for (offset in completedOffsets) {
            habit.originalEntries.add(Entry(today.minus(offset), Entry.YES_MANUAL))
        }
        habit.recompute()
        habitList.add(habit)
        return habit
    }

    @Test
    fun testMarksSharedHabits() {
        val shared = fixtures.createEmptyHabit()
        habitList.add(shared)
        val other = fixtures.createEmptyHabit()
        habitList.add(other)
        val state = ListHabitsPresenter.buildState(
            allHabits = habitList,
            filter = null,
            primaryOrder = Order.BY_POSITION,
            secondaryOrder = Order.BY_NAME_ASC,
            checkmarkCount = checkmarkCount,
            theme = theme,
            sharedHabits = setOf(shared.uuid!!)
        )
        assertEquals(mapOf(shared.id!! to true, other.id!! to false), state.items.associate { it.id to it.isShared })
    }

    @Test
    fun testStreakCountsOnlyPerformedDays() {
        // 3 times in 4 days: days between repetitions are not required (YES_AUTO)
        val habit = fixtures.createEmptyHabit()
        habit.frequency = Frequency(3, 4)
        val today = getToday()
        for (offset in intArrayOf(0, 1, 3)) {
            habit.originalEntries.add(Entry(today.minus(offset), Entry.YES_MANUAL))
        }
        habit.recompute()
        habitList.add(habit)

        val state = ListHabitsPresenter.buildState(
            allHabits = habitList,
            filter = null,
            primaryOrder = Order.BY_POSITION,
            secondaryOrder = Order.BY_NAME_ASC,
            checkmarkCount = checkmarkCount,
            theme = theme
        )
        val card = state.items.single()
        assertEquals(3, card.streak)
        assertEquals(true, card.streakDoneToday)
        // The streak range also covers the days in between, but they are not counted
        val range = habit.streaks.getOngoingStreak(today)!!
        assertEquals(true, range.length > 3)
    }

    private fun streakCard(habit: Habit): HabitCardState {
        if (habit.id == null) habitList.add(habit)
        return ListHabitsPresenter.buildState(
            allHabits = habitList,
            filter = null,
            primaryOrder = Order.BY_POSITION,
            secondaryOrder = Order.BY_NAME_ASC,
            checkmarkCount = checkmarkCount,
            theme = theme
        ).items.single()
    }

    @Test
    fun testFlameStaysColoredDuringPause() {
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        habit.originalEntries.add(Entry(today.minus(2), Entry.YES_MANUAL))
        habit.originalEntries.add(Entry(today.minus(1), Entry.YES_MANUAL))
        habit.pauses.add(Pause(today, today.plus(3)))
        habit.recompute()
        val card = streakCard(habit)
        assertEquals(2, card.streak)
        assertEquals(true, card.streakDoneToday)
    }

    @Test
    fun testFlameIsGrayOnFirstDayAfterPause() {
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        habit.originalEntries.add(Entry(today.minus(3), Entry.YES_MANUAL))
        habit.pauses.add(Pause(today.minus(2), today.minus(1)))
        habit.recompute()
        val card = streakCard(habit)
        assertEquals(1, card.streak)
        assertEquals(false, card.streakDoneToday)
    }

    @Test
    fun testPauseWithoutStreakKeepsFlameGray() {
        val habit = fixtures.createEmptyHabit()
        habit.pauses.add(Pause(getToday(), getToday().plus(3)))
        habit.recompute()
        assertEquals(false, streakCard(habit).streakDoneToday)
    }

    @Test
    fun testStreakUnitDefaultsToFrequencyAndConvertsStreak() {
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        for (i in 0 until 30) habit.originalEntries.add(Entry(today.minus(i), Entry.YES_MANUAL))
        habit.recompute()
        val days = streakCard(habit)
        assertEquals(StreakUnit.DAYS, days.streakUnit)
        assertEquals(30, days.streak)

        // Switching the unit only converts the number, the streak itself stays the same
        habit.streakUnit = StreakUnit.WEEKS
        val weeks = streakCard(habit)
        assertEquals(StreakUnit.WEEKS, weeks.streakUnit)
        assertEquals(4, weeks.streak)
    }

    @Test
    fun testBrokenStreakStaysBrokenAfterUnitChange() {
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        for (i in 10 until 40) habit.originalEntries.add(Entry(today.minus(i), Entry.YES_MANUAL))
        habit.streakUnit = StreakUnit.WEEKS
        habit.recompute()
        assertEquals(0, streakCard(habit).streak)
    }

    @Test
    fun testWeeklyHabitDefaultsToWeeks() {
        val habit = fixtures.createEmptyHabit()
        habit.frequency = Frequency.THREE_TIMES_PER_WEEK
        habit.recompute()
        assertEquals(StreakUnit.WEEKS, streakCard(habit).streakUnit)
    }

    @Test
    fun testFlameIsGrayUntilFirstPeriodIsComplete() {
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        for (i in 0 until 3) habit.originalEntries.add(Entry(today.minus(i), Entry.YES_MANUAL))
        habit.recompute()
        assertEquals(true, streakCard(habit).streakDoneToday)

        habit.streakUnit = StreakUnit.MONTHS
        val card = streakCard(habit)
        assertEquals(0, card.streak)
        assertEquals(false, card.streakDoneToday)
    }

    @Test
    fun testWeeklyGoalReachedCountsAsOneWeek() {
        val habit = fixtures.createEmptyHabit()
        habit.frequency = Frequency.THREE_TIMES_PER_WEEK
        val today = getToday()
        for (i in intArrayOf(0, 1, 4)) habit.originalEntries.add(Entry(today.minus(i), Entry.YES_MANUAL))
        habit.recompute()
        val card = streakCard(habit)
        assertEquals(StreakUnit.WEEKS, card.streakUnit)
        assertEquals(1, card.streak)
        assertEquals(true, card.streakDoneToday)
    }

    @Test
    fun testPausedDaysInTheMiddleKeepTheStreakAliveWithoutCounting() {
        // Rest days reach the habit the same way: as SKIP days that are not done, but not missed
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        habit.originalEntries.add(Entry(today.minus(4), Entry.YES_MANUAL))
        habit.originalEntries.add(Entry(today.minus(1), Entry.YES_MANUAL))
        habit.originalEntries.add(Entry(today, Entry.YES_MANUAL))
        habit.pauses.add(Pause(today.minus(3), today.minus(2)))
        habit.recompute()
        val card = streakCard(habit)
        assertEquals(3, card.streak)
        assertEquals(true, card.streakDoneToday)
    }

    @Test
    fun testSixChecksInARowAreOnlyOneFullWeek() {
        val habit = fixtures.createEmptyHabit()
        habit.frequency = Frequency.THREE_TIMES_PER_WEEK
        val today = getToday()
        for (i in 0 until 6) habit.originalEntries.add(Entry(today.minus(i), Entry.YES_MANUAL))
        habit.recompute()
        // The streak lasts 10 days with the days the goal filled in: one full week, not two
        val streak = habit.streaks.getOngoingStreak()!!
        assertEquals(10, streak.start.daysUntil(streak.end) + 1)
        assertEquals(1, streakCard(habit).streak)
    }

    @Test
    fun testWeeksOnlyCountFullSevenDays() {
        val habit = fixtures.createEmptyHabit()
        habit.frequency = Frequency.WEEKLY
        val today = getToday()
        for (i in 0 until 7) habit.originalEntries.add(Entry(today.minus(i), Entry.YES_MANUAL))
        habit.recompute()
        val streak = habit.streaks.getOngoingStreak()!!
        assertEquals(13, streak.start.daysUntil(streak.end) + 1)
        assertEquals(1, streakCard(habit).streak)
    }

    @Test
    fun testWeeklyGoalNotReachedYetIsZero() {
        val habit = fixtures.createEmptyHabit()
        habit.frequency = Frequency.THREE_TIMES_PER_WEEK
        val today = getToday()
        habit.originalEntries.add(Entry(today, Entry.YES_MANUAL))
        habit.recompute()
        assertEquals(0, streakCard(habit).streak)
    }

    @Test
    fun testUnpausedGapBreaksTheStreak() {
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        habit.originalEntries.add(Entry(today.minus(4), Entry.YES_MANUAL))
        habit.originalEntries.add(Entry(today.minus(1), Entry.YES_MANUAL))
        habit.originalEntries.add(Entry(today, Entry.YES_MANUAL))
        habit.recompute()
        assertEquals(2, streakCard(habit).streak)
    }
}
