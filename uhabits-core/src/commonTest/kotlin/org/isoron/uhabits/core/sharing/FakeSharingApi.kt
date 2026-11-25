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

/** Records calls and fails on demand, see [failNext]. */
class FakeSharingApi : SharingApi {
    val calls = mutableListOf<String>()
    val metas = mutableMapOf<String, String>()
    val entries = mutableMapOf<String, MutableList<EntryUpload>>()
    var registrations = 0
    var apiVersions = listOf("v1")
    val readers = mutableMapOf<String, MutableList<Reader>>()
    private var shareCount = 0

    /** Content returned by [readShare] per share id; tests fill it in. */
    val shareContents = mutableMapOf<String, ShareContent>()
    val sinceRequests = mutableListOf<Long>()
    val left = mutableListOf<String>()
    private var inviteCount = 0
    private val failures = ArrayDeque<Pair<String, Int?>>()

    /** The next call named [call] fails with [status] (null: no connection). */
    fun failNext(call: String, status: Int?) {
        failures.addLast(call to status)
    }

    private fun record(call: String) {
        val failure = failures.firstOrNull { it.first == call }
        if (failure != null) {
            failures.remove(failure)
            calls.add("$call!${failure.second}")
            throw SharingApiException(failure.second, "fake failure")
        }
        calls.add(call)
    }

    override suspend fun discover(serverUrl: String): ServerInfo {
        record("discover")
        return ServerInfo(apiVersions, "1.0.1")
    }

    override suspend fun createShare(serverUrl: String, deviceToken: String, meta: String): String {
        record("createShare")
        val id = "share-${++shareCount}"
        metas[id] = meta
        return id
    }

    override suspend fun createInvite(serverUrl: String, deviceToken: String, shareId: String): Invite {
        record("createInvite")
        val code = "code${++inviteCount}"
        return Invite(code, "${serverUrl.trimEnd('/')}/invite/$code", "2026-12-01T10:00:00.000Z")
    }

    override suspend fun listReaders(serverUrl: String, deviceToken: String, shareId: String): List<Reader> {
        record("listReaders")
        return readers[shareId].orEmpty()
    }

    override suspend fun removeReader(serverUrl: String, deviceToken: String, shareId: String, readerId: String) {
        record("removeReader")
        readers[shareId]?.removeAll { it.readerId == readerId }
    }

    override suspend fun redeemInvite(serverUrl: String, code: String, displayName: String?): Redemption {
        record("redeemInvite")
        return Redemption("share-of-$code", "reader-$code", "rt_$code", "active")
    }

    override suspend fun readShare(serverUrl: String, readerToken: String, shareId: String, since: Long): ShareContent {
        record("readShare")
        sinceRequests.add(since)
        val content = shareContents[shareId] ?: throw SharingApiException(404, "Share not found")
        return content.copy(entries = content.entries.filter { it.revision > since })
    }

    override suspend fun leaveShare(serverUrl: String, readerToken: String) {
        record("leaveShare")
        left.add(readerToken)
    }

    override suspend fun createOwner(serverUrl: String, identity: OwnerIdentity) = record("createOwner")

    override suspend fun registerDevice(serverUrl: String, identity: OwnerIdentity, deviceId: String): String {
        record("registerDevice")
        registrations++
        return "dt_$registrations"
    }

    override suspend fun putMeta(serverUrl: String, deviceToken: String, shareId: String, meta: String) {
        record("putMeta")
        metas[shareId] = meta
    }

    override suspend fun putEntries(serverUrl: String, deviceToken: String, shareId: String, entries: List<EntryUpload>) {
        record("putEntries")
        this.entries.getOrPut(shareId) { mutableListOf() }.addAll(entries)
    }

    override suspend fun endShare(serverUrl: String, deviceToken: String, shareId: String) = record("endShare")

    override suspend fun heartbeat(serverUrl: String, deviceToken: String) = record("heartbeat:$serverUrl")
}

class MemoryDeviceTokenStore : DeviceTokenStore {
    val tokens = mutableMapOf<String, String>()
    override fun get(serverUrl: String) = tokens[serverUrl]
    override fun put(serverUrl: String, token: String) {
        tokens[serverUrl] = token
    }
    override fun remove(serverUrl: String) {
        tokens.remove(serverUrl)
    }
}
