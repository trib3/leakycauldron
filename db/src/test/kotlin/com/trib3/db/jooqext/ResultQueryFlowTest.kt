package com.trib3.db.jooqext

import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.easymock.EasyMock
import org.jooq.Cursor
import org.jooq.Record
import org.jooq.ResultQuery
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testng.annotations.Test
import java.util.concurrent.CountDownLatch

class ResultQueryFlowTest {
    val expectedData = (1..4).toList()

    val mockData = expectedData.map {
        DSL.using(SQLDialect.DEFAULT).newRecord(DSL.field("value")).values(it)
    }.toMutableList()

    @Test
    fun testCollect() {
        val query = EasyMock.mock<ResultQuery<out Record>>(ResultQuery::class.java)!!
        val cursor = EasyMock.mock<Cursor<out Record>>(Cursor::class.java)!!
        EasyMock.expect(query.fetchLazy()).andReturn(cursor).once()
        EasyMock.expect(cursor.iterator()).andReturn(mockData.iterator()).once()
        EasyMock.expect(cursor.close()).once()
        EasyMock.replay(query, cursor)
        runBlocking {
            val list = query.consumeAsFlow().map { it["value"] as Int }.toList()
            assertThat(list).isEqualTo(expectedData)
        }
        EasyMock.verify(query, cursor)
    }

    @Test
    fun testCancelDuringIteration() {
        val latch = CountDownLatch(1)
        val query = EasyMock.mock<ResultQuery<out Record>>(ResultQuery::class.java)!!
        val cursor = EasyMock.mock<Cursor<out Record>>(Cursor::class.java)!!
        EasyMock.expect(query.fetchLazy()).andReturn(cursor).once()
        EasyMock.expect(cursor.iterator()).andReturn(mockData.iterator()).once()
        EasyMock.expect(cursor.close()).once()
        EasyMock.replay(query, cursor)
        val list = mutableListOf<Int>()
        runBlocking {
            val collectJob = launch {
                query.consumeAsFlow().map { it["value"] as Int }.collect {
                    // collect the first element and signal a value returned
                    list.add(it)
                    latch.countDown()
                    delay(100000) // be "slow" so that only one element gets collected
                }
            }
            launch(Dispatchers.IO) {
                latch.await() // wait for a value returned
                collectJob.cancel()
            }
        }
        assertThat(list).isEqualTo(expectedData.subList(0, 1))
        EasyMock.verify(query, cursor)
    }

    @Test
    fun testCancelBeforeIteration() {
        val cancelLatch = CountDownLatch(1)
        val iterateLatch = CountDownLatch(1)
        val query = EasyMock.mock<ResultQuery<out Record>>(ResultQuery::class.java)!!
        // allow mockQuery to be invoked from multiple threads so that cancel()
        // can be called when fetchLazy() is executing
        EasyMock.makeThreadSafe(query, false)
        EasyMock.expect(query.fetchLazy()).andAnswer {
            iterateLatch.countDown() // signal collection has started
            cancelLatch.await() // await cancellation
            throw RuntimeException("cancelled")
        }.once()
        EasyMock.expect(query.cancel()).andAnswer {
            cancelLatch.countDown() // signal cancellation
        }
        EasyMock.replay(query)
        val list = mutableListOf<Int>()
        runBlocking {
            val collectJob = launch {
                assertThat {
                    query.consumeAsFlow().map { it["value"] as Int }.toList(list)
                }.isFailure().isInstanceOf(RuntimeException::class.java).hasMessage("cancelled")
            }
            launch {
                // delay(200)
                iterateLatch.await() // wait for collection to start
                collectJob.cancel()
            }
            collectJob.join()
        }
        assertThat(list).isEmpty()
        EasyMock.verify(query)
    }
}
