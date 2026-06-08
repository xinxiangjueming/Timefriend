package com.example.timefriend.data

import android.content.Context
import androidx.annotation.StringRes
import com.example.timefriend.R

enum class TimeCategory(@StringRes val displayNameRes: Int) {
    INVESTMENT(R.string.category_investment),
    FIXED(R.string.category_fixed),
    SLEEP(R.string.category_sleep),
    WASTE(R.string.category_waste);

    val isTimer: Boolean get() = this != SLEEP

    fun displayName(context: Context): String = context.getString(displayNameRes)
}
