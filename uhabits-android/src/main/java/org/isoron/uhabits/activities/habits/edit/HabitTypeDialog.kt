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

package org.isoron.uhabits.activities.habits.edit

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDialogFragment
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.databinding.SelectHabitTypeBinding
import org.isoron.uhabits.intents.IntentFactory

class HabitTypeDialog : AppCompatDialogFragment() {
    // The dialog stays open behind the edit screen, so that "back" there
    // returns to the type step. It closes once the habit has been saved.
    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) dismiss()
    }

    override fun getTheme() = R.style.Translucent

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = SelectHabitTypeBinding.inflate(inflater, container, false)
        var negative = false
        var inTypeStep = false

        fun showTypeStep(isNegative: Boolean) {
            negative = isNegative
            inTypeStep = true
            binding.yesNoExample.setText(
                if (isNegative) R.string.quit_yes_or_no_example else R.string.yes_or_no_example
            )
            binding.measurableExample.setText(
                if (isNegative) R.string.quit_measurable_example else R.string.measurable_example
            )
            binding.buttonBuild.visibility = View.GONE
            binding.buttonQuit.visibility = View.GONE
            binding.buttonYesNo.visibility = View.VISIBLE
            binding.buttonMeasurable.visibility = View.VISIBLE
        }

        fun showGoalStep() {
            inTypeStep = false
            binding.buttonBuild.visibility = View.VISIBLE
            binding.buttonQuit.visibility = View.VISIBLE
            binding.buttonYesNo.visibility = View.GONE
            binding.buttonMeasurable.visibility = View.GONE
        }

        binding.buttonBuild.setOnClickListener { showTypeStep(false) }
        binding.buttonQuit.setOnClickListener { showTypeStep(true) }

        binding.buttonYesNo.setOnClickListener {
            editLauncher.launch(
                IntentFactory().startEditActivity(requireActivity(), HabitType.YES_NO.value, negative)
            )
        }

        binding.buttonMeasurable.setOnClickListener {
            editLauncher.launch(
                IntentFactory().startEditActivity(requireActivity(), HabitType.NUMERICAL.value, negative)
            )
        }

        (requireDialog() as? ComponentDialog)?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
            if (inTypeStep) showGoalStep() else dismiss()
        }

        binding.background.setOnClickListener {
            dismiss()
        }

        return binding.root
    }
}
