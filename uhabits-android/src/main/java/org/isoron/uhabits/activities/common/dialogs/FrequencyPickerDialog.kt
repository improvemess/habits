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
import org.isoron.uhabits.core.models.WeekdayList
import org.isoron.uhabits.databinding.FrequencyPickerDialogBinding
import org.isoron.uhabits.utils.toFormattedString
import java.util.Locale

class FrequencyPickerDialog(
    var freqNumerator: Int,
    var freqDenominator: Int,
    var calendarPeriod: Boolean = false,
    /** Measurable habits only choose day, week or month, there is no number of repetitions. */
    private val measurable: Boolean = false,
    /** Wording "per week" instead of "every week" for limits of habits to quit. */
    private val quit: Boolean = false,
    /** Bit mask of the fixed weekdays (see DueDays), 0 if the habit uses a frequency (see #19). */
    private val fixedWeekdays: Int = 0
) : AppCompatDialogFragment() {
    // The rolling or calendar choice of the week row and of the month row are independent; only the
    // choice of the selected row is kept.
    private var weekCalendar = calendarPeriod && freqDenominator == 7
    private var monthCalendar = calendarPeriod && (freqDenominator == 30 || freqDenominator == 31)
    private var _binding: FrequencyPickerDialogBinding? = null
    private val binding get() = _binding!!

    var onFrequencyPicked: (num: Int, den: Int, calendarPeriod: Boolean) -> Unit = { _, _, _ -> }

    /** Called on save with the chosen weekdays, or 0 if a frequency was chosen instead. */
    var onFixedDaysPicked: (weekdays: Int) -> Unit = {}

    private var pickedDays = fixedWeekdays

    constructor() : this(1, 1)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = FrequencyPickerDialogBinding.inflate(LayoutInflater.from(requireActivity()))

        if (measurable) {
            setUpMeasurableRows()
        } else {
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
                this.getString(R.string.x_times_per_y_days),
                binding.xTimesPerYDaysContainer
            )
        }

        setUpFixedDays()
        updateModeTexts()
        setUpModePicker(binding.weekModePicker, binding.xTimesPerWeekRadioButton, isWeek = true)
        setUpModePicker(binding.monthModePicker, binding.xTimesPerMonthRadioButton, isWeek = false)

        binding.everyDayRadioButton.setOnClickListener {
            check(binding.everyDayRadioButton)
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
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.select_weekdays)
            .setMultiChoiceItems(
                JavaLocalDateFormatter(Locale.getDefault()).longWeekdayNames(DayOfWeek.SATURDAY),
                selected
            ) { _, which, isChecked -> selected[which] = isChecked }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                pickedDays = DueDays.fromWeekdayList(WeekdayList(selected))
                updateFixedDaysText()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Measurable habits: only the rows every day, every week and every month, without numbers. */
    private fun setUpMeasurableRows() {
        binding.everyXDaysContainer.visibility = View.GONE
        binding.xTimesPerYDaysContainer.visibility = View.GONE
        binding.xTimesPerWeekTextView.visibility = View.GONE
        binding.xTimesPerMonthTextView.visibility = View.GONE
        binding.everyDayText.setText(if (quit) R.string.quit_per_day else R.string.every_day)
        // Without a number, the dropdown takes the place of the text: "Every rolling week"
        moveIntoRow(binding.weekModePicker, binding.xTimesPerWeekContainer)
        moveIntoRow(binding.monthModePicker, binding.xTimesPerMonthContainer)
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
            dismiss()
            return
        }
        onFixedDaysPicked(0)
        saveFrequency()
    }

    private fun saveFrequency() {
        var numerator = 1
        var denominator = 1
        if (measurable) {
            denominator = when {
                binding.xTimesPerWeekRadioButton.isChecked -> 7
                binding.xTimesPerMonthRadioButton.isChecked -> 30
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

    private fun modeText(isWeek: Boolean, calendar: Boolean): Int = when {
        measurable && quit -> if (isWeek) {
            if (calendar) R.string.quit_per_calendar_week else R.string.quit_per_rolling_week
        } else {
            if (calendar) R.string.quit_per_calendar_month else R.string.quit_per_rolling_month
        }
        measurable -> if (isWeek) {
            if (calendar) R.string.every_calendar_week else R.string.every_rolling_week
        } else {
            if (calendar) R.string.every_calendar_month else R.string.every_rolling_month
        }
        else -> if (isWeek) {
            if (calendar) R.string.per_calendar_week else R.string.per_rolling_week
        } else {
            if (calendar) R.string.per_calendar_month else R.string.per_rolling_month
        }
    }

    private fun setUpModePicker(picker: TextView, radio: RadioButton, isWeek: Boolean) {
        picker.setOnClickListener {
            check(radio)
            val menu = PopupMenu(requireActivity(), picker)
            menu.menu.add(0, 0, 0, modeText(isWeek, false))
            menu.menu.add(0, 1, 1, modeText(isWeek, true))
            menu.setOnMenuItemClickListener {
                if (isWeek) weekCalendar = it.itemId == 1 else monthCalendar = it.itemId == 1
                updateModeTexts()
                true
            }
            menu.show()
        }
    }

    private fun selectedCalendar(denominator: Int) = when (denominator) {
        7 -> weekCalendar
        30 -> monthCalendar
        else -> false
    }

    private fun updateModeTexts() {
        binding.weekModePicker.setText(modeText(true, weekCalendar))
        binding.monthModePicker.setText(modeText(false, monthCalendar))
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
                else -> binding.everyDayRadioButton.isChecked = true
            }
            return
        }
        if (freqDenominator == 30 || freqDenominator == 31) {
            binding.xTimesPerMonthRadioButton.isChecked = true
            binding.xTimesPerMonthTextView.setText(freqNumerator.toString())
            selectInputField(binding.xTimesPerMonthTextView)
        } else {
            if (freqNumerator == 1 && !(freqDenominator == 7 && calendarPeriod)) {
                if (freqDenominator == 1) {
                    binding.everyDayRadioButton.isChecked = true
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
        binding.xTimesPerYDaysRadioButton.isChecked = false
        binding.fixedDaysRadioButton.isChecked = false
    }
}
