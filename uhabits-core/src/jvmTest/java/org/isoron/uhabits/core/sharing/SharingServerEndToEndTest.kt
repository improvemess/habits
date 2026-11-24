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
        habit.originalEntries.add(Entry(start, Entry.YES_MANUAL))
        habit.originalEntries.add(Entry(start.plus(1), Entry.SKIP))

        // Registering happens inside the syncer; creating the share is the job of the
        // share flow (#34), so this test does it by hand with the device token.
        assertEquals(ShareSyncer.Result.DONE, syncer.heartbeat()) // no shares yet: no calls
        val identity = identityStore.getOrCreate()
        api.createOwner(url, identity)
        val token = api.registerDevice(url, identity, DeviceIdentity(devices).deviceId)
        tokens.put(url, token)
        val key = ShareKey.generate()
        val placeholder = ShareCipher.sealText(key, "{}", ShareContext.metadata("pending"))
        val shareId = post(url, "/api/v1/shares", token, "{\"meta\":\"$placeholder\"}")["shareId"]!!.jsonPrimitive.content

        val share = store.insert(shareId, habit.uuid!!, url, key, start, 0)
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.META, null, 1))
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.ENTRY, start, 2))
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.ENTRY, start.plus(1), 3))
        assertEquals(ShareSyncer.Result.DONE, syncer.sync())
        assertEquals(ShareSyncer.Result.DONE, syncer.heartbeat())

        val read = get(url, "/api/v1/shares/$shareId", token)
        val meta = SharePayloads.openMetadata(key, shareId, read["meta"]!!.jsonPrimitive.content)
        assertEquals(habit.name, meta.name)
        val entries = read["entries"]!!.jsonArray.associate {
            val e = it.jsonObject
            val (y, m, d) = e["date"]!!.jsonPrimitive.content.split("-").map { part -> part.toInt() }
            val date = LocalDate(y, m, d)
            date to SharePayloads.openEntry(key, shareId, date, e["data"]!!.jsonPrimitive.content).state
        }
        assertEquals(mapOf(start to SharedEntry.State.YES, start.plus(1) to SharedEntry.State.SKIP), entries)

        store.markEnded(share.id)
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.END, null, 4))
        assertEquals(ShareSyncer.Result.DONE, syncer.sync())
        assertEquals("ended", get(url, "/api/v1/shares/$shareId", token)["status"]!!.jsonPrimitive.content)
        db.close()
    }

    private fun post(url: String, path: String, token: String, body: String): JsonObject =
        call(url, "POST", path, token, body)

    private fun get(url: String, path: String, token: String): JsonObject = call(url, "GET", path, token, null)

    private fun call(url: String, method: String, path: String, token: String, body: String?): JsonObject {
        val c = URI(url.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $token")
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
