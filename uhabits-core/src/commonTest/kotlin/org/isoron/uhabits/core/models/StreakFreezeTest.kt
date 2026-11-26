package org.isoron.uhabits.core.models

import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.ui.screens.habits.list.ListHabitsPresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Streak freezes (see #20). */
class StreakFreezeTest : BaseUnitTest() {
    /** "xx.x" pattern: x = done (counts), . = missed. */
    private fun units(pattern: String, from: Int = 0): List<UnitOutcome> =
        pattern.mapIndexed { i, c -> UnitOutcome(LocalDate(from + i), c == 'x', c == 'x') }

    private fun eval(pattern: String, level: FreezeLevel, unit: StreakUnit = StreakUnit.DAYS) =
        StreakFreezeEvaluator.evaluate(units(pattern), unit) { level }

    @Test
    fun testStrictBreaksOnFirstMiss() {
        val r = eval("xxx.", FreezeLevel.STRICT)
        assertFalse(r.alive)
        assertEquals(0, r.count)
        // Strict has no freezes, so there is nothing to warn about
        assertFalse(eval("xxx", FreezeLevel.STRICT).atRisk)
    }

    @Test
    fun testStartFreezeBridgesOneMiss() {
        val r = eval("x.", FreezeLevel.BALANCED)
        assertTrue(r.alive)
        assertTrue(r.frozen)
        assertEquals(1, r.count) // a freeze keeps the number
        assertEquals(0, r.freezes)
        assertTrue(r.atRisk)
    }

    @Test
    fun testFreezesCanBeUsedInARow() {
        assertTrue(eval("x..", FreezeLevel.RELAXED).alive) // 2 start freezes
        val r = eval("x...", FreezeLevel.RELAXED)
        assertFalse(r.alive)
        assertEquals(0, r.count)
        // Balanced has only one at the start
        assertFalse(eval("x..", FreezeLevel.BALANCED).alive)
    }

    @Test
    fun testDoneUnitClearsFrozenState() {
        val r = eval("x.x", FreezeLevel.BALANCED)
        assertTrue(r.alive)
        assertFalse(r.frozen)
        assertEquals(2, r.count)
    }

    @Test
    fun testFreezesAreEarnedByDoneUnitsUpToTheCap() {
        // Balanced, days: 1 at the start, +1 after 14 done days, cap 2
        assertEquals(1, eval("x".repeat(13), FreezeLevel.BALANCED).freezes)
        assertEquals(2, eval("x".repeat(14), FreezeLevel.BALANCED).freezes)
        assertEquals(2, eval("x".repeat(60), FreezeLevel.BALANCED).freezes)
        // Relaxed: 2 at the start, +1 after 7, cap 3
        assertEquals(3, eval("x".repeat(7), FreezeLevel.RELAXED).freezes)
        assertEquals(3, eval("x".repeat(30), FreezeLevel.RELAXED).freezes)
    }

    @Test
    fun testEarningDependsOnTheUnit() {
        assertEquals(2, eval("xxxx", FreezeLevel.BALANCED, StreakUnit.WEEKS).freezes)
        assertEquals(1, eval("xxx", FreezeLevel.BALANCED, StreakUnit.WEEKS).freezes)
        assertEquals(2, eval("xxx", FreezeLevel.BALANCED, StreakUnit.MONTHS).freezes)
        assertEquals(3, eval("xx", FreezeLevel.RELAXED, StreakUnit.MONTHS).freezes)
    }

    @Test
    fun testProgressIsKeptWhenAFreezeIsUsed() {
        // 13 done, a miss (uses the start freeze), the 14th done day earns one again
        val r = eval("x".repeat(13) + "." + "x", FreezeLevel.BALANCED)
        assertTrue(r.alive)
        assertEquals(1, r.freezes)
        assertEquals(14, r.count)
    }

    @Test
    fun testNewStreakStartsWithStartFreezesAfterBreak() {
        val r = eval("xx..x.", FreezeLevel.BALANCED)
        // x x (frozen) . breaks; new streak x gets a start freeze for the last miss
        assertTrue(r.alive)
        assertTrue(r.frozen)
        assertEquals(1, r.count)
    }

    @Test
    fun testAutoDaysKeepTheStreakWithoutCounting() {
        val list = listOf(
            UnitOutcome(LocalDate(0), true, true),
            UnitOutcome(LocalDate(1), true, false),
            UnitOutcome(LocalDate(2), true, true)
        )
        assertEquals(2, StreakFreezeEvaluator.evaluate(list, StreakUnit.DAYS) { FreezeLevel.STRICT }.count)
    }

    @Test
    fun testLevelChangeNeverGrantsFreezes() {
        // Strict until day 6, balanced from day 7: the stock starts at zero, the miss breaks the streak
        val list = units("xxxxxxx", 0) + units(".", 7)
        val r = StreakFreezeEvaluator.evaluate(list, StreakUnit.DAYS) {
            if (it.daysSince2000 < 7) FreezeLevel.STRICT else FreezeLevel.BALANCED
        }
        assertFalse(r.alive)
    }

    @Test
    fun testLooseningNeverRevivesABrokenStreak() {
        val r = StreakFreezeEvaluator.evaluate(units("xxx.xx", 0), StreakUnit.DAYS) {
            if (it.daysSince2000 < 4) FreezeLevel.STRICT else FreezeLevel.BALANCED
        }
        assertEquals(2, r.count)
    }

    @Test
    fun testTighteningDoesNotBreakTheRunningStreak() {
        val r = StreakFreezeEvaluator.evaluate(units("xx.x", 0) + units("xx", 4), StreakUnit.DAYS) {
            if (it.daysSince2000 < 4) FreezeLevel.BALANCED else FreezeLevel.STRICT
        }
        assertTrue(r.alive)
        assertEquals(5, r.count)
    }

    @Test
    fun testHistoryNeverAppliesToThePast() {
        val h = FreezeHistory().changedFrom(LocalDate(100), FreezeLevel.RELAXED)
        assertEquals(FreezeLevel.DEFAULT, h.levelOn(LocalDate(99)))
        assertEquals(FreezeLevel.RELAXED, h.levelOn(LocalDate(100)))
        val back = h.changedFrom(LocalDate(200), FreezeLevel.STRICT)
        assertEquals(FreezeLevel.RELAXED, back.levelOn(LocalDate(150)))
        assertEquals(FreezeLevel.STRICT, back.levelOn(LocalDate(200)))
        assertEquals(FreezeLevel.STRICT, back.current)
        assertEquals(back, FreezeHistory.decode(back.encode()))
        assertEquals("100:2,200:0", back.encode())
        // Choosing the level that already applies adds nothing
        assertEquals(back, back.changedFrom(LocalDate(300), FreezeLevel.STRICT))
    }

    @Test
    fun testFlameKeepsStreakThroughFrozenMiss() {
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        for (i in 0..9) if (i != 3) habit.originalEntries.add(Entry(today.minus(i), Entry.YES_MANUAL))
        habit.recompute()
        val state = ListHabitsPresenter.cardState(habit, 1, 5)
        assertEquals(9, state.streak)
        assertTrue(state.streakDoneToday)
        assertEquals(0, state.streakFreezes)
        assertTrue(state.streakAtRisk)
        // Without freezes the same miss breaks the streak
        val strict = FreezeHistory().changedFrom(today.minus(30), FreezeLevel.STRICT)
        assertEquals(3, ListHabitsPresenter.cardState(habit, 1, 5, freezeHistory = strict).streak)
    }

    @Test
    fun testGlobalChangeAppliesFromItsDayOnly() {
        val habit = fixtures.createEmptyHabit()
        val today = getToday()
        for (i in 0..9) if (i != 3) habit.originalEntries.add(Entry(today.minus(i), Entry.YES_MANUAL))
        habit.recompute()
        // Strict until yesterday, balanced from today: the old miss stays a break
        val history = FreezeHistory()
            .changedFrom(today.minus(30), FreezeLevel.STRICT)
            .changedFrom(today, FreezeLevel.BALANCED)
        assertEquals(3, ListHabitsPresenter.cardState(habit, 1, 5, freezeHistory = history).streak)
    }
}
