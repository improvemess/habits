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
import org.isoron.uhabits.core.io.Logging
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.core.models.NumericalHabitType
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.memory.MemoryModelFactory

/** Result of [FriendsService.accept]. */
sealed class AcceptResult {
    data class Accepted(val friendShare: FriendShare) : AcceptResult()
    object InvalidLink : AcceptResult()

    /** `http` link in a release build. */
    object InsecureServer : AcceptResult()
    object Unreachable : AcceptResult()
    object Incompatible : AcceptResult()

    /** Already redeemed, expired, or the share has ended. */
    object InviteGone : AcceptResult()
}

/**
 * The friend's side of sharing: accept invites, keep followed habits up to
 * date, stop following. Followed habits are read-only by construction: they
 * live in [FriendStore], never in the own habit list, and no command can
 * change them.
 */
class FriendsService(
    private val store: FriendStore,
    private val api: SharingApi,
    private val now: () -> Long,
    logging: Logging
) {
    enum class Result { DONE, RETRY }

    private val logger = logging.getLogger("FriendsService")

    fun friendShares(): List<FriendShare> = store.findAll()

    /**
     * Redeems the invite in [link] and fetches the habit. [displayName] is
     * what the owner sees in their list of friends.
     */
    suspend fun accept(link: String, displayName: String?, allowInsecure: Boolean = false): AcceptResult {
        val invite = InviteLinks.parse(link) ?: return AcceptResult.InvalidLink
        val serverUrl = SharingServers.normalize(invite.serverUrl, allowInsecure) ?: return AcceptResult.InsecureServer
        try {
            if ("v1" !in api.discover(serverUrl).apiVersions) return AcceptResult.Incompatible
        } catch (e: SharingApiException) {
            return if (e.isTemporary) AcceptResult.Unreachable else AcceptResult.Incompatible
        }
        val redemption = try {
            api.redeemInvite(serverUrl, invite.code, displayName?.trim()?.takeIf { it.isNotEmpty() })
        } catch (e: SharingApiException) {
            return when {
                e.isTemporary -> AcceptResult.Unreachable
                e.status == 404 || e.status == 410 -> AcceptResult.InviteGone
                else -> AcceptResult.Incompatible
            }
        }
        val friendShare = store.insert(serverUrl, redemption, invite.key, now())
        syncOne(friendShare)
        return AcceptResult.Accepted(store.findById(friendShare.id)!!)
    }

    /** Fetches what changed for every followed habit. One unreachable server does not stop the others. */
    suspend fun sync(): Result {
        var result = Result.DONE
        for (friendShare in store.findAll()) {
            if (friendShare.status == FriendShare.Status.REMOVED) continue
            if (!syncOne(friendShare)) result = Result.RETRY
        }
        return result
    }

    /** Stops following; tells the server if it can be reached, forgets the habit either way. */
    suspend fun leave(friendShare: FriendShare) {
        try {
            api.leaveShare(friendShare.serverUrl, friendShare.readerToken)
        } catch (e: SharingApiException) {
            logger.info("Could not tell ${friendShare.serverUrl} about leaving: ${e.status}")
        }
        store.delete(friendShare.id)
    }

    /** Returns false if it should be retried later. */
    private suspend fun syncOne(friendShare: FriendShare): Boolean {
        val content = try {
            api.readShare(friendShare.serverUrl, friendShare.readerToken, friendShare.shareId, friendShare.revision)
        } catch (e: SharingApiException) {
            return when {
                e.isTemporary -> false
                e.isUnauthorized -> {
                    store.setStatus(friendShare.id, FriendShare.Status.REMOVED)
                    true
                }
                e.isShareGone -> {
                    store.setStatus(friendShare.id, FriendShare.Status.ENDED)
                    true
                }
                else -> {
                    logger.error("Reading ${friendShare.shareId} failed: ${e.status} ${e.message}")
                    true
                }
            }
        }
        val metadata = content.meta?.let { sealed ->
            try {
                SharePayloads.openMetadata(friendShare.key, friendShare.shareId, sealed)
            } catch (e: Exception) {
                logger.error("Cannot read metadata of ${friendShare.shareId}: ${e.message}")
                null
            }
        } ?: friendShare.metadata
        for (entry in content.entries) {
            try {
                val shared = SharePayloads.openEntry(friendShare.key, friendShare.shareId, entry.date, entry.data)
                val value = shared.toEntryValue()
                store.setEntry(friendShare.id, entry.date, if (value == Entry.UNKNOWN) null else value)
            } catch (e: Exception) {
                logger.error("Skipping unreadable entry ${entry.date} of ${friendShare.shareId}: ${e.message}")
            }
        }
        store.update(friendShare.id, FriendShare.Status.of(content.status), content.revision, metadata)
        return true
    }

    /**
     * Builds a habit for display only, so that history, streak and score are
     * computed exactly like for one's own habits. It belongs to no habit list
     * and is never saved.
     */
    fun habitOf(friendShare: FriendShare): Habit? {
        val meta = friendShare.metadata ?: return null
        val factory = MemoryModelFactory()
        val habit = factory.buildHabit()
        habit.name = meta.name
        habit.description = meta.description
        habit.color = PaletteColor(meta.color)
        habit.frequency = Frequency(meta.frequencyNumerator, meta.frequencyDenominator)
        habit.type = if (meta.habitType == HabitEvent.Type.NUMERICAL) HabitType.NUMERICAL else HabitType.YES_NO
        habit.unit = meta.unit
        meta.target?.let { habit.targetValue = it }
        habit.targetType = when (meta.targetType) {
            HabitEvent.TargetType.AT_MOST -> NumericalHabitType.AT_MOST
            else -> NumericalHabitType.AT_LEAST
        }
        habit.negativeSince = meta.negativeSince
        habit.calendarPeriod = meta.calendarPeriod
        for ((date, value) in store.entries(friendShare.id)) {
            if (date >= meta.historyStart) habit.originalEntries.add(Entry(date, value))
        }
        habit.recompute()
        return habit
    }
}
