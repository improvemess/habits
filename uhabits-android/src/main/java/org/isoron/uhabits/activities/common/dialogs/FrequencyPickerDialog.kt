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

package org.isoron.uhabits.activities.common.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.JavaLocalDateFormatter
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.DueDays
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.WeekdayList
import org.isoron.uhabits.databinding.FrequencyPickerDialogBinding
import org.isoron.uhabits.utils.toFormattedString
import java.util.Locale

class FrequencyPickerDialog(
    var freqNumerator: Int,
    var freqDenominator: Int,
    var calendarPeriod: Boolean = false,
    /** Measurable habits only choose day, week, month or year, there is no number of repetitions. */
    private val measurable: Boolean = false,
    /** Wording "per week" instead of "every week" for limits of habits to quit. */
    private val quit: Boolean = false,
    /** Bit mask of the fixed weekdays (see DueDays), 0 if the habit uses a frequency (see #19). */
    private val fixedWeekdays: Int = 0,
    /** Number of repetitions per day of a new habit; more than one makes it a measurable habit (see #43). */
    private val timesPerDay: Int = 1,
    /** Existing yes/no habits keep one repetition per day, as their entries cannot become counts. */
    private val timesPerDayEditable: Boolean = true
) : AppCompatDialogFragment() {
    // The rolling or calendar choice of the week row and of the month row are independent; only the
    // choice of the selected row is kept.
    private var weekCalendar = calendarPeriod && freqDenominator == 7
    private var monthCalendar = calendarPeriod && (freqDenominator == 30 || freqDenominator == 31)
    private var yearCalendar = calendarPeriod && freqDenominator == Frequency.YEAR_DAYS
    private var _binding: FrequencyPickerDialogBinding? = null
    private val binding get() = _binding!!

    /** The weekday choice is its own dialog; it must not outlive this one (see onDestroyView). */
    private var weekdayDialog: AlertDialog? = null

    var onFrequencyPicked: (num: Int, den: Int, calendarPeriod: Boolean) -> Unit = { _, _, _ -> }

    /** Called on save with the chosen weekdays, or 0 if a frequency was chosen instead. */
    var onFixedDaysPicked: (weekdays: Int) -> Unit = {}

    /** Called on save with the number of repetitions per day, 1 unless "times per day" was chosen. */
    var onTimesPerDayPicked: (times: Int) -> Unit = {}

    private var pickedDays = fixedWeekdays

    constructor() : this(1, 1)

    override fun onDestroyView() {
        // Otherwise tapping OK in a weekday choice left open after this dialog was closed
        // (back, rotation) would update views that no longer exist and crash.
        weekdayDialog?.dismiss()
        weekdayDialog = null
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = FrequencyPickerDialogBinding.inflate(LayoutInflater.from(requireActivity()))

        if (measurable) {
            setUpMeasurableRows()
        } else {
            binding.everyDayText.setText(R.string.times_per_day)
            binding.timesPerDayTextView.setText(timesPerDay.toString())
            binding.timesPerDayTextView.isEnabled = timesPerDayEditable
            addBeforeAfterText(
                this.getString(R.string.every_x_days),
                binding.everyXDaysContainer
            )

            addBeforeAfterText(
                "%d " + getString(R.string.times),
                binding.xTimesPerWeekContainer
            )

            addBeforeAfterText(
                "%d " + getString(R.string.times),
                binding.xTimesPerMonthContainer
            )

            addBeforeAfterText(
                "%d " + getString(R.string.times),
                binding.xTimesPerYearContainer
            )

            addBeforeAfterText(
                this.getString(R.string.x_times_per_y_days),
                binding.xTimesPerYDaysContainer
            )
        }

        setUpFixedDays()
        updateModeTexts()
        setUpModePicker(binding.weekModePicker, binding.xTimesPerWeekRadioButton, Period.WEEK)
        setUpModePicker(binding.monthModePicker, binding.xTimesPerMonthRadioButton, Period.MONTH)
        setUpModePicker(binding.yearModePicker, binding.xTimesPerYearRadioButton, Period.YEAR)

        binding.everyDayRadioButton.setOnClickListener {
            check(binding.everyDayRadioButton)
            selectInputField(binding.timesPerDayTextView)
        }

        binding.timesPerDayTextView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) check(binding.everyDayRadioButton)
        }

        binding.everyXDaysRadioButton.setOnClickListener {
            check(binding.everyXDaysRadioButton)
            val everyXDaysTextView = binding.everyXDaysTextView
            selectInputField(everyXDaysTextView)
        }

        binding.everyXDaysTextView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) check(binding.everyXDaysRadioButton)
        }

        binding.xTimesPerWeekRadioButton.setOnClickListener {
            check(binding.xTimesPerWeekRadioButton)
            selectInputField(binding.xTimesPerWeekTextView)
        }

        binding.xTimesPerWeekTextView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) check(binding.xTimesPerWeekRadioButton)
        }

        binding.xTimesPerMonthRadioButton.setOnClickListener {
            check(binding.xTimesPerMonthRadioButton)
            selectInputField(binding.xTimesPerMonthTextView)
        }

        binding.xTimesPerMonthTextView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) check(binding.xTimesPerMonthRadioButton)
        }

        binding.xTimesPerYearRadioButton.setOnClickListener {
            check(binding.xTimesPerYearRadioButton)
            selectInputField(binding.xTimesPerYearTextView)
        }

        binding.xTimesPerYearTextView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) check(binding.xTimesPerYearRadioButton)
        }

        binding.xTimesPerYDaysRadioButton.setOnClickListener {
            check(binding.xTimesPerYDaysRadioButton)
            selectInputField(binding.xTimesPerYDaysXTextView)
        }

        binding.xTimesPerYDaysXTextView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) check(binding.xTimesPerYDaysRadioButton)
        }

        binding.xTimesPerYDaysYTextView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) check(binding.xTimesPerYDaysRadioButton)
        }

        return AlertDialog.Builder(requireActivity())
            .setView(binding.root)
            .setPositiveButton(R.string.save) { _, _ -> onSaveClicked() }
            .create()
    }

    /** Row "specific days": a dropdown with the chosen weekdays, picked in a multi-choice dialog. */
    private fun setUpFixedDays() {
        // Habits to quit are always daily, they have no fixed days
        if (quit) binding.fixedDaysContainer.visibility = View.GONE
        updateFixedDaysText()
        binding.fixedDaysRadioButton.setOnClickListener {
            check(binding.fixedDaysRadioButton)
            if (pickedDays == 0) showWeekdayChoice()
        }
        binding.fixedDaysPicker.setOnClickListener {
            check(binding.fixedDaysRadioButton)
            showWeekdayChoice()
        }
    }

    private fun updateFixedDaysText() {
        binding.fixedDaysPicker.text = if (pickedDays == 0) {
            getString(R.string.specific_days)
        } else {
            DueDays.toWeekdayList(pickedDays).toFormattedString(requireContext())
        }
    }

    private fun showWeekdayChoice() {
        val selected = DueDays.toWeekdayList(pickedDays).toArray()
        weekdayDialog = AlertDialog.Builder(requireActivity())
            .setTitle(R.string.select_weekdays)
            .setMultiChoiceItems(
                JavaLocalDateFormatter(Locale.getDefault()).longWeekdayNames(DayOfWeek.SATURDAY),
                selected
            ) { _, which, isChecked -> selected[which] = isChecked }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                pickedDays = DueDays.fromWeekdayList(WeekdayList(selected))
                if (_binding != null) updateFixedDaysText()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { weekdayDialog = null }
            .show()
    }

    /** Measurable habits: only the rows every day, week, month and year, without numbers. */
    private fun setUpMeasurableRows() {
        binding.everyXDaysContainer.visibility = View.GONE
        binding.xTimesPerYDaysContainer.visibility = View.GONE
        binding.timesPerDayTextView.visibility = View.GONE
        binding.xTimesPerWeekTextView.visibility = View.GONE
        binding.xTimesPerMonthTextView.visibility = View.GONE
        binding.xTimesPerYearTextView.visibility = View.GONE
        binding.everyDayText.setText(if (quit) R.string.quit_per_day else R.string.every_day)
        // Without a number, the dropdown takes the place of the text: "Every rolling week"
        moveIntoRow(binding.weekModePicker, binding.xTimesPerWeekContainer)
        moveIntoRow(binding.monthModePicker, binding.xTimesPerMonthContainer)
        moveIntoRow(binding.yearModePicker, binding.xTimesPerYearContainer)
    }

    private fun moveIntoRow(picker: TextView, row: LinearLayout) {
        (picker.parent as ViewGroup).removeView(picker)
        val density = resources.displayMetrics.density
        row.addView(
            picker,
            LinearLayout.LayoutParams(0, (40 * density).toInt(), 1f).apply {
                marginStart = (8 * density).toInt()
            }
        )
    }

    private fun addBeforeAfterText(
        str: String,
        container: LinearLayout
    ) {
        val parts = str.split("%d")
        for (i in parts.indices) {
            container.addView(
                TextView(activity).apply { text = parts[i].trim() },
                2 * i + 1
            )
        }
    }

    private fun onSaveClicked() {
        if (binding.fixedDaysRadioButton.isChecked && pickedDays != 0) {
            onFixedDaysPicked(pickedDays)
            onTimesPerDayPicked(1)
            dismiss()
            return
        }
        onFixedDaysPicked(0)
        onTimesPerDayPicked(pickedTimesPerDay())
        saveFrequency()
    }

    /** More than one repetition per day, only if that row is chosen of a yes/no habit. */
    private fun pickedTimesPerDay(): Int {
        if (measurable || !binding.everyDayRadioButton.isChecked || !timesPerDayEditable) return 1
        return binding.timesPerDayTextView.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
    }

    private fun saveFrequency() {
        var numerator = 1
        var denominator = 1
        if (measurable) {
            denominator = when {
                binding.xTimesPerWeekRadioButton.isChecked -> 7
                binding.xTimesPerMonthRadioButton.isChecked -> 30
                binding.xTimesPerYearRadioButton.isChecked -> Frequency.YEAR_DAYS
                else -> 1
            }
            onFrequencyPicked(1, denominator, selectedCalendar(denominator))
            dismiss()
            return
        }
        when {
            binding.everyDayRadioButton.isChecked -> {
                // NOP
            }

            binding.everyXDaysRadioButton.isChecked -> {
                if (binding.everyXDaysTextView.text.isNotEmpty()) {
                    denominator = Integer.parseInt(binding.everyXDaysTextView.text.toString())
                }
            }

            binding.xTimesPerWeekRadioButton.isChecked -> {
                if (binding.xTimesPerWeekTextView.text.isNotEmpty()) {
                    numerator = Integer.parseInt(binding.xTimesPerWeekTextView.text.toString())
                    denominator = 7
                }
            }

            binding.xTimesPerYDaysRadioButton.isChecked -> {
                if (binding.xTimesPerYDaysXTextView.text.isNotEmpty() && binding.xTimesPerYDaysYTextView.text.isNotEmpty()) {
                    numerator =
                        Integer.parseInt(binding.xTimesPerYDaysXTextView.text.toString())
                    denominator =
                        Integer.parseInt(binding.xTimesPerYDaysYTextView.text.toString())
                }
            }

            binding.xTimesPerYearRadioButton.isChecked -> {
                if (binding.xTimesPerYearTextView.text.isNotEmpty()) {
                    numerator = Integer.parseInt(binding.xTimesPerYearTextView.text.toString())
                    denominator = Frequency.YEAR_DAYS
                }
            }

            else -> {
                if (binding.xTimesPerMonthTextView.text.isNotEmpty()) {
                    numerator = Integer.parseInt(binding.xTimesPerMonthTextView.text.toString())
                    denominator = 30
                }
            }
        }
        if (numerator >= denominator || numerator < 1) {
            numerator = 1
            denominator = 1
        }
        onFrequencyPicked(
            numerator,
            denominator,
            selectedCalendar(denominator)
        )
        dismiss()
    }

    private enum class Period { WEEK, MONTH, YEAR }

    private fun modeText(period: Period, calendar: Boolean): Int = when {
        measurable && quit -> when (period) {
            Period.WEEK -> if (calendar) R.string.quit_per_calendar_week else R.string.quit_per_rolling_week
            Period.MONTH -> if (calendar) R.string.quit_per_calendar_month else R.string.quit_per_rolling_month
            Period.YEAR -> if (calendar) R.string.quit_per_calendar_year else R.string.quit_per_rolling_year
        }
        measurable -> when (period) {
            Period.WEEK -> if (calendar) R.string.every_calendar_week else R.string.every_rolling_week
            Period.MONTH -> if (calendar) R.string.every_calendar_month else R.string.every_rolling_month
            Period.YEAR -> if (calendar) R.string.every_calendar_year else R.string.every_rolling_year
        }
        else -> when (period) {
            Period.WEEK -> if (calendar) R.string.per_calendar_week else R.string.per_rolling_week
            Period.MONTH -> if (calendar) R.string.per_calendar_month else R.string.per_rolling_month
            Period.YEAR -> if (calendar) R.string.per_calendar_year else R.string.per_rolling_year
        }
    }

    private fun setUpModePicker(picker: TextView, radio: RadioButton, period: Period) {
        picker.setOnClickListener {
            check(radio)
            val menu = PopupMenu(requireActivity(), picker)
            menu.menu.add(0, 0, 0, modeText(period, false))
            menu.menu.add(0, 1, 1, modeText(period, true))
            menu.setOnMenuItemClickListener {
                val calendar = it.itemId == 1
                when (period) {
                    Period.WEEK -> weekCalendar = calendar
                    Period.MONTH -> monthCalendar = calendar
                    Period.YEAR -> yearCalendar = calendar
                }
                updateModeTexts()
                true
            }
            menu.show()
        }
    }

    private fun selectedCalendar(denominator: Int) = when (denominator) {
        7 -> weekCalendar
        30 -> monthCalendar
        Frequency.YEAR_DAYS -> yearCalendar
        else -> false
    }

    private fun updateModeTexts() {
        binding.weekModePicker.setText(modeText(Period.WEEK, weekCalendar))
        binding.monthModePicker.setText(modeText(Period.MONTH, monthCalendar))
        binding.yearModePicker.setText(modeText(Period.YEAR, yearCalendar))
    }

    private fun check(view: RadioButton) {
        uncheckAll()
        view.isChecked = true
        view.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        populateViews()
    }

    private fun populateViews() {
        uncheckAll()
        if (fixedWeekdays != 0 && !quit) {
            binding.fixedDaysRadioButton.isChecked = true
            return
        }
        if (measurable) {
            when (freqDenominator) {
                7 -> binding.xTimesPerWeekRadioButton.isChecked = true
                30, 31 -> binding.xTimesPerMonthRadioButton.isChecked = true
                Frequency.YEAR_DAYS -> binding.xTimesPerYearRadioButton.isChecked = true
                else -> binding.everyDayRadioButton.isChecked = true
            }
            return
        }
        if (freqDenominator == Frequency.YEAR_DAYS) {
            binding.xTimesPerYearRadioButton.isChecked = true
            binding.xTimesPerYearTextView.setText(freqNumerator.toString())
            selectInputField(binding.xTimesPerYearTextView)
        } else if (freqDenominator == 30 || freqDenominator == 31) {
            binding.xTimesPerMonthRadioButton.isChecked = true
            binding.xTimesPerMonthTextView.setText(freqNumerator.toString())
            selectInputField(binding.xTimesPerMonthTextView)
        } else {
            if (freqNumerator == 1 && !(freqDenominator == 7 && calendarPeriod)) {
                if (freqDenominator == 1) {
                    binding.everyDayRadioButton.isChecked = true
                    selectInputField(binding.timesPerDayTextView)
                } else {
                    binding.everyXDaysRadioButton.isChecked = true
                    binding.everyXDaysTextView.setText(freqDenominator.toString())
                    selectInputField(binding.everyXDaysTextView)
                }
            } else {
                if (freqDenominator == 7) {
                    binding.xTimesPerWeekRadioButton.isChecked = true
                    binding.xTimesPerWeekTextView.setText(freqNumerator.toString())
                    selectInputField(binding.xTimesPerWeekTextView)
                } else {
                    binding.xTimesPerYDaysRadioButton.isChecked = true
                    binding.xTimesPerYDaysXTextView.setText(freqNumerator.toString())
                    binding.xTimesPerYDaysYTextView.setText(freqDenominator.toString())
                }
            }
        }
    }

    private fun selectInputField(view: EditText) {
        view.setSelection(view.text.length)
    }

    private fun uncheckAll() {
        binding.everyDayRadioButton.isChecked = false
        binding.everyXDaysRadioButton.isChecked = false
        binding.xTimesPerWeekRadioButton.isChecked = false
        binding.xTimesPerMonthRadioButton.isChecked = false
        binding.xTimesPerYearRadioButton.isChecked = false
        binding.xTimesPerYDaysRadioButton.isChecked = false
        binding.fixedDaysRadioButton.isChecked = false
    }
}
