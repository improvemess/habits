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
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.events.HabitEventFactory
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit

/** Which entries a new share starts with (see #32). */
enum class SharedHistory {
    /** Only from the day of sharing on. The default. */
    FROM_TODAY,

    /** Everything the habit has, from its first entry on. */
    ALL
}

/** Base URLs of sharing servers. */
object SharingServers {
    const val DEFAULT = "https://habits.improvemess.com"

    private val URL = Regex("^(https?)://[^/?#\\s]+(/[^?#\\s]*)?$")

    /**
     * Trims [url] and drops a trailing slash; null if it is no valid server
     * URL. Only `https` is accepted unless [allowInsecure] (debug builds).
     */
    fun normalize(url: String, allowInsecure: Boolean = false): String? {
        val trimmed = url.trim().trimEnd('/')
        val match = URL.matchEntire(trimmed) ?: return null
        if (match.groupValues[1] == "http" && !allowInsecure) return null
        return trimmed
    }
}

/** Result of [SharingService.checkServer]. */
sealed class ServerCheck {
    data class Ok(val serverUrl: String, val info: ServerInfo) : ServerCheck()
    object InvalidUrl : ServerCheck()
    object Unreachable : ServerCheck()

    /** Reachable, but no uhabits-server with API v1. */
    object Incompatible : ServerCheck()
}

/**
 * What the owner does with shares, for the user interface: share a habit,
 * invite friends, see and remove them, stop sharing. Changes to shared habits
 * afterwards are sent by [FriendsTransport] and [ShareSyncer].
 */
class SharingService(
    private val store: ShareStore,
    private val api: SharingApi,
    private val sessions: ServerSessions,
    private val now: () -> Long,
    private val onOutboxChanged: () -> Unit = {}
) {
    suspend fun checkServer(url: String, allowInsecure: Boolean = false): ServerCheck {
        val serverUrl = SharingServers.normalize(url, allowInsecure) ?: return ServerCheck.InvalidUrl
        val info = try {
            api.discover(serverUrl)
        } catch (e: SharingApiException) {
            return if (e.status == null || e.isTemporary) ServerCheck.Unreachable else ServerCheck.Incompatible
        }
        return if (API_VERSION in info.apiVersions) ServerCheck.Ok(serverUrl, info) else ServerCheck.Incompatible
    }

    /** Active shares of [habit], newest last. */
    fun sharesOf(habit: Habit): List<Share> = habit.uuid?.let { store.findActiveByHabit(it) }.orEmpty()

    /**
     * Shares [habit] on [serverUrl] (already checked with [checkServer]).
     * Needs a connection; the entries of [history] are sent in the
     * background afterwards.
     *
     * @throws SharingApiException if the server cannot be reached or refuses.
     */
    suspend fun share(habit: Habit, serverUrl: String, history: SharedHistory): Share {
        val habitUuid = requireNotNull(habit.uuid) { "habit has no uuid" }
        val key = ShareKey.generate()
        val today = getToday()
        val historyStart = when (history) {
            SharedHistory.FROM_TODAY -> today
            SharedHistory.ALL -> firstEntry(habit)?.let { minOf(it, today) } ?: today
        }
        val metadata = SharedHabitMetadata.from(HabitEventFactory.snapshot(habit), historyStart)

        // The metadata is bound to the share id, which only the server assigns: create the
        // share with a placeholder and replace it right away, before anyone can be invited.
        val placeholder = SharePayloads.sealMetadata(key, PENDING_ID, metadata)
        val share = sessions.withToken(serverUrl) { token ->
            val shareId = api.createShare(serverUrl, token, placeholder)
            val share = store.insert(shareId, habitUuid, serverUrl, key, historyStart, now())
            try {
                api.putMeta(serverUrl, token, shareId, SharePayloads.sealMetadata(key, shareId, metadata))
            } catch (e: SharingApiException) {
                store.enqueue(OutboxItem(share.id, OutboxItem.Kind.META, null, now()))
            }
            share
        }

        val time = now()
        for (entry in habit.originalEntries.getKnown()) {
            if (entry.date < historyStart || entry.value == Entry.UNKNOWN) continue
            store.enqueue(OutboxItem(share.id, OutboxItem.Kind.ENTRY, entry.date, time))
        }
        onOutboxChanged()
        return share
    }

    /**
     * Creates a one-time invite and returns the link to send to a friend:
     * the server's invite URL plus the share key in the fragment, which never
     * reaches a server.
     */
    suspend fun createInvite(share: Share): InviteLink {
        val invite = sessions.withToken(share.serverUrl) { api.createInvite(share.serverUrl, it, share.shareId) }
        return InviteLink("${invite.url}#k=${share.key.encode()}&v=1", invite.expiresAt)
    }

    suspend fun readers(share: Share): List<Reader> =
        sessions.withToken(share.serverUrl) { api.listReaders(share.serverUrl, it, share.shareId) }

    suspend fun removeReader(share: Share, readerId: String) {
        sessions.withToken(share.serverUrl) { api.removeReader(share.serverUrl, it, share.shareId, readerId) }
    }

    /** Stops sharing; works offline, the server is told by the next sync. */
    fun stopSharing(share: Share) {
        store.markEnded(share.id)
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.END, null, now()))
        onOutboxChanged()
    }

    private fun firstEntry(habit: Habit): LocalDate? =
        habit.originalEntries.getKnown().filter { it.value != Entry.UNKNOWN }.minOfOrNull { it.date }

    private companion object {
        const val API_VERSION = "v1"
        const val PENDING_ID = "pending"
    }
}

/** A link that lets one friend follow a share. */
data class InviteLink(val url: String, val expiresAt: String)
