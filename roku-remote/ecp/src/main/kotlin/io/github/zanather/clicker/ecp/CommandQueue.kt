package io.github.zanather.clicker.ecp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Runs commands for one TV strictly one at a time, in submission order, on [scope].
 *
 * Ordering matters: "Down, Down, Select" must not arrive as "Down, Select, Down". Capacity is small on
 * purpose: if the TV stalls, a burst of stale presses replayed seconds later is worse than dropping them.
 *
 * The queue also closes itself when [scope] is cancelled.
 *
 * @param onFailure called (on [scope]) when a command throws an [EcpException]; the queue keeps running.
 */
class CommandQueue(
    scope: CoroutineScope,
    capacity: Int = 16,
    private val onFailure: (EcpException) -> Unit,
) {
    /** [done] is null for [offer]ed commands, which nobody waits for. */
    private class Entry(val command: suspend () -> Unit, val done: CompletableDeferred<Unit>?)

    init {
        require(capacity >= 0) { "capacity must not be negative: $capacity" }
    }

    // Entries that never reach the consumer (discarded by close, or a cancelled send) release their run() callers.
    private val channel = Channel<Entry>(capacity) { it.done?.cancel(CancellationException(CLOSED)) }

    init {
        scope.launch {
            for (entry in channel) process(entry)
        }.invokeOnCompletion { close() }
    }

    /**
     * Enqueues without waiting. Returns false, dropping the command, when the queue is full or closed.
     *
     * A command that throws something other than [EcpException] or [CancellationException] is a bug:
     * it fails the queue's coroutine in [scope], which also closes the queue.
     */
    fun offer(command: suspend () -> Unit): Boolean = channel.trySend(Entry(command, null)).isSuccess

    /**
     * Enqueues and suspends until the command has run, behind anything already queued.
     * Used to pace hold-to-repeat so repeats never pile up faster than the TV accepts them.
     * Rethrows the command's [EcpException] (also reported to [onFailure]).
     *
     * Suspends while the queue is full. Throws [CancellationException] when the queue is closed, or closes
     * before the command starts. If the caller is cancelled before the command starts, it never runs.
     * Any other exception from the command is rethrown here as well, and the queue keeps running.
     */
    suspend fun run(command: suspend () -> Unit) {
        val done = CompletableDeferred<Unit>()
        try {
            // close() cancels the channel, so a closed queue fails this send with CancellationException.
            channel.send(Entry(command, done))
            done.await()
        } catch (e: CancellationException) {
            // Marks the entry as skipped if the consumer hasn't reached it yet; no-op once it has run.
            done.cancel(e)
            throw e
        }
    }

    /** Stops accepting commands; queued ones are discarded. */
    fun close() {
        // Unlike close(), cancel() also drops buffered entries.
        channel.cancel(CancellationException(CLOSED))
    }

    private suspend fun process(entry: Entry) {
        val done = entry.done
        if (done != null && done.isCompleted) return // Its run() caller gave up before it started.
        try {
            entry.command()
            done?.complete(Unit)
        } catch (e: EcpException) {
            try {
                onFailure(e)
            } finally {
                done?.completeExceptionally(e)
            }
        } catch (e: CancellationException) {
            done?.cancel(e)
            // Stop if the queue itself is being cancelled; a command that cancelled itself
            // (its own withTimeout, say) mustn't take the queue down with it.
            currentCoroutineContext().ensureActive()
        } catch (e: Throwable) {
            // A bug in the command: surface it to whoever waits for it, or else fail on the scope.
            if (done == null) throw e
            done.completeExceptionally(e)
        }
    }

    private companion object {
        const val CLOSED = "CommandQueue closed"
    }
}
