package com.wswensw.woolbox.record.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RecordsOperationCoordinator {
    private val mutex = Mutex()

    fun refresh(scope: CoroutineScope, action: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (!mutex.tryLock()) return@launch
            try {
                action()
            } finally {
                mutex.unlock()
            }
        }
    }

    suspend fun <T> writeAndRefresh(
        write: suspend () -> T,
        onWritten: (T) -> Unit = {},
        refresh: suspend () -> Unit,
    ): T = mutex.withLock {
        val result = write()
        onWritten(result)
        refresh()
        result
    }
}
