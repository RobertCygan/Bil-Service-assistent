package app.servicepassport.lookup

import java.time.Instant
import java.time.ZoneOffset

/** Limit zapytań w stałym oknie czasu, osobno dla każdego klucza. Pamięć jest ograniczona [maxKeys]. */
class WindowLimiter(private val limit: Int, private val windowSeconds: Long, private val maxKeys: Int = 100_000) {
    private class Counter(var window: Long, var count: Int)

    private val counters = HashMap<String, Counter>()

    @Synchronized
    fun allow(key: String, now: Instant): Boolean {
        val window = now.epochSecond / windowSeconds
        if (counters.size >= maxKeys) counters.values.removeIf { it.window != window }
        if (counters.size >= maxKeys) return false // pod zalewem nowych adresów wolimy odmówić niż rosnąć bez końca
        val counter = counters.getOrPut(key) { Counter(window, 0) }
        if (counter.window != window) { counter.window = window; counter.count = 0 }
        if (counter.count >= limit) return false
        counter.count++
        return true
    }
}

/** Dzienny budżet zapytań do rejestru (doba UTC). */
class DailyBudget(private val limit: Int) {
    private var day = -1L
    private var used = 0

    @Synchronized
    fun take(now: Instant): Boolean {
        val today = now.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
        if (today != day) { day = today; used = 0 }
        if (used >= limit) return false
        used++
        return true
    }
}

/** Prosty cache w pamięci z czasem życia; najstarsze wpisy wypadają po przekroczeniu rozmiaru. */
class TtlCache(private val ttlSeconds: Long, private val maxEntries: Int) {
    private class Entry(val value: String, val expires: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > maxEntries
    }

    @Synchronized
    fun get(key: String, now: Instant): String? {
        if (ttlSeconds <= 0) return null
        val entry = entries[key] ?: return null
        if (entry.expires <= now.epochSecond) { entries.remove(key); return null }
        return entry.value
    }

    @Synchronized
    fun put(key: String, value: String, now: Instant) {
        if (ttlSeconds > 0) entries[key] = Entry(value, now.epochSecond + ttlSeconds)
    }
}
