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
package org.isoron.uhabits.homeassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import me.tatarka.inject.annotations.Component
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.inject.HabitsApplicationComponent
import org.isoron.uhabits.receivers.ReceiverScope

@ReceiverScope
@Component
internal abstract class HomeAssistantCommandComponent(
    @Component val parent: HabitsApplicationComponent
) {
    abstract val setEntryHandler: SetEntryHandler
    abstract val homeAssistantPreferences: HomeAssistantPreferences
}

/**
 * Receives commands that Home Assistant sends through the Companion App
 * (`notify.mobile_app_*` with `command_broadcast_intent`).
 *
 * Exported, so any app can send to it: every command must carry the shared
 * secret from [HomeAssistantPreferences.token]. Invalid commands are logged
 * and ignored.
 */
class HomeAssistantCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SetEntryHandler.ACTION_SET_ENTRY) return
        val app = context.applicationContext as HabitsApplication
        val component = HomeAssistantCommandComponent::class.create(app.component)
        val extras = intent.extras
        val result = component.setEntryHandler.handle(
            extras = { key ->
                @Suppress("DEPRECATION")
                extras?.get(key)?.toString()
            },
            acceptedToken = component.homeAssistantPreferences.acceptedToken
        )
        // Never log the extras: they contain the token.
        when (result) {
            is SetEntryHandler.Result.Ok -> Log.i(TAG, "Set entry: habit=${result.habit.id} date=${result.date.toCSVString()} value=${result.value}")
            else -> Log.w(TAG, "Ignored command: $result")
        }
    }

    companion object {
        private const val TAG = "HomeAssistantCommand"
    }
}
