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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.sharing.SharingApiException.Kind
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.Instant
import javax.net.ssl.SSLException

/**
 * [SharingApi] over HTTP(S) with [HttpURLConnection], for the JVM and Android.
 * Talks to uhabits-server API v1 (see its `/docs`).
 */
class HttpSharingApi(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000
) : SharingApi {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun discover(serverUrl: String): ServerInfo {
        val response = request(serverUrl, "GET", "/.well-known/uhabits-server")
        val info = decode(serverUrl) { json.decodeFromString(Discovery.serializer(), response) }
        return ServerInfo(info.apiVersions, info.version)
    }

    override suspend fun createShare(serverUrl: String, deviceToken: String, meta: String): String {
        val response = request(
            serverUrl,
            "POST",
            "/api/v1/shares",
            token = deviceToken,
            body = json.encodeToString(Meta.serializer(), Meta(meta))
        )
        return decode(serverUrl) { json.decodeFromString(CreatedShare.serializer(), response).shareId }
    }

    override suspend fun createInvite(serverUrl: String, deviceToken: String, shareId: String): Invite {
        val response = request(serverUrl, "POST", "/api/v1/shares/${path(shareId)}/invites", token = deviceToken)
        val invite = decode(serverUrl) { json.decodeFromString(InviteResponse.serializer(), response) }
        return Invite(invite.code, invite.url, invite.expiresAt)
    }

    override suspend fun listReaders(serverUrl: String, deviceToken: String, shareId: String): List<Reader> {
        val response = request(serverUrl, "GET", "/api/v1/shares/${path(shareId)}/readers", token = deviceToken)
        val readers = decode(serverUrl) { json.decodeFromString(Readers.serializer(), response).readers }
        return readers.map { Reader(it.readerId, it.displayName, it.lastSeenAt) }
    }

    override suspend fun removeReader(serverUrl: String, deviceToken: String, shareId: String, readerId: String) {
        request(
            serverUrl,
            "DELETE",
            "/api/v1/shares/${path(shareId)}/readers/${path(readerId)}",
            token = deviceToken
        )
    }

    override suspend fun redeemInvite(serverUrl: String, code: String, displayName: String?): Redemption {
        val response = request(
            serverUrl,
            "POST",
            "/api/v1/invites/${path(code)}/redeem",
            body = json.encodeToString(Redeem.serializer(), Redeem(displayName))
        )
        val r = decode(serverUrl) { json.decodeFromString(Redeemed.serializer(), response) }
        return Redemption(r.shareId, r.readerId, r.readerToken, r.status)
    }

    override suspend fun readShare(serverUrl: String, readerToken: String, shareId: String, since: Long): ShareContent {
        val response = request(serverUrl, "GET", "/api/v1/shares/${path(shareId)}?since=$since", token = readerToken)
        val share = decode(serverUrl) { json.decodeFromString(ShareResponse.serializer(), response) }
        val entries = share.entries.map {
            val date = decode(serverUrl) { parseDate(it.date) }
            ReadEntry(date, it.data, it.revision)
        }
        return ShareContent(share.status, share.meta, entries, share.revision)
    }

    override suspend fun leaveShare(serverUrl: String, readerToken: String) {
        request(serverUrl, "DELETE", "/api/v1/subscriptions/self", token = readerToken)
    }

    override suspend fun createOwner(serverUrl: String, identity: OwnerIdentity) {
        request(
            serverUrl,
            "POST",
            "/api/v1/owners",
            body = json.encodeToString(CreateOwner.serializer(), CreateOwner(identity.ownerId, identity.ownerSecret))
        )
    }

    override suspend fun registerDevice(serverUrl: String, identity: OwnerIdentity, deviceId: String): String {
        val response = request(
            serverUrl,
            "POST",
            "/api/v1/owners/${path(identity.ownerId)}/devices",
            body = json.encodeToString(RegisterDevice.serializer(), RegisterDevice(identity.ownerSecret, deviceId))
        )
        return decode(serverUrl) { json.decodeFromString(DeviceToken.serializer(), response).deviceToken }
    }

    override suspend fun putMeta(serverUrl: String, deviceToken: String, shareId: String, meta: String) {
        request(
            serverUrl,
            "PUT",
            "/api/v1/shares/${path(shareId)}/meta",
            token = deviceToken,
            body = json.encodeToString(Meta.serializer(), Meta(meta))
        )
    }

    override suspend fun putEntries(
        serverUrl: String,
        deviceToken: String,
        shareId: String,
        entries: List<EntryUpload>
    ) {
        val body = Entries(
            entries.map {
                Entry(
                    date = it.date.toCSVString(),
                    data = it.data,
                    updatedAt = Instant.ofEpochMilli(it.updatedAt).toString(),
                    deviceId = it.deviceId
                )
            }
        )
        request(
            serverUrl,
            "PUT",
            "/api/v1/shares/${path(shareId)}/entries",
            token = deviceToken,
            body = json.encodeToString(Entries.serializer(), body)
        )
    }

    override suspend fun endShare(serverUrl: String, deviceToken: String, shareId: String) {
        request(serverUrl, "DELETE", "/api/v1/shares/${path(shareId)}", token = deviceToken)
    }

    override suspend fun heartbeat(serverUrl: String, deviceToken: String) {
        request(serverUrl, "POST", "/api/v1/heartbeat", token = deviceToken)
    }

    /** Sends the request and returns the response body; throws on anything but 2xx. */
    private suspend fun request(
        serverUrl: String,
        method: String,
        path: String,
        token: String? = null,
        body: String? = null
    ): String = withContext(Dispatchers.IO) {
        val url = try {
            URI(serverUrl.trimEnd('/') + path).toURL()
        } catch (e: Exception) {
            throw SharingApiException(null, "Invalid server URL", e, Kind.INVALID_URL)
        }
        val connection = try {
            url.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw SharingApiException(null, "Cannot connect to ${url.host}", e, kindOf(e))
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.encodeToByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().decodeToString() } ?: ""
            if (status !in 200..299) {
                throw SharingApiException(status, "$method $path: $status ${errorMessage(text)}")
            }
            text
        } catch (e: IOException) {
            throw SharingApiException(null, "$method $path failed: ${e.message}", e, kindOf(e))
        } catch (e: SecurityException) {
            // E.g. network access denied to the app.
            throw SharingApiException(null, "$method $path failed: ${e.message}", e, Kind.CONNECTION)
        } finally {
            connection.disconnect()
        }
    }

    private inline fun <T> decode(serverUrl: String, block: () -> T): T = try {
        block()
    } catch (e: IllegalArgumentException) {
        throw SharingApiException(null, "Unexpected response from $serverUrl", e, Kind.INVALID_RESPONSE)
    }

    private fun kindOf(e: IOException): Kind = when (e) {
        is UnknownHostException -> Kind.DNS
        is SSLException -> Kind.TLS
        is SocketTimeoutException -> Kind.TIMEOUT
        else -> Kind.CONNECTION
    }

    private fun errorMessage(body: String): String = try {
        json.decodeFromString(Error.serializer(), body).error
    } catch (e: IllegalArgumentException) {
        ""
    }

    private fun parseDate(s: String): LocalDate {
        val parts = s.split("-").map { it.toInt() }
        require(parts.size == 3) { "invalid date $s" }
        return LocalDate(parts[0], parts[1], parts[2])
    }

    private fun path(segment: String): String = URLEncoder.encode(segment, "UTF-8")

    @Serializable
    private data class CreateOwner(val ownerId: String, val ownerSecret: String)

    @Serializable
    private data class RegisterDevice(val ownerSecret: String, val deviceId: String)

    @Serializable
    private data class DeviceToken(val deviceToken: String)

    @Serializable
    private data class Meta(val meta: String)

    @Serializable
    private data class Entry(val date: String, val data: String, val updatedAt: String, val deviceId: String)

    @Serializable
    private data class Entries(val entries: List<Entry>)

    @Serializable
    private data class Discovery(val apiVersions: List<String>, val version: String = "")

    @Serializable
    private data class CreatedShare(val shareId: String)

    @Serializable
    private data class InviteResponse(val code: String, val url: String, val expiresAt: String)

    @Serializable
    private data class ReaderResponse(val readerId: String, val displayName: String? = null, val lastSeenAt: String)

    @Serializable
    private data class Readers(val readers: List<ReaderResponse>)

    @Serializable
    private data class Redeem(val displayName: String? = null)

    @Serializable
    private data class Redeemed(val shareId: String, val readerId: String, val readerToken: String, val status: String)

    @Serializable
    private data class ReadEntryResponse(val date: String, val data: String, val revision: Long)

    @Serializable
    private data class ShareResponse(
        val status: String,
        val meta: String? = null,
        val entries: List<ReadEntryResponse> = emptyList(),
        val revision: Long
    )

    @Serializable
    private data class Error(val error: String = "")
}
