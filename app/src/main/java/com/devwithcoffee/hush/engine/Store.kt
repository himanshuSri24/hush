package com.devwithcoffee.hush.engine

import android.content.Context
import android.content.SharedPreferences
import com.devwithcoffee.hush.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** All persisted state. Survives process death; the listener and UI read the same prefs. */
object Store {
    private const val PREFS = "hush"
    private lateinit var prefs: SharedPreferences

    private val _config = MutableStateFlow(Config())
    private val _session = MutableStateFlow(Session())
    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    private val _lastSession = MutableStateFlow<Session?>(null)

    val config: StateFlow<Config> get() = _config
    val session: StateFlow<Session> get() = _session
    val schedules: StateFlow<List<Schedule>> get() = _schedules
    val lastSession: StateFlow<Session?> get() = _lastSession

    @Synchronized
    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _config.value = prefs.getString("config", null)?.let { runCatching { Config.fromJson(JSONObject(it)) }.getOrNull() } ?: Config()
        _session.value = prefs.getString("session", null)?.let { runCatching { Session.fromJson(JSONObject(it)) }.getOrNull() } ?: Session()
        _lastSession.value = prefs.getString("last", null)?.let { runCatching { Session.fromJson(JSONObject(it)) }.getOrNull() }
        _schedules.value = prefs.getString("schedules", null)?.let { s ->
            runCatching { JSONArray(s).let { a -> List(a.length()) { Schedule.fromJson(a.getJSONObject(it)) } } }.getOrNull()
        } ?: emptyList()
    }

    @Synchronized
    fun updateConfig(f: (Config) -> Config) {
        val c = f(_config.value); _config.value = c
        prefs.edit().putString("config", c.toJson().toString()).apply()
    }

    @Synchronized
    fun setSession(s: Session) {
        _session.value = s
        prefs.edit().putString("session", s.toJson().toString()).commit()
    }

    @Synchronized
    fun addEvent(e: Event) {
        val s = _session.value
        if (!s.active) return
        setSession(s.copy(events = (s.events + e).takeLast(Session.MAX_EVENTS)))
    }

    @Synchronized
    fun archive(s: Session) {
        _lastSession.value = s
        prefs.edit().putString("last", s.toJson().toString()).apply()
    }

    @Synchronized
    fun updateSchedules(f: (List<Schedule>) -> List<Schedule>) {
        val l = f(_schedules.value); _schedules.value = l
        prefs.edit().putString("schedules", JSONArray(l.map { it.toJson() }).toString()).apply()
    }

    /** The schedule window the user ended early, so reconcile() won't restart it. */
    var dismissedWindowEnd: Long
        get() = prefs.getLong("dismissedWindowEnd", 0)
        set(v) { prefs.edit().putLong("dismissedWindowEnd", v).apply() }

    /** Interruption filter to restore on Android < 15 (on 15+ the system scopes our rule). */
    var previousFilter: Int
        get() = prefs.getInt("prevFilter", 1)
        set(v) { prefs.edit().putInt("prevFilter", v).apply() }

    var savedAlarmVolume: Int
        get() = prefs.getInt("savedAlarmVol", -1)
        set(v) { prefs.edit().putInt("savedAlarmVol", v).apply() }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) { prefs.edit().putBoolean("onboarded", v).apply() }
}
