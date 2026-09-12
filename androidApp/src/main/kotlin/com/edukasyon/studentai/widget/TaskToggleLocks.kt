package com.edukasyon.studentai.widget

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-task locks so rapid taps (spam on/off) serialize the DB
 * read-modify-write instead of racing. Without this, two taps that both
 * read PENDING before either writes would both write COMPLETED (lost toggle).
 */
object TaskToggleLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun forId(taskId: String): Mutex =
        locks.getOrPut(taskId) { Mutex() }
}
