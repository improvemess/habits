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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialogFragment
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.databinding.SelectHabitTypeBinding
import org.isoron.uhabits.intents.IntentFactory

class HabitTypeDialog : AppCompatDialogFragment() {
    override fun getTheme() = R.style.Translucent

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = SelectHabitTypeBinding.inflate(inflater, container, false)
        var negative = false

        fun showTypeStep(isNegative: Boolean) {
            negative = isNegative
            if (isNegative) {
                binding.yesNoExample.setText(R.string.quit_yes_or_no_example)
                binding.measurableExample.setText(R.string.quit_measurable_example)
            }
            binding.buttonBuild.visibility = View.GONE
            binding.buttonQuit.visibility = View.GONE
            binding.buttonYesNo.visibility = View.VISIBLE
            binding.buttonMeasurable.visibility = View.VISIBLE
        }

        binding.buttonBuild.setOnClickListener { showTypeStep(false) }
        binding.buttonQuit.setOnClickListener { showTypeStep(true) }

        binding.buttonYesNo.setOnClickListener {
            val intent = IntentFactory().startEditActivity(requireActivity(), HabitType.YES_NO.value, negative)
            startActivity(intent)
            dismiss()
        }

        binding.buttonMeasurable.setOnClickListener {
            val intent = IntentFactory().startEditActivity(requireActivity(), HabitType.NUMERICAL.value, negative)
            startActivity(intent)
            dismiss()
        }

        binding.background.setOnClickListener {
            dismiss()
        }

        return binding.root
    }
}
