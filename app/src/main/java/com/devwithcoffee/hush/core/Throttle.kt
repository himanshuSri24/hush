package com.devwithcoffee.hush.core

/**
 * "Don't chime twice for the same sender within [windowMs]" (WhatsApp bursts).
 * Only an alert that actually sounded starts the window; a suppressed one never extends it.
 */
class Throttle(private val windowMs: Long) {
    private val last = HashMap<String, Long>()

    @Synchronized
    fun allow(key: String, now: Long): Boolean {
        val prev = last[key]
        if (prev != null && now - prev < windowMs) return false
        last[key] = now
        if (last.size > 200) last.entries.removeAll { now - it.value > windowMs }
        return true
    }
}
