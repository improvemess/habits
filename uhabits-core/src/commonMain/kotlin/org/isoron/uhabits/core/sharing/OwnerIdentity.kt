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

import org.isoron.platform.utils.secureRandomBytes

/**
 * Identity of the person who owns the habits shared from this app.
 *
 * - [ownerId] is public: it says *who* the owner is and may be shown to the
 *   sharing server and to friends.
 * - [ownerSecret] is the proof that one *is* that owner. It only leaves the
 *   device inside a full database backup; the sharing server stores a hash.
 *
 * Restoring a backup on another device gives it the same identity, which is
 * how one owner can use several devices.
 */
class OwnerIdentity(val ownerId: String, val ownerSecret: String) {

    init {
        require(isUuidV4(ownerId)) { "ownerId must be a lowercase UUID v4" }
        require(SECRET_REGEX.matches(ownerSecret)) {
            "ownerSecret must be $SECRET_BYTES bytes as lowercase hex"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is OwnerIdentity && other.ownerId == ownerId && other.ownerSecret == ownerSecret

    override fun hashCode(): Int = 31 * ownerId.hashCode() + ownerSecret.hashCode()

    /** Never contains [ownerSecret], so that identities are safe to log. */
    override fun toString(): String = "OwnerIdentity(ownerId=$ownerId, ownerSecret=<redacted>)"

    companion object {
        const val SECRET_BYTES = 32
        private val SECRET_REGEX = Regex("^[0-9a-f]{${SECRET_BYTES * 2}}$")

        fun generate(): OwnerIdentity =
            OwnerIdentity(randomUuidV4(), secureRandomBytes(SECRET_BYTES).toHex())

        /** Returns the identity, or null if either value is malformed. */
        fun parseOrNull(ownerId: String?, ownerSecret: String?): OwnerIdentity? {
            if (ownerId == null || ownerSecret == null) return null
            return try {
                OwnerIdentity(ownerId, ownerSecret)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
