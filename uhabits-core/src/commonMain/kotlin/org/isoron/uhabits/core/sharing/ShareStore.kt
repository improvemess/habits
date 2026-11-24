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

/** A habit of the owner, shared through [serverUrl]. */
data class Share(
    /** Local row id. */
    val id: Long,
    /** Id assigned by the server. */
    val shareId: String,
    val habitUuid: String,
    /** Base URL of the sharing server, e.g. `https://habits.improvemess.com`. */
    val serverUrl: String,
    val key: ShareKey,
    /** First day whose entries are shared. */
    val historyStart: LocalDate,
    /** Ended locally; the server is told by an [OutboxItem.Kind.END] item. */
    val ended: Boolean
)

/** Something about a share that the server does not know yet. */
data class OutboxItem(
    val share: Long,
    val kind: Kind,
    /** Day of the entry for [Kind.ENTRY], null otherwise. */
    val date: LocalDate?,
    /** When the change happened (epoch millis); sent as the entry's `updatedAt`. */
    val updatedAt: Long
) {
    enum class Kind { META, ENTRY, END }
}

/**
 * Shares and their outbox, in the `Shares` and `ShareOutbox` tables.
 *
 * Shares, including their keys, are part of every database backup, so that a
 * restored device can keep writing to them (see [OwnerIdentity]).
 *
 * The outbox only records *that* something changed, not *what*: the current
 * state is read when sending. Several changes of the same day therefore
 * collapse into one item, and only the latest state is ever sent.
 */
class ShareStore(private val db: Database) {

    fun insert(
        shareId: String,
        habitUuid: String,
        serverUrl: String,
        key: ShareKey,
        historyStart: LocalDate,
        createdAt: Long
    ): Share {
        db.run(
            "INSERT INTO Shares(share_id, habit_uuid, server_url, share_key, history_start, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)"
        ) {
            bindText(1, shareId)
            bindText(2, habitUuid)
            bindText(3, serverUrl)
            bindText(4, key.encode())
            bindLong(5, historyStart.unixTime)
            bindLong(6, createdAt)
        }
        return findByShareId(shareId)!!
    }

    fun findByShareId(shareId: String): Share? =
        queryShares("WHERE share_id = ?") { bindText(1, shareId) }.firstOrNull()

    fun findById(id: Long): Share? =
        queryShares("WHERE id = ?") { bindLong(1, id) }.firstOrNull()

    /** Shares of the habit that have not been ended. */
    fun findActiveByHabit(habitUuid: String): List<Share> =
        queryShares("WHERE habit_uuid = ? AND ended = 0") { bindText(1, habitUuid) }

    fun findAll(): List<Share> = queryShares("") {}

    /** UUIDs of habits with at least one share that has not been ended. */
    fun sharedHabitUuids(): Set<String> = findAll().filter { !it.ended }.map { it.habitUuid }.toSet()

    /** Servers with at least one share that has not been ended. */
    fun activeServers(): List<String> =
        findAll().filter { !it.ended }.map { it.serverUrl }.distinct()

    fun markEnded(id: Long) {
        db.run("UPDATE Shares SET ended = 1 WHERE id = ?") { bindLong(1, id) }
    }

    /** Removes the share and its outbox, e.g. once the server knows it has ended. */
    fun delete(id: Long) {
        db.run("DELETE FROM ShareOutbox WHERE share = ?") { bindLong(1, id) }
        db.run("DELETE FROM Shares WHERE id = ?") { bindLong(1, id) }
    }

    /** Records a change; a later change of the same item replaces it. */
    fun enqueue(item: OutboxItem) {
        db.run(
            "INSERT OR REPLACE INTO ShareOutbox(share, kind, timestamp, updated_at) VALUES (?, ?, ?, ?)"
        ) {
            bindLong(1, item.share)
            bindText(2, item.kind.name)
            bindLong(3, item.date?.unixTime ?: 0)
            bindLong(4, item.updatedAt)
        }
    }

    fun pending(share: Long): List<OutboxItem> {
        val stmt = db.prepareStatement(
            "SELECT share, kind, timestamp, updated_at FROM ShareOutbox WHERE share = ? ORDER BY kind, timestamp"
        )
        stmt.bindLong(1, share)
        val result = mutableListOf<OutboxItem>()
        while (stmt.step() == StepResult.ROW) {
            val kind = OutboxItem.Kind.valueOf(stmt.getText(1))
            result.add(
                OutboxItem(
                    share = stmt.getLong(0),
                    kind = kind,
                    date = if (kind == OutboxItem.Kind.ENTRY) LocalDate.fromUnixTime(stmt.getLong(2)) else null,
                    updatedAt = stmt.getLong(3)
                )
            )
        }
        stmt.finalize()
        return result
    }

    /** Shares with something in the outbox. */
    fun sharesWithPending(): List<Share> {
        val ids = mutableListOf<Long>()
        val stmt = db.prepareStatement("SELECT DISTINCT share FROM ShareOutbox")
        while (stmt.step() == StepResult.ROW) ids.add(stmt.getLong(0))
        stmt.finalize()
        return ids.mapNotNull { findById(it) }
    }

    /**
     * Removes [item] once it was sent, unless it changed again in the meantime
     * (then its `updated_at` is newer and it stays for the next run).
     */
    fun markSent(item: OutboxItem) {
        db.run(
            "DELETE FROM ShareOutbox WHERE share = ? AND kind = ? AND timestamp = ? AND updated_at <= ?"
        ) {
            bindLong(1, item.share)
            bindText(2, item.kind.name)
            bindLong(3, item.date?.unixTime ?: 0)
            bindLong(4, item.updatedAt)
        }
    }

    private fun queryShares(where: String, bind: PreparedStatement.() -> Unit): List<Share> {
        val stmt = db.prepareStatement(
            "SELECT id, share_id, habit_uuid, server_url, share_key, history_start, ended FROM Shares $where ORDER BY id"
        )
        stmt.bind()
        val result = mutableListOf<Share>()
        while (stmt.step() == StepResult.ROW) {
            val key = ShareKey.decodeOrNull(stmt.getText(4)) ?: continue
            result.add(
                Share(
                    id = stmt.getLong(0),
                    shareId = stmt.getText(1),
                    habitUuid = stmt.getText(2),
                    serverUrl = stmt.getText(3),
                    key = key,
                    historyStart = LocalDate.fromUnixTime(stmt.getLong(5)),
                    ended = stmt.getInt(6) != 0
                )
            )
        }
        stmt.finalize()
        return result
    }
}
