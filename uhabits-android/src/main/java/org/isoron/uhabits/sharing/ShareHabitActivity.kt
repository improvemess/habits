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

import android.content.ContentUris
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.isoron.platform.gui.toInt
import org.isoron.uhabits.BuildConfig
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.sharing.InviteLink
import org.isoron.uhabits.core.sharing.Reader
import org.isoron.uhabits.core.sharing.ServerCheck
import org.isoron.uhabits.core.sharing.Share
import org.isoron.uhabits.core.sharing.SharedHistory
import org.isoron.uhabits.core.sharing.SharingApiException
import org.isoron.uhabits.core.sharing.SharingService
import org.isoron.uhabits.databinding.ShareHabitBinding
import org.isoron.uhabits.utils.applyBottomInset
import org.isoron.uhabits.utils.applyRootViewInsets
import org.isoron.uhabits.utils.currentTheme
import org.isoron.uhabits.utils.setupToolbar
import java.net.URI
import java.text.DateFormat
import java.time.Instant
import java.util.Date

/**
 * Shares one habit with friends (#34): choose the history, share, invite
 * friends with a link or QR code, see and remove them, stop sharing.
 *
 * Opened from the habit screen with the habit's URI as data.
 */
class ShareHabitActivity : AppCompatActivity() {

    private lateinit var binding: ShareHabitBinding
    private lateinit var habit: Habit
    private lateinit var service: SharingService
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var share: Share? = null

    /** The habit color in the current theme, for buttons and radio buttons (#96). */
    private var accentColor = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val component = (application as HabitsApplication).component
        AndroidThemeSwitcher(this, component.preferences).apply()
        val found = intent.data?.let { component.habitList.getById(ContentUris.parseId(it)) }
        if (found == null) {
            finish()
            return
        }
        habit = found
        service = component.sharingService

        binding = ShareHabitBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val appTheme = binding.root.currentTheme()
        binding.root.setupToolbar(
            toolbar = binding.toolbar,
            title = getString(R.string.share_with_friends),
            color = habit.color,
            theme = appTheme
        )
        accentColor = appTheme.color(habit.color).toInt()
        listOf(binding.shareButton, binding.inviteButton, binding.sendLinkButton, binding.stopButton)
            .forEach { colorButton(it) }
        val radioTint = ColorStateList.valueOf(accentColor)
        binding.historyToday.buttonTintList = radioTint
        binding.historyAll.buttonTintList = radioTint
        binding.outerLinearLayout.applyBottomInset()
        binding.root.applyRootViewInsets()

        val server = component.preferences.sharingServerUrl
        binding.newShareServer.text = getString(R.string.share_server, host(server))
        binding.shareButton.setOnClickListener { onShare(server) }
        binding.inviteButton.setOnClickListener { onInvite() }
        binding.stopButton.setOnClickListener { onStopSharing() }

        share = service.sharesOf(habit).firstOrNull()
        render()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render() {
        val current = share
        binding.notSharedCard.visibility = if (current == null) View.VISIBLE else View.GONE
        binding.sharedCard.visibility = if (current != null) View.VISIBLE else View.GONE
        binding.friendsCard.visibility = if (current != null) View.VISIBLE else View.GONE
        if (current != null) {
            binding.shareServer.text = getString(R.string.share_server, host(current.serverUrl))
            loadFriends(current)
        }
    }

    private fun onShare(server: String) {
        val history = if (binding.historyAll.isChecked) SharedHistory.ALL else SharedHistory.FROM_TODAY
        busy {
            when (val check = service.checkServer(server, allowInsecure = BuildConfig.DEBUG)) {
                is ServerCheck.Ok -> {
                    share = service.share(habit, check.serverUrl, history)
                    render()
                }
                ServerCheck.InvalidUrl -> showStatus(getString(R.string.share_error_invalid_server))
                ServerCheck.Unreachable -> showStatus(getString(R.string.share_error_unreachable))
                ServerCheck.Incompatible -> showStatus(getString(R.string.share_error_incompatible, host(server)))
            }
        }
    }

    private fun onInvite() {
        val current = share ?: return
        busy { showInvite(service.createInvite(current)) }
    }

    private fun showInvite(link: InviteLink) {
        binding.invitePanel.visibility = View.VISIBLE
        binding.inviteQr.setImageBitmap(qrCode(link.url))
        binding.inviteLink.text = link.url
        binding.inviteHint.text = getString(R.string.share_invite_hint, formatDate(link.expiresAt))
        binding.sendLinkButton.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getString(R.string.share_invite_message, habit.name, link.url))
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_send_link)))
        }
    }

    private fun loadFriends(current: Share) {
        busy { showFriends(current, service.readers(current)) }
    }

    private fun showFriends(current: Share, readers: List<Reader>) {
        val list = binding.friendsList
        list.removeAllViews()
        if (readers.isEmpty()) {
            list.addView(TextView(this).apply { text = getString(R.string.share_no_friends) })
            return
        }
        for (reader in readers) {
            val name = reader.displayName ?: getString(R.string.share_unnamed_friend)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                TextView(this).apply {
                    text = name
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            row.addView(
                MaterialButton(this, null, R.attr.borderlessButtonStyle).apply {
                    colorButton(this)
                    text = getString(R.string.share_remove_friend)
                    setOnClickListener {
                        AlertDialog.Builder(this@ShareHabitActivity)
                            .setTitle(getString(R.string.share_remove_friend_title, name))
                            .setPositiveButton(R.string.share_remove_friend) { _, _ ->
                                busy {
                                    service.removeReader(current, reader.readerId)
                                    showFriends(current, service.readers(current))
                                }
                            }
                            .setNegativeButton(R.string.no, null)
                            .show()
                    }
                }
            )
            list.addView(row)
        }
    }

    /** Text and ripple in the habit color, like the toolbar (#96). */
    private fun colorButton(button: MaterialButton) {
        button.setTextColor(accentColor)
        button.rippleColor = ColorStateList.valueOf(accentColor).withAlpha(RIPPLE_ALPHA)
    }

    private fun onStopSharing() {
        val current = share ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.share_stop)
            .setMessage(R.string.share_stop_message)
            .setPositiveButton(R.string.share_stop) { _, _ ->
                service.stopSharing(current)
                share = null
                binding.invitePanel.visibility = View.GONE
                render()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    /** Runs [block] with a progress indicator; shows server errors instead of crashing. */
    private fun busy(block: suspend () -> Unit) {
        binding.status.visibility = View.GONE
        binding.progress.visibility = View.VISIBLE
        scope.launch {
            try {
                block()
            } catch (e: SharingApiException) {
                Log.w("ShareHabitActivity", "Sharing request failed", e)
                showStatus(
                    if (e.status == null) {
                        getString(R.string.share_error_unreachable)
                    } else {
                        getString(R.string.share_error_failed, e.message)
                    }
                )
            } finally {
                binding.progress.visibility = View.GONE
            }
        }
    }

    private fun showStatus(message: String) {
        binding.status.text = message
        binding.status.visibility = View.VISIBLE
    }

    private fun host(url: String): String = try {
        URI(url).host ?: url
    } catch (e: Exception) {
        url
    }

    private fun formatDate(iso: String): String = try {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date.from(Instant.parse(iso)))
    } catch (e: Exception) {
        iso
    }

    private fun qrCode(text: String): Bitmap {
        val size = 512
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    companion object {
        /** About 20 %, like the ripple of a Material text button. */
        private const val RIPPLE_ALPHA = 0x33
    }
}
