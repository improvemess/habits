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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.Instant

/**
 * [SharingApi] over HTTP(S) with [HttpURLConnection], for the JVM and Android.
 * Talks to uhabits-server API v1 (see its `/docs`).
 */
class HttpSharingApi(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000
) : SharingApi {

    private val json = Json { ignoreUnknownKeys = true }

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
        return try {
            json.decodeFromString(DeviceToken.serializer(), response).deviceToken
        } catch (e: IllegalArgumentException) {
            throw SharingApiException(null, "Unexpected response from $serverUrl", e)
        }
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
            throw SharingApiException(null, "Invalid server URL", e)
        }
        val connection = try {
            url.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw SharingApiException(null, "Cannot connect to ${url.host}", e)
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
            throw SharingApiException(null, "$method $path failed: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(body: String): String = try {
        json.decodeFromString(Error.serializer(), body).error
    } catch (e: IllegalArgumentException) {
        ""
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
    private data class Error(val error: String = "")
}
