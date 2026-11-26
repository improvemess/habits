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

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.isoron.uhabits.BuildConfig
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.core.sharing.AcceptResult
import org.isoron.uhabits.core.sharing.FriendsService
import org.isoron.uhabits.core.sharing.InviteLinks
import org.isoron.uhabits.databinding.AcceptInviteBinding

/**
 * Accepts an invite to follow a friend's habit (#35). Opened by an invite
 * link (`uhabits://invite?…` from the server's landing page, or the server's
 * `https://…/invite/…` link), by "Share to Loop" from a messenger, or from the
 * friends screen with a link from the clipboard.
 */
class AcceptInviteActivity : AppCompatActivity() {

    private lateinit var binding: AcceptInviteBinding
    private lateinit var service: FriendsService
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val component = (application as HabitsApplication).component
        service = component.friendsService
        binding = AcceptInviteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val link = intent.data?.toString() ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        val invite = link?.let { InviteLinks.parse(it) }
        if (link == null || invite == null) {
            binding.message.setText(R.string.accept_invite_no_link)
            binding.acceptButton.visibility = View.GONE
            binding.nameInput.visibility = View.GONE
            binding.cancelButton.setOnClickListener { finish() }
            return
        }
        binding.message.text = getString(R.string.accept_invite_message, sharingHost(invite.serverUrl))
        binding.cancelButton.setOnClickListener { finish() }
        binding.acceptButton.setOnClickListener { accept(link) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun accept(link: String) {
        binding.acceptButton.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        binding.status.visibility = View.GONE
        scope.launch {
            val result = service.accept(link, binding.nameInput.text.toString(), allowInsecure = BuildConfig.DEBUG)
            binding.progress.visibility = View.GONE
            if (result is AcceptResult.Accepted) {
                ShareSyncWork.scheduleFriendsSync(this@AcceptInviteActivity)
                startActivity(Intent(this@AcceptInviteActivity, FriendsActivity::class.java))
                finish()
                return@launch
            }
            binding.acceptButton.isEnabled = true
            binding.status.visibility = View.VISIBLE
            binding.status.text = when (result) {
                AcceptResult.InvalidLink -> getString(R.string.accept_invite_no_link)
                AcceptResult.InsecureServer -> getString(R.string.accept_invite_insecure)
                is AcceptResult.Unreachable ->
                    sharingErrorMessage(result.error, InviteLinks.parse(link)?.serverUrl ?: link)
                AcceptResult.Incompatible -> getString(R.string.accept_invite_incompatible)
                AcceptResult.InviteGone -> getString(R.string.accept_invite_gone)
                is AcceptResult.Accepted -> getString(R.string.accept_invite_no_link)
            }
        }
    }
}
