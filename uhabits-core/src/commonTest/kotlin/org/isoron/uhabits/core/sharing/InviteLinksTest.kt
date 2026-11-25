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
import kotlin.test.assertNull

class InviteLinksTest {
    private val keyText = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
    private val key = ShareKey.decodeOrNull(keyText)!!

    @Test
    fun testHttpsLink() {
        assertEquals(
            InviteLinkData("https://habits.improvemess.com", "Zq3v1mT0cX8yWb2nA5rK4g", key),
            InviteLinks.parse("https://habits.improvemess.com/invite/Zq3v1mT0cX8yWb2nA5rK4g#k=$keyText&v=1")
        )
    }

    @Test
    fun testServerBelowSubPath() {
        assertEquals(
            "https://example.com/habits",
            InviteLinks.parse("https://example.com/habits/invite/c1#k=$keyText&v=1")?.serverUrl
        )
    }

    @Test
    fun testLinkInsideMessage() {
        val message = "Follow my habit \"Run\" in Loop Habit Tracker: https://h.example/invite/c1#k=$keyText&v=1 :)"
        assertEquals(InviteLinkData("https://h.example", "c1", key), InviteLinks.parse(message))
    }

    @Test
    fun testAppSchemeLinkFromLandingPage() {
        val link = "uhabits://invite?server=https%3A%2F%2Fexample.com%2Fhabits&code=c1&k=$keyText&v=1"
        assertEquals(InviteLinkData("https://example.com/habits", "c1", key), InviteLinks.parse(link))
    }

    @Test
    fun testRejectsInvalidLinks() {
        assertNull(InviteLinks.parse(""))
        assertNull(InviteLinks.parse("https://h.example/invite/c1"))
        assertNull(InviteLinks.parse("https://h.example/invite/c1#v=1"))
        assertNull(InviteLinks.parse("https://h.example/invite/c1#k=tooshort&v=1"))
        assertNull(InviteLinks.parse("https://h.example/invite/c1#k=$keyText&v=2"))
        assertNull(InviteLinks.parse("uhabits://invite?code=c1&k=$keyText&v=1"))
        assertNull(InviteLinks.parse("uhabits://invite?server=ftp%3A%2F%2Fh&code=c1&k=$keyText&v=1"))
    }
}
