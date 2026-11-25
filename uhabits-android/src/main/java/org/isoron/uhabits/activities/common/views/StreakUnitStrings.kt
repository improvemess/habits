/*
 * Copyright (C) 2026 Christoph Schmidtmeier
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

package org.isoron.uhabits.activities.common.views

import androidx.annotation.StringRes
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.StreakUnit

/** Full name of the unit, e.g. "Weeks". */
@get:StringRes
val StreakUnit.nameRes: Int
    get() = when (this) {
        StreakUnit.DAYS -> R.string.streak_unit_days
        StreakUnit.WEEKS -> R.string.streak_unit_weeks
        StreakUnit.MONTHS -> R.string.streak_unit_months
    }

/** Compact label shown next to the number on the flame, e.g. "W". */
@get:StringRes
val StreakUnit.shortRes: Int
    get() = when (this) {
        StreakUnit.DAYS -> R.string.streak_unit_days_short
        StreakUnit.WEEKS -> R.string.streak_unit_weeks_short
        StreakUnit.MONTHS -> R.string.streak_unit_months_short
    }
