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
package org.isoron.uhabits.sharing

import android.content.Context
import me.tatarka.inject.annotations.Inject
import org.isoron.uhabits.core.sharing.DeviceTokenStore
import org.isoron.uhabits.inject.AppContext

/**
 * Keeps device tokens per sharing server in their own preferences file.
 * [org.isoron.uhabits.HabitsBackupAgent] only backs up the "preferences" file
 * and the database, so tokens never move to another device.
 */
@Inject
class SharedPreferencesDeviceTokenStore(
    @AppContext context: Context
) : DeviceTokenStore {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun get(serverUrl: String): String? = prefs.getString(serverUrl, null)

    override fun put(serverUrl: String, token: String) {
        prefs.edit().putString(serverUrl, token).apply()
    }

    override fun remove(serverUrl: String) {
        prefs.edit().remove(serverUrl).apply()
    }

    private companion object {
        const val FILE_NAME = "sharing_device_tokens"
    }
}
