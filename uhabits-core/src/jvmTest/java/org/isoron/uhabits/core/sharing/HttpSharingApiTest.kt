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

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.isoron.platform.time.LocalDate
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpSharingApiTest {
    private data class Request(val method: String, val path: String, val auth: String?, val body: String)

    private lateinit var server: HttpServer
    private lateinit var url: String
    private val requests = mutableListOf<Request>()
    private var status = 200
    private var response = "{}"
    private val api = HttpSharingApi()
    private val identity = OwnerIdentity.generate()

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(
                Request(
                    exchange.requestMethod,
                    exchange.requestURI.rawPath,
                    exchange.requestHeaders.getFirst("Authorization"),
                    exchange.requestBody.readBytes().decodeToString()
                )
            )
            val bytes = response.encodeToByteArray()
            if (status == 204) {
                exchange.sendResponseHeaders(204, -1)
            } else {
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        url = "http://127.0.0.1:${server.address.port}/"
    }

    @AfterTest
    fun stop() {
        server.stop(0)
    }

    @Test
    fun testCreateOwner() = runTest {
        status = 201
        api.createOwner(url, identity)
        val r = requests.single()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/owners", r.path)
        assertNull(r.auth)
        assertEquals("{\"ownerId\":\"${identity.ownerId}\",\"ownerSecret\":\"${identity.ownerSecret}\"}", r.body)
    }

    @Test
    fun testRegisterDeviceReturnsToken() = runTest {
        status = 201
        response = "{\"ownerId\":\"${identity.ownerId}\",\"deviceId\":\"d\",\"deviceToken\":\"dt_abc\"}"
        assertEquals("dt_abc", api.registerDevice(url, identity, "d"))
        val r = requests.single()
        assertEquals("/api/v1/owners/${identity.ownerId}/devices", r.path)
        assertEquals("{\"ownerSecret\":\"${identity.ownerSecret}\",\"deviceId\":\"d\"}", r.body)
    }

    @Test
    fun testPutEntries() = runTest {
        api.putEntries(
            url,
            "dt_abc",
            "s1",
            listOf(EntryUpload(LocalDate(2026, 11, 24), "v1.AAAA", 1795538234066, "d"))
        )
        val r = requests.single()
        assertEquals("PUT", r.method)
        assertEquals("/api/v1/shares/s1/entries", r.path)
        assertEquals("Bearer dt_abc", r.auth)
        assertEquals(
            "{\"entries\":[{\"date\":\"2026-11-24\",\"data\":\"v1.AAAA\"," +
                "\"updatedAt\":\"2026-11-24T16:37:14.066Z\",\"deviceId\":\"d\"}]}",
            r.body
        )
    }

    @Test
    fun testPutMetaEndShareAndHeartbeat() = runTest {
        api.putMeta(url, "dt", "s1", "v1.BBBB")
        status = 204
        api.endShare(url, "dt", "s1")
        status = 200
        api.heartbeat(url, "dt")
        assertEquals(
            listOf("PUT /api/v1/shares/s1/meta", "DELETE /api/v1/shares/s1", "POST /api/v1/heartbeat"),
            requests.map { "${it.method} ${it.path}" }
        )
        assertEquals("{\"meta\":\"v1.BBBB\"}", requests[0].body)
        assertTrue(requests.all { it.auth == "Bearer dt" })
    }

    @Test
    fun testDiscover() = runTest {
        response = "{\"apiVersions\":[\"v1\"],\"version\":\"1.0.1\"}"
        assertEquals(ServerInfo(listOf("v1"), "1.0.1"), api.discover(url))
        assertEquals("GET /.well-known/uhabits-server", requests.single().let { "${it.method} ${it.path}" })
    }

    @Test
    fun testCreateShareAndInvite() = runTest {
        status = 201
        response = "{\"shareId\":\"s9\",\"status\":\"active\",\"revision\":1}"
        assertEquals("s9", api.createShare(url, "dt", "v1.META"))
        assertEquals("{\"meta\":\"v1.META\"}", requests.last().body)
        response = "{\"code\":\"c1\",\"url\":\"https://h/invite/c1\",\"expiresAt\":\"2026-12-01T10:00:00.000Z\"}"
        assertEquals(Invite("c1", "https://h/invite/c1", "2026-12-01T10:00:00.000Z"), api.createInvite(url, "dt", "s9"))
        assertEquals("POST /api/v1/shares/s9/invites", requests.last().let { "${it.method} ${it.path}" })
    }

    @Test
    fun testReaders() = runTest {
        response = "{\"readers\":[{\"readerId\":\"r1\",\"displayName\":\"Anna\",\"createdAt\":\"x\"," +
            "\"lastSeenAt\":\"2026-11-24T07:12:00.000Z\"},{\"readerId\":\"r2\",\"displayName\":null," +
            "\"createdAt\":\"x\",\"lastSeenAt\":\"2026-11-24T07:12:00.000Z\"}]}"
        assertEquals(
            listOf(Reader("r1", "Anna", "2026-11-24T07:12:00.000Z"), Reader("r2", null, "2026-11-24T07:12:00.000Z")),
            api.listReaders(url, "dt", "s9")
        )
        status = 204
        api.removeReader(url, "dt", "s9", "r1")
        assertEquals("DELETE /api/v1/shares/s9/readers/r1", requests.last().let { "${it.method} ${it.path}" })
    }

    @Test
    fun testUnexpectedResponse() = runTest {
        response = "<html>proxy error</html>"
        assertFailsWith<SharingApiException> { api.discover(url) }
    }

    @Test
    fun testServerBelowSubPath() = runTest {
        api.heartbeat(url + "habits", "dt")
        assertEquals("/habits/api/v1/heartbeat", requests.single().path)
    }

    @Test
    fun testHttpErrorsCarryStatusAndMessage() = runTest {
        status = 410
        response = "{\"error\":\"Share has ended\"}"
        val e = assertFailsWith<SharingApiException> { api.putMeta(url, "dt", "s1", "v1.X") }
        assertEquals(410, e.status)
        assertTrue(e.isShareGone)
        assertTrue(e.message!!.contains("Share has ended"))
    }

    @Test
    fun testUnreachableServerIsTemporary() = runTest {
        server.stop(0)
        val e = assertFailsWith<SharingApiException> { api.heartbeat(url, "dt") }
        assertNull(e.status)
        assertTrue(e.isTemporary)
    }

    @Test
    fun testInvalidServerUrl() = runTest {
        val e = assertFailsWith<SharingApiException> { api.heartbeat("not a url", "dt") }
        assertNull(e.status)
    }
}
