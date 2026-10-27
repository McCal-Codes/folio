package com.mccal.folio

/**
 * An access-ordered map that keeps only the [capacity] most recently used entries, safe to use from two threads.
 *
 * A LinkedHashMap in access order reorders itself on every read, so even `get` changes it. The Dynamic Island's two memories
 * were read and written on its own worker thread while a notification being swiped away removed from one on the main thread; at the same
 * moment that corrupts the map (ConcurrentModificationException, a null, or a loop that never ends). Every call here takes the same lock.
 */
internal class RecentMap<K, V>(private val capacity: Int) {
    private val map = object : LinkedHashMap<K, V>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > capacity
    }

    @Synchronized operator fun get(key: K): V? = map[key]
    @Synchronized operator fun set(key: K, value: V) { map[key] = value }
    @Synchronized fun remove(key: K): V? = map.remove(key)
    @Synchronized fun size(): Int = map.size
}
