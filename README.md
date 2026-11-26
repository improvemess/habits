<h1 align="center">Loop Habit Tracker (fork)</h1>
<p align="center">
  <a href="https://github.com/oliverlorenz/uhabits/releases/latest">
    <img alt="release" src="https://img.shields.io/github/v/release/oliverlorenz/uhabits" />
  </a>
</p>

This is a personal fork of [Loop Habit Tracker](https://github.com/iSoron/uhabits)
by Álinson Santos Xavier. Loop helps you create and maintain good habits, with
detailed graphs and statistics that show how your habits improved over time.

The fork adds features that upstream does not have (see
[Differences from upstream](#differences-from-upstream)) and is **not**
published on Google Play or F-Droid. APKs are only available from the
[releases page][releases]. It is not affiliated with the upstream project; for
the official app, use upstream.

## Screenshots

The screenshots show the upstream app. They do not yet include the fork's
additions.

[![Main screen][screen1th]][screen1]
[![Edit habit][screen2th]][screen2]
[![Habit strength][screen3th]][screen3]
[![Habit history and streaks][screen4th]][screen4]
[![Widgets][screen5th]][screen5]
[![Night mode][screen6th]][screen6]

## Features of Loop (from upstream)

* **Habit score.** An advanced formula calculates the strength of your habits. Every repetition makes a habit stronger and every missed day makes it weaker. A few missed days after a long streak do not completely destroy your progress.
* **Flexible schedules.** Daily habits, but also more complex schedules such as 3 times per week or every other day.
* **Reminders.** Each habit can have its own reminder. Check or dismiss a habit directly from the notification.
* **Widgets.** Track your habits directly from your home screen.
* **Your data, your files.** Export to spreadsheets (CSV) or to a database file (SQLite). Checkmarks can be added through other apps, such as Tasker.
* **No limitations, no ads.** Track as many habits as you wish. No advertisements, no in-app purchases. The app is open source (GPLv3).

## Differences from upstream

This fork adds the following on top of upstream. Changes are listed by pull request; this list is updated with every change.

* **Streak flame instead of score** (#11, port of upstream [#2393](https://github.com/iSoron/uhabits/pull/2393)). New setting *Show streak flame*: the habit card shows the current streak instead of the score ring. The flame stays gray until today's check-in is done. On by default (#48); existing users keep their stored setting.* **Lazy-loaded history in the habit list** (#21). The main list loads only the last 14 days at startup. When you scroll back in time, older days are loaded in steps of 14 days, up to one year. Upstream loads a fixed block of 60 days up front. A "Today" chip appears right behind the month while you are scrolled back; tapping it jumps back to today. You can also swipe horizontally anywhere on the habit rows (not only on the date header) to move through the days, and the header shows the month (short, e.g. "Nov '26") that has the most visible days.
* **Pausing habits** (#25, #41). Several pause periods per habit, each with an optional title. Paused days count as skipped, so score and streak are preserved. Pauses can only start today or later and at most 31 days ahead, may not overlap, and can be shortened or removed. Pauses are included in the database backup and restored on import.
* **Home Assistant integration** (#8, #12, #38). Every change to an entry and to a habit (edit, archive, delete) produces an event for a pluggable transport. The Companion App transport broadcasts them to Home Assistant, and Home Assistant can set entries through a token-protected receiver. Disabled by default; the token is not part of backups.
* **Sharing habits with friends** (#37, #39, #40, #42, #44). End-to-end encrypted (AES-256-GCM) sharing through a sharing server (uhabits-server). Includes the owner and device identity, the plaintext format of shared data (notes and automatic check marks are never shared), an outbox with background sync via WorkManager and a weekly heartbeat. The sharing UI is still to come.
* **Network access and privacy** (#44). Upstream works fully offline and has no `INTERNET` permission. This fork requests it for sharing. As long as no habit is shared, nothing is sent over the network. The database backup now contains the owner secret and sharing keys, so treat it as confidential.
* **Measurable habits** (#62, #64, #73). Days without an entry look the same as an entry reset to 0 (both gray). For measurable habits you want to quit, every day since the creation day counts as 0 (and as done) instead of showing a question mark, and the question mark is no longer offered in the entry dialogs from the creation day on. Days before the creation day stay unknown, and a question mark set there is never counted as done (#69). The editor no longer asks for *At least* / *At most*: habits to build are always "at least", habits to quit always "at most" (#73, closes #63). Existing habits to build that are stored as "at most" still show the choice, so they are not changed silently.
* **Rolling or calendar week and month** (#23, #82). Weekly and monthly goals ("x times per week/month" of yes/no habits, weekly or monthly targets and limits of measurable habits) can be counted per *rolling* period (default: the last 7 days, or the last 30 days / one month) or per *calendar* week or month (the week starts on the first weekday of your settings). Both kinds of habits choose it in the frequency dialog, in bordered dropdowns that work independently for the week and the month row: "x times per rolling week" or "per calendar week", and the same for months. Measurable habits only offer every day, every rolling or calendar week and every rolling or calendar month, without a number. The habit editor and the habit screen show the calendar variant in the frequency text. In calendar mode a yes/no habit completes the whole period once the goal is reached, streaks count completed periods and the score starts counting again each period. Existing yes/no habits stay rolling; existing weekly and monthly measurable habits were always evaluated per calendar period and are migrated to *Calendar*, so nothing changes for them. Included in the database backup; older backups import with these defaults. Shared habits and the CSV export do not carry the mode.
* **Streak unit** (#15, #83). The streak flame can count in days, weeks or months and shows the unit next to the number (e.g. `14 d`, `5 w`, `3 mo`; `T`/`W`/`M` in German). The *Streak unit* field in the habit editor defaults to *Automatic*: weeks for "x times per week", months for "x times per month", otherwise days. The unit only changes how the streak is displayed, never which days belong to it, so switching never revives a broken streak and an existing streak is converted (30 days = 4 weeks; with calendar weeks or months only complete periods count). Weeks and months always count full periods of 7 or 30 days, including the days a rolling goal fills in (6 check marks in a row of a "3 times per week" goal last about 10 days and show 1 W, not 2). The only exception is a goal that was just reached while the streak is still shorter than a period: it shows 1 W right away. Until a period is complete the flame is gray at 0, as for days without a streak. Years and a tolerance for missed periods (#20) are not included. Included in the database backup; older backups import as *Automatic*. Shared habits and the CSV export do not carry the unit.
* **Fixed weekdays** (#19, #95). A yes/no habit can be due on fixed weekdays (for example Monday, Wednesday and Friday) instead of a frequency: the habit editor has a *Fixed days* row (nothing selected = *Not fixed*, the frequency applies), and the frequency row is hidden while days are fixed. On the other days the habit shows a faded "zz" (rest day); tapping it still checks it voluntarily. Rest days keep a streak alive but do not count, and do not lower the score; a missed due day counts as a missed day. Streaks of such habits are counted in days by default. A change only applies from today on, past days keep the weekdays that were due then (a history is stored), so a broken streak cannot be repaired by moving rest days; days before the first selection count as due every day. Not available for measurable or avoided habits. Included in the database backup and import (older backups import without fixed days); the CSV export and shared habits do not carry them.
* **No question field** (#49, #72). The *Question* field is gone from the habit editor and the habit screen; the habit title is used everywhere instead, so reminder notifications show only the title. Search no longer looks at questions, the habits CSV export has no *Question* column, and the sharing format no longer carries a question (payloads from older versions with one are still accepted). Existing databases and backups stay importable; the old `question` database column is left in place but no longer read or written.
* **Keyboard in the number dialog** (#74, refs #66). When entering a value for a measurable habit, the keyboard now opens together with the dialog instead of a quarter second later, so a quick tap on the value field is no longer disturbed.
* **No confetti** (#71, closes #61). The confetti animation after checking a habit and the *Disable animations* setting are removed, as are the confetti library and its string. The stored value of the old setting is simply ignored.
* **Build and release** (#9, #10, #17, #18, #59, #89, #90). CI quality gates (ktlint, core and Android tests, lint, instrumented tests on an emulator) on every pull request, run as parallel jobs so the emulator tests start as soon as the APKs are built; the instrumented tests are split across three emulators; the quality gates also run after each merge to `dev`, which fills the build cache for pull requests; debug APKs with a stable signing key, and automatic releases on merges to `dev` via semantic-release with Conventional Commit titles.

## Installing

Download the APK from the [releases page][releases] and install it. The app is
not updated automatically; install newer releases over the old one. Releases
are signed with a fixed debug key, so they can only be installed over other
releases of this fork.

The fork uses the same application ID as upstream (`org.isoron.uhabits`). If
you have the original Loop installed, uninstall it first (export a backup
before) or the installation will be rejected because of the different
signature.

To build the app from the source code, see the [build instructions][build].

## Contributing

This is a personal fork. Bugs and ideas for the fork are welcome in the
[issues of this repository](https://github.com/oliverlorenz/uhabits/issues).
Changes go through pull requests against `dev`; see the
[guidelines](docs/GUIDELINES.md). Problems that also occur in the original app
are better reported [upstream](https://github.com/iSoron/uhabits/issues).

## License

<img align="right" alt="GPL v3" src="https://www.gnu.org/graphics/gplv3-88x31.png">

  Copyright (C) 2016-2025 Álinson Santos Xavier <isoron@gmail.com>

  Loop Habit Tracker is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by the
  Free Software Foundation, either version 3 of the License, or (at your
  option) any later version.

  Loop Habit Tracker is distributed in the hope that it will be useful, but
  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
  or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
  more details.

  You should have received a copy of the GNU General Public License along
  with this program. If not, see <https://www.gnu.org/licenses/>.

[screen1]: screenshots/1.png
[screen2]: screenshots/2.png
[screen3]: screenshots/3.png
[screen4]: screenshots/4.png
[screen5]: screenshots/5.png
[screen6]: screenshots/6.png
[screen1th]: screenshots/1.thumb.png
[screen2th]: screenshots/2.thumb.png
[screen3th]: screenshots/3.thumb.png
[screen4th]: screenshots/4.thumb.png
[screen5th]: screenshots/5.thumb.png
[screen6th]: screenshots/6.thumb.png
[releases]: https://github.com/oliverlorenz/uhabits/releases
[build]: docs/BUILD.md
