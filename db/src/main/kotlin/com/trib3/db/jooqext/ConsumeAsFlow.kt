package com.trib3.db.jooqext

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.jooq.Record
import org.jooq.ResultQuery
import kotlin.coroutines.CoroutineContext

private val log = KotlinLogging.logger {}
private const val DELAY_TIME = 100L

/**
 * Convert a [ResultQuery] to a [Flow], ensuring that the underlying cursor gets closed on completion,
 * and that the query is cancelled when the flow consuming coroutine is cancelled.
 *
 * Will run [ResultQuery.fetchLazy] in a new coroutine launched with the passed [coroutineContext].
 * Will run an additional coroutine launched with the passed [coroutineContext] that monitors the
 * fetch-ing coroutine for cancellation, and calls [ResultQuery.cancel] when that occurs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T : Record> ResultQuery<T>.consumeAsFlow(coroutineContext: CoroutineContext = Dispatchers.IO): Flow<T> {
    val query = this
    return flow {
        coroutineScope {
            val fetchJob =
                async(coroutineContext) {
                    query.fetchLazy()
                }
            launch(coroutineContext, CoroutineStart.ATOMIC) {
                // wait until the fetch job is cancelled or complete before doing anything
                log.trace("fetch monitor awaiting job cancellation or completion")
                val cancelException =
                    try {
                        while (fetchJob.isActive) {
                            delay(DELAY_TIME)
                        }
                        null
                    } catch (e: CancellationException) {
                        e
                    }
                log.trace("fetch monitor awaiting job completion")
                // wait until the fetch job is actually complete, cancelling the query to speed its completion
                while (!fetchJob.isCompleted) {
                    query.cancel()
                    delay(DELAY_TIME)
                }
                log.trace("fetch monitor job finished, throwing $cancelException")
                if (cancelException != null) {
                    throw cancelException
                }
            }
            val cursor = fetchJob.await()
            try {
                for (i in cursor) {
                    emit(i)
                }
            } finally {
                log.debug("Closing cursor")
                cursor.close()
            }
        }
    }
}
