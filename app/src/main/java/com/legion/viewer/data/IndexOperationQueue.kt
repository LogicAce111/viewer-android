package com.legion.viewer.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/** Owned by the UI thread. Cancellation alone cannot serialize a blocking document-provider query. */
class IndexOperationQueue(private val scope: CoroutineScope) {
    private val jobs = mutableMapOf<String, Job>()
    private val locks = mutableMapOf<String, Mutex>()

    fun isActive(category: String): Boolean = jobs[category]?.isActive == true

    fun replace(category: String, action: suspend () -> Unit): Job {
        jobs[category]?.cancel()
        val lock = locks.getOrPut(category) { Mutex() }
        return scope.launch {
            lock.withLock {
                coroutineContext.ensureActive()
                action()
            }
        }.also { jobs[category] = it }
    }
}
