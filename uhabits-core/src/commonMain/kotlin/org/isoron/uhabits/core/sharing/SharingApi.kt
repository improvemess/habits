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

import org.isoron.platform.time.LocalDate

/** An entry as uploaded to the server: plain date and timestamp, sealed data. */
data class EntryUpload(
    val date: LocalDate,
    /** Sealed [SharedEntry], see [SharePayloads.sealEntry]. */
    val data: String,
    /** Epoch millis of the change on the device; the server keeps the latest. */
    val updatedAt: Long,
    val deviceId: String
)

/**
 * The calls of the sharing server (uhabits-server, API v1) that the owner's
 * devices need to keep shares up to date. Implemented per platform.
 *
 * @throws SharingApiException on any failure.
 */
interface SharingApi {
    /** Creates the owner; succeeds if it already exists with the same secret. */
    suspend fun createOwner(serverUrl: String, identity: OwnerIdentity)

    /** Registers this device and returns its device token. */
    suspend fun registerDevice(serverUrl: String, identity: OwnerIdentity, deviceId: String): String

    suspend fun putMeta(serverUrl: String, deviceToken: String, shareId: String, meta: String)

    /** At most [MAX_ENTRIES_PER_REQUEST] entries per call. */
    suspend fun putEntries(serverUrl: String, deviceToken: String, shareId: String, entries: List<EntryUpload>)

    suspend fun endShare(serverUrl: String, deviceToken: String, shareId: String)

    suspend fun heartbeat(serverUrl: String, deviceToken: String)

    companion object {
        const val MAX_ENTRIES_PER_REQUEST = 500
    }
}

/**
 * @property status HTTP status, or null if the server could not be reached.
 */
class SharingApiException(val status: Int?, message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** Worth trying again later: no connection, server error or rate limit. */
    val isTemporary: Boolean get() = status == null || status >= 500 || status == 429 || status == 408

    val isUnauthorized: Boolean get() = status == 401

    /** The share no longer exists on the server. */
    val isShareGone: Boolean get() = status == 404 || status == 410
}

/**
 * Device tokens per server. Implementations must keep them outside the
 * database and outside backups: a token belongs to one device.
 */
interface DeviceTokenStore {
    fun get(serverUrl: String): String?
    fun put(serverUrl: String, token: String)
    fun remove(serverUrl: String)
}
