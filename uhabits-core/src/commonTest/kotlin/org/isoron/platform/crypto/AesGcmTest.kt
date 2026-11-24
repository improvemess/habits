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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/**
 * The expected ciphertext was produced independently with Python's
 * `cryptography` package (AESGCM), so this checks interoperability with
 * standard AES-256-GCM on every platform, not only a round trip.
 */
class AesGcmTest {
    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(12) { (100 + it).toByte() }
    private val aad = "uhabits-share:v1:entry:share-1:2026-11-24".encodeToByteArray()
    private val plaintext = "{\"state\":\"YES\"}".encodeToByteArray()
    private val expected = hex("3339ad12189d33bc044006ad8947171c0c05069c9e7b5cf2717108bfe735ea")

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

    @Test
    fun testEncryptMatchesReferenceImplementation() = runTest {
        assertContentEquals(expected, AesGcm.encrypt(key, nonce, plaintext, aad))
    }

    @Test
    fun testDecryptReferenceCiphertext() = runTest {
        assertContentEquals(plaintext, AesGcm.decrypt(key, nonce, expected, aad))
    }

    @Test
    fun testDecryptWithWrongKeyFails() = runTest {
        val wrongKey = key.copyOf().also { it[0] = 99 }
        assertFailsWith<AesGcmException> { AesGcm.decrypt(wrongKey, nonce, expected, aad) }
    }

    @Test
    fun testDecryptWithWrongAadFails() = runTest {
        val wrongAad = "uhabits-share:v1:entry:share-1:2026-11-25".encodeToByteArray()
        assertFailsWith<AesGcmException> { AesGcm.decrypt(key, nonce, expected, wrongAad) }
    }

    @Test
    fun testDecryptTamperedCiphertextFails() = runTest {
        val tampered = expected.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFailsWith<AesGcmException> { AesGcm.decrypt(key, nonce, tampered, aad) }
    }
}
