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
package org.isoron.uhabits.core.models

import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.LocalDate

/**
 * How generous the streak freezes are (see #20). A freeze is used automatically when a due unit
 * (day, week or month) is missed: the streak survives, but the number does not grow. Freezes are
 * earned by done units, up to [cap]; a new streak starts with [startFreezes].
 *
 * [earnEvery] holds the number of done units that earn one freeze, for days, weeks and months.
 */
enum class FreezeLevel(
    val id: Int,
    val startFreezes: Int,
    val cap: Int,
    private val earnEvery: List<Int>
) {
    /** No freezes: a miss breaks the streak. */
    STRICT(0, 0, 0, listOf(0, 0, 0)),

    BALANCED(1, 1, 2, listOf(14, 4, 3)),

    RELAXED(2, 2, 3, listOf(7, 3, 2));

    /** Done units that earn one freeze in [unit]; 0 if the level has no freezes. */
    fun earnEvery(unit: StreakUnit): Int = earnEvery[unit.id]

    companion object {
        /** Level used when nothing was chosen. */
        val DEFAULT = BALANCED

        fun fromId(id: Int): FreezeLevel? = entries.firstOrNull { it.id == id }
    }
}

/** From [since] on (inclusive) the level with id [levelId] applies. */
data class FreezeChange(val since: LocalDate, val levelId: Int)

/**
 * The global freeze level as a history: a change only applies from its day on and never to the
 * past, so that past days are always judged by the rule that held back then. Days before the first
 * change use [FreezeLevel.DEFAULT].
 */
data class FreezeHistory(val changes: List<FreezeChange> = emptyList()) {
    private val sorted: List<FreezeChange> =
        changes.sortedBy { it.since.daysSince2000 }.associateBy { it.since }.values.toList()

    /** The level that applies on [date]. */
    fun levelOn(date: LocalDate): FreezeLevel {
        val change = sorted.lastOrNull { !it.since.isNewerThan(date) } ?: return FreezeLevel.DEFAULT
        return FreezeLevel.fromId(change.levelId) ?: FreezeLevel.DEFAULT
    }

    /** The latest level. */
    val current: FreezeLevel
        get() = sorted.lastOrNull()?.let { FreezeLevel.fromId(it.levelId) } ?: FreezeLevel.DEFAULT

    /** Returns the history with [level] valid from [since] on; later planned changes are replaced. */
    fun changedFrom(since: LocalDate, level: FreezeLevel): FreezeHistory {
        val kept = sorted.filter { it.since.isOlderThan(since) }
        val before = kept.lastOrNull()?.levelId ?: FreezeLevel.DEFAULT.id
        if (before == level.id) return FreezeHistory(kept)
        return FreezeHistory(kept + FreezeChange(since, level.id))
    }

    /** Text stored in the preferences: `daysSince2000:levelId` pairs separated by commas. */
    fun encode(): String = sorted.joinToString(",") { "${it.since.daysSince2000}:${it.levelId}" }

    companion object {
        fun decode(text: String?): FreezeHistory {
            if (text.isNullOrBlank()) return FreezeHistory()
            val result = mutableListOf<FreezeChange>()
            for (part in text.split(",")) {
                val pair = part.split(":")
                val days = pair.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                val id = pair.getOrNull(1)?.trim()?.toIntOrNull() ?: continue
                result.add(FreezeChange(LocalDate(days), id))
            }
            return FreezeHistory(result)
        }
    }
}

/** What happened in one due unit. Units that were not due are left out. */
data class UnitOutcome(val date: LocalDate, val done: Boolean, val counts: Boolean)

/**
 * Result of walking through the due units: the streak [count] (only units with [UnitOutcome.counts]),
 * whether the streak is still [alive], whether the latest unit was bridged by a freeze ([frozen]),
 * the [freezes] left, and whether the next miss would break the streak ([atRisk]; never for a level
 * without freezes).
 */
data class FreezeResult(
    val count: Int,
    val alive: Boolean,
    val frozen: Boolean,
    val freezes: Int,
    val atRisk: Boolean
)

object StreakFreezeEvaluator {
    /**
     * Walks through [units] (oldest first). A miss uses a freeze if one is left, otherwise the streak
     * breaks and the count falls to 0; freezes may be used in a row. Done units earn freezes (one
     * per `earnEvery`, never above the cap). A new streak starts with the start freezes of its level.
     * When the level changes, the stock starts at zero, so a change never grants freezes.
     */
    fun evaluate(
        units: List<UnitOutcome>,
        unit: StreakUnit,
        levelOn: (LocalDate) -> FreezeLevel
    ): FreezeResult {
        var count = 0
        var alive = false
        var frozen = false
        var freezes = 0
        var progress = 0
        var level: FreezeLevel? = null
        for (outcome in units) {
            val unitLevel = levelOn(outcome.date)
            if (unitLevel != level) {
                freezes = 0
                progress = 0
                level = unitLevel
            }
            if (outcome.done) {
                if (!alive) {
                    alive = true
                    freezes = unitLevel.startFreezes
                    progress = 0
                }
                frozen = false
                if (outcome.counts) count++
                val every = unitLevel.earnEvery(unit)
                if (every > 0 && freezes < unitLevel.cap) {
                    progress++
                    if (progress >= every) {
                        freezes++
                        progress = 0
                    }
                }
            } else if (alive) {
                if (freezes > 0) {
                    freezes--
                    frozen = true
                } else {
                    alive = false
                    frozen = false
                    count = 0
                    progress = 0
                }
            }
        }
        val current = level ?: return FreezeResult(0, false, false, 0, false)
        return FreezeResult(count, alive, frozen && alive, if (alive) freezes else 0, alive && freezes == 0 && current.cap > 0)
    }

    /**
     * The due units of [habit] up to [today]: days, or calendar weeks or months for [unit]. A day or
     * period only counts as missed once it is over; today and the running period are pending, unless
     * already done.
     */
    fun unitsOf(habit: Habit, unit: StreakUnit, today: LocalDate, firstWeekday: DayOfWeek): List<UnitOutcome> {
        val first = habit.computedEntries.getKnown().lastOrNull()?.date ?: return emptyList()
        if (first.isNewerThan(today)) return emptyList()
        val result = mutableListOf<UnitOutcome>()
        if (unit == StreakUnit.DAYS) {
            var day = first
            while (!day.isNewerThan(today)) {
                val entry = habit.computedEntries.get(day)
                when {
                    entry.value == Entry.SKIP -> {}
                    entry.completed -> result.add(UnitOutcome(day, true, isPerformed(habit, entry)))
                    day != today -> result.add(UnitOutcome(day, false, false))
                }
                day = day.plus(1)
            }
            return result
        }
        val den = habit.effectiveFrequency.denominator
        val matchesGoal = (unit == StreakUnit.WEEKS && den == 7) || (unit == StreakUnit.MONTHS && (den == 30 || den == 31))
        val goal = maxOf(1, habit.effectiveFrequency.numerator)
        for ((start, end) in periodsOf(habit, unit, first, today, firstWeekday, matchesGoal)) {
            val last = if (end.isNewerThan(today)) today else end
            var performed = 0
            var completed = 0
            var missed = 0
            var day = if (start.isOlderThan(first)) first else start
            while (!day.isNewerThan(last)) {
                val entry = habit.computedEntries.get(day)
                if (entry.value != Entry.SKIP) {
                    if (entry.completed) completed++ else if (day != today) missed++
                    if (entry.completed && isPerformed(habit, entry)) performed++
                }
                day = day.plus(1)
            }
            val done = if (matchesGoal) performed >= goal else missed == 0 && completed > 0
            val over = !end.isNewerThan(today)
            if (done) {
                result.add(UnitOutcome(last, true, true))
            } else if (over && (completed + missed) > 0) {
                result.add(UnitOutcome(last, false, false))
            }
        }
        return result
    }

    /**
     * The periods of [unit] that count, oldest first: calendar weeks or months in calendar mode,
     * otherwise blocks of 7 or 30 days that end today. Periods that start before the first entry of
     * the habit are left out, unless the goal matches the unit (then reaching it is enough).
     */
    private fun periodsOf(
        habit: Habit,
        unit: StreakUnit,
        first: LocalDate,
        today: LocalDate,
        firstWeekday: DayOfWeek,
        matchesGoal: Boolean
    ): List<Pair<LocalDate, LocalDate>> {
        val periods = mutableListOf<Pair<LocalDate, LocalDate>>()
        if (EntryList.isCalendarPeriod(habit.effectiveFrequency, habit.calendarPeriod)) {
            var start = if (unit == StreakUnit.WEEKS) first.startOfWeek(firstWeekday) else first.startOfMonth()
            while (!start.isNewerThan(today)) {
                val end = if (unit == StreakUnit.WEEKS) start.plus(6) else start.plus(start.monthLength - 1)
                if (!start.isOlderThan(first) || matchesGoal) periods.add(start to end)
                start = end.plus(1)
            }
        } else {
            val length = if (unit == StreakUnit.WEEKS) 7 else 30
            var end = today
            while (!end.isOlderThan(first)) {
                val start = end.minus(length - 1)
                if (!start.isOlderThan(first) || matchesGoal) periods.add(start to end)
                end = start.minus(1)
            }
            periods.reverse()
        }
        return periods
    }

    private fun isPerformed(habit: Habit, entry: Entry): Boolean =
        if (habit.isNumerical) entry.completed && entry.value != Entry.SKIP else entry.value == Entry.YES_MANUAL
}
