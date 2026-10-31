package com.devwithcoffee.hush.core

import org.json.JSONArray
import org.json.JSONObject

/** A person who always gets through, on any messaging app or a normal call. */
data class Vip(
    val id: String,
    val name: String,
    val numbers: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("numbers", JSONArray(numbers))

    companion object {
        fun fromJson(o: JSONObject) = Vip(
            id = o.getString("id"),
            name = o.getString("name"),
            numbers = o.optJSONArray("numbers")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
        )
    }
}

/** A recurring quiet window, e.g. Mon-Fri 13:30-14:15. Overnight when end <= start. */
data class Schedule(
    val id: String,
    val label: String,
    val days: Set<Int>,          // java.time.DayOfWeek values, 1 = Monday .. 7 = Sunday
    val startMin: Int,           // minutes after midnight
    val endMin: Int,
    val enabled: Boolean = true,
) {
    val overnight get() = endMin <= startMin

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("label", label).put("days", JSONArray(days.sorted()))
        .put("start", startMin).put("end", endMin).put("enabled", enabled)

    companion object {
        fun fromJson(o: JSONObject) = Schedule(
            id = o.getString("id"),
            label = o.optString("label", "Quiet time"),
            days = o.getJSONArray("days").let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() },
            startMin = o.getInt("start"),
            endMin = o.getInt("end"),
            enabled = o.optBoolean("enabled", true),
        )
    }
}

data class Config(
    val allowedApps: Set<String> = DEFAULT_ALLOWED,
    val vips: List<Vip> = emptyList(),
    val repeatCallers: Boolean = true,
    val repeatWindowMin: Int = 15,
    val vipInGroups: Boolean = false,
    val alertVolume: Float = 0.8f,       // 0..1 of the alarm stream
    val vibrate: Boolean = true,
    val tileMinutes: Int = 60,           // 0 = until turned off
) {
    fun toJson(): JSONObject = JSONObject()
        .put("allowedApps", JSONArray(allowedApps.sorted()))
        .put("vips", JSONArray(vips.map { it.toJson() }))
        .put("repeatCallers", repeatCallers)
        .put("repeatWindowMin", repeatWindowMin)
        .put("vipInGroups", vipInGroups)
        .put("alertVolume", alertVolume.toDouble())
        .put("vibrate", vibrate)
        .put("tileMinutes", tileMinutes)

    companion object {
        val DEFAULT_ALLOWED = setOf(
            "com.Slack",
            "com.microsoft.teams",
            "com.microsoft.office.outlook",
        )

        fun fromJson(o: JSONObject) = Config(
            allowedApps = o.optJSONArray("allowedApps")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                ?: DEFAULT_ALLOWED,
            vips = o.optJSONArray("vips")?.let { a -> List(a.length()) { Vip.fromJson(a.getJSONObject(it)) } } ?: emptyList(),
            repeatCallers = o.optBoolean("repeatCallers", true),
            repeatWindowMin = o.optInt("repeatWindowMin", 15),
            vipInGroups = o.optBoolean("vipInGroups", false),
            alertVolume = o.optDouble("alertVolume", 0.8).toFloat(),
            vibrate = o.optBoolean("vibrate", true),
            tileMinutes = o.optInt("tileMinutes", 60),
        )
    }
}

enum class Kind { CALL, MESSAGE, APP }
enum class Reason { ALLOWED_APP, VIP, REPEAT_CALLER }

/** One thing that happened during a session, for the activity feed. */
data class Event(
    val at: Long,
    val pkg: String,
    val who: String,
    val through: Boolean,
    val kind: Kind,
    val reason: Reason? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("at", at).put("pkg", pkg).put("who", who).put("through", through)
        .put("kind", kind.name).put("reason", reason?.name ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = Event(
            at = o.getLong("at"),
            pkg = o.getString("pkg"),
            who = o.optString("who", ""),
            through = o.getBoolean("through"),
            kind = Kind.valueOf(o.getString("kind")),
            reason = if (o.isNull("reason")) null else Reason.valueOf(o.getString("reason")),
        )
    }
}

/** The running (or last) quiet session. endsAt == 0 means "until I turn it off". */
data class Session(
    val active: Boolean = false,
    val startedAt: Long = 0,
    val endsAt: Long = 0,
    val source: String = "manual",       // "manual" | "schedule:<id>" | "tile"
    val events: List<Event> = emptyList(),
) {
    val held get() = events.filter { !it.through }
    val through get() = events.filter { it.through }

    fun toJson(): JSONObject = JSONObject()
        .put("active", active).put("startedAt", startedAt).put("endsAt", endsAt)
        .put("source", source).put("events", JSONArray(events.map { it.toJson() }))

    companion object {
        const val MAX_EVENTS = 400

        fun fromJson(o: JSONObject) = Session(
            active = o.optBoolean("active", false),
            startedAt = o.optLong("startedAt", 0),
            endsAt = o.optLong("endsAt", 0),
            source = o.optString("source", "manual"),
            events = o.optJSONArray("events")?.let { a -> List(a.length()) { Event.fromJson(a.getJSONObject(it)) } } ?: emptyList(),
        )
    }
}
