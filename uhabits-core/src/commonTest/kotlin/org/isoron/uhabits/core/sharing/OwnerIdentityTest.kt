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

class OwnerIdentityTest {
    @Test
    fun testGenerateProducesValidValues() {
        val identity = OwnerIdentity.generate()
        assertTrue(isUuidV4(identity.ownerId))
        assertEquals(OwnerIdentity.SECRET_BYTES * 2, identity.ownerSecret.length)
        assertTrue(identity.ownerSecret.all { it in "0123456789abcdef" })
    }

    @Test
    fun testGenerateProducesDifferentIdentities() {
        val a = OwnerIdentity.generate()
        val b = OwnerIdentity.generate()
        assertNotEquals(a.ownerId, b.ownerId)
        assertNotEquals(a.ownerSecret, b.ownerSecret)
    }

    @Test
    fun testToStringDoesNotContainSecret() {
        val identity = OwnerIdentity.generate()
        assertFalse(identity.toString().contains(identity.ownerSecret))
        assertTrue(identity.toString().contains(identity.ownerId))
    }

    @Test
    fun testRejectsMalformedValues() {
        val valid = OwnerIdentity.generate()
        assertFailsWith<IllegalArgumentException> { OwnerIdentity("abc", valid.ownerSecret) }
        assertFailsWith<IllegalArgumentException> { OwnerIdentity(valid.ownerId, "abc") }
        assertFailsWith<IllegalArgumentException> {
            OwnerIdentity(valid.ownerId.uppercase(), valid.ownerSecret)
        }
    }

    @Test
    fun testParseOrNull() {
        val valid = OwnerIdentity.generate()
        assertEquals(valid, OwnerIdentity.parseOrNull(valid.ownerId, valid.ownerSecret))
        assertNull(OwnerIdentity.parseOrNull(null, valid.ownerSecret))
        assertNull(OwnerIdentity.parseOrNull(valid.ownerId, null))
        assertNull(OwnerIdentity.parseOrNull("not-a-uuid", valid.ownerSecret))
        assertNull(OwnerIdentity.parseOrNull(valid.ownerId, "zz"))
    }

    @Test
    fun testRandomUuidV4Format() {
        repeat(100) {
            val uuid = randomUuidV4()
            assertTrue(isUuidV4(uuid), uuid)
        }
        assertFalse(isUuidV4("0f8fad5b-d9cb-169f-a165-70867728950e")) // version 1
        assertFalse(isUuidV4("0f8fad5b-d9cb-469f-c165-70867728950e")) // wrong variant
    }
}
