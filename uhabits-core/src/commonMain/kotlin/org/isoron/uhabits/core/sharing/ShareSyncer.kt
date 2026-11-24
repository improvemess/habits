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

import org.isoron.uhabits.core.events.HabitEvent
import org.isoron.uhabits.core.events.HabitEventFactory
import org.isoron.uhabits.core.io.Logging
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList

/**
 * Sends the outbox of [store] to the sharing servers, and keeps the owner's
 * devices registered there. Meant to run in the background, e.g. from a
 * WorkManager worker; every call is safe to repeat.
 *
 * Error handling per server:
 * - temporary failures (no connection, 5xx, 429) stop that server and ask for
 *   a retry; the outbox keeps the items;
 * - 401 drops the device token, registers the device again and retries once;
 * - a share that is gone on the server (404, 410) is removed locally;
 * - other errors (e.g. 400) drop the share's pending items and are logged, so
 *   that one bad item cannot block the outbox forever.
 */
class ShareSyncer(
    private val store: ShareStore,
    private val habitList: HabitList,
    private val api: SharingApi,
    tokens: DeviceTokenStore,
    ownerIdentityStore: OwnerIdentityStore,
    private val deviceIdentity: DeviceIdentity,
    logging: Logging
) {
    enum class Result { DONE, RETRY }

    private val logger = logging.getLogger("ShareSyncer")
    private val sessions = ServerSessions(api, tokens, ownerIdentityStore, deviceIdentity)

    suspend fun sync(): Result {
        var result = Result.DONE
        for ((server, shares) in store.sharesWithPending().groupBy { it.serverUrl }) {
            if (!perServer(server) { token -> shares.forEach { syncShare(server, token, it) } }) {
                result = Result.RETRY
            }
        }
        return result
    }

    /** Tells every server with active shares that this device is still around. */
    suspend fun heartbeat(): Result {
        var result = Result.DONE
        for (server in store.activeServers()) {
            if (!perServer(server) { token -> api.heartbeat(server, token) }) result = Result.RETRY
        }
        return result
    }

    /** Runs [block] with a device token for [server]; false if it should be retried later. */
    private suspend fun perServer(server: String, block: suspend (String) -> Unit): Boolean = try {
        sessions.withToken(server, block)
        true
    } catch (e: SharingApiException) {
        if (e.isTemporary) {
            logger.info("Sharing server $server not available (${e.status}), retrying later")
            false
        } else {
            logger.error("Sharing server $server failed: ${e.status} ${e.message}")
            true
        }
    }

    private suspend fun syncShare(server: String, token: String, share: Share) {
        val pending = store.pending(share.id)
        try {
            val habit = habitList.getByUUID(share.habitUuid)
            if (share.ended || habit == null || pending.any { it.kind == OutboxItem.Kind.END }) {
                api.endShare(server, token, share.shareId)
                store.delete(share.id)
                return
            }
            pending.firstOrNull { it.kind == OutboxItem.Kind.META }?.let { item ->
                sendMeta(server, token, share, habit)
                store.markSent(item)
            }
            val entries = pending.filter { it.kind == OutboxItem.Kind.ENTRY && it.date!! >= share.historyStart }
            for (chunk in entries.chunked(SharingApi.MAX_ENTRIES_PER_REQUEST)) {
                api.putEntries(server, token, share.shareId, chunk.map { upload(share, habit, it) })
                chunk.forEach { store.markSent(it) }
            }
            // Days before the history start are never sent; forget them.
            pending.filter { it.kind == OutboxItem.Kind.ENTRY && it.date!! < share.historyStart }
                .forEach { store.markSent(it) }
        } catch (e: SharingApiException) {
            when {
                e.isUnauthorized || e.isTemporary -> throw e
                e.isShareGone -> {
                    logger.info("Share ${share.shareId} is gone on $server, removing it")
                    store.delete(share.id)
                }
                else -> {
                    logger.error("Dropping pending changes of share ${share.shareId}: ${e.status} ${e.message}")
                    pending.forEach { store.markSent(it) }
                }
            }
        }
    }

    private suspend fun sendMeta(server: String, token: String, share: Share, habit: Habit) {
        val metadata = SharedHabitMetadata.from(HabitEventFactory.snapshot(habit), share.historyStart)
        api.putMeta(server, token, share.shareId, SharePayloads.sealMetadata(share.key, share.shareId, metadata))
    }

    private suspend fun upload(share: Share, habit: Habit, item: OutboxItem): EntryUpload {
        val date = item.date!!
        val type = if (habit.isNumerical) HabitEvent.Type.NUMERICAL else HabitEvent.Type.YES_NO
        val entry = SharedEntry.fromEntryValue(habit.originalEntries.get(date).value, type)
        return EntryUpload(
            date = date,
            data = SharePayloads.sealEntry(share.key, share.shareId, date, entry),
            updatedAt = item.updatedAt,
            deviceId = deviceIdentity.deviceId
        )
    }
}
