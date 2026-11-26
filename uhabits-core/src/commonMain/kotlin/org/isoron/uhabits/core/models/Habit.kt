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

import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import kotlin.math.max
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class Habit(
    var category: String = "",
    var color: PaletteColor = PaletteColor(8),
    var description: String = "",
    var frequency: Frequency = Frequency.DAILY,
    var id: Long? = null,
    var isArchived: Boolean = false,
    var name: String = "",
    var position: Int = 0,
    var reminder: Reminder? = null,
    var stepSize: Double = 1.0,
    var targetType: NumericalHabitType = NumericalHabitType.AT_LEAST,
    var targetValue: Double = 0.0,
    var type: HabitType = HabitType.YES_NO,
    var unit: String = "",
    var uuid: String? = null,
    /** First day on which an avoided ("negative") habit counts as done; null for normal habits. */
    var negativeSince: LocalDate? = null,
    /** Day on which the habit was created; null for habits from before it was recorded (see #70). */
    var createdOn: LocalDate? = null,
    /** Whether a weekly goal counts per calendar week instead of per rolling 7 days (see #23). */
    var calendarPeriod: Boolean = false,
    /**
     * Measurable habit: [targetValue] is a threshold per day and [frequency] says on how many days of
     * the period it must be reached, instead of one sum per period (see #81).
     */
    var countDays: Boolean = false,
    /** Unit of the streak flame; null means automatic, derived from the frequency (see #15). */
    var streakUnit: StreakUnit? = null,
    /** Fixed weekdays on which the habit is due, as a history; empty for the plain frequency (see #19). */
    var dueDays: DueDays = DueDays(),
    val computedEntries: EntryList,
    val originalEntries: EntryList,
    val scores: ScoreList,
    val streaks: StreakList,
    val pauses: PauseList = PauseList()
) {
    init {
        if (uuid == null) this.uuid = Uuid.random().toHexString()
    }

    var observable = ModelObservable()

    val isNegative: Boolean
        get() = negativeSince != null

    /** First day that belongs to the habit: the start of an avoided habit, otherwise [createdOn]. */
    val creationDay: LocalDate?
        get() = negativeSince ?: createdOn

    val isNumerical: Boolean
        get() = type == HabitType.NUMERICAL

    val uriString: String
        get() = "content://org.isoron.uhabits/habit/$id"

    fun hasReminder(): Boolean = reminder != null

    /**
     * Whether the given day lies before the habit was created. Only there "unknown" (no entry) is a
     * state of its own and can be set again; from the creation day on, a day is done or not (see #70).
     */
    fun isUnknownAllowedOn(date: LocalDate): Boolean {
        val since = creationDay ?: return false
        return since.isNewerThan(date)
    }

    fun isCompletedOn(date: LocalDate): Boolean {
        return computedEntries.get(date).completed
    }

    fun isCompletedToday(): Boolean {
        return isCompletedOn(getToday())
    }

    fun isEnteredToday(): Boolean {
        val today = getToday()
        val value = computedEntries.get(today).value
        return value != Entry.UNKNOWN
    }

    /** Whether this measurable habit counts the days that reach its daily threshold (see #81). */
    val countsDays: Boolean
        get() = isNumerical && countDays && frequency.denominator > 1 && !dueDays.isEverFixed

    /** Denominator of the period that [targetValue] refers to: one day if the target is per day. */
    val valuePeriodDays: Int
        get() = if (countsDays) 1 else effectiveFrequency.denominator

    fun recompute() {
        val base = entriesWithAutoChecks(entriesWithRestDays(entriesWithPauses()))
        // Days that reach the threshold count like the yes of a "X times per period" habit. The
        // values stay in the computed entries; completion, score and streaks follow the days.

        computedEntries.recomputeFrom(
            originalEntries = base,
            frequency = effectiveFrequency,
            isNumerical = isNumerical,
            calendarPeriod = calendarPeriod
        )

        if (countsDays) {
            val days = EntryList()
            days.recomputeFrom(computeDayEntries(base), frequency, false, calendarPeriod)
            days.recomputeCompletion(frequency, false, targetType, targetValue, calendarPeriod)
            for (entry in computedEntries.getKnown()) {
                val completed = days.get(entry.date).completed
                if (entry.completed != completed) computedEntries.add(entry.copy(completed = completed))
            }
            // Days of a fulfilled period before the first entry have no placeholder yet
            val today = getToday()
            for (entry in days.getKnown()) {
                if (entry.completed && !entry.date.isNewerThan(today) && computedEntries.get(entry.date).value == Entry.UNKNOWN &&
                    !computedEntries.get(entry.date).completed
                ) {
                    computedEntries.add(Entry(entry.date, Entry.UNKNOWN, completed = true))
                }
            }
            recomputeScoresAndStreaks(days, false)
            clearCompletionBeforeCreation()
            return
        }

        computedEntries.recomputeCompletion(
            frequency = effectiveFrequency,
            isNumerical = isNumerical,
            targetType = targetType,
            targetValue = targetValue,
            calendarPeriod = calendarPeriod
        )

        clearCompletionBeforeCreation()
        recomputeScoresAndStreaks(computedEntries, isNumerical)
    }

    /** Turns every value of [source] into a yes or no for the daily threshold; skips and unknowns stay. */
    private fun computeDayEntries(source: EntryList): EntryList {
        val result = EntryList()
        for (entry in source.getKnown()) {
            val value = when {
                entry.value == Entry.SKIP || entry.value == Entry.UNKNOWN -> entry.value
                reachesThreshold(entry.value) -> Entry.YES_MANUAL
                else -> Entry.NO
            }
            result.add(Entry(entry.date, value, entry.notes))
        }
        return result
    }

    private fun reachesThreshold(rawValue: Int): Boolean {
        val value = max(0, rawValue) / 1000.0
        return when (targetType) {
            NumericalHabitType.AT_LEAST -> value >= targetValue
            NumericalHabitType.AT_MOST -> value <= targetValue
        }
    }

    private fun recomputeScoresAndStreaks(entries: EntryList, numerical: Boolean) {
        val today = getToday()
        val to = today.plus(30)
        var from = entries.getKnown().lastOrNull()?.date ?: today
        if (from.isNewerThan(to)) from = to

        scores.recompute(
            frequency = effectiveFrequency,
            isNumerical = numerical,
            numericalHabitType = targetType,
            calendarPeriod = calendarPeriod,
            targetValue = targetValue,
            computedEntries = entries,
            from = from,
            to = to
        )

        streaks.recompute(
            entries,
            from,
            to,
            numerical,
            targetValue,
            targetType
        )
    }

    /**
     * Returns the original entries, where every paused day that has no entry of its own (or only
     * a NO) is marked as SKIP. Paused days far in the future are ignored until they come closer.
     */
    private fun entriesWithPauses(): EntryList {
        val pauseList = pauses.getAll()
        if (pauseList.isEmpty()) return originalEntries
        val result = EntryList()
        originalEntries.getKnown().forEach { result.add(it) }
        val limit = getToday().plus(PauseList.MAX_DAYS_AHEAD)
        for (pause in pauseList) {
            var day = pause.start
            while (!day.isNewerThan(pause.end) && !day.isNewerThan(limit)) {
                val existing = originalEntries.get(day)
                // A paused day replaces "unknown" and "no", but never a manual "yes"
                if (existing.value == Entry.UNKNOWN || existing.value == Entry.NO) {
                    result.add(Entry(day, Entry.SKIP, existing.notes))
                }
                day = day.plus(1)
            }
        }
        return result
    }

    /**
     * Returns [source], where every day on which the habit is not due (see [dueDays]) and that has
     * no entry of its own (or only a NO) is marked as SKIP: a rest day. It does not count as done,
     * but it is not missed either, so it neither breaks a streak nor lowers the score. A manual
     * "yes" on a rest day stays. Rest days far in the future are ignored until they come closer.
     */
    private fun entriesWithRestDays(source: EntryList): EntryList {
        val firstFixed = dueDays.firstFixedSince
        if (firstFixed == null || isNegative) return source
        val today = getToday()
        val limit = today.plus(PauseList.MAX_DAYS_AHEAD)
        // Starts 90 days back or at the oldest entry, not at the year 2000 of a new habit
        val oldestEntry = source.getKnown().lastOrNull()?.date ?: today
        val oldest = if (oldestEntry.isOlderThan(today.minus(90))) oldestEntry else today.minus(90)
        var day = if (oldest.isOlderThan(firstFixed)) firstFixed else oldest
        val result = EntryList()
        source.getKnown().forEach { result.add(it) }
        while (!day.isNewerThan(limit)) {
            if (!dueDays.isDueOn(day)) {
                val existing = source.get(day)
                if (existing.value == Entry.UNKNOWN || existing.value == Entry.NO) {
                    result.add(Entry(day, Entry.SKIP, existing.notes))
                }
            }
            day = day.plus(1)
        }
        return result
    }

    /** Whether the habit is not due on [date] because of its fixed weekdays. */
    fun isRestDay(date: LocalDate): Boolean = !isNegative && !dueDays.isDueOn(date)

    /**
     * Avoided habits are always evaluated daily: every day is either kept or relapsed. The same
     * holds for habits with fixed weekdays: each due day counts like a day of a daily habit.
     */
    val effectiveFrequency: Frequency
        get() = if ((isNegative && !isNumerical) || (dueDays.isEverFixed && !isNegative)) Frequency.DAILY else frequency

    /**
     * Days before [negativeSince] of an avoided habit are not part of it. A question mark set there
     * stays unknown and is never counted as done, so it neither looks achieved nor extends a streak.
     */
    private fun clearCompletionBeforeCreation() {
        val since = negativeSince ?: return
        for (entry in computedEntries.getKnown()) {
            if (entry.date < since && entry.value == Entry.UNKNOWN && entry.completed) {
                computedEntries.add(entry.copy(completed = false))
            }
        }
    }

    /**
     * For avoided ("negative") habits, every day from [negativeSince] until today without an
     * entry counts as done. Nothing is persisted: a tap on a day stores NO (relapse), and
     * returning to the check removes it again. For measurable habits, such days count as 0.
     * Days before [negativeSince] stay unknown.
     */
    private fun entriesWithAutoChecks(source: EntryList): EntryList {
        val since = negativeSince
        if (since == null) return source
        val autoValue = if (isNumerical) 0 else Entry.YES_MANUAL
        val result = EntryList()
        source.getKnown().forEach { result.add(it) }
        val today = getToday()
        var day: LocalDate = since
        while (!day.isNewerThan(today)) {
            val existing = source.get(day)
            if (existing.value == Entry.UNKNOWN) {
                result.add(Entry(day, autoValue, existing.notes))
            }
            day = day.plus(1)
        }
        return result
    }

    fun copyFrom(other: Habit) {
        this.category = other.category
        this.color = other.color
        this.description = other.description
        this.frequency = other.frequency
        // this.id should not be copied
        this.isArchived = other.isArchived
        this.name = other.name
        this.position = other.position
        this.reminder = other.reminder
        this.stepSize = other.stepSize
        this.targetType = other.targetType
        this.targetValue = other.targetValue
        this.type = other.type
        this.unit = other.unit
        this.uuid = other.uuid
        this.negativeSince = other.negativeSince
        this.createdOn = other.createdOn
        this.calendarPeriod = other.calendarPeriod
        this.countDays = other.countDays
        this.streakUnit = other.streakUnit
        this.dueDays = other.dueDays
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Habit) return false

        if (category != other.category) return false
        if (color != other.color) return false
        if (description != other.description) return false
        if (frequency != other.frequency) return false
        if (id != other.id) return false
        if (isArchived != other.isArchived) return false
        if (name != other.name) return false
        if (position != other.position) return false
        if (reminder != other.reminder) return false
        if (stepSize != other.stepSize) return false
        if (targetType != other.targetType) return false
        if (targetValue != other.targetValue) return false
        if (type != other.type) return false
        if (unit != other.unit) return false
        if (uuid != other.uuid) return false
        if (negativeSince != other.negativeSince) return false
        if (createdOn != other.createdOn) return false
        if (calendarPeriod != other.calendarPeriod) return false
        if (countDays != other.countDays) return false
        if (streakUnit != other.streakUnit) return false
        if (dueDays != other.dueDays) return false

        return true
    }

    override fun hashCode(): Int {
        var result = category.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + frequency.hashCode()
        result = 31 * result + (id?.hashCode() ?: 0)
        result = 31 * result + isArchived.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + position
        result = 31 * result + (reminder?.hashCode() ?: 0)
        result = 31 * result + stepSize.hashCode()
        result = 31 * result + targetType.value
        result = 31 * result + targetValue.hashCode()
        result = 31 * result + type.value
        result = 31 * result + unit.hashCode()
        result = 31 * result + (uuid?.hashCode() ?: 0)
        result = 31 * result + (negativeSince?.hashCode() ?: 0)
        result = 31 * result + (createdOn?.hashCode() ?: 0)
        result = 31 * result + calendarPeriod.hashCode()
        result = 31 * result + countDays.hashCode()
        result = 31 * result + (streakUnit?.hashCode() ?: 0)
        result = 31 * result + dueDays.hashCode()
        return result
    }
}
