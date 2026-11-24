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

import org.isoron.platform.crypto.AesGcm
import org.isoron.platform.crypto.AesGcmException
import org.isoron.platform.time.LocalDate
import org.isoron.platform.utils.secureRandomBytes

/**
 * What a sealed payload belongs to. It is bound to the ciphertext as
 * associated data, so that the server cannot move a payload to another share,
 * or an entry to another date, without decryption failing.
 */
class ShareContext private constructor(internal val value: String) {
    override fun equals(other: Any?) = other is ShareContext && other.value == value
    override fun hashCode() = value.hashCode()
    override fun toString() = "ShareContext($value)"

    companion object {
        private const val PREFIX = "uhabits-share:v1"

        /** The habit's metadata (name, color, frequency, …) of share [shareId]. */
        fun metadata(shareId: String) = ShareContext("$PREFIX:meta:$shareId")

        /** The entry of share [shareId] on [date]. */
        fun entry(shareId: String, date: LocalDate) =
            ShareContext("$PREFIX:entry:$shareId:${date.toCSVString()}")
    }
}

class ShareDecryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Encrypts what is shared with friends, so that the sharing server only stores
 * ciphertext.
 *
 * Format of a sealed payload, a string safe for JSON and URLs:
 * `v1.` + Base64url(12-byte random nonce ‖ AES-256-GCM ciphertext with 16-byte tag).
 * The version prefix lets the format change later without breaking old data.
 */
object ShareCipher {
    private const val VERSION_PREFIX = "v1."
    private const val NONCE_BYTES = 12

    suspend fun seal(key: ShareKey, plaintext: ByteArray, context: ShareContext): String {
        val nonce = secureRandomBytes(NONCE_BYTES)
        val ciphertext = AesGcm.encrypt(key.toByteArray(), nonce, plaintext, context.aad())
        return VERSION_PREFIX + ShareKey.BASE64.encode(nonce + ciphertext)
    }

    /**
     * @throws ShareDecryptionException if [sealed] is malformed, of an unknown
     *   version, or was not sealed with [key] for [context].
     */
    suspend fun open(key: ShareKey, sealed: String, context: ShareContext): ByteArray {
        if (!sealed.startsWith(VERSION_PREFIX)) {
            throw ShareDecryptionException("Unknown format: ${sealed.substringBefore('.').take(8)}")
        }
        val bytes = try {
            ShareKey.BASE64.decode(sealed.substring(VERSION_PREFIX.length))
        } catch (e: IllegalArgumentException) {
            throw ShareDecryptionException("Malformed payload", e)
        }
        if (bytes.size < NONCE_BYTES + TAG_BYTES) throw ShareDecryptionException("Payload too short")
        val nonce = bytes.copyOfRange(0, NONCE_BYTES)
        val ciphertext = bytes.copyOfRange(NONCE_BYTES, bytes.size)
        return try {
            AesGcm.decrypt(key.toByteArray(), nonce, ciphertext, context.aad())
        } catch (e: AesGcmException) {
            throw ShareDecryptionException("Wrong key, wrong context or tampered payload", e)
        }
    }

    suspend fun sealText(key: ShareKey, plaintext: String, context: ShareContext): String =
        seal(key, plaintext.encodeToByteArray(), context)

    suspend fun openText(key: ShareKey, sealed: String, context: ShareContext): String =
        open(key, sealed, context).decodeToString()

    private const val TAG_BYTES = 16

    private fun ShareContext.aad(): ByteArray = value.encodeToByteArray()
}
