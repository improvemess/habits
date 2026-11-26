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
 * How generous the cheat days are (see #20). A cheat day is used automatically when a due unit
 * (day, week or month) is missed: the streak survives, but the number does not grow. Cheat days are
 * earned by done units, up to [cap]; a new streak starts with [startCheatDays].
 *
 * [earnEvery] holds the number of done units that earn one cheat day, for days, weeks, months and years.
 */
enum class CheatDayLevel(
    val id: Int,
    val startCheatDays: Int,
    val cap: Int,
    private val earnEvery: List<Int>
) {
    /** No cheat days: a miss breaks the streak. */
    STRICT(0, 0, 0, listOf(0, 0, 0, 0)),

    BALANCED(1, 1, 2, listOf(14, 4, 3, 1)),

    RELAXED(2, 2, 3, listOf(7, 3, 2, 1));

    /** Done units that earn one cheat day in [unit]; 0 if the level has no cheat days. */
    fun earnEvery(unit: StreakUnit): Int = earnEvery[unit.id]

    companion object {
        /** Level used when nothing was chosen. */
        val DEFAULT = BALANCED

        fun fromId(id: Int): CheatDayLevel? = entries.firstOrNull { it.id == id }
    }
}

/** From [since] on (inclusive) the level with id [levelId] applies. */
data class CheatDayChange(val since: LocalDate, val levelId: Int)

/**
 * The global cheat day level as a history: a change only applies from its day on and never to the
 * past, so that past days are always judged by the rule that held back then. Days before the first
 * change use [CheatDayLevel.DEFAULT].
 */
data class CheatDayHistory(val changes: List<CheatDayChange> = emptyList()) {
    private val sorted: List<CheatDayChange> =
        changes.sortedBy { it.since.daysSince2000 }.associateBy { it.since }.values.toList()

    /** The level that applies on [date]. */
    fun levelOn(date: LocalDate): CheatDayLevel {
        val change = sorted.lastOrNull { !it.since.isNewerThan(date) } ?: return CheatDayLevel.DEFAULT
        return CheatDayLevel.fromId(change.levelId) ?: CheatDayLevel.DEFAULT
    }

    /** The latest level. */
    val current: CheatDayLevel
        get() = sorted.lastOrNull()?.let { CheatDayLevel.fromId(it.levelId) } ?: CheatDayLevel.DEFAULT

    /** Returns the history with [level] valid from [since] on; later planned changes are replaced. */
    fun changedFrom(since: LocalDate, level: CheatDayLevel): CheatDayHistory {
        val kept = sorted.filter { it.since.isOlderThan(since) }
        val before = kept.lastOrNull()?.levelId ?: CheatDayLevel.DEFAULT.id
        if (before == level.id) return CheatDayHistory(kept)
        return CheatDayHistory(kept + CheatDayChange(since, level.id))
    }

    /** Text stored in the preferences: `daysSince2000:levelId` pairs separated by commas. */
    fun encode(): String = sorted.joinToString(",") { "${it.since.daysSince2000}:${it.levelId}" }

    companion object {
        fun decode(text: String?): CheatDayHistory {
            if (text.isNullOrBlank()) return CheatDayHistory()
            val result = mutableListOf<CheatDayChange>()
            for (part in text.split(",")) {
                val pair = part.split(":")
                val days = pair.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                val id = pair.getOrNull(1)?.trim()?.toIntOrNull() ?: continue
                result.add(CheatDayChange(LocalDate(days), id))
            }
            return CheatDayHistory(result)
        }
    }
}

/** What happened in one due unit. Units that were not due are left out. */
data class UnitOutcome(val date: LocalDate, val done: Boolean, val counts: Boolean)

/**
 * Result of walking through the due units: the streak [count] (only units with [UnitOutcome.counts]),
 * whether the streak is still [alive], whether the latest unit was bridged by a cheat day ([used]),
 * the [cheatDays] left, and whether the next miss would break the streak ([atRisk]; never for a level
 * without cheat days). [untilNext] is the number of done units until the next cheat day is earned,
 * or null if none can be earned (level without cheat days, stock full, or no streak).
 */
data class CheatDayResult(
    val count: Int,
    val alive: Boolean,
    val used: Boolean,
    val cheatDays: Int,
    val atRisk: Boolean,
    val untilNext: Int? = null
)

object CheatDayEvaluator {
    /**
     * Walks through [units] (oldest first). A miss uses a cheat day if one is left, otherwise the streak
     * breaks and the count falls to 0; cheat days may be used in a row. Done units earn cheat days (one
     * per `earnEvery`, never above the cap). A new streak starts with the start cheat days of its level.
     * When the level changes, the stock starts at zero, so a change never grants cheat days.
     */
    fun evaluate(
        units: List<UnitOutcome>,
        unit: StreakUnit,
        levelOn: (LocalDate) -> CheatDayLevel
    ): CheatDayResult {
        var count = 0
        var alive = false
        var used = false
        var cheatDays = 0
        var progress = 0
        var level: CheatDayLevel? = null
        for (outcome in units) {
            val unitLevel = levelOn(outcome.date)
            if (unitLevel != level) {
                cheatDays = 0
                progress = 0
                level = unitLevel
            }
            if (outcome.done) {
                if (!alive) {
                    alive = true
                    cheatDays = unitLevel.startCheatDays
                    progress = 0
                }
                used = false
                if (outcome.counts) count++
                val every = unitLevel.earnEvery(unit)
                if (every > 0 && cheatDays < unitLevel.cap) {
                    progress++
                    if (progress >= every) {
                        cheatDays++
                        progress = 0
                    }
                }
            } else if (alive) {
                if (cheatDays > 0) {
                    cheatDays--
                    used = true
                } else {
                    alive = false
                    used = false
                    count = 0
                    progress = 0
                }
            }
        }
        val current = level ?: return CheatDayResult(0, false, false, 0, false)
        val every = current.earnEvery(unit)
        val untilNext = if (alive && every > 0 && cheatDays < current.cap) every - progress else null
        return CheatDayResult(
            count = count,
            alive = alive,
            used = used && alive,
            cheatDays = if (alive) cheatDays else 0,
            atRisk = alive && cheatDays == 0 && current.cap > 0,
            untilNext = untilNext
        )
    }

    /** Evaluates [habit] up to [today] in its streak unit, with the global level [history]. */
    fun forHabit(habit: Habit, history: CheatDayHistory, today: LocalDate, firstWeekday: DayOfWeek): CheatDayResult {
        val unit = habit.streakUnit ?: StreakUnit.defaultFor(habit.effectiveFrequency)
        return evaluate(unitsOf(habit, unit, today, firstWeekday), unit) { history.levelOn(it) }
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
        val matchesGoal = (unit == StreakUnit.WEEKS && den == 7) || (unit == StreakUnit.MONTHS && (den == 30 || den == 31)) || (unit == StreakUnit.YEARS && den == Frequency.YEAR_DAYS)
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
            var start = when (unit) {
                StreakUnit.WEEKS -> first.startOfWeek(firstWeekday)
                StreakUnit.YEARS -> first.startOfYear()
                else -> first.startOfMonth()
            }
            while (!start.isNewerThan(today)) {
                val end = when (unit) {
                    StreakUnit.WEEKS -> start.plus(6)
                    StreakUnit.YEARS -> start.plus(start.yearLength - 1)
                    else -> start.plus(start.monthLength - 1)
                }
                if (!start.isOlderThan(first) || matchesGoal) periods.add(start to end)
                start = end.plus(1)
            }
        } else {
            val length = unit.days
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
