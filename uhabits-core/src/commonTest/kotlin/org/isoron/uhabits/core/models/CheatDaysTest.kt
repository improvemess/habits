package org.isoron.uhabits.core.models

import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.ui.screens.habits.list.ListHabitsPresenter
import org.isoron.uhabits.core.ui.screens.habits.show.views.CheatDaysCardPresenter
import org.isoron.uhabits.core.ui.views.LightTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Cheat days (see #20). */
class CheatDaysTest : BaseUnitTest() {
    /** "xx.x" pattern: x = done (counts), . = missed. */
    private fun units(pattern: String, from: Int = 0): List<UnitOutcome> =
        pattern.mapIndexed { i, c -> UnitOutcome(LocalDate(from + i), c == 'x', c == 'x') }

    private fun eval(pattern: String, level: CheatDayLevel, unit: StreakUnit = StreakUnit.DAYS) =
        CheatDayEvaluator.evaluate(units(pattern), unit) { level }

    @Test
    fun testStrictBreaksOnFirstMiss() {
        val r = eval("xxx.", CheatDayLevel.STRICT)
        assertFalse(r.alive)
        assertEquals(0, r.count)
        // Strict has no cheatDays, so there is nothing to warn about
        assertFalse(eval("xxx", CheatDayLevel.STRICT).atRisk)
    }

    @Test
    fun testStartCheatDayBridgesOneMiss() {
        val r = eval("x.", CheatDayLevel.BALANCED)
        assertTrue(r.alive)
        assertTrue(r.used)
        assertEquals(1, r.count) // a cheat day keeps the number
        assertEquals(0, r.cheatDays)
        assertTrue(r.atRisk)
    }

    @Test
    fun testCheatDaysCanBeUsedInARow() {
        assertTrue(eval("x..", CheatDayLevel.RELAXED).alive) // 2 start cheatDays
        val r = eval("x...", CheatDayLevel.RELAXED)
        assertFalse(r.alive)
        assertEquals(0, r.count)
        // Balanced has only one at the start
        assertFalse(eval("x..", CheatDayLevel.BALANCED).alive)
    }

    @Test
    fun testDoneUnitClearsFrozenState() {
        val r = eval("x.x", CheatDayLevel.BALANCED)
        assertTrue(r.alive)
        assertFalse(r.used)
        assertEquals(2, r.count)
    }

    @Test
    fun testCheatDaysAreEarnedByDoneUnitsUpToTheCap() {
        // Balanced, days: 1 at the start, +1 after 14 done days, cap 2
        assertEquals(1, eval("x".repeat(13), CheatDayLevel.BALANCED).cheatDays)
        assertEquals(2, eval("x".repeat(14), CheatDayLevel.BALANCED).cheatDays)
        assertEquals(2, eval("x".repeat(60), CheatDayLevel.BALANCED).cheatDays)
        // Relaxed: 2 at the start, +1 after 7, cap 3
        assertEquals(3, eval("x".repeat(7), CheatDayLevel.RELAXED).cheatDays)
        assertEquals(3, eval("x".repeat(30), CheatDayLevel.RELAXED).cheatDays)
    }

    @Test
    fun testEarningDependsOnTheUnit() {
        assertEquals(2, eval("xxxx", CheatDayLevel.BALANCED, StreakUnit.WEEKS).cheatDays)
        assertEquals(1, eval("xxx", CheatDayLevel.BALANCED, StreakUnit.WEEKS).cheatDays)
        assertEquals(2, eval("xxx", CheatDayLevel.BALANCED, StreakUnit.MONTHS).cheatDays)
        assertEquals(3, eval("xx", CheatDayLevel.RELAXED, StreakUnit.MONTHS).cheatDays)
    }

    @Test
    fun testUntilNextCountsDownToTheNextCheatDay() {
        assertEquals(14, eval("x", CheatDayLevel.BALANCED).untilNext?.plus(1))
        assertEquals(9, eval("xxxxx", CheatDayLevel.BALANCED).untilNext)
        // At the cap nothing more can be earned, and a level without cheat days never earns
        assertEquals(null, eval("x".repeat(30), CheatDayLevel.BALANCED).untilNext)
        assertEquals(null, eval("xxx", CheatDayLevel.STRICT).untilNext)
    }

    @Test
    fun testProgressIsKeptWhenACheatDayIsUsed() {
        // 13 done, a miss (uses the start cheat day), the 14th done day earns one again
        val r = eval("x".repeat(13) + "." + "x", CheatDayLevel.BALANCED)
        assertTrue(r.alive)
        assertEquals(1, r.cheatDays)
        assertEquals(14, r.count)
    }

    @Test
    fun testNewStreakStartsWithStartCheatDaysAfterBreak() {
        val r = eval("xx..x.", CheatDayLevel.BALANCED)
        // x x (used) . breaks; new streak x gets a start cheat day for the last miss
        assertTrue(r.alive)
        assertTrue(r.used)
        assertEquals(1, r.count)
    }

    @Test
    fun testAutoDaysKeepTheStreakWithoutCounting() {
        val list = listOf(
            UnitOutcome(LocalDate(0), true, true),
            UnitOutcome(LocalDate(1), true, false),
            UnitOutcome(LocalDate(2), true, true)
        )
        assertEquals(2, CheatDayEvaluator.evaluate(list, StreakUnit.DAYS) { CheatDayLevel.STRICT }.count)
    }

    @Test
    fun testLevelChangeNeverGrantsCheatDays() {
        // Strict until day 6, balanced from day 7: the stock starts at zero, the miss breaks the streak
        val list = units("xxxxxxx", 0) + units(".", 7)
        val r = CheatDayEvaluator.evaluate(list, StreakUnit.DAYS) {
            if (it.daysSince2000 < 7) CheatDayLevel.STRICT else CheatDayLevel.BALANCED
        }
        assertFalse(r.alive)
    }

    @Test
    fun testLooseningNeverRevivesABrokenStreak() {
        val r = CheatDayEvaluator.evaluate(units("xxx.xx", 0), StreakUnit.DAYS) {
            if (it.daysSince2000 < 4) CheatDayLevel.STRICT else CheatDayLevel.BALANCED
        }
        assertEquals(2, r.count)
    }

    @Test
    fun testTighteningDoesNotBreakTheRunningStreak() {
        val r = CheatDayEvaluator.evaluate(units("xx.x", 0) + units("xx", 4), StreakUnit.DAYS) {
            if (it.daysSince2000 < 4) CheatDayLevel.BALANCED else CheatDayLevel.STRICT
        }
        assertTrue(r.alive)
        assertEquals(5, r.count)
    }

    @Test
    fun testHistoryNeverAppliesToThePast() {
        val h = CheatDayHistory().changedFrom(LocalDate(100), CheatDayLevel.RELAXED)
        assertEquals(CheatDayLevel.DEFAULT, h.levelOn(LocalDate(99)))
        assertEquals(CheatDayLevel.RELAXED, h.levelOn(LocalDate(100)))
        val back = h.changedFrom(LocalDate(200), CheatDayLevel.STRICT)
        assertEquals(CheatDayLevel.RELAXED, back.levelOn(LocalDate(150)))
        assertEquals(CheatDayLevel.STRICT, back.levelOn(LocalDate(200)))
        assertEquals(CheatDayLevel.STRICT, back.current)
        assertEquals(back, CheatDayHistory.decode(back.encode()))
        assertEquals("100:2,200:0", back.encode())
        // Choosing the level that already applies adds nothing
        assertEquals(back, back.changedFrom(LocalDate(300), CheatDayLevel.STRICT))
    }

    @Test
    fun testCardShowsStreakAndCheatDaysThroughUsedMiss() {
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        for (i in 0..9) if (i != 3) habit.originalEntries.add(Entry(today.minus(i), Entry.YES_MANUAL))
        habit.recompute()
        val state = ListHabitsPresenter.cardState(habit, 1, 5)
        assertEquals(9, state.streak)
        assertTrue(state.streakDoneToday)
        val card = CheatDaysCardPresenter.buildState(habit, CheatDayHistory(), DayOfWeek.MONDAY, LightTheme())
        assertTrue(card.visible)
        assertEquals(0, card.cheatDays)
        assertEquals(2, card.cap)
        assertTrue(card.atRisk)
        assertEquals(5, card.untilNext) // 14 done days earn the next one, 9 are done
        // Without cheat days the same miss breaks the streak
        val strict = CheatDayHistory().changedFrom(today.minus(30), CheatDayLevel.STRICT)
        assertEquals(3, ListHabitsPresenter.cardState(habit, 1, 5, cheatDayHistory = strict).streak)
    }

    @Test
    fun testCardIsHiddenForStrict() {
        val habit = fixtures.createEmptyHabit()
        habit.recompute()
        val strict = CheatDayHistory().changedFrom(getToday().minus(30), CheatDayLevel.STRICT)
        assertFalse(CheatDaysCardPresenter.buildState(habit, strict, DayOfWeek.MONDAY, LightTheme()).visible)
    }

    @Test
    fun testGlobalChangeAppliesFromItsDayOnly() {
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        for (i in 0..9) if (i != 3) habit.originalEntries.add(Entry(today.minus(i), Entry.YES_MANUAL))
        habit.recompute()
        // Strict until yesterday, balanced from today: the old miss stays a break
        val history = CheatDayHistory()
            .changedFrom(today.minus(30), CheatDayLevel.STRICT)
            .changedFrom(today, CheatDayLevel.BALANCED)
        assertEquals(3, ListHabitsPresenter.cardState(habit, 1, 5, cheatDayHistory = history).streak)
    }
}
