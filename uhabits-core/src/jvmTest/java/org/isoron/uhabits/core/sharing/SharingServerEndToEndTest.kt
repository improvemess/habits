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

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.setToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.io.StandardLogging
import org.isoron.uhabits.core.models.Entry
import org.junit.Assume.assumeTrue
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs the owner side of sharing against a real uhabits-server. Skipped unless
 * `UHABITS_SERVER_URL` is set, e.g.:
 *
 *     docker run --rm -p 3000:3000 ghcr.io/oliverlorenz/uhabits-server
 *     UHABITS_SERVER_URL=http://localhost:3000 ./gradlew :uhabits-core:jvmTest
 */
class SharingServerEndToEndTest : BaseUnitTest() {
    private val server: String? = System.getenv("UHABITS_SERVER_URL")

    @Test
    fun testOwnerDeviceSyncsMetaAndEntries() = runTest {
        assumeTrue("UHABITS_SERVER_URL not set", server != null)
        val url = server!!
        val db = buildMemoryDatabase()
        val store = ShareStore(db)
        val tokens = MemoryDeviceTokenStore()
        val identityStore = OwnerIdentityStore(db)
        val devices = object : DeviceIdStorage {
            var id: String? = null
            override fun load() = id
            override fun store(deviceId: String) {
                id = deviceId
            }
        }
        val api = HttpSharingApi()
        val syncer = ShareSyncer(store, habitList, api, tokens, identityStore, DeviceIdentity(devices), StandardLogging())

        val habit = fixtures.createEmptyHabit()
        habitList.add(habit)
        val start = LocalDate(2026, 11, 20)
        setToday(LocalDate(2026, 11, 24))
        habit.originalEntries.add(Entry(start, Entry.YES_MANUAL))
        habit.originalEntries.add(Entry(start.plus(1), Entry.SKIP))

        val sessions = ServerSessions(api, tokens, identityStore, DeviceIdentity(devices))
        val service = SharingService(store, api, sessions, { System.currentTimeMillis() })
        val check = service.checkServer(url, allowInsecure = true)
        check(check is ServerCheck.Ok) { "server check failed: $check" }

        val share = service.share(habit, url, SharedHistory.ALL)
        assertEquals(start, share.historyStart)
        assertEquals(ShareSyncer.Result.DONE, syncer.sync())
        assertEquals(ShareSyncer.Result.DONE, syncer.heartbeat())

        // A friend redeems the invite: the link carries the server and, in the fragment, the key.
        val link = service.createInvite(share)
        val code = link.url.substringAfter("/invite/").substringBefore('#')
        val key = ShareKey.decodeOrNull(link.url.substringAfter("#k=").substringBefore('&'))!!
        assertEquals(share.key, key)
        val redeemed = post(url, "/api/v1/invites/$code/redeem", null, "{\"displayName\":\"Anna\"}")
        val readerToken = redeemed["readerToken"]!!.jsonPrimitive.content
        assertEquals(listOf("Anna"), service.readers(share).map { it.displayName })

        val shareId = share.shareId
        val read = get(url, "/api/v1/shares/$shareId", readerToken)
        val meta = SharePayloads.openMetadata(key, shareId, read["meta"]!!.jsonPrimitive.content)
        assertEquals(habit.name, meta.name)
        assertEquals(start, meta.historyStart)
        val entries = read["entries"]!!.jsonArray.associate {
            val e = it.jsonObject
            val (y, m, d) = e["date"]!!.jsonPrimitive.content.split("-").map { part -> part.toInt() }
            val date = LocalDate(y, m, d)
            date to SharePayloads.openEntry(key, shareId, date, e["data"]!!.jsonPrimitive.content).state
        }
        assertEquals(mapOf(start to SharedEntry.State.YES, start.plus(1) to SharedEntry.State.SKIP), entries)

        service.removeReader(share, redeemed["readerId"]!!.jsonPrimitive.content)
        assertEquals(emptyList(), service.readers(share))

        service.stopSharing(share)
        assertEquals(ShareSyncer.Result.DONE, syncer.sync())
        val token = tokens.get(url)!!
        assertEquals("ended", get(url, "/api/v1/shares/$shareId", token)["status"]!!.jsonPrimitive.content)
        db.close()
    }
    private fun post(url: String, path: String, token: String?, body: String): JsonObject =
        call(url, "POST", path, token, body)

    private fun get(url: String, path: String, token: String): JsonObject = call(url, "GET", path, token, null)

    private fun call(url: String, method: String, path: String, token: String?, body: String?): JsonObject {
        val c = URI(url.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
        c.requestMethod = method
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.encodeToByteArray()) }
        }
        val text = (if (c.responseCode < 300) c.inputStream else c.errorStream).use { it.readBytes().decodeToString() }
        check(c.responseCode < 300) { "$method $path: ${c.responseCode} $text" }
        return Json.parseToJsonElement(text).jsonObject
    }
}
