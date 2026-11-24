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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareKeyTest {
    @Test
    fun testEncodeDecodeRoundTrip() {
        val key = ShareKey.generate()
        assertEquals(key, ShareKey.decodeOrNull(key.encode()))
    }

    @Test
    fun testEncodingIsUrlSafeWithoutPadding() {
        val encoded = ShareKey(ByteArray(32) { it.toByte() }).encode()
        assertEquals("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8", encoded)
        repeat(50) {
            val e = ShareKey.generate().encode()
            assertEquals(43, e.length)
            assertTrue(e.all { it.isLetterOrDigit() || it == '-' || it == '_' }, e)
        }
    }

    @Test
    fun testDecodeRejectsInvalidInput() {
        assertNull(ShareKey.decodeOrNull(""))
        assertNull(ShareKey.decodeOrNull("not base64 !"))
        assertNull(ShareKey.decodeOrNull("AAECAwQF")) // too short
    }

    @Test
    fun testRejectsWrongLength() {
        assertFailsWith<IllegalArgumentException> { ShareKey(ByteArray(16)) }
    }

    @Test
    fun testGeneratedKeysDiffer() {
        assertNotEquals(ShareKey.generate(), ShareKey.generate())
    }

    @Test
    fun testToStringDoesNotContainKey() {
        val key = ShareKey.generate()
        assertFalse(key.toString().contains(key.encode()))
    }

    @Test
    fun testKeyIsCopiedOnConstruction() {
        val bytes = ByteArray(32)
        val key = ShareKey(bytes)
        bytes[0] = 1
        assertEquals(ShareKey(ByteArray(32)), key)
    }
}
