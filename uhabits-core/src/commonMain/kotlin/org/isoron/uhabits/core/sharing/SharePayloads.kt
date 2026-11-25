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

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.events.HabitEvent
import org.isoron.uhabits.core.events.HabitSnapshot
import org.isoron.uhabits.core.models.Entry
import kotlin.math.roundToInt

/** A shared habit as friends see it. */
data class SharedHabitMetadata(
    val name: String,
    val description: String,
    val category: String,
    /** Index into the app's color palette. */
    val color: Int,
    val frequencyNumerator: Int,
    val frequencyDenominator: Int,
    val habitType: HabitEvent.Type,
    val unit: String,
    /** Target value (numerical habits only, null otherwise). */
    val target: Double?,
    /** Target type (numerical habits only, null otherwise). */
    val targetType: HabitEvent.TargetType?,
    /** First day that is shared; friends show the days before it as neutral. */
    val historyStart: LocalDate
) {
    companion object {
        fun from(snapshot: HabitSnapshot, historyStart: LocalDate) = SharedHabitMetadata(
            name = snapshot.name,
            description = snapshot.description,
            category = snapshot.category,
            color = snapshot.color,
            frequencyNumerator = snapshot.frequencyNumerator,
            frequencyDenominator = snapshot.frequencyDenominator,
            habitType = snapshot.habitType,
            unit = snapshot.unit,
            target = snapshot.target,
            targetType = snapshot.targetType,
            historyStart = historyStart
        )
    }
}

/**
 * One day of a shared habit, as entered by its owner. Only manual entries are
 * shared: friends compute automatic check marks, streaks and scores from these
 * and the frequency themselves. Notes are never shared.
 */
data class SharedEntry(val state: State, val value: Double? = null) {
    enum class State {
        YES,
        NO,
        SKIP,

        /** No entry (e.g. the owner removed it). */
        UNKNOWN,

        /** A numerical habit with a known [value]. */
        VALUE
    }

    init {
        require((state == State.VALUE) == (value != null)) { "value is set exactly for VALUE" }
    }

    /** The value as stored in [Entry.value]. */
    fun toEntryValue(): Int = when (state) {
        State.YES -> Entry.YES_MANUAL
        State.NO -> Entry.NO
        State.SKIP -> Entry.SKIP
        State.UNKNOWN -> Entry.UNKNOWN
        State.VALUE -> (value!! * 1000).roundToInt()
    }

    companion object {
        /** Converts an original (not computed) [Entry.value] of a habit of [type]. */
        fun fromEntryValue(entryValue: Int, type: HabitEvent.Type): SharedEntry = when {
            entryValue == Entry.UNKNOWN -> SharedEntry(State.UNKNOWN)
            entryValue == Entry.SKIP -> SharedEntry(State.SKIP)
            type == HabitEvent.Type.NUMERICAL -> SharedEntry(State.VALUE, entryValue / 1000.0)
            entryValue == Entry.YES_MANUAL -> SharedEntry(State.YES)
            entryValue == Entry.NO -> SharedEntry(State.NO)
            // Automatic check marks are recomputed by friends.
            else -> SharedEntry(State.UNKNOWN)
        }
    }
}

class SharePayloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The plaintext format of what is shared, before [ShareCipher] encrypts it:
 * compact JSON with a version field `v`. Unknown fields are ignored, so later
 * versions of the app can add fields without breaking older ones; a payload
 * with another `v` is rejected.
 *
 * Metadata, e.g.:
 * `{"v":1,"name":"Run","description":"","category":"",
 *   "color":3,"freqNum":3,"freqDen":7,"type":"YES_NO","unit":"","historyStart":"2026-11-24"}`
 *
 * Entry, e.g. `{"v":1,"state":"YES"}` or `{"v":1,"state":"VALUE","value":2.5}`.
 */
object SharePayloads {
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encodeMetadata(metadata: SharedHabitMetadata): String = json.encodeToString(
        MetadataV1.serializer(),
        MetadataV1(
            name = metadata.name,
            description = metadata.description,
            category = metadata.category,
            color = metadata.color,
            freqNum = metadata.frequencyNumerator,
            freqDen = metadata.frequencyDenominator,
            type = metadata.habitType.name,
            unit = metadata.unit,
            target = metadata.target,
            targetType = metadata.targetType?.name,
            historyStart = metadata.historyStart.toCSVString()
        )
    )

    /** @throws SharePayloadException if [encoded] is not valid metadata of [VERSION]. */
    fun decodeMetadata(encoded: String): SharedHabitMetadata = decoding {
        val m = json.decodeFromString(MetadataV1.serializer(), encoded)
        checkVersion(m.v)
        require(m.freqNum > 0 && m.freqDen > 0) { "invalid frequency" }
        SharedHabitMetadata(
            name = m.name,
            description = m.description,
            category = m.category,
            color = m.color,
            frequencyNumerator = m.freqNum,
            frequencyDenominator = m.freqDen,
            habitType = HabitEvent.Type.valueOf(m.type),
            unit = m.unit,
            target = m.target,
            targetType = m.targetType?.let { HabitEvent.TargetType.valueOf(it) },
            historyStart = parseDate(m.historyStart)
        )
    }

    fun encodeEntry(entry: SharedEntry): String =
        json.encodeToString(EntryV1.serializer(), EntryV1(state = entry.state.name, value = entry.value))

    /** @throws SharePayloadException if [encoded] is not a valid entry of [VERSION]. */
    fun decodeEntry(encoded: String): SharedEntry = decoding {
        val e = json.decodeFromString(EntryV1.serializer(), encoded)
        checkVersion(e.v)
        SharedEntry(SharedEntry.State.valueOf(e.state), e.value)
    }

    suspend fun sealMetadata(key: ShareKey, shareId: String, metadata: SharedHabitMetadata): String =
        ShareCipher.sealText(key, encodeMetadata(metadata), ShareContext.metadata(shareId))

    /** @throws ShareDecryptionException or [SharePayloadException]. */
    suspend fun openMetadata(key: ShareKey, shareId: String, sealed: String): SharedHabitMetadata =
        decodeMetadata(ShareCipher.openText(key, sealed, ShareContext.metadata(shareId)))

    suspend fun sealEntry(key: ShareKey, shareId: String, date: LocalDate, entry: SharedEntry): String =
        ShareCipher.sealText(key, encodeEntry(entry), ShareContext.entry(shareId, date))

    /** @throws ShareDecryptionException or [SharePayloadException]. */
    suspend fun openEntry(key: ShareKey, shareId: String, date: LocalDate, sealed: String): SharedEntry =
        decodeEntry(ShareCipher.openText(key, sealed, ShareContext.entry(shareId, date)))

    private fun checkVersion(v: Int) {
        if (v != VERSION) throw SharePayloadException("Unsupported payload version $v")
    }

    private val DATE_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    private fun parseDate(s: String): LocalDate {
        val (y, m, d) = DATE_REGEX.matchEntire(s)?.destructured
            ?: throw IllegalArgumentException("invalid date")
        val date = LocalDate(y.toInt(), m.toInt(), d.toInt())
        require(date.toCSVString() == s) { "invalid date" }
        return date
    }

    private inline fun <T> decoding(block: () -> T): T = try {
        block()
    } catch (e: SharePayloadException) {
        throw e
    } catch (e: SerializationException) {
        throw SharePayloadException("Malformed payload", e)
    } catch (e: IllegalArgumentException) {
        throw SharePayloadException("Invalid payload: ${e.message}", e)
    }

    @Serializable
    private data class MetadataV1(
        val v: Int = VERSION,
        val name: String,
        val description: String = "",
        val category: String = "",
        val color: Int,
        val freqNum: Int,
        val freqDen: Int,
        val type: String,
        val unit: String = "",
        val target: Double? = null,
        val targetType: String? = null,
        val historyStart: String
    )

    @Serializable
    private data class EntryV1(
        val v: Int = VERSION,
        val state: String,
        val value: Double? = null
    )
}
