package com.wswensw.woolbox.record.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordsOperationCoordinatorTest {
    @Test
    fun writeWaitsForCurrentRefreshAndRunsFreshRefresh() = runBlocking {
        val coordinator = RecordsOperationCoordinator()
        val firstRefreshStarted = CompletableDeferred<Unit>()
        val finishFirstRefresh = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        coordinator.refresh(this) {
            events += "refresh-1-start"
            firstRefreshStarted.complete(Unit)
            finishFirstRefresh.await()
            events += "refresh-1-end"
        }
        firstRefreshStarted.await()
        coordinator.refresh(this) { events += "redundant-refresh" }

        val write = async {
            coordinator.writeAndRefresh(
                write = {
                    events += "write"
                    42
                },
                onWritten = { events += "written" },
                refresh = { events += "refresh-2" },
            )
        }
        finishFirstRefresh.complete(Unit)

        assertEquals(42, write.await())
        assertEquals(
            listOf("refresh-1-start", "refresh-1-end", "write", "written", "refresh-2"),
            events,
        )
    }
}
