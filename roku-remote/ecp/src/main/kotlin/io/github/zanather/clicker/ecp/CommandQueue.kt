package io.github.zanather.clicker.ecp

import kotlinx.coroutines.CoroutineScope

/**
 * Runs commands for one TV strictly one at a time, in submission order, on [scope].
 *
 * Ordering matters: "Down, Down, Select" must not arrive as "Down, Select, Down". Capacity is small on
 * purpose: if the TV stalls, a burst of stale presses replayed seconds later is worse than dropping them.
 *
 * @param onFailure called (on [scope]) when a command throws an [EcpException]; the queue keeps running.
 */
class CommandQueue(
    scope: CoroutineScope,
    capacity: Int = 16,
    private val onFailure: (EcpException) -> Unit,
) {
    /** Enqueues without waiting. Returns false, dropping the command, when the queue is full or closed. */
    fun offer(command: suspend () -> Unit): Boolean = TODO()

    /**
     * Enqueues and suspends until the command has run, behind anything already queued.
     * Used to pace hold-to-repeat so repeats never pile up faster than the TV accepts them.
     * Rethrows the command's [EcpException] (also reported to [onFailure]).
     */
    suspend fun run(command: suspend () -> Unit): Unit = TODO()

    /** Stops accepting commands; queued ones are discarded. */
    fun close(): Unit = TODO()
}
