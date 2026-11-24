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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DeviceIdentityTest {
    private class MemoryDeviceIdStorage(var value: String? = null) : DeviceIdStorage {
        var stores = 0
        override fun load(): String? = value
        override fun store(deviceId: String) {
            value = deviceId
            stores++
        }
    }

    @Test
    fun testGeneratesAndStoresOnFirstAccess() {
        val storage = MemoryDeviceIdStorage()
        val id = DeviceIdentity(storage).deviceId
        assertTrue(isUuidV4(id))
        assertEquals(id, storage.value)
        assertEquals(1, storage.stores)
    }

    @Test
    fun testKeepsStoredId() {
        val storage = MemoryDeviceIdStorage()
        val first = DeviceIdentity(storage).deviceId
        val second = DeviceIdentity(storage).deviceId
        assertEquals(first, second)
        assertEquals(1, storage.stores)
    }

    @Test
    fun testReplacesMalformedId() {
        val storage = MemoryDeviceIdStorage("garbage")
        val id = DeviceIdentity(storage).deviceId
        assertTrue(isUuidV4(id))
        assertNotEquals("garbage", id)
    }

    @Test
    fun testDevicesGetDifferentIds() {
        val a = DeviceIdentity(MemoryDeviceIdStorage()).deviceId
        val b = DeviceIdentity(MemoryDeviceIdStorage()).deviceId
        assertNotEquals(a, b)
    }
}
