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
import org.isoron.uhabits.core.events.HabitEvent
import org.isoron.uhabits.core.events.HabitSnapshot
import org.isoron.uhabits.core.models.Entry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SharePayloadsTest {
    private val start = LocalDate(2026, 11, 24)

    private val yesNo = SharedHabitMetadata(
        name = "Run",
        description = "",
        category = "",
        color = 3,
        frequencyNumerator = 3,
        frequencyDenominator = 7,
        habitType = HabitEvent.Type.YES_NO,
        unit = "",
        target = null,
        targetType = null,
        historyStart = start
    )

    private val numerical = yesNo.copy(
        name = "Läufen \"weit\"",
        habitType = HabitEvent.Type.NUMERICAL,
        unit = "km",
        target = 5.5,
        targetType = HabitEvent.TargetType.AT_LEAST
    )

    @Test
    fun testMetadataFormatIsStable() {
        assertEquals(
            "{\"v\":1,\"name\":\"Run\",\"description\":\"\"," +
                "\"category\":\"\",\"color\":3,\"freqNum\":3,\"freqDen\":7,\"type\":\"YES_NO\"," +
                "\"unit\":\"\",\"historyStart\":\"2026-11-24\"}",
            SharePayloads.encodeMetadata(yesNo)
        )
    }

    @Test
    fun testEntryFormatIsStable() {
        assertEquals("{\"v\":1,\"state\":\"YES\"}", SharePayloads.encodeEntry(SharedEntry(SharedEntry.State.YES)))
        assertEquals(
            "{\"v\":1,\"state\":\"VALUE\",\"value\":2.5}",
            SharePayloads.encodeEntry(SharedEntry(SharedEntry.State.VALUE, 2.5))
        )
    }

    @Test
    fun testMetadataRoundTrip() {
        assertEquals(yesNo, SharePayloads.decodeMetadata(SharePayloads.encodeMetadata(yesNo)))
        assertEquals(numerical, SharePayloads.decodeMetadata(SharePayloads.encodeMetadata(numerical)))
    }

    @Test
    fun testOptionalFieldsOfNewerHabitFeatures() {
        val negative = yesNo.copy(negativeSince = LocalDate(2026, 11, 1), calendarPeriod = true)
        val encoded = SharePayloads.encodeMetadata(negative)
        assertTrue(encoded.contains("\"negativeSince\":\"2026-11-01\""), encoded)
        assertTrue(encoded.contains("\"calendarPeriod\":true"), encoded)
        assertEquals(negative, SharePayloads.decodeMetadata(encoded))
    }

    @Test
    fun testEntryRoundTrip() {
        for (entry in listOf(
            SharedEntry(SharedEntry.State.YES),
            SharedEntry(SharedEntry.State.NO),
            SharedEntry(SharedEntry.State.SKIP),
            SharedEntry(SharedEntry.State.UNKNOWN),
            SharedEntry(SharedEntry.State.VALUE, 0.0),
            SharedEntry(SharedEntry.State.VALUE, 12.345)
        )) {
            assertEquals(entry, SharePayloads.decodeEntry(SharePayloads.encodeEntry(entry)))
        }
    }

    @Test
    fun testUnknownFieldsAreIgnored() {
        assertEquals(
            SharedEntry(SharedEntry.State.NO),
            SharePayloads.decodeEntry("{\"v\":1,\"state\":\"NO\",\"mood\":\"great\"}")
        )
    }

    @Test
    fun testOtherVersionsAreRejected() {
        assertFailsWith<SharePayloadException> { SharePayloads.decodeEntry("{\"v\":2,\"state\":\"NO\"}") }
        val v2 = SharePayloads.encodeMetadata(yesNo).replace("\"v\":1", "\"v\":2")
        assertFailsWith<SharePayloadException> { SharePayloads.decodeMetadata(v2) }
    }

    @Test
    fun testInvalidPayloadsAreRejected() {
        val valid = SharePayloads.encodeMetadata(yesNo)
        for (bad in listOf(
            "",
            "not json",
            "{\"v\":1}",
            valid.replace("YES_NO", "MAYBE"),
            valid.replace("\"freqNum\":3", "\"freqNum\":0"),
            valid.replace("2026-11-24", "2026-02-31"),
            valid.replace("2026-11-24", "24.11.2026")
        )) {
            assertFailsWith<SharePayloadException>(bad) { SharePayloads.decodeMetadata(bad) }
        }
        for (bad in listOf(
            "{\"v\":1,\"state\":\"MAYBE\"}",
            "{\"v\":1,\"state\":\"VALUE\"}",
            "{\"v\":1,\"state\":\"YES\",\"value\":1.0}"
        )) {
            assertFailsWith<SharePayloadException>(bad) { SharePayloads.decodeEntry(bad) }
        }
    }

    @Test
    fun testEntryValuesOfYesNoHabits() {
        val t = HabitEvent.Type.YES_NO
        assertEquals(SharedEntry.State.YES, SharedEntry.fromEntryValue(Entry.YES_MANUAL, t).state)
        assertEquals(SharedEntry.State.NO, SharedEntry.fromEntryValue(Entry.NO, t).state)
        assertEquals(SharedEntry.State.SKIP, SharedEntry.fromEntryValue(Entry.SKIP, t).state)
        assertEquals(SharedEntry.State.UNKNOWN, SharedEntry.fromEntryValue(Entry.UNKNOWN, t).state)
        assertEquals(SharedEntry.State.UNKNOWN, SharedEntry.fromEntryValue(Entry.YES_AUTO, t).state)
        for (v in listOf(Entry.YES_MANUAL, Entry.NO, Entry.SKIP, Entry.UNKNOWN)) {
            assertEquals(v, SharedEntry.fromEntryValue(v, t).toEntryValue())
        }
    }

    @Test
    fun testEntryValuesOfNumericalHabits() {
        val t = HabitEvent.Type.NUMERICAL
        assertEquals(SharedEntry(SharedEntry.State.VALUE, 2.5), SharedEntry.fromEntryValue(2500, t))
        assertEquals(SharedEntry(SharedEntry.State.VALUE, 0.0), SharedEntry.fromEntryValue(0, t))
        assertEquals(SharedEntry.State.UNKNOWN, SharedEntry.fromEntryValue(Entry.UNKNOWN, t).state)
        for (v in listOf(0, 1, 2500, 123456, Entry.UNKNOWN)) {
            assertEquals(v, SharedEntry.fromEntryValue(v, t).toEntryValue())
        }
    }

    @Test
    fun testMetadataFromSnapshot() {
        val snapshot = HabitSnapshot(
            habitUuid = "0f8fad5b-d9cb-469f-a165-70867728950e",
            name = "Run",
            description = "",
            category = "",
            color = 3,
            frequencyNumerator = 3,
            frequencyDenominator = 7,
            habitType = HabitEvent.Type.YES_NO,
            unit = "",
            target = null,
            targetType = null
        )
        assertEquals(yesNo, SharedHabitMetadata.from(snapshot, start))
    }

    @Test
    fun testSealAndOpen() = runTest {
        val key = ShareKey.generate()
        val sealedMeta = SharePayloads.sealMetadata(key, "share-1", numerical)
        assertEquals(numerical, SharePayloads.openMetadata(key, "share-1", sealedMeta))

        val entry = SharedEntry(SharedEntry.State.VALUE, 3.0)
        val sealedEntry = SharePayloads.sealEntry(key, "share-1", start, entry)
        assertEquals(entry, SharePayloads.openEntry(key, "share-1", start, sealedEntry))
        assertFailsWith<ShareDecryptionException> {
            SharePayloads.openEntry(key, "share-1", start.plus(1), sealedEntry)
        }
    }
}
