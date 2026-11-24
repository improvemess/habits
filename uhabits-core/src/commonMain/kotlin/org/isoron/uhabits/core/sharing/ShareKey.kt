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

import org.isoron.platform.utils.secureRandomBytes
import kotlin.io.encoding.Base64

/**
 * Symmetric key of one shared habit. Only the owner's devices and the friends
 * the habit is shared with know it; the sharing server never does. Friends get
 * it through the fragment of the invite link (`…#k=<key>`), which browsers and
 * apps never send to a server.
 */
class ShareKey(bytes: ByteArray) {
    private val bytes: ByteArray = bytes.copyOf()

    init {
        require(bytes.size == SIZE_BYTES) { "A share key has $SIZE_BYTES bytes" }
    }

    internal fun toByteArray(): ByteArray = bytes.copyOf()

    /** URL-safe Base64 without padding, for the invite link fragment and local storage. */
    fun encode(): String = BASE64.encode(bytes)

    override fun equals(other: Any?): Boolean =
        other is ShareKey && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    /** Never contains the key, so that keys are safe to log. */
    override fun toString(): String = "ShareKey(<redacted>)"

    companion object {
        const val SIZE_BYTES = 32

        internal val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        fun generate(): ShareKey = ShareKey(secureRandomBytes(SIZE_BYTES))

        /** Returns the key encoded by [encode], or null if [encoded] is not one. */
        fun decodeOrNull(encoded: String): ShareKey? {
            val bytes = try {
                BASE64.decode(encoded)
            } catch (e: IllegalArgumentException) {
                return null
            }
            return if (bytes.size == SIZE_BYTES) ShareKey(bytes) else null
        }
    }
}
