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
    var question: String = "",
    var reminder: Reminder? = null,
    var stepSize: Double = 1.0,
    var targetType: NumericalHabitType = NumericalHabitType.AT_LEAST,
    var targetValue: Double = 0.0,
    var type: HabitType = HabitType.YES_NO,
    var unit: String = "",
    var uuid: String? = null,
    /** First day on which an avoided ("negative") habit counts as done; null for normal habits. */
    var negativeSince: LocalDate? = null,
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

    val isNumerical: Boolean
        get() = type == HabitType.NUMERICAL

    val uriString: String
        get() = "content://org.isoron.uhabits/habit/$id"

    fun hasReminder(): Boolean = reminder != null

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

    fun recompute() {
        computedEntries.recomputeFrom(
            originalEntries = entriesWithAutoChecks(entriesWithPauses()),
            frequency = effectiveFrequency,
            isNumerical = isNumerical
        )

        computedEntries.recomputeCompletion(
            frequency = effectiveFrequency,
            isNumerical = isNumerical,
            targetType = targetType,
            targetValue = targetValue
        )

        val today = getToday()
        val to = today.plus(30)
        val entries = computedEntries.getKnown()
        var from = entries.lastOrNull()?.date ?: today
        if (from.isNewerThan(to)) from = to

        scores.recompute(
            frequency = effectiveFrequency,
            isNumerical = isNumerical,
            numericalHabitType = targetType,
            targetValue = targetValue,
            computedEntries = computedEntries,
            from = from,
            to = to
        )

        streaks.recompute(
            computedEntries,
            from,
            to,
            isNumerical,
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

    /** Avoided habits are always evaluated daily: every day is either kept or relapsed. */
    private val effectiveFrequency: Frequency
        get() = if (isNegative && !isNumerical) Frequency.DAILY else frequency

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
        this.question = other.question
        this.reminder = other.reminder
        this.stepSize = other.stepSize
        this.targetType = other.targetType
        this.targetValue = other.targetValue
        this.type = other.type
        this.unit = other.unit
        this.uuid = other.uuid
        this.negativeSince = other.negativeSince
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
        if (question != other.question) return false
        if (reminder != other.reminder) return false
        if (stepSize != other.stepSize) return false
        if (targetType != other.targetType) return false
        if (targetValue != other.targetValue) return false
        if (type != other.type) return false
        if (unit != other.unit) return false
        if (uuid != other.uuid) return false
        if (negativeSince != other.negativeSince) return false

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
        result = 31 * result + question.hashCode()
        result = 31 * result + (reminder?.hashCode() ?: 0)
        result = 31 * result + stepSize.hashCode()
        result = 31 * result + targetType.value
        result = 31 * result + targetValue.hashCode()
        result = 31 * result + type.value
        result = 31 * result + unit.hashCode()
        result = 31 * result + (uuid?.hashCode() ?: 0)
        result = 31 * result + (negativeSince?.hashCode() ?: 0)
        return result
    }
}
