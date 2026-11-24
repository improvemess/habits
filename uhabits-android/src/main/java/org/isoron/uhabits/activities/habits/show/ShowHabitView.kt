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

package org.isoron.uhabits.activities.habits.show

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.Pause
import org.isoron.uhabits.core.ui.screens.habits.show.ShowHabitPresenter
import org.isoron.uhabits.core.ui.screens.habits.show.ShowHabitState
import org.isoron.uhabits.databinding.ShowHabitBinding
import org.isoron.uhabits.utils.applyBottomInset
import org.isoron.uhabits.utils.applyToolbarInsets
import org.isoron.uhabits.utils.setupToolbar

class ShowHabitView(context: Context) : FrameLayout(context) {
    private val binding = ShowHabitBinding.inflate(LayoutInflater.from(context))
    private var onRemovePause: (Pause) -> Unit = {}

    init {
        binding.toolbar.applyToolbarInsets()
        addView(binding.root)
    }

    fun setState(data: ShowHabitState) {
        setupToolbar(
            binding.toolbar,
            title = data.title,
            color = data.color,
            theme = data.theme
        )
        binding.subtitleCard.setState(data.subtitle)
        binding.overviewCard.setState(data.overview)
        binding.notesCard.setState(data.notes)
        binding.targetCard.setState(data.target)
        binding.streakCard.setState(data.streaks)
        binding.scoreCard.setState(data.scores)
        binding.frequencyCard.setState(data.frequency)
        binding.historyCard.setState(data.history)
        binding.barCard.setState(data.bar)
        if (data.isNumerical) {
            binding.overviewCard.visibility = GONE
        } else {
            binding.targetCard.visibility = GONE
        }
        setPauses(data.pauses)
        binding.linearLayout.applyBottomInset()
    }

    fun setOnRemovePauseListener(listener: (Pause) -> Unit) {
        onRemovePause = listener
    }

    private fun setPauses(pauses: List<Pause>) {
        val container = binding.pauseList
        container.removeAllViews()
        val dp = resources.displayMetrics.density
        val format = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).apply {
            // LocalDate.unixTime is midnight UTC
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        fun fmt(d: LocalDate) = format.format(java.util.Date(d.unixTime))
        for (pause in pauses) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            }
            val label = TextView(context).apply {
                text = context.getString(
                    R.string.pause_period,
                    pause.title.ifBlank { context.getString(R.string.pause_default_title) },
                    fmt(pause.start),
                    fmt(pause.end)
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            row.addView(label, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            val close = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundResource(android.R.color.transparent)
                contentDescription = context.getString(R.string.pause_remove_description)
                setOnClickListener { onRemovePause(pause) }
            }
            row.addView(close, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))
            container.addView(row)
        }
    }

    fun setListener(presenter: ShowHabitPresenter) {
        binding.scoreCard.setListener(presenter.scoreCardPresenter)
        binding.historyCard.setListener(presenter.historyCardPresenter)
        binding.barCard.setListener(presenter.barCardPresenter)
    }
}
