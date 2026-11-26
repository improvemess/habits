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
import org.isoron.uhabits.core.sharing.DeviceIdStorage
import org.isoron.uhabits.inject.AppContext

/**
 * Keeps the device ID in its own preferences file. [org.isoron.uhabits.HabitsBackupAgent]
 * only backs up the "preferences" file and the database, so the device ID is
 * never restored onto another device.
 */
@Inject
class SharedPreferencesDeviceIdStorage(
    @AppContext context: Context
) : DeviceIdStorage {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY_DEVICE_ID, null)

    override fun store(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    companion object {
        const val FILE_NAME = "device_identity"
        const val KEY_DEVICE_ID = "deviceId"
    }
}
