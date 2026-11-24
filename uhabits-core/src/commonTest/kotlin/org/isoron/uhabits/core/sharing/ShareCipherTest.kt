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
import org.isoron.platform.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ShareCipherTest {
    private val key = ShareKey.decodeOrNull("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8")!!
    private val day = LocalDate(2026, 11, 24)
    private val entry = ShareContext.entry("share-1", day)
    private val meta = ShareContext.metadata("share-1")

    @Test
    fun testOpensPayloadSealedByReferenceImplementation() = runTest {
        // Sealed with Python's `cryptography` package, nonce 0x64..0x6f.
        val sealed = "v1.ZGVmZ2hpamtsbW5vMzmtEhidM7wEQAatiUcXHAwFBpyee1zycXEIv-c16g"
        assertEquals("{\"state\":\"YES\"}", ShareCipher.openText(key, sealed, entry))
        val sealedMeta = "v1.ZGVmZ2hpamtsbW5vBHqrAByHdm6h7dyaEYY_zLnAgVQHaHIkqJdQ"
        assertEquals("Laufen 🏃", ShareCipher.openText(key, sealedMeta, meta))
    }

    @Test
    fun testRoundTrip() = runTest {
        val sealed = ShareCipher.sealText(key, "Laufen 🏃", meta)
        assertTrue(sealed.startsWith("v1."))
        assertEquals("Laufen 🏃", ShareCipher.openText(key, sealed, meta))
    }

    @Test
    fun testEverySealUsesFreshNonce() = runTest {
        assertNotEquals(
            ShareCipher.sealText(key, "same", meta),
            ShareCipher.sealText(key, "same", meta)
        )
    }

    @Test
    fun testWrongKeyFails() = runTest {
        val sealed = ShareCipher.sealText(key, "secret", meta)
        assertFailsWith<ShareDecryptionException> {
            ShareCipher.open(ShareKey.generate(), sealed, meta)
        }
    }

    @Test
    fun testPayloadCannotBeMovedToAnotherShareOrDate() = runTest {
        val sealed = ShareCipher.sealText(key, "YES", entry)
        assertFailsWith<ShareDecryptionException> {
            ShareCipher.open(key, sealed, ShareContext.entry("share-2", day))
        }
        assertFailsWith<ShareDecryptionException> {
            ShareCipher.open(key, sealed, ShareContext.entry("share-1", day.plus(1)))
        }
        assertFailsWith<ShareDecryptionException> {
            ShareCipher.open(key, sealed, meta)
        }
    }

    @Test
    fun testTamperedPayloadFails() = runTest {
        val sealed = ShareCipher.sealText(key, "secret", meta)
        val last = sealed.last()
        val tampered = sealed.dropLast(1) + (if (last == 'A') 'B' else 'A')
        assertFailsWith<ShareDecryptionException> { ShareCipher.open(key, tampered, meta) }
    }

    @Test
    fun testRejectsUnknownVersionAndMalformedInput() = runTest {
        val sealed = ShareCipher.sealText(key, "secret", meta)
        assertFailsWith<ShareDecryptionException> {
            ShareCipher.open(key, "v2." + sealed.removePrefix("v1."), meta)
        }
        assertFailsWith<ShareDecryptionException> { ShareCipher.open(key, "v1.!!!", meta) }
        assertFailsWith<ShareDecryptionException> { ShareCipher.open(key, "v1.AAAA", meta) }
        assertFailsWith<ShareDecryptionException> { ShareCipher.open(key, "", meta) }
    }
}
