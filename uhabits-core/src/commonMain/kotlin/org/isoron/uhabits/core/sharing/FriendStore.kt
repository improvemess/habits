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
import org.isoron.platform.io.PreparedStatement
import org.isoron.platform.io.StepResult
import org.isoron.platform.io.run
import org.isoron.platform.time.LocalDate

/** A friend's habit this device follows, read-only. */
data class FriendShare(
    val id: Long,
    val serverUrl: String,
    val shareId: String,
    val key: ShareKey,
    val readerId: String,
    /** Read-only token; part of backups, so a restored device keeps following. */
    val readerToken: String,
    val status: Status,
    /** Last revision received; the next sync asks for changes after it. */
    val revision: Long,
    /** Null until the first sync received the metadata. */
    val metadata: SharedHabitMetadata?
) {
    enum class Status(val value: String) {
        ACTIVE("active"),

        /** The owner has not been seen for a while. */
        INACTIVE("inactive"),

        /** The owner stopped sharing. */
        ENDED("ended"),

        /** The owner removed this device from the share. */
        REMOVED("removed");

        companion object {
            fun of(value: String) = entries.firstOrNull { it.value == value } ?: ACTIVE
        }
    }
}

/**
 * Friends' habits in the `FriendShares` and `FriendEntries` tables. They are
 * kept apart from the own habits and entries on purpose: they can never be
 * checked off, exported as one's own or mixed into one's statistics.
 */
class FriendStore(private val db: Database) {

    fun insert(serverUrl: String, redemption: Redemption, key: ShareKey, createdAt: Long): FriendShare {
        db.run(
            "INSERT OR REPLACE INTO FriendShares(server_url, share_id, share_key, reader_id, reader_token, " +
                "status, revision, created_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?)"
        ) {
            bindText(1, serverUrl)
            bindText(2, redemption.shareId)
            bindText(3, key.encode())
            bindText(4, redemption.readerId)
            bindText(5, redemption.readerToken)
            bindText(6, FriendShare.Status.of(redemption.status).value)
            bindLong(7, createdAt)
        }
        return find(serverUrl, redemption.shareId)!!
    }

    fun find(serverUrl: String, shareId: String): FriendShare? =
        query("WHERE server_url = ? AND share_id = ?") {
            bindText(1, serverUrl)
            bindText(2, shareId)
        }.firstOrNull()

    fun findById(id: Long): FriendShare? = query("WHERE id = ?") { bindLong(1, id) }.firstOrNull()

    fun findAll(): List<FriendShare> = query("") {}

    fun update(id: Long, status: FriendShare.Status, revision: Long, metadata: SharedHabitMetadata?) {
        db.run("UPDATE FriendShares SET status = ?, revision = ?, meta = ? WHERE id = ?") {
            bindText(1, status.value)
            bindLong(2, revision)
            if (metadata == null) bindNull(3) else bindText(3, SharePayloads.encodeMetadata(metadata))
            bindLong(4, id)
        }
    }

    fun setStatus(id: Long, status: FriendShare.Status) {
        db.run("UPDATE FriendShares SET status = ? WHERE id = ?") {
            bindText(1, status.value)
            bindLong(2, id)
        }
    }

    fun delete(id: Long) {
        db.run("DELETE FROM FriendEntries WHERE friend_share = ?") { bindLong(1, id) }
        db.run("DELETE FROM FriendShares WHERE id = ?") { bindLong(1, id) }
    }

    /** Stores the value of a day as in [org.isoron.uhabits.core.models.Entry.value]; UNKNOWN removes it. */
    fun setEntry(friendShare: Long, date: LocalDate, value: Int?) {
        if (value == null) {
            db.run("DELETE FROM FriendEntries WHERE friend_share = ? AND timestamp = ?") {
                bindLong(1, friendShare)
                bindLong(2, date.unixTime)
            }
        } else {
            db.run("INSERT OR REPLACE INTO FriendEntries(friend_share, timestamp, value) VALUES (?, ?, ?)") {
                bindLong(1, friendShare)
                bindLong(2, date.unixTime)
                bindInt(3, value)
            }
        }
    }

    fun entries(friendShare: Long): Map<LocalDate, Int> {
        val stmt = db.prepareStatement("SELECT timestamp, value FROM FriendEntries WHERE friend_share = ?")
        stmt.bindLong(1, friendShare)
        val result = mutableMapOf<LocalDate, Int>()
        while (stmt.step() == StepResult.ROW) {
            result[LocalDate.fromUnixTime(stmt.getLong(0))] = stmt.getInt(1)
        }
        stmt.finalize()
        return result
    }

    private fun query(where: String, bind: PreparedStatement.() -> Unit): List<FriendShare> {
        val stmt = db.prepareStatement(
            "SELECT id, server_url, share_id, share_key, reader_id, reader_token, status, revision, meta " +
                "FROM FriendShares $where ORDER BY id"
        )
        stmt.bind()
        val result = mutableListOf<FriendShare>()
        while (stmt.step() == StepResult.ROW) {
            val key = ShareKey.decodeOrNull(stmt.getText(3)) ?: continue
            val meta = stmt.getTextOrNull(8)?.let {
                try {
                    SharePayloads.decodeMetadata(it)
                } catch (e: SharePayloadException) {
                    null
                }
            }
            result.add(
                FriendShare(
                    id = stmt.getLong(0),
                    serverUrl = stmt.getText(1),
                    shareId = stmt.getText(2),
                    key = key,
                    readerId = stmt.getText(4),
                    readerToken = stmt.getText(5),
                    status = FriendShare.Status.of(stmt.getText(6)),
                    revision = stmt.getLong(7),
                    metadata = meta
                )
            )
        }
        stmt.finalize()
        return result
    }
}
