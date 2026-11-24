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

import org.isoron.platform.io.Database
import org.isoron.platform.io.querySingle
import org.isoron.platform.io.run

/**
 * Keeps the [OwnerIdentity] in the `OwnerIdentity` table of [db], so that it is
 * part of every full database backup.
 */
class OwnerIdentityStore(private val db: Database) {

    /** Returns the stored identity, or null if there is none or it is malformed. */
    fun load(): OwnerIdentity? {
        val row = db.querySingle("SELECT owner_id, owner_secret FROM OwnerIdentity WHERE id = 1") {
            Pair(it.getTextOrNull(0), it.getTextOrNull(1))
        } ?: return null
        return OwnerIdentity.parseOrNull(row.first, row.second)
    }

    fun save(identity: OwnerIdentity) {
        db.run("INSERT OR REPLACE INTO OwnerIdentity(id, owner_id, owner_secret) VALUES (1, ?, ?)") {
            bindText(1, identity.ownerId)
            bindText(2, identity.ownerSecret)
        }
    }

    /** Returns the stored identity, generating and storing one if needed. */
    fun getOrCreate(): OwnerIdentity =
        load() ?: OwnerIdentity.generate().also { save(it) }
}
