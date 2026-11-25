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
package org.isoron.uhabits.sharing

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.core.sharing.FriendsService
import org.isoron.uhabits.core.sharing.ShareSyncer
import java.util.concurrent.TimeUnit

/** Schedules sending the sharing outbox and the weekly heartbeat. */
object ShareSyncWork {
    private const val SYNC = "share-sync"
    private const val HEARTBEAT = "share-heartbeat"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Sends the outbox as soon as there is a connection; retries with backoff. */
    fun scheduleSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<ShareSyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Append, so that a change made while a sync is running is sent afterwards.
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Keeps the owner's shares from becoming inactive on the server (see uhabits-server#3). */
    fun scheduleHeartbeat(context: Context) {
        val request = PeriodicWorkRequestBuilder<ShareHeartbeatWorker>(7, TimeUnit.DAYS)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(HEARTBEAT, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Keeps friends' habits up to date: every 6 hours, and once right away. */
fun ShareSyncWork.scheduleFriendsSync(context: Context) {
    val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val manager = WorkManager.getInstance(context)
    manager.enqueueUniquePeriodicWork(
        "friends-sync",
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<FriendsSyncWorker>(6, TimeUnit.HOURS).setConstraints(online).build()
    )
    manager.enqueueUniqueWork(
        "friends-sync-now",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<FriendsSyncWorker>().setConstraints(online).build()
    )
}

class FriendsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        when (HabitsApplication.component.friendsService.sync()) {
            FriendsService.Result.DONE -> Result.success()
            FriendsService.Result.RETRY -> Result.retry()
        }
    } catch (e: Exception) {
        Log.e("ShareSyncWork", "Syncing friends' habits failed", e)
        Result.retry()
    }
}

class ShareSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runSharing("sync") { it.sync() }
}

class ShareHeartbeatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runSharing("heartbeat") { it.heartbeat() }
}

private suspend fun runSharing(
    name: String,
    block: suspend (ShareSyncer) -> ShareSyncer.Result
): ListenableWorker.Result = try {
    when (block(HabitsApplication.component.shareSyncer)) {
        ShareSyncer.Result.DONE -> ListenableWorker.Result.success()
        ShareSyncer.Result.RETRY -> ListenableWorker.Result.retry()
    }
} catch (e: Exception) {
    Log.e("ShareSyncWork", "Sharing $name failed", e)
    ListenableWorker.Result.retry()
}
