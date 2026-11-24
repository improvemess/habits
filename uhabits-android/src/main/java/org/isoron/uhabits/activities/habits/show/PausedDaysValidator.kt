package org.isoron.uhabits.activities.habits.show

import android.os.Parcel
import android.os.Parcelable
import com.google.android.material.datepicker.CalendarConstraints

/**
 * Disables the days of existing pauses in the date range picker, so that new pauses cannot
 * overlap them. [ranges] holds pairs of (start, end) as UTC midnight timestamps in milliseconds,
 * both inclusive.
 */
class PausedDaysValidator(private val ranges: LongArray) : CalendarConstraints.DateValidator {
    override fun isValid(date: Long): Boolean {
        for (i in ranges.indices step 2) {
            if (date >= ranges[i] && date <= ranges[i + 1]) return false
        }
        return true
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLongArray(ranges)
    }

    companion object CREATOR : Parcelable.Creator<PausedDaysValidator> {
        override fun createFromParcel(source: Parcel) = PausedDaysValidator(source.createLongArray()!!)

        override fun newArray(size: Int): Array<PausedDaysValidator?> = arrayOfNulls(size)
    }
}
