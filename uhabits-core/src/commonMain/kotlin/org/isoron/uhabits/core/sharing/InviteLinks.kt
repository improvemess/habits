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

/** An invite to follow a friend's habit, as carried by an invite link. */
data class InviteLinkData(val serverUrl: String, val code: String, val key: ShareKey)

/**
 * Reads invite links in both forms the sharing server produces:
 *
 * - `https://<server>/invite/<code>#k=<key>&v=1`, as created by the owner's app
 *   ([SharingService.createInvite]);
 * - `uhabits://invite?server=<server>&code=<code>&k=<key>&v=1`, as opened by
 *   the server's invite landing page.
 *
 * The link may be surrounded by other text, e.g. a pasted chat message.
 */
object InviteLinks {
    const val SCHEME = "uhabits"
    private const val VERSION = "1"

    private val HTTP_LINK = Regex("""https?://[^\s#]+/invite/([A-Za-z0-9_-]+)#(\S+)""")
    private val APP_LINK = Regex("""uhabits://invite\?(\S+)""")

    /** Returns the invite in [text], or null if there is no valid invite link. */
    fun parse(text: String): InviteLinkData? {
        HTTP_LINK.find(text)?.let { match ->
            val url = match.value
            val server = url.substringBefore("#").substringBeforeLast("/invite/")
            val params = parseQuery(match.groupValues[2])
            return build(server, match.groupValues[1], params)
        }
        APP_LINK.find(text)?.let { match ->
            val params = parseQuery(match.groupValues[1])
            return build(params["server"] ?: return null, params["code"] ?: return null, params)
        }
        return null
    }

    private fun build(server: String, code: String, params: Map<String, String>): InviteLinkData? {
        if (params["v"] != null && params["v"] != VERSION) return null
        val key = params["k"]?.let { ShareKey.decodeOrNull(it) } ?: return null
        if (!Regex("^[A-Za-z0-9_-]+$").matches(code)) return null
        val serverUrl = server.trimEnd('/')
        if (!serverUrl.startsWith("https://") && !serverUrl.startsWith("http://")) return null
        return InviteLinkData(serverUrl, code, key)
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').mapNotNull { part ->
            val name = part.substringBefore('=', "")
            if (name.isEmpty()) null else name to decode(part.substringAfter('='))
        }.toMap()

    /** Percent-decoding for the parameters of `uhabits://` links. */
    private fun decode(value: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '%' && i + 2 <= value.lastIndex -> {
                    val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex == null) {
                        bytes.addAll(c.toString().encodeToByteArray().toList())
                        i++
                    } else {
                        bytes.add(hex.toByte())
                        i += 3
                    }
                }
                c == '+' -> {
                    bytes.add(' '.code.toByte())
                    i++
                }
                else -> {
                    bytes.addAll(c.toString().encodeToByteArray().toList())
                    i++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}
