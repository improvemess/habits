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
package org.isoron.uhabits.homeassistant

import android.content.Context
import me.tatarka.inject.annotations.Inject
import org.isoron.uhabits.core.AppScope
import org.isoron.uhabits.inject.AppContext
import java.security.SecureRandom

/**
 * Settings of the Home Assistant integration.
 *
 * Kept in a SharedPreferences file of its own, so that the shared secret is
 * not included in backups: HabitsBackupAgent only backs up the default
 * preferences and the database.
 */
@Inject
@AppScope
class HomeAssistantPreferences(
    @AppContext context: Context
) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Package of the Companion App flavor to send events to. */
    var companionPackage: String
        get() = prefs.getString(KEY_COMPANION_PACKAGE, null) ?: COMPANION_PACKAGE_FULL
        set(value) = prefs.edit().putString(KEY_COMPANION_PACKAGE, value).apply()

    /** Shared secret that commands from Home Assistant must carry; null if not set up. */
    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    /** Token expected from Home Assistant, or null if commands are not accepted. */
    val acceptedToken: String?
        get() = if (isEnabled) token else null

    companion object {
        const val FILE_NAME = "home_assistant"
        const val COMPANION_PACKAGE_FULL = "io.homeassistant.companion.android"
        const val COMPANION_PACKAGE_MINIMAL = "io.homeassistant.companion.android.minimal"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_COMPANION_PACKAGE = "companion_package"
        private const val KEY_TOKEN = "token"

        /**
         * A random 256-bit token, hex-encoded: it must not contain `:` or `,`,
         * which the Companion App's `intent_extras` syntax uses as separators.
         */
        fun generateToken(random: SecureRandom = SecureRandom()): String {
            val bytes = ByteArray(32).also(random::nextBytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
