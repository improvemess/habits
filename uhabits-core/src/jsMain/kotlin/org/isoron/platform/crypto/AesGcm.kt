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
package org.isoron.platform.crypto

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.Promise

actual object AesGcm {
    actual suspend fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cryptoKey = importKey(key, "encrypt")
        val result: Promise<ArrayBuffer> =
            subtle().encrypt(params(nonce, aad), cryptoKey, plaintext.toBufferSource())
        return result.await().toByteArray()
    }

    actual suspend fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cryptoKey = importKey(key, "decrypt")
        return try {
            val result: Promise<ArrayBuffer> =
                subtle().decrypt(params(nonce, aad), cryptoKey, ciphertext.toBufferSource())
            result.await().toByteArray()
        } catch (e: Throwable) {
            throw AesGcmException("Decryption failed", e)
        }
    }

    private fun subtle(): dynamic = js("globalThis.crypto.subtle")

    private suspend fun importKey(key: ByteArray, usage: String): dynamic {
        val algorithm: dynamic = js("({})")
        algorithm.name = "AES-GCM"
        val result: Promise<dynamic> =
            subtle().importKey("raw", key.toBufferSource(), algorithm, false, arrayOf(usage))
        return result.await()
    }

    private fun params(nonce: ByteArray, aad: ByteArray): dynamic {
        val params: dynamic = js("({})")
        params.name = "AES-GCM"
        params.iv = nonce.toBufferSource()
        params.additionalData = aad.toBufferSource()
        params.tagLength = 128
        return params
    }

    /** Kotlin/JS represents a ByteArray as an Int8Array, which WebCrypto accepts. */
    private fun ByteArray.toBufferSource(): Int8Array = unsafeCast<Int8Array>()

    private fun ArrayBuffer.toByteArray(): ByteArray = Int8Array(this).unsafeCast<ByteArray>()
}
