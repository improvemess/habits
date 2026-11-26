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
package org.isoron.uhabits.sharing

import android.content.Context
import org.isoron.uhabits.R
import org.isoron.uhabits.core.sharing.ServerCheck
import org.isoron.uhabits.core.sharing.SharingApiException
import org.isoron.uhabits.core.sharing.SharingApiException.Kind
import java.net.URI

/** Host of a server URL for messages, or the URL itself if it has none. */
fun sharingHost(url: String): String = try {
    URI(url).host ?: url
} catch (e: Exception) {
    url
}

/**
 * What went wrong talking to [server], for the user, with the technical
 * cause in a second line (#97). The cause never contains tokens, see
 * [SharingApiException.detail].
 */
fun Context.sharingErrorMessage(e: SharingApiException, server: String): String {
    val host = sharingHost(server)
    val message = when (e.kind) {
        Kind.HTTP -> getString(R.string.share_error_failed, e.message)
        Kind.DNS -> getString(R.string.share_error_dns, host)
        Kind.TLS -> getString(R.string.share_error_tls, host)
        Kind.TIMEOUT -> getString(R.string.share_error_timeout, host)
        Kind.CONNECTION -> getString(R.string.share_error_unreachable)
        Kind.INVALID_RESPONSE -> getString(R.string.share_error_invalid_response, host)
        Kind.INVALID_URL -> getString(R.string.share_error_invalid_server)
    }
    val detail = e.detail ?: return message
    return message + "\n" + getString(R.string.share_error_detail, detail)
}

/** Message for a failed [ServerCheck]; null for [ServerCheck.Ok]. */
fun Context.serverCheckMessage(check: ServerCheck, server: String): String? = when (check) {
    is ServerCheck.Ok -> null
    ServerCheck.InvalidUrl -> getString(R.string.share_error_invalid_server)
    is ServerCheck.Unreachable -> sharingErrorMessage(check.error, server)
    is ServerCheck.Incompatible -> {
        val message = getString(R.string.share_error_incompatible, sharingHost(server))
        val detail = check.error?.let { it.detail ?: it.message }
        if (detail == null) message else message + "\n" + getString(R.string.share_error_detail, detail)
    }
}
