package io.github.zanather.clicker.ecp

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
class CommandQueueTest {
    private val address = RokuAddress("192.168.1.5")
    private val failures = Collections.synchronizedList(mutableListOf<EcpException>())
    private val scopes = mutableListOf<CoroutineScope>()

    /**
     * The queue's own scope on the test scheduler. Not backgroundScope: advanceUntilIdle() skips background
     * work. Not a child of the test either, or runTest would wait forever for the consumer.
     */
    private fun TestScope.queueScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)).also { scopes += it }

    private fun TestScope.newQueue(capacity: Int = 16) = CommandQueue(queueScope(), capacity) { failures += it }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun `offered commands run one at a time in order`() = runTest {
        val queue = newQueue()
        val ran = mutableListOf<Int>()
        var active = 0
        var maxActive = 0
        for (i in 0 until 10) {
            assertThat(
                queue.offer {
                    active++
                    maxActive = maxOf(maxActive, active)
                    delay((10 - i) * 10L) // Earlier commands are slower; order must still hold.
                    ran += i
                    active--
                },
            ).isTrue()
        }
        advanceUntilIdle()
        assertThat(ran).isEqualTo((0 until 10).toList())
        assertThat(maxActive).isEqualTo(1)
    }

    @Test
    fun `concurrent offers keep each producer's order and never overlap`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val producers = 4
        val perProducer = 200
        val queue = CommandQueue(scope, capacity = producers * perProducer) { failures += it }
        val ran = Collections.synchronizedList(mutableListOf<Pair<Int, Int>>())
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val start = CountDownLatch(1)
        val threads = (0 until producers).map { producer ->
            thread {
                start.await()
                for (seq in 0 until perProducer) {
                    check(
                        queue.offer {
                            maxActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                            if (seq % 50 == 0) delay(1)
                            ran += producer to seq
                            active.decrementAndGet()
                        },
                    )
                }
            }
        }
        start.countDown()
        threads.forEach { it.join(TimeUnit.SECONDS.toMillis(10)) }
        withTimeout(10_000) { queue.run { } } // Everything offered before this has run.

        assertThat(ran).hasSize(producers * perProducer)
        for (producer in 0 until producers) {
            assertThat(ran.filter { it.first == producer }.map { it.second }).isEqualTo((0 until perProducer).toList())
        }
        assertThat(maxActive.get()).isEqualTo(1)
        scope.cancel()
    }

    @Test
    fun `offer returns false when the queue is full`() = runTest {
        val queue = newQueue(capacity = 2)
        val gate = CompletableDeferred<Unit>()
        val ran = mutableListOf<Int>()
        assertThat(queue.offer { gate.await(); ran += 1 }).isTrue()
        runCurrent() // The consumer takes command 1 and blocks on the gate.
        assertThat(queue.offer { ran += 2 }).isTrue()
        assertThat(queue.offer { ran += 3 }).isTrue()
        assertThat(queue.offer { ran += 4 }).isFalse()
        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(ran).containsExactly(1, 2, 3).inOrder()
        assertThat(queue.offer { ran += 5 }).isTrue()
        advanceUntilIdle()
        assertThat(ran).containsExactly(1, 2, 3, 5).inOrder()
    }

    @Test
    fun `run waits behind queued commands and returns once its command has run`() = runTest {
        val queue = newQueue()
        val ran = mutableListOf<String>()
        queue.offer { delay(100); ran += "offered" }
        queue.run { delay(50); ran += "run" }
        assertThat(ran).containsExactly("offered", "run").inOrder()
        assertThat(currentTime).isEqualTo(150)
    }

    @Test
    fun `run rethrows the command's EcpException after reporting it`() = runTest {
        val queue = newQueue()
        val error = EcpException.Http(address, "keypress/Home", 500)
        var thrown: Throwable? = null
        try {
            queue.run { throw error }
        } catch (e: EcpException) {
            thrown = e
        }
        assertThat(thrown).isInstanceOf(EcpException.Http::class.java)
        assertThat((thrown as EcpException.Http).code).isEqualTo(500)
        assertThat(failures).containsExactly(error)
    }

    @Test
    fun `the queue keeps running after a failure`() = runTest {
        val queue = newQueue()
        val ran = mutableListOf<Int>()
        queue.offer { ran += 1 }
        queue.offer { throw EcpException.Forbidden(address, "keypress/Home", limitedMode = true) }
        queue.offer { ran += 3 }
        advanceUntilIdle()
        assertThat(ran).containsExactly(1, 3).inOrder()
        assertThat(failures).hasSize(1)
        assertThat((failures.single() as EcpException.Forbidden).limitedMode).isTrue()

        queue.run { ran += 4 }
        assertThat(ran).containsExactly(1, 3, 4).inOrder()
    }

    @Test
    fun `close discards queued commands, cancels their run callers and is idempotent`() = runTest {
        val queue = newQueue()
        val gate = CompletableDeferred<Unit>()
        val ran = mutableListOf<Int>()
        queue.offer { gate.await(); ran += 1 }
        queue.offer { ran += 2 }
        val waiting = async { queue.run { ran += 3 } }
        runCurrent()

        queue.close()
        queue.close()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(ran).containsExactly(1) // The command already running finishes; the queued ones never start.
        assertThat(waiting.isCancelled).isTrue()
        assertThat(queue.offer { ran += 4 }).isFalse()

        var afterClose: Throwable? = null
        try {
            queue.run { ran += 5 }
        } catch (e: CancellationException) {
            afterClose = e
        }
        assertThat(afterClose).isNotNull()
        advanceUntilIdle()
        assertThat(ran).containsExactly(1)
        assertThat(failures).isEmpty()
    }

    @Test
    fun `discardOffered skips waiting offered commands but not run callers or later offers`() = runTest {
        val queue = newQueue()
        val gate = CompletableDeferred<Unit>()
        val ran = mutableListOf<Int>()
        queue.offer { gate.await(); ran += 1 }
        runCurrent() // The consumer takes command 1 and blocks on the gate.
        queue.offer { ran += 2 }
        val waiting = async { queue.run { ran += 3 } }
        runCurrent()
        queue.offer { ran += 4 }

        queue.discardOffered()
        queue.offer { ran += 5 }
        gate.complete(Unit)
        waiting.await()
        advanceUntilIdle()

        // 1 was already running; 2 and 4 were discarded.
        assertThat(ran).containsExactly(1, 3, 5).inOrder()
    }

    @Test
    fun `discarding from onFailure skips everything queued behind the failed command`() = runTest {
        lateinit var queue: CommandQueue
        queue = CommandQueue(queueScope()) { failures += it; queue.discardOffered() }
        val ran = mutableListOf<Int>()
        queue.offer { throw EcpException.Unreachable(address, IOException("down")) }
        queue.offer { ran += 2 }
        queue.offer { ran += 3 }
        advanceUntilIdle()

        assertThat(ran).isEmpty()
        assertThat(failures).hasSize(1)
    }

    @Test
    fun `a run caller cancelled before its command starts skips the command`() = runTest {
        val queue = newQueue()
        val gate = CompletableDeferred<Unit>()
        val ran = mutableListOf<Int>()
        queue.offer { gate.await(); ran += 1 }
        val caller = launch { queue.run { ran += 2 } }
        runCurrent()
        caller.cancel()
        queue.offer { ran += 3 }
        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(ran).containsExactly(1, 3).inOrder()
        assertThat(caller.isCancelled).isTrue()
    }

    @Test
    fun `a run caller cancelled after its command starts lets it finish`() = runTest {
        val queue = newQueue()
        val ran = mutableListOf<Int>()
        val caller = launch { queue.run { delay(100); ran += 1 } }
        runCurrent()
        caller.cancel()
        advanceUntilIdle()
        assertThat(ran).containsExactly(1)
        assertThat(caller.isCancelled).isTrue()
    }

    @Test
    fun `a run caller waiting on a full queue gets in once there is room`() = runTest {
        val queue = newQueue(capacity = 1)
        val gate = CompletableDeferred<Unit>()
        val ran = mutableListOf<Int>()
        queue.offer { gate.await(); ran += 1 }
        runCurrent()
        queue.offer { ran += 2 }
        val waiting = async { queue.run { ran += 3 } }
        runCurrent()
        assertThat(waiting.isCompleted).isFalse()
        gate.complete(Unit)
        waiting.await()
        assertThat(ran).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `a bug in a run command reaches its caller and the queue survives`() = runTest {
        val queue = newQueue()
        var thrown: Throwable? = null
        try {
            queue.run { error("bug") }
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertThat(thrown).hasMessageThat().isEqualTo("bug")
        assertThat(failures).isEmpty()
        val ran = mutableListOf<Int>()
        queue.run { ran += 1 }
        assertThat(ran).containsExactly(1)
    }

    @Test
    fun `a bug in an offered command fails the scope and closes the queue`() = runTest {
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, e -> errors += e },
        )
        val queue = CommandQueue(scope) { failures += it }
        queue.offer { error("bug") }
        advanceUntilIdle()
        assertThat(errors.single()).hasMessageThat().isEqualTo("bug")
        assertThat(queue.offer { }).isFalse()
        scope.cancel()
    }

    @Test
    fun `a command that cancels itself doesn't stop the queue`() = runTest {
        val queue = newQueue()
        val ran = mutableListOf<Int>()
        queue.offer { withTimeout(10) { delay(1_000) } }
        queue.offer { ran += 2 }
        advanceUntilIdle()
        assertThat(ran).containsExactly(2)
        assertThat(failures).isEmpty()
    }

    @Test
    fun `cancelling the scope closes the queue`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val queue = CommandQueue(scope) { failures += it }
        val gate = CompletableDeferred<Unit>()
        queue.offer { gate.await() }
        val waiting = async { queue.run { } }
        runCurrent()
        scope.cancel()
        advanceUntilIdle()
        assertThat(waiting.isCancelled).isTrue()
        assertThat(queue.offer { }).isFalse()
    }
}
