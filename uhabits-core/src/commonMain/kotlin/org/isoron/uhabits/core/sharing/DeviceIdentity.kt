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
package org.isoron.uhabits.core.sharing

/**
 * Where the [DeviceIdentity] is kept. Implementations must store it outside of
 * the database and outside of any backup, so that two devices restored from
 * the same backup still have different device IDs.
 */
interface DeviceIdStorage {
    fun load(): String?
    fun store(deviceId: String)
}

/**
 * Identifies this app installation. Unlike [OwnerIdentity], it is never
 * exported or imported: each device of an owner gets its own ID, so that the
 * sharing server can revoke a single lost device.
 */
class DeviceIdentity(private val storage: DeviceIdStorage) {

    /** The ID of this installation, generated on first access. */
    val deviceId: String by lazy {
        storage.load()?.takeIf { isUuidV4(it) } ?: randomUuidV4().also { storage.store(it) }
    }
}
