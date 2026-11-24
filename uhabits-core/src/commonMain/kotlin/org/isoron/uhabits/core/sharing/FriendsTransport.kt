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
import org.isoron.uhabits.core.events.HabitEventTransport
import org.isoron.uhabits.core.events.HabitLifecycleEvent

/**
 * Records changes to shared habits in the outbox of [store]; [ShareSyncer]
 * sends them later. Never talks to the network itself, because
 * [HabitEventTransport] is called on the main thread.
 *
 * - Entry changes on or after a share's history start become
 *   [OutboxItem.Kind.ENTRY] items; earlier days are never shared.
 * - Changes to the habit itself become [OutboxItem.Kind.META] items.
 * - Deleting the habit ends its shares ([OutboxItem.Kind.END]). Archiving
 *   does not: friends keep seeing the habit.
 *
 * @param onOutboxChanged called after something was enqueued, e.g. to
 *   schedule a sync.
 */
class FriendsTransport(
    private val store: ShareStore,
    private val now: () -> Long,
    private val onOutboxChanged: () -> Unit = {}
) : HabitEventTransport {

    override fun send(event: HabitEvent) {
        val shares = store.findActiveByHabit(event.habitUuid).filter { event.date >= it.historyStart }
        if (shares.isEmpty()) return
        val time = now()
        for (share in shares) {
            store.enqueue(OutboxItem(share.id, OutboxItem.Kind.ENTRY, event.date, time))
        }
        onOutboxChanged()
    }

    override fun send(event: HabitLifecycleEvent) {
        val shares = store.findActiveByHabit(event.habitUuid)
        if (shares.isEmpty()) return
        val time = now()
        when (event) {
            is HabitLifecycleEvent.Updated -> for (share in shares) {
                store.enqueue(OutboxItem(share.id, OutboxItem.Kind.META, null, time))
            }
            is HabitLifecycleEvent.Deleted -> for (share in shares) {
                store.markEnded(share.id)
                store.enqueue(OutboxItem(share.id, OutboxItem.Kind.END, null, time))
            }
            is HabitLifecycleEvent.Archived, is HabitLifecycleEvent.Unarchived -> return
        }
        onOutboxChanged()
    }
}
