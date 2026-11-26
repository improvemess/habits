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

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.activities.habits.list.views.CheckmarkButtonViewFactory
import org.isoron.uhabits.activities.habits.list.views.CheckmarkPanelViewFactory
import org.isoron.uhabits.activities.habits.list.views.HabitCardListView
import org.isoron.uhabits.activities.habits.list.views.HabitCardView
import org.isoron.uhabits.activities.habits.list.views.HabitCardViewState
import org.isoron.uhabits.activities.habits.list.views.HeaderState
import org.isoron.uhabits.activities.habits.list.views.HeaderView
import org.isoron.uhabits.activities.habits.list.views.NumberButtonViewFactory
import org.isoron.uhabits.activities.habits.list.views.NumberPanelViewFactory
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.sharing.FriendShare
import org.isoron.uhabits.core.sharing.FriendsService
import org.isoron.uhabits.core.sharing.InviteLinks
import org.isoron.uhabits.core.ui.screens.habits.list.ListHabitsPresenter
import org.isoron.uhabits.utils.applyBottomInset
import org.isoron.uhabits.utils.applyRootViewInsets
import org.isoron.uhabits.utils.currentTheme
import org.isoron.uhabits.utils.dim
import org.isoron.uhabits.utils.dp
import org.isoron.uhabits.utils.setupToolbar
import java.net.URI
import kotlin.math.max
import kotlin.math.min

/**
 * Habits that friends share with this device (#35). They use the same cards
 * as the own list, but are read-only: tapping does nothing, and they never
 * show up among one's own habits.
 */
class FriendsActivity : AppCompatActivity() {

    private lateinit var service: FriendsService
    private lateinit var preferences: Preferences
    private lateinit var header: HeaderView
    private lateinit var content: LinearLayout
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cards = mutableListOf<HabitCardView>()

    /** Friends' cards can be looked at, never toggled. */
    private val readOnly = object : HabitCardListView.Controller {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val component = (application as HabitsApplication).component
        AndroidThemeSwitcher(this, component.preferences).apply()
        service = component.friendsService
        preferences = component.preferences

        // Inflated for its dark action bar theme, like the toolbar of the habit list.
        val toolbar = layoutInflater.inflate(R.layout.toolbar, null) as Toolbar
        header = HeaderView(this, preferences, component.midnightTimer)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, MATCH_PARENT, WRAP_CONTENT)
            addView(header, MATCH_PARENT, WRAP_CONTENT)
            addView(ScrollView(this@FriendsActivity).apply { addView(content) }, MATCH_PARENT, MATCH_PARENT)
        }
        setContentView(root)
        root.setupToolbar(
            toolbar = toolbar,
            title = getString(R.string.friends),
            color = PaletteColor(17),
            theme = root.currentTheme()
        )
        content.applyBottomInset()
        root.applyRootViewInsets()
        root.post { render() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_ACCEPT, Menu.NONE, R.string.accept_invite)
        menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE, R.string.refresh)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_ACCEPT -> {
            acceptFromClipboard()
            true
        }
        MENU_REFRESH -> {
            refresh()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun refresh() {
        scope.launch {
            service.sync()
            render()
        }
    }

    private fun buttonCount(): Int {
        val width = content.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val labelWidth = max((width / 3).toFloat(), content.dim(R.dimen.habitNameWidth))
        return min(MAX_BUTTONS, max(0, ((width - labelWidth) / content.dim(R.dimen.checkmarkWidth)).toInt()))
    }

    private fun render() {
        content.removeAllViews()
        cards.clear()
        val count = buttonCount()
        header.setState(HeaderState(buttonCount = count, maxDataOffset = 0))
        val shares = service.friendShares()
        if (shares.isEmpty()) {
            content.addView(note(getString(R.string.friends_empty)))
            return
        }
        for (share in shares) {
            val habit = service.habitOf(share)
            if (habit == null) {
                content.addView(note(getString(R.string.friends_loading, host(share.serverUrl))))
                continue
            }
            val card = HabitCardView(this, checkmarkPanels(), numberPanels(), readOnly)
            card.setState(
                HabitCardViewState(
                    record = ListHabitsPresenter.cardState(habit, -share.id, count, cheatDayHistory = preferences.cheatDayHistory),
                    selected = false,
                    buttonCount = count,
                    showStreak = preferences.isStreakFlameEnabled
                )
            )
            card.setOnLongClickListener {
                confirmLeave(share, habit.name)
                true
            }
            cards.add(card)
            content.addView(card, MATCH_PARENT, WRAP_CONTENT)
            statusText(share)?.let { content.addView(note(it)) }
        }
    }

    private fun statusText(share: FriendShare): String? = when (share.status) {
        FriendShare.Status.ACTIVE -> null
        FriendShare.Status.INACTIVE -> getString(R.string.friends_status_inactive)
        FriendShare.Status.ENDED -> getString(R.string.friends_status_ended)
        FriendShare.Status.REMOVED -> getString(R.string.friends_status_removed)
    }

    private fun confirmLeave(share: FriendShare, name: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.friends_leave_title, name))
            .setMessage(R.string.friends_leave_message)
            .setPositiveButton(R.string.friends_leave) { _, _ ->
                scope.launch {
                    service.leave(share)
                    render()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun acceptFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text == null || InviteLinks.parse(text) == null) {
            Toast.makeText(this, R.string.accept_invite_no_link, Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, AcceptInviteActivity::class.java).putExtra(Intent.EXTRA_TEXT, text))
    }

    private fun checkmarkPanels() =
        CheckmarkPanelViewFactory(this, preferences, CheckmarkButtonViewFactory(this, preferences))

    private fun numberPanels() =
        NumberPanelViewFactory(this, preferences, NumberButtonViewFactory(this, preferences))

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        val padding = content.dp(16f).toInt()
        setPadding(padding, content.dp(8f).toInt(), padding, content.dp(8f).toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        visibility = View.VISIBLE
    }

    private fun host(url: String): String = try {
        URI(url).host ?: url
    } catch (e: Exception) {
        url
    }

    private companion object {
        const val MENU_ACCEPT = 1
        const val MENU_REFRESH = 2
        const val MAX_BUTTONS = 7
    }
}
