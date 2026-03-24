package com.myapp.budget.util

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

fun LocalDate.dayOfWeekKo(): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

fun Long.formatAsWon(): String {
    val abs = kotlin.math.abs(this)
    val str = abs.toString()
    val withCommas = buildString {
        str.forEachIndexed { index, c ->
            if (index > 0 && (str.length - index) % 3 == 0) append(',')
            append(c)
        }
    }
    return if (this < 0) "-₩$withCommas" else "₩$withCommas"
}
