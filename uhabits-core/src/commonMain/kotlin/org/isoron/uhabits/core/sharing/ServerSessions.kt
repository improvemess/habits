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
 * Device tokens for sharing servers: registers the owner and this device on
 * first use, and registers again once if the server rejects a token.
 */
class ServerSessions(
    private val api: SharingApi,
    private val tokens: DeviceTokenStore,
    private val ownerIdentityStore: OwnerIdentityStore,
    private val deviceIdentity: DeviceIdentity
) {
    /** Runs [block] with a device token for [server]. */
    suspend fun <T> withToken(server: String, block: suspend (String) -> T): T {
        val token = tokens.get(server) ?: register(server)
        return try {
            block(token)
        } catch (e: SharingApiException) {
            if (!e.isUnauthorized) throw e
            tokens.remove(server)
            block(register(server))
        }
    }

    private suspend fun register(server: String): String {
        val identity = ownerIdentityStore.getOrCreate()
        api.createOwner(server, identity)
        val token = api.registerDevice(server, identity, deviceIdentity.deviceId)
        tokens.put(server, token)
        return token
    }
}
