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

import android.content.Context
import android.content.Intent
import me.tatarka.inject.annotations.Inject
import org.isoron.uhabits.core.AppScope
import org.isoron.uhabits.core.events.HabitEvent
import org.isoron.uhabits.core.events.HabitEventTransport
import org.isoron.uhabits.inject.AppContext

/**
 * Sends [HabitEvent]s to Home Assistant through the Companion App.
 *
 * The Companion App forwards the broadcast as HA event `android.intent_received`,
 * provided [CompanionAppPayload.ACTION_HABIT_EVENT] is registered in its
 * "Last update trigger" sensor. Delivery is best effort: if HA cannot be
 * reached, the Companion App drops the event.
 */
@Inject
@AppScope
class CompanionAppTransport(
    @AppContext private val context: Context,
    private val prefs: HomeAssistantPreferences
) : HabitEventTransport {

    override fun send(event: HabitEvent) {
        if (!prefs.isEnabled) return
        val intent = Intent(CompanionAppPayload.ACTION_HABIT_EVENT).apply {
            setPackage(prefs.companionPackage)
            for ((key, value) in CompanionAppPayload.extras(event)) putExtra(key, value)
        }
        context.sendBroadcast(intent)
    }
}
