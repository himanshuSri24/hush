package com.devwithcoffee.hush.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Schedules {

    /** A concrete occurrence of a schedule. */
    data class Window(val schedule: Schedule, val start: LocalDateTime, val end: LocalDateTime)

    /** Occurrences that start on [day] (an overnight window ends the next day). */
    private fun occurrenceOn(s: Schedule, day: LocalDate): Window? {
        if (!s.enabled || day.dayOfWeek.value !in s.days) return null
        val start = day.atStartOfDay().plusMinutes(s.startMin.toLong())
        val endDay = if (s.overnight) day.plusDays(1) else day
        val end = endDay.atStartOfDay().plusMinutes(s.endMin.toLong())
        return Window(s, start, end)
    }

    /** The window containing [now], if any (latest end wins when they overlap). */
    fun activeAt(list: List<Schedule>, now: LocalDateTime): Window? =
        list.flatMap { s -> listOfNotNull(occurrenceOn(s, now.toLocalDate()), occurrenceOn(s, now.toLocalDate().minusDays(1))) }
            .filter { !now.isBefore(it.start) && now.isBefore(it.end) }
            .maxByOrNull { it.end }

    /** The next window that starts strictly after [now], looking up to 8 days ahead. */
    fun nextStart(list: List<Schedule>, now: LocalDateTime): Window? =
        (0L..8L).flatMap { d -> list.mapNotNull { occurrenceOn(it, now.toLocalDate().plusDays(d)) } }
            .filter { it.start.isAfter(now) }
            .minByOrNull { it.start }

    fun toMillis(t: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()) = t.atZone(zone).toInstant().toEpochMilli()

    fun describeDays(days: Set<Int>): String = when {
        days.size == 7 -> "Every day"
        days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
        days == setOf(6, 7) -> "Weekends"
        days.isEmpty() -> "Never"
        else -> days.sorted().joinToString(" ") { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[it - 1] }
    }

    fun fmt(min: Int): String {
        val h = min / 60; val m = min % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
    }
}
